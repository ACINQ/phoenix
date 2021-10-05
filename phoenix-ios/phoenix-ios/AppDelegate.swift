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

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, MessagingDelegate {
	
	static func get() -> AppDelegate {
		UIApplication.shared.delegate as! AppDelegate
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
	var syncManager: SyncManager? {
		_syncManager
	}
	
	private var walletLoaded = false
	private var fcmToken: String? = nil
	private var peerConnection: Lightning_kmpConnection? = nil
	
	private var badgeCount = 0
	private var cancellables = Set<AnyCancellable>()
	
	private var isInBackground = false
	
	private var longLivedTask: UIBackgroundTaskIdentifier = .invalid
	
	public var externalLightningUrlPublisher = PassthroughSubject<URL, Never>()
	
	// The taskID must match the value in Info.plist
	private let taskId_watchTower = "co.acinq.phoenix.WatchTower"

	override init() {
		setenv("CFNETWORK_DIAGNOSTICS", "3", 1);
		business = PhoenixBusiness(ctx: PlatformContext())
		AppDelegate._isTestnet = business.chain.isTestnet()
		super.init()
		performVersionUpgradeChecks()
		business.start()
		
		let electrumConfig = Prefs.shared.electrumConfig
		business.appConfigurationManager.updateElectrumConfig(server: electrumConfig?.serverAddress)
	}
	
	// --------------------------------------------------
	// MARK: UIApplication Lifecycle
	// --------------------------------------------------

    func application(
		_ application: UIApplication,
		didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
	) -> Bool {

		UIKitAppearance()
		
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
		Prefs.shared.isTorEnabledPublisher.sink {[weak self](isTorEnabled: Bool) in
			self?.business.updateTorUsage(isEnabled: isTorEnabled)
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
				target: AppConnectionsDaemon.ControlTarget.all
			)
			isInBackground = true
		}
		
		scheduleBackgroundTasks()
	}
	
	func _applicationWillEnterForeground(_ application: UIApplication) {
		log.trace("### applicationWillEnterForeground(_:)")
		
		if isInBackground {
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.all
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
		let all = AppConnectionsDaemon.ControlTarget.all
		appConnectionsDaemon?.decrementDisconnectCount(target: all)
		
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
				appConnectionsDaemon?.incrementDisconnectCount(target: all)
				
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

			let paymentInfo = WalletPaymentInfo(payment: payment, metadata: WalletPaymentMetadata.empty())
			
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
				target: AppConnectionsDaemon.ControlTarget.all
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
				target: AppConnectionsDaemon.ControlTarget.all
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
		let electrumTarget = AppConnectionsDaemon.ControlTarget.electrum
		
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
	
	func loadWallet(mnemonics: [String]) -> Void {
		log.trace("loadWallet(mnemonics:)")
		assertMainThread()
		
		let seed = business.prepWallet(mnemonics: mnemonics, passphrase: "")
		loadWallet(seed: seed)
	}
	
	func loadWallet(seed: KotlinByteArray) -> Void {
		log.trace("loadWallet(seed:)")
		assertMainThread()
		
		if !walletLoaded {
			let cloudInfo = business.loadWallet(seed: seed)
			walletLoaded = true
			maybeRegisterFcmToken()
			setupActivePaymentsListener()
			
			if let cloudKey = cloudInfo?.first,
				let encryptedNodeId = cloudInfo?.second
			{
				_syncManager = SyncManager(cloudKey: cloudKey, encryptedNodeId: encryptedNodeId as String)
			}
		}
	}
	
	private func connectionsChanged(_ connections: Connections) -> Void {
		log.trace("connectionsChanged()")
		
		let prvPeerConnection = peerConnection
		peerConnection = connections.peer
		
		if prvPeerConnection != Lightning_kmpConnection.established &&
		   peerConnection == Lightning_kmpConnection.established
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
		if peerConnection != Lightning_kmpConnection.established {
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
	
	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	private func performVersionUpgradeChecks() -> Void {
		
		// Upgrade check(s)

		let key = "lastVersionCheck"
		let previousBuild = UserDefaults.standard.string(forKey: key) ?? "3"

		// v0.7.3 (build 4)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		// - we decided to delete old channels database (due to low number of test users)
		//
		if previousBuild.isVersion(lessThan: "4") {
			migrateChannelsDbFiles()
		}

		// v0.7.4 (build 5)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		//
		if previousBuild.isVersion(lessThan: "5") {
			migrateChannelsDbFiles()
		}

		// v0.7.6 (build 7)
		// - adding support for both soft & hard biometrics
		// - previously only supported hard biometics
		//
		if previousBuild.isVersion(lessThan: "7") {
			AppSecurity.shared.performMigration(previousBuild: previousBuild)
		}
		
		// v0.8.0 (build 8)
		// - app db structure has changed
		// - channels/payments db have changed but files are renamed, no need to delete
		//
		if previousBuild.isVersion(lessThan: "8") {
			removeAppDbFile()
		}

		let currentBuild = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
		if previousBuild.isVersion(lessThan: currentBuild) {

			UserDefaults.standard.set(currentBuild, forKey: key)
		}
	}
	
	private func migrateChannelsDbFiles() -> Void {
		
		let fm = FileManager.default
		
		let appSupportDirs = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)
		guard let appSupportDir = appSupportDirs.first else {
			return
		}
		
		let databasesDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		
		let db1 = databasesDir.appendingPathComponent("channels.sqlite", isDirectory: false)
		let db2 = databasesDir.appendingPathComponent("channels.sqlite-shm", isDirectory: false)
		let db3 = databasesDir.appendingPathComponent("channels.sqlite-wal", isDirectory: false)
		
		if !fm.fileExists(atPath: db1.path) &&
		   !fm.fileExists(atPath: db2.path) &&
		   !fm.fileExists(atPath: db3.path)
		{
			// Database files don't exist. So there's nothing to migrate.
			return
		}
		
		let placeholder = "{version}"
		
		let template1 = "channels.\(placeholder).sqlite"
		let template2 = "channels.\(placeholder).sqlite-shm"
		let template3 = "channels.\(placeholder).sqlite-wal"
		
		var done = false
		var version = 0
		
		while !done {
			
			let f1 = template1.replacingOccurrences(of: placeholder, with: String(version))
			let f2 = template2.replacingOccurrences(of: placeholder, with: String(version))
			let f3 = template3.replacingOccurrences(of: placeholder, with: String(version))
			
			let dst1 = databasesDir.appendingPathComponent(f1, isDirectory: false)
			let dst2 = databasesDir.appendingPathComponent(f2, isDirectory: false)
			let dst3 = databasesDir.appendingPathComponent(f3, isDirectory: false)
			
			if fm.fileExists(atPath: dst1.path) ||
			   fm.fileExists(atPath: dst2.path) ||
			   fm.fileExists(atPath: dst2.path)
			{
				version += 1
			} else {
				
				try? fm.moveItem(at: db1, to: dst1)
				try? fm.moveItem(at: db2, to: dst2)
				try? fm.moveItem(at: db3, to: dst3)
				
				done = true
			}
		}
		
		// As a safety precaution (to prevent a crash), always delete the original filePath.
		
		try? fm.removeItem(at: db1)
		try? fm.removeItem(at: db2)
		try? fm.removeItem(at: db3)
		
		// We just migrated the user's channels database.
		// Which means their existing channels are going to get force closed by the server.
		// So we need to inform the user about what just happened.
		
//		popoverState.display(dismissable: false) {
//			PardonOurMess()
//		}
	}
	
	private func removeAppDbFile() {
		let fm = FileManager.default
		
		let appSupportDirs = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)
		guard let appSupportDir = appSupportDirs.first else {
			return
		}
		
		let databasesDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		let db = databasesDir.appendingPathComponent("app.sqlite", isDirectory: false)
		if !fm.fileExists(atPath: db.path) {
			return
		} else {
			try? fm.removeItem(at: db)
		}
	}
}

func assertMainThread() -> Void {
	assert(Thread.isMainThread, "Improper thread: expected main thread; Thread-unsafe code ahead")
}
