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
	
	// --------------------------------------------------
	// MARK: Finish
	// --------------------------------------------------
	
	private func finish() {
		log.trace("finish()")
		assertMainThread()
		
		guard !srvExtDone else {
			return
		}
		srvExtDone = true
		
		guard let contentHandler, let remoteNotificationContent else {
			log.error("finish(): invalid state")
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
					// This was probably an incoming Bolt 12 payment.
					// But it could be anything, so let's code defensively.
					
					if let item = NotificationServiceQueue.shared.dequeue() {
						updateNotificationContent_localNotification(content, item)
					} else if let payment = popFirstReceivedPayment() {
						updateNotificationContent_receivedPayment(content, payment)
					} else {
						updateNotificationContent_unknown(content)
					}
					
				case .pendingSettlement:
					updateNotificationContent_pendingSettlement(content)
					
				case .unknown:
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
		
		if let groupPrefs, !groupPrefs.discreetNotifications {
			let paymentInfo = WalletPaymentInfo(
				payment: payment,
				metadata: WalletPaymentMetadata.empty(),
				contact: nil
			)
			
			let amountString = formatAmount(groupPrefs, msat: payment.amount.msat)
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
	
	private func formatAmount(_ groupPrefs: GroupPrefs_Wallet, msat: Int64) -> String {
		
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
	}
}
