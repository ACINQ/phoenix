import UserNotifications
import PhoenixShared
import Combine
import os.log
import notify

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NotificationService"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class NotificationService: UNNotificationServiceExtension {

	private var contentHandler: ((UNNotificationContent) -> Void)?
	private var bestAttemptContent: UNMutableNotificationContent?
	
	private var mainAppIsRunning: Bool = false
	private var done: Bool = false
	
	private var receivedPayments: [Lightning_kmpIncomingPayment] = []
	
	private var totalTimer: Timer? = nil
	private var postPaymentTimer: Timer? = nil
	
	override func didReceive(
		_ request: UNNotificationRequest,
		withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
	) {
		let selfPtr = Unmanaged.passUnretained(self).toOpaque().debugDescription
		
		log.trace("instance => \(selfPtr)")
		log.trace("didReceive(_:withContentHandler:)")
		
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
		
		// Called just before the extension will be terminated by the system.
		// Use this as an opportunity to deliver your "best attempt" at modified content,
		// otherwise the original push payload will be used.
		
		displayPushNotification()
	}
	
	// --------------------------------------------------
	// MARK: Timers
	// --------------------------------------------------
	
	private func startTotalTimer() {
		log.trace("startTotalTimer()")
		assertMainThread()
		
		// The OS gives us 30 seconds to fetch data, and then invoke the completionHandler.
		// Failure to properly "clean up" in this way will result in the OS reprimanding us.
		// So we set a timer to ensure we stop before the max allowed.
		totalTimer = Timer.scheduledTimer(
			withTimeInterval : 29.0,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self = self {
				log.debug("totalTimer.fire()")
				self.displayPushNotification()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: XPC
	// --------------------------------------------------
	
	private func startXpc() {
		log.trace("startXpc()")
		
		CrossProcessCommunication.shared.start(receivedMessage: {[weak self] in
			
			self?.didReceiveXpcMessage()
		})
	}
	
	private func didReceiveXpcMessage() {
		log.trace("didReceiveXpcMessage()")
		assertMainThread()
		
		// This means the main phoenix app is running.
		// So we can allow it to handle the payment.
		
		if !mainAppIsRunning {
			mainAppIsRunning = true
			PhoenixManager.shared.disconnect()
		}
		
		// And we don't have to wait for the main app to finish handling the payment.
		// Because whatever we emit from this app extension won't be displayed to the user.
		// That is, the modified push content we emit isn't actually shown to the user.
		
		displayPushNotification()
	}
	
	// --------------------------------------------------
	// MARK: Phoenix
	// --------------------------------------------------
	
	private func startPhoenix() {
		log.trace("startPhoenix()")
		assertMainThread()
		
		PhoenixManager.shared.register(didReceivePayment: {[weak self](payment: Lightning_kmpIncomingPayment) in
			
			self?.didReceivePayment(payment)
		})
		PhoenixManager.shared.connect()
	}
	
	private func stopPhoenix() {
		log.trace("stopPhoenix()")
		assertMainThread()
		
		if !mainAppIsRunning {
			PhoenixManager.shared.disconnect()
		}
		PhoenixManager.shared.unregister()
	}
	
	private func didReceivePayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("didReceivePayment()")
		assertMainThread()
		
		receivedPayments.append(payment)
		
		postPaymentTimer?.invalidate()
		postPaymentTimer = Timer.scheduledTimer(
			withTimeInterval : 5.0,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self = self {
				log.debug("postPaymentTimer.fire()")
				self.displayPushNotification()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Finish
	// --------------------------------------------------
	
	private func displayPushNotification() {
		log.trace("displayPushNotification()")
		assertMainThread()
		
		guard !done else {
			return
		}
		done = true
		
		guard
			let contentHandler = contentHandler,
			let bestAttemptContent = bestAttemptContent
		else {
			return
		}
		
		totalTimer?.invalidate()
		totalTimer = nil
		postPaymentTimer?.invalidate()
		postPaymentTimer = nil
		stopPhoenix()
		
		if receivedPayments.isEmpty {
			bestAttemptContent.title = NSLocalizedString("Missed incoming payment", comment: "")
			
		} else { // received 1 or more payments
			
			let paymentInfos = receivedPayments.map { payment in
				WalletPaymentInfo(
					payment: payment,
					metadata: WalletPaymentMetadata.empty(),
					fetchOptions: WalletPaymentFetchOptions.companion.None
				)
			}
			
			let bitcoinUnit = GroupPrefs.shared.bitcoinUnit
			let fiatCurrency = GroupPrefs.shared.fiatCurrency
			let exchangeRate = PhoenixManager.shared.exchangeRate(fiatCurrency: fiatCurrency)
			
			bestAttemptContent.fillForReceivedPayments(
				payments: paymentInfos,
				bitcoinUnit: bitcoinUnit,
				exchangeRate: exchangeRate
			)
		}
		
		contentHandler(bestAttemptContent)
	}
}
