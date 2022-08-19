import UIKit
import PhoenixShared
import os.log
import Firebase
import Combine
import BackgroundTasks

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppDelegate"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum WalletRestoreType {
	case fromManualEntry
	case fromCloudBackup(name: String?)
}

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, MessagingDelegate {
	
	static func get() -> AppDelegate {
		// In Swift5, we get a runtime warning:
		// > UIApplication.delegate must be used from main thread only
		// 
	//	if !Thread.isMainThread {
	//		log.debug("Accessing UIApplication.shared on non-main thread")
	//	}
		return UIApplication.shared.delegate as! AppDelegate
	}
	
	// There are some places in the code where we need to access the testnet state from a background thread.
	// This is problematic because:
	// - Calling `UIApplication.shared.delegate` from a background thread
	//   produces an annoying runtime warning.
	// - Calling into Kotlin via `business.chain.isTestnet()` from a background thread
	//   will throw an exception.
	//
	// So we're caching this value here for background access.
	// Also, this makes it easier to test mainnet UI & colors.
	//
	private static var _isTestnet: Bool? = nil
	static let isTestnet = _isTestnet!
	static let showTestnetBackground = _isTestnet!
	
	let business: PhoenixBusiness
	
	private var _syncManager: SyncManager? = nil
	var syncManager: SyncManager? { // read-only getter
		_syncManager
	}
	
	private var _encryptedNodeId: String? = nil
	var encryptedNodeId: String? { // read-only getter
		return _encryptedNodeId
	}
	
	private var walletLoaded = false
	private var fcmToken: String? = nil
	private var peerConnectionState: Lightning_kmpConnection? = nil
	
	private var badgeCount = 0
	private var cancellables = Set<AnyCancellable>()
	
	private var isInBackground = false
	
	private var longLivedTask: UIBackgroundTaskIdentifier = .invalid
	
	public var externalLightningUrlPublisher = PassthroughSubject<String, Never>()
	
	// The taskID must match the value in Info.plist
	private let taskId_watchTower = "co.acinq.phoenix.WatchTower"

	override init() {
	#if DEBUG
		setenv("CFNETWORK_DIAGNOSTICS", "3", 1);
	#endif
		business = PhoenixBusiness(ctx: PlatformContext())
		AppDelegate._isTestnet = business.chain.isTestnet()
		super.init()
		AppMigration.performMigrationChecks()
		
		let electrumConfig = Prefs.shared.electrumConfig
		business.appConfigurationManager.updateElectrumConfig(server: electrumConfig?.serverAddress)
		
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: Prefs.shared.fiatCurrency,
			others: Prefs.shared.preferredFiatCurrencies
		)
		business.appConfigurationManager.updatePreferredFiatCurrencies(current: preferredFiatCurrencies)

		let startupParams = StartupParams(requestCheckLegacyChannels: false, isTorEnabled: true)
		business.start(startupParams: startupParams)
	}
	
	// --------------------------------------------------
	// MARK: UIApplication Lifecycle
	// --------------------------------------------------

    internal func application(
		_ application: UIApplication,
		didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
	) -> Bool {

		let navBarAppearance = UINavigationBarAppearance()
		navBarAppearance.backgroundColor = .primaryBackground
		navBarAppearance.shadowColor = .clear // no separator line between navBar & content
		UINavigationBar.appearance().scrollEdgeAppearance = navBarAppearance
		UINavigationBar.appearance().compactAppearance = navBarAppearance
		UINavigationBar.appearance().standardAppearance = navBarAppearance
		
		UITableView.appearance().backgroundColor = .primaryBackground
		
		#if !targetEnvironment(simulator) // push notifications don't work on iOS simulator
			UIApplication.shared.registerForRemoteNotifications()
		#endif
		
		FirebaseApp.configure()
		Messaging.messaging().delegate = self
	
		registerBackgroundTasks()

		let nc = NotificationCenter.default
		
		// Firebase broke application lifecycle functions with their stupid swizzling stuff.
		nc.publisher(for: UIApplication.didBecomeActiveNotification).sink { _ in
			self._applicationDidBecomeActive(application)
		}.store(in: &cancellables)
		
		nc.publisher(for: UIApplication.willResignActiveNotification).sink { _ in
			self._applicationWillResignActive(application)
		}.store(in: &cancellables)
		
		nc.publisher(for: UIApplication.didEnterBackgroundNotification).sink { _ in
			self._applicationDidEnterBackground(application)
		}.store(in: &cancellables)
		
		nc.publisher(for: UIApplication.willEnterForegroundNotification).sink { _ in
			self._applicationWillEnterForeground(application)
		}.store(in: &cancellables)
		
		// Connections observer
		let connectionsManager = business.connectionsManager
		connectionsManager.publisher.sink {(connections: Connections) in
			self.connectionsChanged(connections)
		}.store(in: &cancellables)
		
		// Tor configuration observer
//		Prefs.shared.isTorEnabledPublisher.sink {(isTorEnabled: Bool) in
//			self.business.updateTorUsage(isEnabled: isTorEnabled)
//		}.store(in: &cancellables)
		
		// PreferredFiatCurrenies observers
		Publishers.CombineLatest(
			Prefs.shared.fiatCurrencyPublisher,
			Prefs.shared.currencyConverterListPublisher
		).sink { _ in
			let current = AppConfigurationManager.PreferredFiatCurrencies(
				primary: Prefs.shared.fiatCurrency,
				others: Prefs.shared.preferredFiatCurrencies
			)
			self.business.appConfigurationManager.updatePreferredFiatCurrencies(current: current)
		}.store(in: &cancellables)
		
		return true
	}
	
	/// This function isn't called, because Firebase broke it with their stupid swizzling stuff.
	func applicationDidBecomeActive(_ application: UIApplication) {/* :( */}
	
	/// This function isn't called, because Firebase broke it with their stupid swizzling stuff.
	func applicationWillResignActive(_ application: UIApplication) {/* :( */}
	
	/// This function isn't called, because Firebase broke it with their stupid swizzling stuff.
	func applicationDidEnterBackground(_ application: UIApplication) {/* :( */}
	
	/// This function isn't called, because Firebase broke it with their stupid swizzling stuff.
	func applicationWillEnterForeground(_ application: UIApplication) {/* :( */}
	
	func _applicationDidBecomeActive(_ application: UIApplication) {
		log.trace("### applicationDidBecomeActive(_:)")
		
		UIApplication.shared.applicationIconBadgeNumber = 0
		self.badgeCount = 0
	}
	
	func _applicationWillResignActive(_ application: UIApplication) {
		log.trace("### applicationWillResignActive(_:)")
	}
	
	func _applicationDidEnterBackground(_ application: UIApplication) {
		log.trace("### applicationDidEnterBackground(_:)")
		
		if !isInBackground {
			business.appConnectionsDaemon?.incrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
			isInBackground = true
		}
		
		scheduleBackgroundTasks()
	}
	
	func _applicationWillEnterForeground(_ application: UIApplication) {
		log.trace("### applicationWillEnterForeground(_:)")
		
		if isInBackground {
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
			isInBackground = false
		}
	}
	
	// --------------------------------------------------
	// MARK: UISceneSession Lifecycle
	// --------------------------------------------------

	func application(
		_ application: UIApplication,
		configurationForConnecting connectingSceneSession: UISceneSession,
		options: UIScene.ConnectionOptions
	) -> UISceneConfiguration {
		// Called when a new scene session is being created.
		// Use this method to select a configuration to create the new scene with.
		return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
	}

	func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
		// Called when the user discards a scene session.
		// If any sessions were discarded while the application was not running,
		// this will be called shortly after application:didFinishLaunchingWithOptions.
		// Use this method to release any resources that were specific to the discarded
		// scenes, as they will not return.
	}

	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	func application(
		_ application: UIApplication,
		didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
	) -> Void
	{
		log.trace("application(didRegisterForRemoteNotificationsWithDeviceToken:)")
		
		let pushToken = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
		log.debug("pushToken: \(pushToken)")
		
		Messaging.messaging().apnsToken = deviceToken
	}

	func application(
		_ application: UIApplication,
		didFailToRegisterForRemoteNotificationsWithError error: Error
	) -> Void
	{
		log.trace("application(didFailToRegisterForRemoteNotificationsWithError:)")
		
		log.error("Remote notification support is unavailable due to error: \(error.localizedDescription)")
	}

	func application(
		_ application: UIApplication,
		didReceiveRemoteNotification userInfo: [AnyHashable : Any],
		fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) -> Void
	{
		// Handle incoming remote notification
		
		log.debug("Received remote notification: \(userInfo)")
		
		// allow network connection, even if app in background
		let appConnectionsDaemon = business.appConnectionsDaemon
		let targets =
			AppConnectionsDaemon.ControlTarget.companion.Peer.plus(
				other: AppConnectionsDaemon.ControlTarget.companion.Electrum
			)
		appConnectionsDaemon?.decrementDisconnectCount(target: targets)
		
		var didReceivePayment = false
		var totalTimer: Timer? = nil
		var postPaymentTimer: Timer? = nil
		var publisher: AnyPublisher<Lightning_kmpIncomingPayment, Never>? = nil
		var cancellable: AnyCancellable? = nil
		
		let pushReceivedAt = Date()
		
		var isFinished = false
		let Finish = { (_: Timer) -> Void in
			
			if !isFinished {
				isFinished = true
				
				// balance previous decrement call
				appConnectionsDaemon?.incrementDisconnectCount(target: targets)
				
				totalTimer?.invalidate()
				postPaymentTimer?.invalidate()
				publisher = nil
				cancellable?.cancel()
				
				if didReceivePayment {
					log.info("Background fetch: Cleaning up")
				} else {
					log.info("Background fetch: Didn't receive payment - giving up")
				}
				completionHandler(didReceivePayment ? .newData : .noData)
			}
		}
		
		// The OS gives us 30 seconds to fetch data, and then invoke the completionHandler.
		// Failure to properly "clean up" in this way will result in the OS reprimanding us.
		// So we set a timer to ensure we stop before the max allowed.
		totalTimer = Timer.scheduledTimer(withTimeInterval: 29.0, repeats: false, block: Finish)
		
		publisher = business.paymentsManager.lastIncomingPaymentPublisher()
		cancellable = publisher!.sink(receiveValue: { (payment: Lightning_kmpIncomingPayment) in
				
			assertMainThread()
			
			guard
				let paymentReceivedAt = payment.received?.receivedAtDate,
				paymentReceivedAt > pushReceivedAt
			else {
				// Ignoring - this is the most recently received incomingPayment, but not a new one
				return
			}
			
			log.info("Background fetch: Payment received !")
			
			didReceivePayment = true
			postPaymentTimer?.invalidate()
			postPaymentTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false, block: Finish)
			self.displayLocalNotification_receivedPayment(payment)
		})
	}
	
	func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
		assertMainThread()
		
		log.trace("messaging(:didReceiveRegistrationToken:)")
		log.debug("Firebase registration token: \(String(describing: fcmToken))")
		
		self.fcmToken = fcmToken
		maybeRegisterFcmToken()
	}
	
	// --------------------------------------------------
	// MARK: Local Notifications
	// --------------------------------------------------
	
	func requestPermissionForLocalNotifications(_ callback: @escaping (Bool) -> Void) {
		log.trace("requestPermissionForLocalNotifications()")
		
		let center = UNUserNotificationCenter.current()
		center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
			
			log.debug("UNUserNotificationCenter.requestAuthorization(): granted = \(granted)")
			if let error = error {
				// How can an error possibly occur ?!?
				// Apple doesn't tell us...
				log.debug("UNUserNotificationCenter.requestAuthorization(): \(String(describing: error))")
			}
			
			callback(granted)
		}
	}
	
	func displayLocalNotification_receivedPayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("displayLocalNotification_receivedPayment()")
		
		// We are having problems interacting with the `payment` parameter outside the main thread.
		// This might have to do with the goofy Kotlin freezing stuff.
		// So let's be safe and always operate on the main thread here.
		//
		let handler = {(settings: UNNotificationSettings) -> Void in
			
			guard settings.authorizationStatus == .authorized else {
				return
			}
			
			let currencyPrefs = CurrencyPrefs()
			let formattedAmt = Utils.format(currencyPrefs, msat: payment.amount)

			let paymentInfo = WalletPaymentInfo(
				payment: payment,
				metadata: WalletPaymentMetadata.empty(),
				fetchOptions: WalletPaymentFetchOptions.companion.None
			)
			
			var body: String
			if let desc = paymentInfo.paymentDescription(), desc.count > 0 {
				body = "\(formattedAmt.string): \(desc)"
			} else {
				body = formattedAmt.string
			}
			
			// The user can independently enabled/disable:
			// - alerts
			// - badges
			// So we may only be able to badge the app icon, and that's it.
			self.badgeCount += 1
			
			let content = UNMutableNotificationContent()
			content.title = "Payment received"
			content.body = body
			content.badge = NSNumber(value: self.badgeCount)
			
			let request = UNNotificationRequest(
				identifier: payment.id(),
				content: content,
				trigger: nil
			)
			
			UNUserNotificationCenter.current().add(request) { error in
				if let error = error {
					log.error("NotificationCenter.add(request): error: \(String(describing: error))")
				}
			}
		}
		
		UNUserNotificationCenter.current().getNotificationSettings { settings in
			
			if Thread.isMainThread {
				handler(settings)
			} else {
				DispatchQueue.main.async { handler(settings) }
			}
		}
	}
	
	func displayLocalNotification_revokedCommit() {
		log.trace("displayLocalNotification_revokedCommit()")
		
		let handler = {(settings: UNNotificationSettings) -> Void in
			
			guard settings.authorizationStatus == .authorized else {
				return
			}
			
			self.badgeCount += 1
			
			let content = UNMutableNotificationContent()
			content.title = "Some of your channels have closed"
			content.body = "Please start Phoenix to review your channels."
			content.badge = NSNumber(value: self.badgeCount)
			
			let request = UNNotificationRequest(
				identifier: "revokedCommit",
				content: content,
				trigger: nil
			)
			
			UNUserNotificationCenter.current().add(request) { error in
				if let error = error {
					log.error("NotificationCenter.add(request): error: \(String(describing: error))")
				}
			}
		}
		
		UNUserNotificationCenter.current().getNotificationSettings { settings in
			
			if Thread.isMainThread {
				handler(settings)
			} else {
				DispatchQueue.main.async { handler(settings) }
			}
		}
	}

	// --------------------------------------------------
	// MARK: Long-Lived Tasks
	// --------------------------------------------------
	
	// A long-lived task is for:
	//
	// > when leaving a task unfinished may cause a bad user experience in your app.
	// > For example: to complete disk writes, finish user-initiated requests, network calls, ...
	//
	// For historical reasons, this is also called a "background task".
	// However, in order to differentiate from the new BGTask's introduced in iOS 13,
	// we're now calling these "long-lived tasks".
	
	func setupActivePaymentsListener() -> Void {
		
		business.paymentsManager.inFlightOutgoingPaymentsPublisher().sink { [weak self](count: Int) in
			
			log.debug("inFlightOutgoingPaymentsPublisher: count = \(count)")
			if count > 0 {
				self?.beginLongLivedTask()
			} else {
				self?.endLongLivedTask()
			}
			
		}.store(in: &cancellables)
	}
	
	func beginLongLivedTask() {
		log.trace("beginLongLivedTask()")
		assertMainThread()
		
		if longLivedTask == .invalid {
			longLivedTask = UIApplication.shared.beginBackgroundTask { [weak self] in
				self?.endLongLivedTask()
			}
			log.debug("Invoking: business.decrementDisconnectCount()")
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	func endLongLivedTask() {
		log.trace("endLongLivedTask()")
		assertMainThread()
		
		if longLivedTask != .invalid {
			
			let task = longLivedTask
			longLivedTask = .invalid
			
			UIApplication.shared.endBackgroundTask(task)
			log.debug("Invoking: business.incrementDisconnectCount()")
			business.appConnectionsDaemon?.incrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Background Execution
	// --------------------------------------------------
	
	func registerBackgroundTasks() -> Void {
		log.trace("registerWatchTowerTask()")
		
		BGTaskScheduler.shared.register(
			forTaskWithIdentifier: taskId_watchTower,
			using: DispatchQueue.main
		) { (task) in
			
			if let task = task as? BGAppRefreshTask {
				log.debug("BGTaskScheduler.executeTask: WatchTower")
				
				self.performWatchTowerTask(task)
			}
		}
	}
	
	func scheduleBackgroundTasks(soon: Bool = false) {
		
		// As per the docs:
		// > There can be a total of 1 refresh task and 10 processing tasks scheduled at any time.
		// > Trying to schedule more tasks returns BGTaskScheduler.Error.Code.tooManyPendingTaskRequests.
		
		let task = BGAppRefreshTaskRequest(identifier: taskId_watchTower)
		
		// As per WWDC talk (https://developer.apple.com/videos/play/wwdc2019/707):
		// It's recommended this value be a week or less.
		//
		if soon { // last attempt failed
			task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 4)) // 4 hours
			
		} else { // last attempt succeeded
			task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 24 * 2)) // 2 days
		}
		
	#if !targetEnvironment(simulator) // background tasks not available in simulator
		do {
			try BGTaskScheduler.shared.submit(task)
			log.debug("BGTaskScheduler.submit: success")
		} catch {
			log.error("BGTaskScheduler.submit: \(error.localizedDescription)")
		}
	#endif
	}
	
	/// How to debug this:
	/// https://www.andyibanez.com/posts/modern-background-tasks-ios13/
	///
	func performWatchTowerTask(_ task: BGAppRefreshTask) -> Void {
		log.trace("performWatchTowerTask()")
		
		// kotlin will crash below if we attempt to run this code on non-main thread
		assertMainThread()
		
		let appConnectionsDaemon = business.appConnectionsDaemon
		let electrumTarget = AppConnectionsDaemon.ControlTarget.companion.Electrum
		
		var didDecrement = false
		var upToDateListener: AnyCancellable? = nil
		
		var peer: Lightning_kmpPeer? = nil
		var oldChannels = [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState]()
		
		let cleanup = {(success: Bool) in
			
			if didDecrement { // need to balance decrement call
				appConnectionsDaemon?.incrementDisconnectCount(target: electrumTarget)
			}
			upToDateListener?.cancel()

			var notifyRevokedCommit = false
			let newChannels = peer?.channels ?? [:]

			for (channelId, oldChannel) in oldChannels {
				if let newChannel = newChannels[channelId] {

					var oldHasRevokedCommit = false
					do {
						var oldClosing: Lightning_kmpClosing? = oldChannel.asClosing()
						if oldClosing == nil {
							oldClosing = oldChannel.asOffline()?.state.asClosing()
						}

						if let oldClosing = oldClosing {
							oldHasRevokedCommit = !oldClosing.revokedCommitPublished.isEmpty
						}
					}

					var newHasRevokedCommit = false
					do {
						var newClosing: Lightning_kmpClosing? = newChannel.asClosing()
						if newClosing == nil {
							newClosing = newChannel.asOffline()?.state.asClosing()
						}

						if let newClosing = newChannel.asClosing() {
							newHasRevokedCommit = !newClosing.revokedCommitPublished.isEmpty
						}
					}

					if !oldHasRevokedCommit && newHasRevokedCommit {
						notifyRevokedCommit = true
					}
				}
			}

			if notifyRevokedCommit {
				self.displayLocalNotification_revokedCommit()
			}

			self.scheduleBackgroundTasks(soon: success ? false : true)
			task.setTaskCompleted(success: false)
		}
		
		var isFinished = false
		let finishTask = {(success: Bool) in
			
			DispatchQueue.main.async {
				if !isFinished {
					isFinished = true
					cleanup(success)
				}
			}
		}
		
		task.expirationHandler = {
			finishTask(false)
		}
		
		peer = business.getPeer()
		guard let _peer = peer else {
			// If there's not a peer, then the wallet is locked.
			return finishTask(true)
		}
		
		oldChannels = _peer.channels
		guard oldChannels.count > 0 else {
			// We don't have any channels, so there's nothing to watch.
			return finishTask(true)
		}
		
		appConnectionsDaemon?.decrementDisconnectCount(target: electrumTarget)
		didDecrement = true
		
		// We setup a handler so we know when the WatchTower task has completed.
		// I.e. when the channel subscriptions are considered up-to-date.
		
		upToDateListener = _peer.watcher.upToDatePublisher().sink { (millis: Int64) in
			finishTask(true)
		}
	}
	
	// --------------------------------------------------
	// MARK: PhoenixBusiness
	// --------------------------------------------------
	
	/// Loads the given wallet, and starts the Lightning node.
	///
	/// - Parameters:
	///   - mnemonics: The 12-word recovery phrase
	///   - seed: The seed is extracted from the mnemonics. If you've already performed this
	///           step (i.e. during verification), then pass it here to avoid the duplicate effort.
	///   - walletRestoreType: If restoring a wallet from a backup, pass the type here.
	///
	@discardableResult
	func loadWallet(
		mnemonics: [String],
		seed knownSeed: KotlinByteArray? = nil,
		walletRestoreType: WalletRestoreType? = nil
	) -> Bool {
		
		log.trace("loadWallet()")
		assertMainThread()
		
		guard walletLoaded == false else {
			return false
		}
		
		let seed = knownSeed ?? business.prepWallet(mnemonics: mnemonics, passphrase: "")
		let cloudInfo = business.loadWallet(seed: seed)
		walletLoaded = true
		
		maybeRegisterFcmToken()
		setupActivePaymentsListener()
		
		if let cloudInfo = cloudInfo {
			
			let cloudKey = cloudInfo.first!
			let encryptedNodeId = cloudInfo.second! as String
			
			if let walletRestoreType = walletRestoreType {
				switch walletRestoreType {
				case .fromManualEntry:
					//
					// User is restoring wallet after manually typing in the recovery phrase.
					// So we can mark the manual_backup task as completed.
					//
					Prefs.shared.backupSeed.manualBackup_setTaskDone(true, encryptedNodeId: encryptedNodeId)
					//
					// And ensure cloud backup is disabled for the wallet.
					//
					Prefs.shared.backupSeed.isEnabled = false
					Prefs.shared.backupSeed.setName(nil, encryptedNodeId: encryptedNodeId)
					Prefs.shared.backupSeed.setHasUploadedSeed(false, encryptedNodeId: encryptedNodeId)
					
				case .fromCloudBackup(let name):
					//
					// User is restoring wallet from an existing iCloud backup.
					// So we can mark the iCloud backpu as completed.
					//
					Prefs.shared.backupSeed.isEnabled = true
					Prefs.shared.backupSeed.setName(name, encryptedNodeId: encryptedNodeId)
					Prefs.shared.backupSeed.setHasUploadedSeed(true, encryptedNodeId: encryptedNodeId)
					//
					// And ensure manual backup is diabled for the wallet.
					//
					Prefs.shared.backupSeed.manualBackup_setTaskDone(false, encryptedNodeId: encryptedNodeId)
				}
			}
			
			_encryptedNodeId = encryptedNodeId
			_syncManager = SyncManager(
				chain: business.chain,
				mnemonics: mnemonics,
				cloudKey: cloudKey,
				encryptedNodeId: encryptedNodeId
			)
		}
		
		return true
	}
	
	private func connectionsChanged(_ connections: Connections) -> Void {
		log.trace("connectionsChanged()")
		
		let prvPeerConnectionState = peerConnectionState
		peerConnectionState = connections.peer
		
		if !(prvPeerConnectionState is Lightning_kmpConnection.ESTABLISHED) &&
		   (peerConnectionState is Lightning_kmpConnection.ESTABLISHED)
		{
			maybeRegisterFcmToken()
		}
	}
	
	func maybeRegisterFcmToken() -> Void {
		log.trace("maybeRegisterFcmToken()")
		assertMainThread()
		
		if !walletLoaded {
			log.debug("maybeRegisterFcmToken: no: !walletLoaded")
			return
		}
		if fcmToken == nil {
			log.debug("maybeRegisterFcmToken: no: !fcmToken")
			return
		}
		if !(peerConnectionState is Lightning_kmpConnection.ESTABLISHED) {
			log.debug("maybeRegisterFcmToken: no: !peerConnection")
			return
		}
		
		// It's possible for the user to disable "background app refresh".
		// This is done via:
		// Settings -> General -> Background App Refresh
		//
		// If the user turns this off for Phoenix,
		// then the OS won't deliver silent push notifications.
		// So in this case, we want to register a "null" with the server.
		
		var token = self.fcmToken
		if UIApplication.shared.backgroundRefreshStatus != .available {
			token = nil
		}

		log.debug("registering fcm token: \(token?.description ?? "<nil>")")
		business.registerFcmToken(token: token) { result, error in
			if let e = error {
				log.error("failed to register fcm token: \(e.localizedDescription)")
			}
		}
		
		// Future optimization:
		//
		// Technically, we only need to register the (node_id, fcm_token) with the server once.
		// So we could store this tuple in the UserDefaults system,
		// and then only register with the server if the tuple changes.
		// We even have some code in place to support this.
		// But the problem we currently have is:
		//
		// When do we know for sure that the server has registered our fcm_token ?
		//
		// Currently we send off the request to lightning-kmp, and it will perform the registration
		// at some point. If the connection is currently established, it will send the
		// LightningMessage right away. Otherwise, it will send the LightningMessage after
		// establishing the connection.
		//
		// The ideal solution would be to have the server send some kind of Ack for the
		// registration. Which we could then use to trigger a storage in UserDefaults.
	}
}
