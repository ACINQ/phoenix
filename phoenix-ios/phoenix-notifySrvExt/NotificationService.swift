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

	private var contentHandler: ((UNNotificationContent) -> Void)?
	private var bestAttemptContent: UNMutableNotificationContent?
	
	private var xpcStarted: Bool = false
	private var phoenixStarted: Bool = false
	private var srvExtDone: Bool = false
	
	private var isConnectedToPeer = false
	private var receivedPayments: [Lightning_kmpIncomingPayment] = []
	
	private var totalTimer: Timer? = nil
	private var connectionTimer: Timer? = nil
	private var postPaymentTimer: Timer? = nil
	
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
	
	private func startPostPaymentTimer() {
		log.trace("startPostPaymentTimer()")
		assertMainThread()
		
		// This method is called everytime we receive a payment,
		// and it's possible we receive multiple payments.
		// So for every payment, we want to restart the timer.
		postPaymentTimer?.invalidate()
		
	#if DEBUG
		let delay: TimeInterval = 5.0
	#else
		let delay: TimeInterval = 5.0
	#endif
		
		postPaymentTimer = Timer.scheduledTimer(
			withTimeInterval : delay,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self = self {
				log.debug("postPaymentTimer.fire()")
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
		}
	}
	
	private func stopPhoenix() {
		log.trace("stopPhoenix()")
		assertMainThread()
		
		if phoenixStarted {
			phoenixStarted = false
			
			PhoenixManager.shared.teardownBusiness()
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
			startPostPaymentTimer()
		}
	}
	
	// --------------------------------------------------
	// MARK: Finish
	// --------------------------------------------------
	
	enum PushNotificationReason {
		case incomingPayment
		case pendingSettlement
		case unknown
	}
	
	private func pushNotificationReason() -> PushNotificationReason {
		
		// Example: request.content.userInfo:
		// {
		//   "gcm.message_id": 1605136272123442,
		//   "google.c.sender.id": 458618232423,
		//   "google.c.a.e": 1,
		//   "google.c.fid": "dRLLO-mxUxbDvmV1urj5Tt",
		//   "reason": "IncomingPayment",
		//   "aps": {
		//     "alert": {
		//       "title": "Phoenix is running in the background",
		//     },
		//     "mutable-content": 1
		//   }
		// }
		
		if let userInfo = bestAttemptContent?.userInfo,
		   let reason = userInfo["reason"] as? String
		{
			switch reason {
				case "IncomingPayment"   : return .incomingPayment
				case "PendingSettlement" : return .pendingSettlement
				default                  : break
			}
		}
		
		return .unknown
	}
	
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
		postPaymentTimer?.invalidate()
		postPaymentTimer = nil
		stopXpc()
		stopPhoenix()
		
		updateBestAttemptContent()
		contentHandler(bestAttemptContent)
	}
	
	private func updateBestAttemptContent() {
		log.trace("updateBestAttemptContent()")
		assertMainThread()
		
		guard let bestAttemptContent else {
			return
		}
		
		if receivedPayments.isEmpty {
			
			if pushNotificationReason() == .pendingSettlement {
				bestAttemptContent.title = NSLocalizedString("Please start Phoenix", comment: "")
				bestAttemptContent.body = NSLocalizedString("An incoming settlement is pending.", comment: "")
			} else {
				bestAttemptContent.title = NSLocalizedString("Missed incoming payment", comment: "")
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
