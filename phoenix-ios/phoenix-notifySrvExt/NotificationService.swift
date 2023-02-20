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
 * - XpcManager.shared
 */
class NotificationService: UNNotificationServiceExtension {

	private var contentHandler: ((UNNotificationContent) -> Void)?
	private var bestAttemptContent: UNMutableNotificationContent?

	private var xpcStarted: Bool = false
	private var phoenixStarted: Bool = false
	private var done: Bool = false
	
	private var receivedPayments: [Lightning_kmpIncomingPayment] = []
	
	private var totalTimer: Timer? = nil
	private var postPaymentTimer: Timer? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
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
			self.processRequest(request)
		}
	}
	
	override func serviceExtensionTimeWillExpire() {
		log.trace("serviceExtensionTimeWillExpire()")
		
		// iOS calls this function just before the extension will be terminated by the system.
		// Use this as an opportunity to deliver your "best attempt" at modified content,
		// otherwise the original push payload will be used.
		
		displayPushNotification()
	}
	
	// --------------------------------------------------
	// MARK: Invoice Requests
	// --------------------------------------------------
	
	private func processRequest(_ request: UNNotificationRequest) {
		log.trace("processRequest()")
		assertMainThread()
		
		let userInfo = request.content.userInfo
		log.debug("userInfo: \(userInfo)")
		
		// This could be a push notification coming from either:
		// - Google's Firebas Cloud Messaging (FCM)
		// - Amazon Web Services (AWS)
		
		if PushNotification.isFCM(userInfo: userInfo) {
			processRequest_fcm(request)
		} else {
			processRequest_aws(request)
		}
	}
	
	private func processRequest_fcm(_ request: UNNotificationRequest) {
		log.trace("processRequest_fcm()")
		
		// All FCM notifications are for incoming payments.
		// No custom processing needed here.
	}
	
	private func processRequest_aws(_ request: UNNotificationRequest) {
		log.trace("processRequest_aws()")
		
		let userInfo = request.content.userInfo
		guard
			let acinq = userInfo["acinq"] as? [String: Any]
		else {
			log.error("processRequest: missing/invalid `acinq` section")
			return displayPushNotification()
		}
		
		let type = acinq["t"] as? String ?? "payment"
		if type == "invoice" {
			Task { await processRequest_aws_invoice(request) }
		}
	}
	
	@MainActor
	private func processRequest_aws_invoice(_ request: UNNotificationRequest) async {
		log.trace("processRequest_aws_invoice()")
		assertMainThread()
		
		let userInfo = request.content.userInfo
		guard
			let acinq = userInfo["acinq"] as? [String: Any],
			let nodeId = acinq["n"] as? String, // Todo: verify this is correct
			let amt = acinq["amt"] as? Int,
			let h = acinq["h"] as? String,
			let hBytes = Data(fromHex: h),
			hBytes.count == 32
		else {
			log.error("Invalid `acinq` section !")
			return
		}
		
		let msat = Int64(amt)
		let business = PhoenixManager.shared.business
		
		let invoice: String
		do {
			invoice = try await business.paymentsManager.generateInvoice(
				amount          : Lightning_kmpMilliSatoshi(msat: msat),
				descriptionHash : Bitcoin_kmpByteVector32(bytes: hBytes.toKotlinByteArray()),
				expirySeconds   : (60 * 60 * 24 * 7)
			)
		} catch {
			log.error("Error generating invoice: \(error)")
			return
		}
		
		// TEST THEORY: don't post the invoice until AFTER we're connected
		
		let peer: Lightning_kmpPeer
		do {
			peer = try await business.peerManager.getPeer()
		} catch {
			log.error("Error getting peer: \(error)")
			return
		}
		
		for await state in peer.connectionStateStream() {
			if state is Lightning_kmpConnection.ESTABLISHED {
				break
			}
		}
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlp/enqueue")
		guard let requestUrl = url else {
			log.error("Error generating url")
			return
		}
		
		let body = [
			"node_id"     : nodeId,
			"amount_msat" : msat,
			"ln_invoice"  : invoice
		] as [String : Any]
		let bodyData = try? JSONSerialization.data(
			 withJSONObject: body,
			 options: []
		)
		
		var request = URLRequest(url: requestUrl)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		do {
			log.debug("/lnurlp/enqueue: posting...")
			let (data, response) = try await URLSession.shared.data(for: request)
			
			var statusCode = 418
			var success = false
			if let httpResponse = response as? HTTPURLResponse {
				statusCode = httpResponse.statusCode
				if statusCode >= 200 && statusCode < 300 {
					success = true
				}
			}
			
			if success {
				log.debug("/lnurlp/enqueue: success")
			}
			else {
				log.debug("/lnurlp/enqueue: statusCode: \(statusCode)")
				if let dataString = String(data: data, encoding: .utf8) {
					log.debug("/lnurlp/enqueue: response:\n\(dataString)")
				}
			}
			
		} catch {
			log.debug("/lnurlp/enqueue: error: \(error)")
		}
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
			withTimeInterval : 29.5,
			repeats          : false
		) {[weak self](_: Timer) -> Void in
			
			if let self = self {
				log.debug("totalTimer.fire()")
				self.displayPushNotification()
			}
		}
	}
	
	private func startPostPaymentTimer() {
		log.trace("startPostPaymentTimer()")
		assertMainThread()
		
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
	// MARK: XPC
	// --------------------------------------------------
	
	private func startXpc() {
		log.trace("startXpc()")
		assertMainThread()
		
		if !xpcStarted && !done {
			xpcStarted = true
			
			XpcManager.shared.register {[weak self] in
				self?.didReceiveXpcMessage()
			}
		}
	}
	
	private func stopXpc() {
		log.trace("stopXpc()")
		assertMainThread()
		
		if xpcStarted {
			xpcStarted = false
			
			XpcManager.shared.unregister()
		}
	}
	
	private func didReceiveXpcMessage() {
		log.trace("didReceiveXpcMessage()")
		assertMainThread()
		
		// This means the main phoenix app is running.
		// So we can allow it to handle the payment.
		//
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
		
		if !phoenixStarted && !done {
			phoenixStarted = true
			
			PhoenixManager.shared.register(didReceivePayment: {[weak self](payment: Lightning_kmpIncomingPayment) in
				self?.didReceivePayment(payment)
			})
			PhoenixManager.shared.connect()
		}
	}
	
	private func stopPhoenix() {
		log.trace("stopPhoenix()")
		assertMainThread()
		
		if phoenixStarted {
			phoenixStarted = false
			
			PhoenixManager.shared.disconnect()
			PhoenixManager.shared.unregister()
		}
	}
	
	private func didReceivePayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("didReceivePayment()")
		assertMainThread()
		
		receivedPayments.append(payment)
		startPostPaymentTimer()
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
		stopXpc()
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
