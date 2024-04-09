import UIKit
import PhoenixShared
import Firebase
import Combine
import BackgroundTasks

fileprivate let filename = "AppDelegate"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif


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
	
	private var appCancellables = Set<AnyCancellable>()
	private var groupPrefsCancellables = Set<AnyCancellable>()

	private var isInBackground = false
	private var xpc: CrossProcessCommunication? = nil
	
	public var externalLightningUrlPublisher = PassthroughSubject<String, Never>()

	public var clearPasteboardOnReturnToApp: Bool = false

	
	override init() {
	#if DEBUG
		setenv("CFNETWORK_DIAGNOSTICS", "3", 1);
	#endif
		super.init()
		AppMigration.shared.performMigrationChecks()
		Biz.start()
	}
	
	// --------------------------------------------------
	// MARK: UIApplication Lifecycle
	// --------------------------------------------------

    internal func application(
		_ application: UIApplication,
		didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
	) -> Bool {
		log.trace("### application(_:didFinishLaunchingWithOptions:)")
		
		let navBarAppearance = UINavigationBarAppearance()
		navBarAppearance.backgroundColor = .primaryBackground
		navBarAppearance.shadowColor = .clear // no separator line between navBar & content
		UINavigationBar.appearance().scrollEdgeAppearance = navBarAppearance
		UINavigationBar.appearance().compactAppearance = navBarAppearance
		UINavigationBar.appearance().standardAppearance = navBarAppearance
		
		if #available(iOS 16, *) {
			// In iOS 16, List uses UICollectionView instead of UITableView.
			// So this does have an effect:
			// UICollectionView.appearance().backgroundColor = .primaryBackground
			//
			// However, in iOS 16 there's a better option:
			// .listBackgroundColor(.someColor)
			//
			// Furthermore, if you set the global appearance option as above,
			// then it will *override* the per-list settings via .listBackgroundColor().
			// And we prefer to have more fine-grained control.
			// For example, in the CurrencyConverterView we're instead using
			// .listBackgroundColor(Color(.systemBackground))

		} else {
			// iOS 15
			UITableView.appearance().backgroundColor = .primaryBackground
			UICollectionView.appearance().backgroundColor = .primaryBackground
		}

		#if !targetEnvironment(simulator) // push notifications don't work on iOS simulator
			UIApplication.shared.registerForRemoteNotifications()
		#endif
		
		FirebaseApp.configure()
		Messaging.messaging().delegate = self

		let nc = NotificationCenter.default
		
		// Firebase broke application lifecycle functions with their stupid swizzling stuff.
		nc.publisher(for: UIApplication.didBecomeActiveNotification).sink { _ in
			self._applicationDidBecomeActive(application)
		}.store(in: &appCancellables)
		
		nc.publisher(for: UIApplication.willResignActiveNotification).sink { _ in
			self._applicationWillResignActive(application)
		}.store(in: &appCancellables)

		nc.publisher(for: UIApplication.willEnterForegroundNotification).sink { _ in
			self._applicationWillEnterForeground()
		}.store(in: &appCancellables)
		
		nc.publisher(for: UIApplication.didEnterBackgroundNotification).sink { _ in
			self._applicationDidEnterBackground()
		}.store(in: &appCancellables)
		
		xpc = CrossProcessCommunication(actor: .mainApp, receivedMessage: {(_: XpcMessage) in
			self.didReceivePaymentViaAppExtension()
		})
		
		NotificationsManager.shared.requestPermissionForProvisionalNotifications()
		
		return true
	}
	
	// The following functions are not called,
	// because Firebase broke it with their stupid swizzling stuff.
	// 
	func applicationDidBecomeActive(_ application: UIApplication) {/* :( */}
	func applicationWillResignActive(_ application: UIApplication) {/* :( */}
//	func applicationWillEnterForeground(_ application: UIApplication) {/* :( */}
//	func applicationDidEnterBackground(_ application: UIApplication) {/* :( */}
	
	func _applicationDidBecomeActive(_ application: UIApplication) {
		log.trace("### applicationDidBecomeActive(_:)")
		
		GroupPrefs.shared.badgeCountPublisher.sink {[self](count: Int) in
			if count > 0 {
				self.didReceivePaymentViaAppExtension()
				GroupPrefs.shared.badgeCount = 0
				UIApplication.shared.applicationIconBadgeNumber = 0
			}
		}.store(in: &groupPrefsCancellables)
		
		// We've had reports of the app's badge (number) not getting cleared:
		// https://github.com/ACINQ/phoenix/issues/451
		//
		// The implementation assumes that `GroupPrefs.badgeCount` & `UIApp.applicationIconBadgeNumber`
		// are always in-sync. But if somehow they get out-of-sync, then the bug would reproduce.
		// It's not entirely clear how that would happen...
		// but it's safe to always clear the badge here anyways.
		//
		UIApplication.shared.applicationIconBadgeNumber = 0
		
		if clearPasteboardOnReturnToApp {
			if UIPasteboard.general.hasStrings {
				UIPasteboard.general.string = ""
			}
			clearPasteboardOnReturnToApp = false
		}
	}
	
	func _applicationWillResignActive(_ application: UIApplication) {
		log.trace("### applicationWillResignActive(_:)")

		groupPrefsCancellables.removeAll()
	}
	
	func _applicationWillEnterForeground() {
		log.trace("### applicationWillEnterForeground()")
		
		if isInBackground {
			isInBackground = false
			xpc?.resume()
		}
	}
	
	func _applicationDidEnterBackground() {
		log.trace("### applicationDidEnterBackground()")
		
		if !isInBackground {
			isInBackground = true
			xpc?.suspend()
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
	) {
		log.trace("application(_:didRegisterForRemoteNotificationsWithDeviceToken:)")
		
		let pushToken = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
		log.debug("pushToken: \(pushToken)")
		
		Biz.setPushToken(pushToken)
		Messaging.messaging().apnsToken = deviceToken
	}

	func application(
		_ application: UIApplication,
		didFailToRegisterForRemoteNotificationsWithError error: Error
	) {
		log.trace("application(_:didFailToRegisterForRemoteNotificationsWithError:)")
		log.error("Remote notification support is unavailable due to error: \(error.localizedDescription)")
	}

	func application(
		_ application: UIApplication,
		didReceiveRemoteNotification userInfo: [AnyHashable : Any],
		fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		log.trace("application(_:didReceiveRemoteNotification:fetchCompletionHandler:)")
		log.debug("remote notification: \(userInfo)")

		// If the app is in the foreground:
		// - we can ignore this notification
		//
		// If the app is in the background:
		// - this notification was delivered to the notifySrvExt, which is in charge of processing it

		DispatchQueue.main.async {
			completionHandler(.noData)
		}
	}
	
	func messaging(
		_ messaging: Messaging,
		didReceiveRegistrationToken fcmToken: String?
	) {
		log.trace("messaging(_:didReceiveRegistrationToken:)")
		log.debug("Firebase registration token: \(String(describing: fcmToken))")
		
		assertMainThread()
		
		if let fcmToken = fcmToken {
			Biz.setFcmToken(fcmToken)
		}
	}

	// --------------------------------------------------
	// MARK: CrossProcessCommunication
	// --------------------------------------------------

	private func didReceivePaymentViaAppExtension() {
		log.trace("didReceivePaymentViaAppExtension()")
		
		// This function is called when:
		// - phoenix was running in the background
		// - a received push notification launched our notification-service-extension
		// - our app extension received an incoming payment
		// - the user returns to phoenix app
		//
		// So our app extension may have updated the database.
		// However, we don't know about all these changes yet...
		//
		// This is because the SQLDelight query flows do NOT automatically update
		// if changes occur in a separate process. Within SQLDelight there is:
		//
		// `TransactorImpl.notifyQueries(...)`
		//
		// This function needs to get called in order for the flows to re-perform
		// their query, and update their state.
		//
		// So there are 2 ways in which we can accomplish this:
		// - Jump thru a bunch of hoops to subclass the SqlDriver,
		//   and then add a custom transaction that calls invokes notifyQueries
		//   with the appropriate parameters.
		// - Just make some no-op calls, which automatically invoke notifyQueries for us.
		//
		// We're using the easier option for now.
		// Especially since there are changes in the upcoming v2.0 release of SQLDelight
		// that change the corresponding API, and aim to make it more accesible for us.

		let business = Biz.business
		Task { @MainActor in
			let paymentsDb = try await business.databaseManager.paymentsDb()
			
			let fakePaymentId = WalletPaymentId.IncomingPaymentId(paymentHash: Bitcoin_kmpByteVector32.random())
			try await paymentsDb.deletePayment(paymentId: fakePaymentId)
		}
		Task { @MainActor in
			try await business.appDb.deleteBitcoinRate(fiat: "FakeFiatCurrency")
		}
	}
}
