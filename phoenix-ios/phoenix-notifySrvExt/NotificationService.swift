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
		case pendingSettlement
		case withdrawRequest
		case unknown
		
		var description: String {
			switch self {
				case .incomingPayment   : return "incomingPayment"
				case .pendingSettlement : return "pendingSettlement"
				case .withdrawRequest   : return "withdrawRequest"
				case .unknown           : return "unknown"
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
			self.processRequest(request)
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
	// MARK: Request Processing
	// --------------------------------------------------
	
	private func processRequest(_ request: UNNotificationRequest) {
		log.trace("processRequest()")
		assertMainThread()
		
		let userInfo = request.content.userInfo
		
		// This could be a push notification coming from either:
		// - Google's Firebase Cloud Messaging (FCM)
		// - Amazon Web Services (AWS)
		
		if PushNotification.isFCM(userInfo: userInfo) {
			processRequest_fcm(userInfo)
		} else {
			processRequest_aws(userInfo)
		}
	}
	
	private func processRequest_fcm(_ userInfo: [AnyHashable : Any]) {
		log.trace("processRequest_fcm()")
		assertMainThread()
		
		// Example: request.content.userInfo:
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
			switch reason {
				case "IncomingPayment"   : pushNotificationReason = .incomingPayment
				case "PendingSettlement" : pushNotificationReason = .pendingSettlement
				default                  : pushNotificationReason = .unknown
			}
		} else {
			pushNotificationReason = .unknown
		}
		
		log.debug("pushNotificationReason = \(pushNotificationReason)")
		
		// Nothing else to do here.
		// No custom processing is needed for either `.incomingPayment` or `.pendingSettlement`.
		// Those types of requests are handled automatically by the Peer.
	}
	
	private func processRequest_aws(_ userInfo: [AnyHashable : Any]) {
		log.trace("processRequest_aws()")
		assertMainThread()
		
		if let withdrawRequest = PushNotification.parseWithdrawRequest(userInfo: userInfo) {
			pushNotificationReason = .withdrawRequest
			log.debug("pushNotificationReason = \(pushNotificationReason)")
			
			Task {
				await processRequest_aws_withdraw(withdrawRequest)
			}
		} else {
			pushNotificationReason = .unknown
			log.debug("pushNotificationReason = \(pushNotificationReason)")
			
			return displayPushNotification()
		}
	}
	
	@MainActor
	private func processRequest_aws_withdraw(
		_ request: WithdrawRequest
	) async {
		log.trace("processRequest_aws_withdraw()")
		
		guard let business else {
			log.warning("processRequest_aws_withdraw(): business is nil")
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
		
		let result = await business.checkWithdrawRequest(request)
		withdrawRequestResult = result
		
		switch result {
		case .failure(let error):
			await reject(error)
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere:
				displayPushNotification()
			
			case .continueAndSendPayment(let card, let invoice, let amount):
				guard
					let peer = business.peerManager.peerStateValue(),
					let defaultTrampolineFees = peer.walletParams.trampolineFees.first
				else {
					return await reject(.internalError(card: card, details: "peer is nil"))
				}
				
				do {
					try await business.sendManager.payBolt11Invoice(
						amountToSend   : amount,
						trampolineFees : defaultTrampolineFees,
						invoice        : invoice,
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
						if details.paymentHash == invoice.paymentHash {
							self.sentPayment = lnPayment
							log.debug("sentPayment = \(lnPayment)")
							
							if self.withdrawResponseSent {
								self.displayPushNotification()
							}
						}
					}
				}
				.store(in: &cancellables)
				
			//	return accept(request)
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
		
		if !phoenixStarted && !srvExtDone {
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
					let paymentReceivedAt = payment.received?.receivedAtDate,
					paymentReceivedAt > pushReceivedAt
				else {
					// Ignoring - this is the most recently received incomingPayment, but not a new one
					return
				}
		
				self?.didReceivePayment(payment)
			}
			.store(in: &cancellables)
		}
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
			case .incomingPayment   : updateBestAttemptContent_fcm()
			case .pendingSettlement : updateBestAttemptContent_fcm()
			case .withdrawRequest   : updateBestAttemptContent_aws()
			case .unknown           : updateBestAttemptContent_fcm()
		}
		
		contentHandler(bestAttemptContent)
	}
	
	private func updateBestAttemptContent_fcm() {
		log.trace("updateBestAttemptContent_fcm()")
		assertMainThread()
		
		guard let bestAttemptContent else {
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
						contact: nil,
						fetchOptions: WalletPaymentFetchOptions.companion.None
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
	
	private func updateBestAttemptContent_aws() {
		log.trace("updateBestAttemptContent_aws()")
		assertMainThread()
		
		guard let bestAttemptContent, let result = withdrawRequestResult else {
			return
		}
		
		switch result {
		case .failure(let error):
			switch error {
			case .unknownCard:
				bestAttemptContent.title = String(localized: "Payment attempt rejected")
				bestAttemptContent.body = String(localized: "Unknown bolt card")
				
			case .replayDetected(let card):
				bestAttemptContent.title = String(localized: "Payment attempt rejected")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Replay attempt detected")
				
			case .frozenCard(let card):
				bestAttemptContent.title = String(localized: "Payment attempt failed")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Card is frozen.")
				
			case .badInvoice(let card, let details):
				bestAttemptContent.title = String(localized: "Payment attempt rejected")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Bad invoice: \(details)")
				
			case .alreadyPaidInvoice(let card):
				bestAttemptContent.title = String(localized: "Duplicate payment attempt rejected")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "You've already paid this invoice.")
				
			case .paymentPending(let card):
				bestAttemptContent.title = String(localized: "Duplicate payment attempt rejected")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "A payment for this invoice in in-flight.")
				
			case .internalError(let card, let details):
				bestAttemptContent.title = String(localized: "Payment attempt rejected")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Internal error: \(details)")
			}
			
		case .success(let status):
			switch status {
			case .abortHandledElsewhere(let card):
				bestAttemptContent.title = String(localized: "Payment attempt ignored")
				bestAttemptContent.subtitle = card.sanitizedName
				bestAttemptContent.body = String(localized: "Handled elsewhere in the system")
				
			case .continueAndSendPayment(let card, let invoice, let amount):
				
				if let sentPayment, let failedStatus = sentPayment.status.asFailed() {
					
					bestAttemptContent.title = String(localized: "Payment attempt failed")
					bestAttemptContent.subtitle = card.sanitizedName
					
					let localizedReason = failedStatus.reason.localizedDescription()
					
					if failedStatus.reason is Lightning_kmpFinalFailure.InsufficientBalance {
						let amountString = formatAmount(msat: amount.msat)
						bestAttemptContent.body = "\(localizedReason) \(amountString)"
						
					} else {
						bestAttemptContent.body = localizedReason
					}
					
				} else {
					
					let amountString = formatAmount(msat: amount.msat)
					
					bestAttemptContent.title = String(localized: "Paid: \(amountString)")
					bestAttemptContent.subtitle = card.sanitizedName
					
					if let desc = invoice.description_ {
						bestAttemptContent.body = String(localized: "For: \(desc)")
					}
				}
			}
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
