import UserNotifications
import PhoenixShared
import Combine
import notify

fileprivate let filename = "NotificationService"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/**
 * What happens if multiple push notifications arrive ?
 *
 * iOS will launch the notification-service-extension upon receiving the first push notification.
 * Subsequent push notifications are queued by the OS. After the app extension finishes processing
 * the first notification (by invoking the `contentHandler`), then iOS will:
 *
 * - display the first push notification
 * - dealloc the `UNNotificationServiceExtension`
 * - Initialize a new `UNNotificationServiceExtension` instance
 * - And invoke it's `didReceive(_:)` function with the next item in the queue
 *
 * Note that it does **NOT** create a new app extension process.
 * It re-uses the existing process, and launches a new `UNNotificationServiceExtension` within it.
 *
 * This means that the following instances are recycled (continue existing in memory):
 * - PhoenixManager.shared
 * - NotificationServiceQueue.shared
 * - XPC.shared
 */
class NotificationService: UNNotificationServiceExtension {
	
	private var contentHandler: ((UNNotificationContent) -> Void)?
	private var remoteNotificationContent: UNMutableNotificationContent?
	
	private var xpcStarted: Bool = false
	private var phoenixStarted: Bool = false
	private var srvExtDone: Bool = false
	
	private var business: PhoenixBusiness? = nil
	private var pushNotification: PushNotification? = nil
	private var targetNodeIdHash: String? = nil
	
	private var isConnectedToPeer = false
	private var receivedPayments: [Lightning_kmpIncomingPayment] = []
	
	private var withdrawRequestResult: Result<WithdrawRequestStatus, WithdrawRequestError>? = nil
	private var withdrawResponseSent: Bool = false
	private var sentPayment: Lightning_kmpLightningOutgoingPayment? = nil

	private var totalTimer: Timer? = nil
	private var connectionTimer: Timer? = nil
	private var postReceivedPaymentTimer: Timer? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	override func didReceive(
		_ request: UNNotificationRequest,
		withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
	) {
		let selfPtr: String = Unmanaged.passUnretained(self).toOpaque().debugDescription
		
		log.trace("instance => \(selfPtr)")
		log.trace("didReceive(request:withContentHandler:)")
		log.trace("request.content.userInfo: \(request.content.userInfo)")
		
		self.contentHandler = contentHandler
		self.remoteNotificationContent = (request.content.mutableCopy() as? UNMutableNotificationContent)
		
		// IMPORTANT: This function is called on a NON-main thread.
		// 
		// And Kotlin-KMM has an unworkable threading policy that requires
		// everything to be on the main thread...
		DispatchQueue.main.async {
			
			self.startTotalTimer()
			self.processNotification(request.content.userInfo)
			self.startXpc()
			self.startPhoenix()
		}
	}
	
	override func serviceExtensionTimeWillExpire() {
		log.trace("serviceExtensionTimeWillExpire()")
		
		// iOS calls this function just before the extension will be terminated by the system.
		// Use this as an opportunity to deliver your "best attempt" at modified content,
		// otherwise the original push payload will be used.
		
		// IMPORTANT: This function is called on a NON-main thread.
		DispatchQueue.main.async {
			self.finish()
		}
	}
	
	// --------------------------------------------------
	// MARK: Notification Processing
	// --------------------------------------------------
	
	private func processNotification(_ userInfo: [AnyHashable : Any]) {
		log.trace("processNotification()")
		assertMainThread()
		
		// This could be a push notification coming from either:
		// - Google's Firebase Cloud Messaging (FCM)
		// - Amazon Web Services (AWS) (only used for debugging)
		
		if let notification = PushNotification.parse(userInfo) {
			
			self.pushNotification = notification
			switch notification {
			case .fcm(let notification):
				processNotification_fcm(notification)
			case .lnurlWithdraw(let notification):
				Task {
					await processNotification_lnurlWithdraw(notification)
				}
			}
			
		} else {
			
			log.warning("processNotification(): Failed to parse userInfo as PushNotification")
			finish()
		}
	}
	
	private func processNotification_fcm(_ notification: FcmPushNotification) {
		log.trace(#function)
		
		targetNodeIdHash = notification.nodeIdHash
	}
	
	@MainActor
	private func processNotification_lnurlWithdraw(
		_ request: LnurlWithdrawNotification
	) async {
		log.trace(#function)
		
		guard let business else {
			log.warning("\(#function): business is nil")
			return
		}
		
		let reject = { @MainActor (error: WithdrawRequestError) async -> Void in
		
			// Stop other processing
			self.stopPhoenix()
			self.stopXpc()
			
			// Send the response to the merchant
			let _ = await request.postResponse(errorReason: error.description)
			
			// And finally, display notification to the user
			self.finish()
		}
		
		let result = await business.checkWithdrawRequest(request.toWithdrawRequest())
		withdrawRequestResult = result
		
		switch result {
		case .failure(let error):
			log.error("\(#function): error: \(error.description)")
			await reject(error)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere:
				log.warning("\(#function): abort: handled elsewhere")
				finish()
			
			case .continueAndSendPayment(let card, _, _):
				log.debug("\(#function): continue: send payment")
				
				guard
					let peer = business.peerManager.peerStateValue(),
					let defaultTrampolineFees = peer.walletParams.trampolineFees.first
				else {
					return await reject(.internalError(card: card, details: "peer is nil"))
				}
				
				// Send the payment
				do {
					try await business.sendManager.payBolt11Invoice(
						amountToSend   : request.invoiceAmount,
						trampolineFees : defaultTrampolineFees,
						invoice        : request.invoice,
						metadata       : WalletPaymentMetadata.withCard(card.id)
					)
				} catch {
					log.error("SendManager.payBolt11Invoice(): threw error: \(error)")
					return await reject(.internalError(card: card, details: "payBolt11Invoice failed"))
				}
				
				// We have 2 tasks to finish before we're done:
				// 1). Send the response to the merchant
				// 2). Wait for our outgoing payment to complete
				//
				// We can perform these in parallel.
				
				Task { @MainActor in
					let _ = await request.postResponse(errorReason: nil)
					
					self.withdrawResponseSent = true
					log.debug("withdrawResponseSent = true")
					
					if self.sentPayment != nil {
						self.finish()
					}
				}.store(in: &cancellables)
				
				Task { @MainActor in
					for await payment in business.paymentsManager.lastCompletedPaymentSequence() {
						if let lnPayment = payment as? Lightning_kmpLightningOutgoingPayment,
						   let details = lnPayment.details as? Lightning_kmpLightningOutgoingPayment.DetailsNormal
						{
							if details.paymentHash == request.invoice.paymentHash {
								self.sentPayment = lnPayment
								log.debug("sentPayment = \(lnPayment)")
								
								if self.withdrawResponseSent {
									self.finish()
									break
								}
							}
						}
					}
				}.store(in: &cancellables)
				
			} // </switch status>
		} // </switch result>
	}
	
	// --------------------------------------------------
	// MARK: Timers
	// --------------------------------------------------
	
	private func startTotalTimer() {
		log.trace("startTotalTimer()")
		assertMainThread()
		
		guard !srvExtDone else {
			log.debug("startTotalTimer(): ignoring: srvExtDone")
			return
		}
		guard totalTimer == nil else {
			log.debug("startTotalTimer(): ignoring: already started")
			return
		}
		
		// The OS gives us 30 seconds to fetch data, and then invoke the completionHandler.
		// Failure to properly "clean up" in this way will result in the OS reprimanding us.
		// So we set a timer to ensure we stop before the max allowed.
		totalTimer = Timer.scheduledTimer(
			withTimeInterval : 29.5,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self {
				log.debug("totalTimer.fire()")
				self.finish()
			}
		}
	}
	
	private func startConnectionTimer() {
		log.trace("startConnectionTimer()")
		assertMainThread()
		
		guard !srvExtDone else {
			log.debug("startConnectionTimer(): ignoring: srvExtDone")
			return
		}
		guard connectionTimer == nil else {
			log.debug("startConnectionTimer(): ignoring: already started")
			return
		}
		guard let groupPrefs = PhoenixManager.shared.groupPrefs() else {
			log.debug("startConnectionTimer(): ignoring: groupPrefs is nil")
			return
		}
		
		log.debug("GroupPrefs.current.srvExtConnection = now")
		groupPrefs.srvExtConnection = Date.now
		
		connectionTimer = Timer.scheduledTimer(
			withTimeInterval : 2.0,
			repeats          : true
		) {[weak self](_: Timer) in
		
			if let _ = self {
				log.debug("connectionsTimer.fire()")
				log.debug("GroupPrefs.current.srvExtConnection = now")
				groupPrefs.srvExtConnection = Date.now
			}
		}
	}
	
	private func startPostReceivedPaymentTimer() {
		log.trace("startPostReceivedPaymentTimer()")
		assertMainThread()
		
		// This method is called everytime we receive a payment,
		// and it's possible we receive multiple payments.
		// So for every payment, we want to restart the timer.
		postReceivedPaymentTimer?.invalidate()
		
	#if DEBUG
		let delay: TimeInterval = 5.0
	#else
		let delay: TimeInterval = 5.0
	#endif
		
		postReceivedPaymentTimer = Timer.scheduledTimer(
			withTimeInterval : delay,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self {
				log.debug("postReceivedPaymentTimer.fire()")
				self.finish()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: XPC
	// --------------------------------------------------
	
	private func startXpc() {
		log.trace("startXpc()")
		assertMainThread()
		
		guard !srvExtDone else {
			log.debug("startXpc(): ignoring: srvExtDone")
			return
		}
		guard !xpcStarted else {
			log.debug("startXpc(): ignoring: already started")
			return
		}
		xpcStarted = true
		
		XPC.shared.receivedMessagePublisher.sink {[weak self](msg: XpcMessage) in
			self?.didReceiveXpcMessage(msg)
		}.store(in: &cancellables)
		
		XPC.shared.resume()
	}
	
	private func stopXpc() {
		log.trace("stopXpc()")
		assertMainThread()
		
		guard xpcStarted else {
			log.debug("stopXpc(): ignoring: already stopped")
			return
		}
		xpcStarted = false
		
		XPC.shared.suspend()
	}
	
	private func didReceiveXpcMessage(_ msg: XpcMessage) {
		log.trace("didReceiveXpcMessage()")
		assertMainThread()
		
		if msg == .available {
			
			// The main phoenix app is running.
			
			if isConnectedToPeer {
				
				// But we're already connected to the peer, and processing the payment.
				// So we're going to continue working on the payment,
				// and the main app will have to wait for us to finish before connecting to the peer itself.
				
				log.debug("isConnectedToPeer is true => continue processing incoming payment")
				
			} else {
				
				// Since we're not connected yet, we'll just allow the main app to handle the payment.
				//
				// And we don't have to wait for the main app to finish handling the payment.
				// Because whatever we emit from this app extension won't be displayed to the user.
				// That is, the modified push content we emit isn't actually shown to the user.
				
				finish()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Phoenix
	// --------------------------------------------------
	
	private func startPhoenix() {
		log.trace("startPhoenix()")
		assertMainThread()
		
		guard !srvExtDone else {
			log.debug("startPhoenix(): ignoring: srvExtDone")
			return
		}
		guard !phoenixStarted else {
			log.debug("startPhoenix(): ignoring: already started")
			return
		}
		phoenixStarted = true
		
		let newBusiness = PhoenixManager.shared.setupBusiness(targetNodeIdHash)
		business = newBusiness
		
		Task { @MainActor [newBusiness, weak self] in
			for await connections in newBusiness.connectionsManager.connectionsSequence() {
				self?.connectionsChanged(connections)
			}
		}.store(in: &cancellables)
			
		let pushReceivedAt = Date()
		Task { @MainActor [newBusiness, weak self] in
			for await payment in newBusiness.paymentsManager.lastIncomingPaymentSequence() {
				
				guard
					let paymentReceivedAt = payment.completedAtDate,
					paymentReceivedAt > pushReceivedAt
				else {
					// Ignoring - this is the most recently received incomingPayment, but not a new one
					return
				}

				self?.didReceivePayment(payment)
			}
		}.store(in: &cancellables)
		
		Task { @MainActor [newBusiness, weak self] in
			let peer = try await newBusiness.peerManager.getPeer()
			for await event in peer.eventsFlow {
				if let msg = event as? Lightning_kmp_coreCardPaymentRequestReceived {
					log.debug("found event: CardPaymentRequestReceived")
					
					if let cardRequest = CardRequest.fromOnionMessage(msg) {
						Task { @MainActor [weak self] in
							await self?.handleCardRequest(cardRequest)
						}
					} else {
						log.debug("CardRequest.fromOnionMessage() failed")
					}
				}
			}
			
		}.store(in: &cancellables)
	}
	
	private func stopPhoenix() {
		log.trace("stopPhoenix()")
		assertMainThread()
		
		guard phoenixStarted else {
			log.debug("stopPhoenix(): ignoring: already stopped")
			return
		}
		phoenixStarted = false
		
		PhoenixManager.shared.teardownBusiness()
		business = nil
	}
	
	private func connectionsChanged(_ connections: Connections) {
		log.trace("connectionsChanged(): isConnectedToPeer = \(connections.peer.isEstablished())")
		assertMainThread()
		
		isConnectedToPeer = connections.peer.isEstablished()
		if isConnectedToPeer && !srvExtDone {
			startConnectionTimer()
		}
	}
	
	private func didReceivePayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("didReceivePayment()")
		assertMainThread()
		
		receivedPayments.append(payment)
		if !srvExtDone {
			startPostReceivedPaymentTimer()
		}
	}
	
	@MainActor
	private func handleCardRequest(
		_ cardRequest: CardRequest
	) async {
		log.trace(#function)
		
		guard let business else {
			log.warning("handleCardRequest: business is nil")
			return
		}
		
		let reject = { @MainActor (error: WithdrawRequestError) async -> Void in
		
			// Stop other processing
			self.stopPhoenix()
			self.stopXpc()
			
			// Display notification to the user
			self.finish()
		}
		
		let result = await business.checkWithdrawRequest(cardRequest.toWithdrawRequest())
		withdrawRequestResult = result
		
		switch result {
		case .failure(let error):
			log.error("\(#function): error: \(error.description)")
			
			// Send error message to merchant
			do {
				let peer = try await business.peerManager.getPeer()
				try await peer.sendCardResponse(
					request : cardRequest.invoice,
					msg     : error.cardResponseMessage,
					code    : error.cardResponseCode.rawValue
				)
			} catch {
				log.error("peer.sendCardResponse(): error: \(error)")
			}
			
			await reject(error)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere(_):
				log.warning("\(#function): abort: handled elsewhere")
				
			case .continueAndSendPayment(let card, _, _):
				log.debug("\(#function): continue: send payment")
				
				// Send payment to merchant
				do {
					try await business.sendManager.payUnsolicitedInvoice(
						invoice: cardRequest.invoice,
						metadata: WalletPaymentMetadata.withCard(card.id)
					)
				} catch {
					log.error("peer.payUnsolicitedInvoice(): error: \(error)")
				}
				
				// Wait for the outgoing payment to complete
				Task { @MainActor [weak self] in
					for await payment in business.paymentsManager.lastCompletedPaymentSequence() {
						if let lnPayment = payment as? Lightning_kmpLightningOutgoingPayment {
							if lnPayment.details.paymentHash == cardRequest.invoice.paymentHash {
								self?.sentPayment = lnPayment
								log.debug("sentPayment = \(lnPayment)")
								
								self?.finish()
							} else {
								log.debug("!sentPayment: \(lnPayment)")
							}
						}
					}
				}.store(in: &cancellables)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Finish
	// --------------------------------------------------
	
	private func finish() {
		log.trace(#function)
		assertMainThread()
		
		guard !srvExtDone else {
			return
		}
		srvExtDone = true
		
		guard let contentHandler, let remoteNotificationContent else {
			log.error("\(#function): invalid state")
			return
		}
		
		totalTimer?.invalidate()
		totalTimer = nil
		connectionTimer?.invalidate()
		connectionTimer = nil
		postReceivedPaymentTimer?.invalidate()
		postReceivedPaymentTimer = nil
		
		// We purposefully have the following call order:
		// - updateRemoteNotificationContent()
		// - stopPhoenix()
		//
		// Because we may need to fetch exchangeRates,
		// and to do that we call PhoenixManager.groupPrefs().

		updateRemoteNotificationContent(remoteNotificationContent)
		displayLocalNotificationsForAdditionalPayments()
		
		stopXpc()
		stopPhoenix()
		
		contentHandler(remoteNotificationContent)
	}
	
	private func updateRemoteNotificationContent(
		_ content: UNMutableNotificationContent
	) {
		log.trace(#function)
		
		// The first thing we need to do is switch on the type of notification that we received
		
		if let pushNotification {
			switch pushNotification {
			case .fcm(let notification):
				
				switch notification.reason {
				case .incomingPayment:
					// We expected to receive 1 or more incoming payments
					
					if let item = NotificationServiceQueue.shared.dequeue() {
						updateNotificationContent_localNotification(content, item)
					} else if let payment = popFirstReceivedPayment() {
						updateNotificationContent_receivedPayment(content, payment)
					} else {
						updateNotificationContent_missedPayment(content)
					}
					
				case .incomingOnionMessage:
					// This was probably:
					// - an incoming Bolt 12 payment
					// - or a CardPayment request
					// But it could be anything, so let's code defensively.
					
					if let item = NotificationServiceQueue.shared.dequeue() {
						updateNotificationContent_localNotification(content, item)
					} else if let payment = popFirstReceivedPayment() {
						updateNotificationContent_receivedPayment(content, payment)
					} else if let result = withdrawRequestResult {
						updateNotificationContent_outgoingPayment(content, result)
					} else {
						updateNotificationContent_unknown(content)
					}
					
				case .pendingSettlement:
					updateNotificationContent_pendingSettlement(content)
					
				case .unknown:
					updateNotificationContent_unknown(content)
				}
				
			case .lnurlWithdraw(_):
				
				if let result = withdrawRequestResult {
					updateNotificationContent_outgoingPayment(content, result)
				} else {
					updateNotificationContent_unknown(content)
				}
			}
			
		} else {
			updateNotificationContent_unknown(content)
		}
	}
	
	private func displayLocalNotificationsForAdditionalPayments() {
		log.trace("displayLocalNotificationsForAdditionalPayments()")
		
		for additionalPayment in receivedPayments {
			log.debug("processing additional payment...")
			
			let identifier = UUID().uuidString
			let content = UNMutableNotificationContent()
			updateNotificationContent_receivedPayment(content, additionalPayment)
			
			// Display local notification
			let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
			UNUserNotificationCenter.current().add(request)
			
			// Add to queue
			NotificationServiceQueue.shared.enqueue(identifier: identifier, content: content)
		}
	}
	
	// --------------------------------------------------
	// MARK: Notification Content
	// --------------------------------------------------
	
	private func updateNotificationContent_localNotification(
		_ content: UNMutableNotificationContent,
		_ item: NotificationServiceQueue.Item
	) {
		log.trace(#function)
		
		content.title = item.content.title
		content.body = item.content.body
		content.badge = item.content.badge
		content.targetContentIdentifier = item.content.targetContentIdentifier
		
		UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [item.identifier])
	}
	
	private func updateNotificationContent_receivedPayment(
		_ content: UNMutableNotificationContent,
		_ payment: Lightning_kmpIncomingPayment
	) {
		log.trace(#function)
		
		content.title = String(localized: "Received payment", comment: "Push notification title")
		
		let groupPrefs = PhoenixManager.shared.groupPrefs()
		let discreetNotifications = groupPrefs?.discreetNotifications ?? false
		
		if !discreetNotifications {
			let paymentInfo = WalletPaymentInfo(
				payment: payment,
				metadata: WalletPaymentMetadata.empty(),
				contact: nil
			)
			
			let amountString = formatAmount(msat: payment.amount.msat)
			if let desc = paymentInfo.paymentDescription(), desc.count > 0 {
				content.body = "\(amountString): \(desc)"
			} else {
				content.body = amountString
			}
		}
		
		content.targetContentIdentifier = payment.id.description()
		
		// The user can independently enable/disable:
		// - alerts
		// - badges
		// So we may only be able to badge the app icon, and that's it.
		
		GroupPrefs.global.badgeCount += 1
		content.badge = NSNumber(value: GroupPrefs.global.badgeCount)
	}
	
	private func updateNotificationContent_missedPayment(
		_ content: UNMutableNotificationContent
	) {
		log.trace(#function)
		
		content.title = String(localized: "Missed incoming payment", comment: "")
	}
	
	private func updateNotificationContent_pendingSettlement(
		_ content: UNMutableNotificationContent
	) {
		log.trace(#function)
		
		content.title = String(localized: "Please start Phoenix", comment: "")
		content.body = String(localized: "An incoming settlement is pending.", comment: "")
	}
	
	private func updateNotificationContent_outgoingPayment(
		_ content: UNMutableNotificationContent,
		_ result: Result<WithdrawRequestStatus, WithdrawRequestError>
	) {
		log.trace(#function)
		
		switch result {
		case .failure(let error):
			content.title = String(localized: "Payment rejected")
			
			switch error {
			case .unknownCard:
				content.body = String(localized: "Unknown bolt card")
				
			case .replayDetected(let card):
				content.body = String(localized:
					"""
					Replay attempt detected
					Card: \(card.sanitizedName)
					""")
				
			case .frozenCard(let card):
				content.body = String(localized:
					"""
					Card is frozen
					Card: \(card.sanitizedName)
					""")
				
			case .dailyLimitExceeded(let card, let amount):
				let amtStr = Utils.format(currencyAmount: amount).string
				content.body = String(localized:
					"""
					Daily limit exceeded
					Payment amount: \(amtStr)
					Card: \(card.sanitizedName)
					""")
				
			case .monthlyLimitExceeded(let card, let amount):
				let amtStr = Utils.format(currencyAmount: amount).string
				content.body = String(localized:
					"""
					Monthly limit exceeded
					Payment amount: \(amtStr)
					Card: \(card.sanitizedName)
					""")
				
			case .badInvoice(let card, let details):
				content.body = String(localized:
					"""
					Bad invoice: \(details)
					Card: \(card.sanitizedName)
					""")
				
			case .alreadyPaidInvoice(let card):
				content.body = String(localized:
					"""
					You've already paid this invoice
					Card: \(card.sanitizedName)
					""")
				
			case .paymentPending(let card):
				content.body = String(localized:
					"""
					A payment for this invoice is in-flight
					Card: \(card.sanitizedName)
					""")
				
			case .internalError(let card, let details):
				if let card {
					content.body = String(localized:
						"""
						Internal error: \(details)
						Card: \(card.sanitizedName)
						""")
				} else {
					content.body = String(localized:
						"""
						Internal error: \(details)
						""")
				}
			}
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere(let card):
				content.title = String(localized: "Payment attempt ignored")
				content.subtitle = card.sanitizedName
				content.body = String(localized: "Handled elsewhere in the system")
				
			case .continueAndSendPayment(let card, let method, let amount):
					
				if let sentPayment, let failedStatus = sentPayment.status.asFailed() {
					
					content.title = String(localized: "Payment attempt failed")
						
					let localizedReason = failedStatus.reason.localizedDescription()
						
					if failedStatus.reason is Lightning_kmpFinalFailure.InsufficientBalance {
						let amountString = formatAmount(msat: amount.msat)
						content.body = String(localized:
							"""
							\(localizedReason)
							Payment amount: \(amountString)
							Card: \(card.sanitizedName)
							""")
						
					} else {
						content.body = String(localized:
							"""
							\(localizedReason)
							Card: \(card.sanitizedName)
							""")
					}
						
				} else {
					
					content.title = String(localized: "Payment successful 💳")
					let amountString = formatAmount(msat: amount.msat)
					
					if let desc = method.description {
						content.body = String(localized:
							"""
							\(amountString)
							For: \(desc)
							Card: \(card.sanitizedName) 
							""")
					} else {
						content.body = String(localized:
							"""
							\(amountString)
							Card: \(card.sanitizedName) 
							""")
					}
				}
			} // </switch status {>
		} // </switch result>
	}
	
	private func updateNotificationContent_unknown(
		_ content: UNMutableNotificationContent
	) {
		log.trace(#function)
		
		content.title = "Phoenix"
		content.body = String(localized: "is running in the background", comment: "")
		content.relevanceScore = 0.0
		content.threadIdentifier = "unknown"
	}
	
	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------
	
	private func popFirstReceivedPayment() -> Lightning_kmpIncomingPayment? {
		
		if receivedPayments.isEmpty {
			return nil
		} else {
			return receivedPayments.removeFirst()
		}
	}
	
	private func formatAmount(msat: Int64) -> String {
		
		if let groupPrefs = PhoenixManager.shared.groupPrefs() {
			
			let bitcoinUnit = groupPrefs.bitcoinUnit
			let fiatCurrency = groupPrefs.fiatCurrency
			let exchangeRate = PhoenixManager.shared.exchangeRate(fiatCurrency: fiatCurrency)
			
			let bitcoinAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
			var amountString = bitcoinAmt.string
			
			if let exchangeRate {
				let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				amountString += " (≈\(fiatAmt.string))"
			}
			
			return amountString
			
		} else {
			
			// Something is wrong - but let's at least display some amount.
			// We can default to showing the amount in sats.
			
			return Utils.formatBitcoin(msat: msat, bitcoinUnit: .sat).string
		}
	}
}
