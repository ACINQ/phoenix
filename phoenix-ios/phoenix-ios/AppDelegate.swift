import UIKit
import PhoenixShared
import os.log
import Firebase
import Combine

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
	
    let business: PhoenixBusiness
	
	private var walletLoaded = false
	private var fcmToken: String? = nil
	
	private var badgeCount = 0
	private var cancellables = Set<AnyCancellable>()
	
	private var didIncrementDisconnectCount = false

    override init() {
        setenv("CFNETWORK_DIAGNOSTICS", "3", 1);

        business = PhoenixBusiness(ctx: PlatformContext())
        business.start()
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

        #if DEBUG
            var injectionBundlePath = "/Applications/InjectionIII.app/Contents/Resources"
            #if targetEnvironment(macCatalyst)
                injectionBundlePath = "\(injectionBundlePath)/macOSInjection.bundle"
            #elseif os(iOS)
                injectionBundlePath = "\(injectionBundlePath)/iOSInjection.bundle"
            #elseif os(tvOS)
                injectionBundlePath = "\(injectionBundlePath)/tvOSInjection.bundle"
            #endif
            Bundle(path: injectionBundlePath)?.load()
        #endif

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
		
		if !didIncrementDisconnectCount {
			business.incrementDisconnectCount()
			didIncrementDisconnectCount = true
		}
	}
	
	func _applicationWillEnterForeground(_ application: UIApplication) {
		log.trace("### applicationWillEnterForeground(_:)")
		
		if didIncrementDisconnectCount {
			business.decrementDisconnectCount()
			didIncrementDisconnectCount = false
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
		
		business.decrementDisconnectCount() // allow network connection, even if app in background
		
		var didReceivePayment = false
		var totalTimer: Timer? = nil
		var postPaymentTimer: Timer? = nil
		var watcher: Ktor_ioCloseable? = nil
		
		var isFinished = false
		let Finish = { (_: Timer) -> Void in
			
			if !isFinished {
				isFinished = true
				self.business.incrementDisconnectCount() // balance previous call
				
				totalTimer?.invalidate()
				postPaymentTimer?.invalidate()
				watcher?.close()
				
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
		totalTimer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: false, block: Finish)
		
		var isCurrentValue = true
		let flow = SwiftFlow<Transaction>(origin: business.incomingTransactionFlow())
		watcher = flow.watch { (transaction: Transaction?) in
			assertMainThread()
			if isCurrentValue {
				isCurrentValue = false
				return // from block
			}
			if let transaction = transaction {
				log.info("Background fetch: Payment received !")
				
				didReceivePayment = true
				postPaymentTimer?.invalidate()
				postPaymentTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false, block: Finish)
				self.displayLocalNotification(transaction)
			}
		}
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
	
	func displayLocalNotification(_ transaction: Transaction) {
		
		let handler = {(settings: UNNotificationSettings) -> Void in
			
			// We are having problems interacting with the Transaction instance outside the main thread.
			
			guard settings.authorizationStatus == .authorized else {
				return
			}
			
			let currencyPrefs = CurrencyPrefs()
			let formattedAmt = Utils.format(currencyPrefs, msat: transaction.amountMsat)

			var body: String
			if transaction.desc.count > 0 {
				body = "\(formattedAmt.string): \(transaction.desc)"
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
				identifier: transaction.id,
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
	// MARK: PhoenixBusiness
	// --------------------------------------------------
	
	func loadWallet(mnemonics: [String]) -> Void {
		log.trace("loadWallet(mnemonics:)")
		
		let seed = business.prepWallet(mnemonics: mnemonics, passphrase: "")
		loadWallet(seed: seed)
	}
	
	func loadWallet(seed: KotlinByteArray) -> Void {
		log.trace("loadWallet(seed:)")
		assertMainThread()
		
		if !walletLoaded {
			business.loadWallet(seed: seed)
			walletLoaded = true
			maybeRegisterFcmToken()
		}
	}
	
	func maybeRegisterFcmToken() -> Void {
		log.trace("maybeRegisterFcmToken()")
		assertMainThread()
		
		if walletLoaded && fcmToken != nil {

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
			
			business.registerFcmToken(token: token)
			
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
			// Currently we send off the request to eclair-kmp, and it will perform the registration
			// at some point. If the connection is currently established, it will send the
			// LightningMessage right away. Otherwise, it will send the LightningMessage after
			// establishing the connection.
			//
			// The ideal solution would be to have the server send some kind of Ack for the
			// registration. Which we could then use to trigger a storage in UserDefaults.
		}
	}
}

func assertMainThread() -> Void {
	assert(Thread.isMainThread, "Improper thread: expected main thread; Thread-unsafe code ahead")
}
