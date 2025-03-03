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
 * - XPC.shared
 */
class NotificationService: UNNotificationServiceExtension {

	enum PushNotificationReason: CustomStringConvertible {
		case incomingPayment
		case incomingOnionMessage
		case pendingSettlement
		case withdrawRequest
		case unknown
		
		var description: String {
			switch self {
				case .incomingPayment      : return "incomingPayment"
				case .incomingOnionMessage : return "incomingOnionMessage"
				case .pendingSettlement    : return "pendingSettlement"
				case .withdrawRequest      : return "withdrawRequest"
				case .unknown              : return "unknown"
			}
		}
	}
	
	private var contentHandler: ((UNNotificationContent) -> Void)?
	private var bestAttemptContent: UNMutableNotificationContent?
	
	private var xpcStarted: Bool = false
	private var phoenixStarted: Bool = false
	private var srvExtDone: Bool = false
	
	private var business: PhoenixBusiness? = nil
	private var pushNotificationReason: PushNotificationReason = .unknown
	
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
		let selfPtr = Unmanaged.passUnretained(self).toOpaque().debugDescription
		
		log.trace("instance => \(selfPtr)")
		log.trace("didReceive(request:withContentHandler:)")
		log.trace("request.content.userInfo: \(request.content.userInfo)")
		
		self.contentHandler = contentHandler
		self.bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)
		
		// IMPORTANT: This function is called on a NON-main thread.
		// 
		// And Kotlin-KMM has an unworkable threading policy that requires
		// everything to be on the main thread...
		DispatchQueue.main.async {
			
			self.startTotalTimer()
			self.startXpc()
			self.startPhoenix()
			self.processNotification(request.content.userInfo)
		}
	}
	
	override func serviceExtensionTimeWillExpire() {
		log.trace("serviceExtensionTimeWillExpire()")
		
		// iOS calls this function just before the extension will be terminated by the system.
		// Use this as an opportunity to deliver your "best attempt" at modified content,
		// otherwise the original push payload will be used.
		
		// IMPORTANT: This function is called on a NON-main thread.
		DispatchQueue.main.async {
			self.displayPushNotification()
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
		// - Amazon Web Services (AWS)
		
		if PushNotification.isFCM(userInfo: userInfo) {
			processNotification_fcm(userInfo)
		} else {
			processNotification_aws(userInfo)
		}
	}
	
	private func processNotification_fcm(_ userInfo: [AnyHashable : Any]) {
		log.trace("processNotification_fcm()")
		assertMainThread()
		
		// Example:
		// {
		//   "gcm.message_id": 1605136272123442,
		//   "google.c.sender.id": 458618232423,
		//   "google.c.a.e": 1,
		//   "google.c.fid": "dRLLO-mxUxbDvmV1urj5Tt",
		//   "reason": "IncomingPayment",
		//   "aps": {
		//     "alert": {
		//       "title": "Missed incoming payment",
		//     },
		//     "mutable-content": 1
		//   }
		// }
		
		if let reason = userInfo["reason"] as? String {
			log.debug("reason: (\(reason))")
			
			switch reason {
				case "IncomingPayment"       : pushNotificationReason = .incomingPayment
				case "IncomingOnionMessage$" : pushNotificationReason = .incomingOnionMessage
				case "PendingSettlement"     : pushNotificationReason = .pendingSettlement
				default                      : pushNotificationReason = .unknown
			}
		} else {
			log.debug("reason: !string")
			pushNotificationReason = .unknown
		}
		
		log.debug("pushNotificationReason = \(pushNotificationReason)")
		
		// Nothing else to do here.
		// No custom processing is needed for either `.incomingPayment` or `.pendingSettlement`.
		// Those types of requests are handled automatically by the Peer.
	}
	
	private func processNotification_aws(_ userInfo: [AnyHashable : Any]) {
		log.trace("processNotification_aws()")
		assertMainThread()
		
		if let withdrawRequest = PushNotification.parseLnurlWithdraw(userInfo: userInfo) {
			pushNotificationReason = .withdrawRequest
			log.debug("pushNotificationReason = \(pushNotificationReason)")
			
			Task {
				await processNotification_aws_withdraw(withdrawRequest)
			}
		} else {
			pushNotificationReason = .unknown
			log.debug("pushNotificationReason = \(pushNotificationReason)")
			
			return displayPushNotification()
		}
	}
	
	@MainActor
	private func processNotification_aws_withdraw(
		_ request: LnurlWithdrawNotification
	) async {
		log.trace("processNotification_aws_withdraw()")
		
		guard let business else {
			log.warning("processNotification_aws_withdraw(): business is nil")
			return
		}
		
		let reject = { @MainActor (error: WithdrawRequestError) async -> Void in
		
			// Stop other processing
			self.stopPhoenix()
			self.stopXpc()
			
			// Send the response to the merchant
			let _ = await request.postResponse(errorReason: error.description)
			
			// And finally, display notification to the user
			self.displayPushNotification()
		}
		
		let result = await business.checkWithdrawRequest(request.toWithdrawRequest())
		withdrawRequestResult = result
		
		switch result {
		case .failure(let error):
			log.error("handleCardRequest: error: \(error.description)")
			await reject(error)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere:
				log.warning("handleCardReqeust: abort: handled elsewhere")
				displayPushNotification()
			
			case .continueAndSendPayment(let card, _, _):
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
						self.displayPushNotification()
					}
				}
				
				business.paymentsManager.lastCompletedPaymentPublisher().sink { payment in
					if let lnPayment = payment as? Lightning_kmpLightningOutgoingPayment,
						let details = lnPayment.details as? Lightning_kmpLightningOutgoingPayment.DetailsNormal
					{
						if details.paymentHash == request.invoice.paymentHash {
							self.sentPayment = lnPayment
							log.debug("sentPayment = \(lnPayment)")
							
							if self.withdrawResponseSent {
								self.displayPushNotification()
							}
						}
					}
				}
				.store(in: &cancellables)
				
			} // </switch status>
		} // </switch result>
	}
	
	// --------------------------------------------------
	// MARK: Timers
	// --------------------------------------------------
	
	private func startTotalTimer() {
		log.trace("startTotalTimer()")
		assertMainThread()
		
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
			
			if let self = self {
				log.debug("totalTimer.fire()")
				self.displayPushNotification()
			}
		}
	}
	
	private func startConnectionTimer() {
		log.trace("startConnectionTimer()")
		assertMainThread()
		
		guard connectionTimer == nil else {
			log.debug("startConnectionTimer(): ignoring: already started")
			return
		}
		
		log.debug("GroupPrefs.shared.srvExtConnection = now")
		GroupPrefs.shared.srvExtConnection = Date.now
		
		connectionTimer = Timer.scheduledTimer(
			withTimeInterval : 2.0,
			repeats          : true
		) {[weak self](_: Timer) in
		
			if let _ = self {
				log.debug("connectionsTimer.fire()")
				log.debug("GroupPrefs.shared.srvExtConnection = now")
				GroupPrefs.shared.srvExtConnection = Date.now
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
			
			if let self = self {
				log.debug("postReceivedPaymentTimer.fire()")
				self.displayPushNotification()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: XPC
	// --------------------------------------------------
	
	private func startXpc() {
		log.trace("startXpc()")
		assertMainThread()
		
		if !xpcStarted && !srvExtDone {
			xpcStarted = true
			
			XPC.shared.receivedMessagePublisher.sink {[weak self](msg: XpcMessage) in
				self?.didReceiveXpcMessage(msg)
			}.store(in: &cancellables)
			
			XPC.shared.resume()
		}
	}
	
	private func stopXpc() {
		log.trace("stopXpc()")
		assertMainThread()
		
		if xpcStarted {
			xpcStarted = false
			
			XPC.shared.suspend()
		}
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
				
				// Since we're not connected yet, we'll just go ahead and allow the main app to handle the payment.
				//
				// And we don't have to wait for the main app to finish handling the payment.
				// Because whatever we emit from this app extension won't be displayed to the user.
				// That is, the modified push content we emit isn't actually shown to the user.
				
				displayPushNotification()
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
		
		let newBusiness = PhoenixManager.shared.setupBusiness()
		business = newBusiness
		
		newBusiness.connectionsManager.connectionsPublisher().sink {
			[weak self](connections: Connections) in
				
			self?.connectionsChanged(connections)
		}
		.store(in: &cancellables)
			
		let pushReceivedAt = Date()
		newBusiness.paymentsManager.lastIncomingPaymentPublisher().sink {
			[weak self](payment: Lightning_kmpIncomingPayment) in
			
			guard
				let paymentReceivedAt = payment.completedAtDate,
				paymentReceivedAt > pushReceivedAt
			else {
				// Ignoring - this is the most recently received incomingPayment, but not a new one
				return
			}
			
			self?.didReceivePayment(payment)
		}
		.store(in: &cancellables)
		
		newBusiness.peerManager.peerStatePublisher()
			.flatMap { $0.eventsFlowPublisher() }
			.sink { (event: Lightning_kmpPeerEvent) in
				
				if let msg = event as? Lightning_kmp_coreCardRequestReceived {
					log.debug("found event: CardRequestReceived")
					
					if let cardRequest = CardRequest.fromOnionMessage(msg) {
						Task { @MainActor in
							await self.handleCardRequest(cardRequest)
						}
					} else {
						log.debug("CardRequest.fromOnionMessage() failed")
					}
				}
			}
			.store(in: &cancellables)
	}
	
	private func stopPhoenix() {
		log.trace("stopPhoenix()")
		assertMainThread()
		
		if phoenixStarted {
			phoenixStarted = false
			
			PhoenixManager.shared.teardownBusiness()
			business = nil
		}
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
		log.trace("handleCardRequest()")
		
		guard let business else {
			log.warning("handleCardRequest: business is nil")
			return
		}
		
		let reject = { @MainActor (error: WithdrawRequestError) async -> Void in
		
			// Stop other processing
			self.stopPhoenix()
			self.stopXpc()
			
			// Display notification to the user
			self.displayPushNotification()
		}
		
		let result = await business.checkWithdrawRequest(cardRequest.toWithdrawRequest())
		withdrawRequestResult = result
		
		switch result {
		case .failure(let error):
			log.error("handleCardRequest: error: \(error.description)")
			await reject(error)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere(_):
				log.warning("handleCardReqeust: abort: handled elsewhere")
				
			case .continueAndSendPayment(_, _, _):
				log.debug("handleCardReqeust: continue: send payment")
				
				guard let peer = business.peerManager.peerStateValue() else {
					log.error("handleCardReqeust: peer is nil")
					return
				}
				
				// Send the payment
				let paymentId = Lightning_kmpUUID.companion.randomUUID()
				do {
					try await peer.betterPayOffer(
						paymentId: paymentId,
						amount: cardRequest.amount,
						offer: cardRequest.offer,
						payerKey: Lightning_randomKey(),
						payerNote: nil,
						fetchInvoiceTimeoutInSeconds: 30
					)
				} catch {
					log.error("peer.betterPayOffer(): error: \(error)")
				}
				
				// Wait for the outgoing payment to complete
				business.paymentsManager.lastCompletedPaymentPublisher().sink { payment in
					if let lnPayment = payment as? Lightning_kmpLightningOutgoingPayment {
						if lnPayment.id == paymentId {
							self.sentPayment = lnPayment
							log.debug("sentPayment = \(lnPayment)")
							
							self.displayPushNotification()
						} else {
							log.debug("!sentPayment: \(lnPayment)")
						}
					}
				}
				.store(in: &cancellables)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Finish
	// --------------------------------------------------
	
	private func displayPushNotification() {
		log.trace("displayPushNotification()")
		assertMainThread()
		
		guard !srvExtDone else {
			return
		}
		srvExtDone = true
		
		guard let contentHandler, let bestAttemptContent else {
			log.error("displayPushNotification(): invalid state")
			return
		}
		
		totalTimer?.invalidate()
		totalTimer = nil
		connectionTimer?.invalidate()
		connectionTimer = nil
		postReceivedPaymentTimer?.invalidate()
		postReceivedPaymentTimer = nil
		stopXpc()
		stopPhoenix()
		
		switch pushNotificationReason {
		case .incomingPayment:
			// We expected to receive 1 or more incoming payments
			updateBestAttemptContent_incomingPayment()
		
		case .incomingOnionMessage:
			// This is probably a CardPayment request.
			// But it could be anything, so let's code defensively.
			
			if withdrawRequestResult != nil {
				updateBestAttemptContent_outgoingPayment()
			} else if !receivedPayments.isEmpty {
				updateBestAttemptContent_incomingPayment()
			} else {
				updateBestAttemptContent_unknown()
			}
			
			case .pendingSettlement:
				updateBestAttemptContent_incomingPayment()
			
			case .withdrawRequest:
				updateBestAttemptContent_outgoingPayment()
			
			case .unknown:
				updateBestAttemptContent_incomingPayment()
		}
		
		contentHandler(bestAttemptContent)
	}
	
	private func updateBestAttemptContent_incomingPayment() {
		log.trace("updateBestAttemptContent_incomingPayment()")
		assertMainThread()
		
		guard let bestAttemptContent else {
			log.warning("updateBestAttemptContent: bestAttemptContent is nil")
			return
		}
		
		if receivedPayments.isEmpty {
			
			if pushNotificationReason == .pendingSettlement {
				bestAttemptContent.title = String(localized: "Please start Phoenix", comment: "")
				bestAttemptContent.body = String(localized: "An incoming settlement is pending.", comment: "")
			} else {
				bestAttemptContent.title = String(localized: "Missed incoming payment", comment: "")
			}
			
		} else { // received 1 or more payments
			
			var msat: Int64 = 0
			for payment in receivedPayments {
				msat += payment.amount.msat
			}
			
			let amountString = formatAmount(msat: msat)
			
			if receivedPayments.count == 1 {
				bestAttemptContent.title = String(
					localized: "Received payment",
					comment: "Push notification title"
				)
				
				if !GroupPrefs.shared.discreetNotifications {
					let paymentInfo = WalletPaymentInfo(
						payment: receivedPayments.first!,
						metadata: WalletPaymentMetadata.empty(),
						contact: nil
					)
					if let desc = paymentInfo.paymentDescription(), desc.count > 0 {
						bestAttemptContent.body = "\(amountString): \(desc)"
					} else {
						bestAttemptContent.body = amountString
					}
				}
				
			} else {
				bestAttemptContent.title = String(
					localized: "Received multiple payments",
					comment: "Push notification title"
				)
				
				if !GroupPrefs.shared.discreetNotifications {
					bestAttemptContent.body = amountString
				}
			}
			
			// The user can independently enable/disable:
			// - alerts
			// - badges
			// So we may only be able to badge the app icon, and that's it.
			
			GroupPrefs.shared.badgeCount += receivedPayments.count
			bestAttemptContent.badge = NSNumber(value: GroupPrefs.shared.badgeCount)
		}
	}
	
	private func updateBestAttemptContent_outgoingPayment() {
		log.trace("updateBestAttemptContent_outgoingPayment()")
		assertMainThread()
		
		guard let bestAttemptContent else {
			log.warning("updateBestAttemptContent: bestAttemptContent is nil")
			return
		}
		guard let result = withdrawRequestResult else {
			log.warning("updateBestAttemptContent_outgoing: withdrawRequestResult is nil")
			return
		}
		
		switch result {
		case .failure(let error):
			bestAttemptContent.title = String(localized: "Payment rejected")
			
			switch error {
			case .unknownCard:
				bestAttemptContent.body = String(localized: "Unknown bolt card")
				
			case .replayDetected(let card):
				bestAttemptContent.body = String(localized:
					"""
					Replay attempt detected
					Card: \(card.sanitizedName)
					""")
				
			case .frozenCard(let card):
				bestAttemptContent.body = String(localized:
					"""
					Card is frozen
					Card: \(card.sanitizedName)
					""")
				
			case .dailyLimitExceeded(let card, let amount):
				let amtStr = Utils.format(currencyAmount: amount).string
				bestAttemptContent.body = String(localized:
					"""
					Daily limit exceeded
					Payment amount: \(amtStr)
					Card: \(card.sanitizedName)
					""")
				
			case .monthlyLimitExceeded(let card, let amount):
				let amtStr = Utils.format(currencyAmount: amount).string
				bestAttemptContent.body = String(localized:
					"""
					Monthly limit exceeded
					Payment amount: \(amtStr)
					Card: \(card.sanitizedName)
					""")
				
			case .badInvoice(let card, let details):
				bestAttemptContent.body = String(localized:
					"""
					Bad invoice: \(details)
					Card: \(card.sanitizedName)
					""")
				
			case .alreadyPaidInvoice(let card):
				bestAttemptContent.body = String(localized:
					"""
					You've already paid this invoice
					Card: \(card.sanitizedName)
					""")
				
			case .paymentPending(let card):
				bestAttemptContent.body = String(localized:
					"""
					A payment for this invoice is in-flight
					Card: \(card.sanitizedName)
					""")
				
			case .internalError(let card, let details):
				bestAttemptContent.body = String(localized:
					"""
					Internal error: \(details)
					Card: \(card.sanitizedName)
					""")
			}
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere(let card):
				bestAttemptContent.title = String(localized: "Payment attempt ignored")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Handled elsewhere in the system")
				
			case .continueAndSendPayment(let card, let method, let amount):
				
				if let sentPayment, let failedStatus = sentPayment.status.asFailed() {
					
					bestAttemptContent.title = String(localized: "Payment attempt failed")
					
					let localizedReason = failedStatus.reason.localizedDescription()
					
					if failedStatus.reason is Lightning_kmpFinalFailure.InsufficientBalance {
						let amountString = formatAmount(msat: amount.msat)
						bestAttemptContent.body = String(localized:
							"""
							\(localizedReason)
							Payment amount: \(amountString)
							Card: \(card.sanitizedName)
							""")
						
					} else {
						bestAttemptContent.body = String(localized:
							"""
							\(localizedReason)
							Card: \(card.sanitizedName)
							""")
					}
					
				} else {
					
					bestAttemptContent.title = String(localized: "Payment successful 💳")
					let amountString = formatAmount(msat: amount.msat)
					
					if let desc = method.description {
						bestAttemptContent.body = String(localized:
							"""
							\(amountString)
							For: \(desc)
							Card: \(card.sanitizedName) 
							""")
					} else {
						bestAttemptContent.body = String(localized:
							"""
							\(amountString)
							Card: \(card.sanitizedName) 
							""")
					}
				}
			}
		}
	}
	
	private func updateBestAttemptContent_unknown() {
		log.trace("updateBestAttemptContent_unknown()")
		assertMainThread()
		
		guard let bestAttemptContent else {
			log.warning("updateBestAttemptContent: bestAttemptContent is nil")
			return
		}
		
		let fiatCurrency = GroupPrefs.shared.fiatCurrency
		let exchangeRate = PhoenixManager.shared.exchangeRate(fiatCurrency: fiatCurrency)
		
		if let exchangeRate {
			let fiatAmt = Utils.formatFiat(amount: exchangeRate.price, fiatCurrency: exchangeRate.fiatCurrency)
			
			bestAttemptContent.title = String(localized: "Current bitcoin price", comment: "")
			bestAttemptContent.body = fiatAmt.string
			
		} else {
			bestAttemptContent.title = String(localized: "Current bitcoin price", comment: "")
			bestAttemptContent.body = "?"
		}
	}
	
	private func formatAmount(msat: Int64) -> String {
		
		let bitcoinUnit = GroupPrefs.shared.bitcoinUnit
		let fiatCurrency = GroupPrefs.shared.fiatCurrency
		let exchangeRate = PhoenixManager.shared.exchangeRate(fiatCurrency: fiatCurrency)
		
		let bitcoinAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
		var amountString = bitcoinAmt.string
		
		if let exchangeRate {
			let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
			amountString += " (≈\(fiatAmt.string))"
		}
		
		return amountString
	}
}
