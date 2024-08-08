import UIKit
import SwiftUI
import Combine


fileprivate let filename = "SceneDelegate"
#if DEBUG && true
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

	var window: UIWindow?
	var cancellables = Set<AnyCancellable>()
	
	var lockWindow: UIWindow?
	var resetWalletWindow: UIWindow?
	var errorWindow: UIWindow?
	
	var isAppLaunch = true
	var firstUnlockAttempted = false

	static func get() -> SceneDelegate {
		
		return UIApplication.shared.connectedScenes.first!.delegate as! SceneDelegate
	}
	
	func scene(
		_ scene: UIScene,
		willConnectTo session: UISceneSession,
		options connectionOptions: UIScene.ConnectionOptions
	) {
		log.trace("scene(_:willConnectTo:options:)")
		
		if let windowScene = scene as? UIWindowScene {
			showRootWindow(windowScene)
		}
		
		// From Apple docs:
		//
		// > If your app has opted into Scenes, and your app is not running, the system delivers the
		// > URL to the `scene(_:willConnectTo:options:)` delegate method after launch,
		// > and to `scene(_:openURLContexts:)` when your app opens a URL while running or suspended in memory.
		
		if let context = connectionOptions.urlContexts.first {
			log.debug("openURL: \(context.url)")
			
			// At this moment, the UI isn't ready to respond to the externalLightningUrlPublisher.
			// Because it hasn't been displayed yet.
			let url = context.url
			DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
				self.handleExternalURL(url)
			}
		}
		
		if isAppLaunch {
			isAppLaunch = false
			onAppLaunch()
		}
	}

	func sceneDidDisconnect(_ scene: UIScene) {
		log.trace("sceneDidDisconnect()")
		
		// Called as the scene is being released by the system.
		// This occurs shortly after the scene enters the background, or when its session is discarded.
		// Release any resources associated with this scene that can be re-created the next time the
		// scene connects.
		// The scene may re-connect later, as its session was not neccessarily discarded
		// (see `application:didDiscardSceneSessions` instead).
	}

	func sceneDidBecomeActive(_ scene: UIScene) {
		log.trace("sceneDidBecomeActive()")
		
		// Called when the scene has moved from an inactive state to an active state.
		// Use this method to restart any tasks that were paused (or not yet started)
		// when the scene was inactive.
	}

	func sceneWillResignActive(_ scene: UIScene) {
		log.trace("sceneWillResignActive()")
		
		// Called when the scene will move from an active state to an inactive state.
		// This may occur due to temporary interruptions (ex. an incoming phone call).
	}

	func sceneWillEnterForeground(_ scene: UIScene) {
		log.trace("sceneWillEnterForeground()")
	}

	func sceneDidEnterBackground(_ scene: UIScene) {
		log.trace("sceneDidEnterBackground()")
		
		let currentSecurity = AppSecurity.shared.enabledSecurityPublisher.value
		if !currentSecurity.isEmpty {
			
			LockState.shared.isUnlocked = false
			showLockWindow()
			
			// Shortly after this method returns:
			//
			// > UIKit takes a snapshot of your app’s current user interface.
			// > The system displays the resulting image in the app switcher.
			// > It also displays the image temporarily when bringing your app
			// > back to the foreground.
			//
			// We've requested a UI update (via @State change to LockState.shared).
			// But it appears that the update will run AFTER the OS takes the screenshot.
			// So we have to explicitly notify the system than an update is needed / pending.
			//
			lockWindow?.rootViewController?.view.setNeedsLayout()
		}
	}
	
	// --------------------------------------------------
	// MARK: URL Handling
	// --------------------------------------------------
	
	/// From Apple docs:
	///
	/// > If your app has opted into Scenes, and your app is not running, the system delivers the
	/// > URL to the `scene(_:willConnectTo:options:)` delegate method after launch,
	/// > and to `scene(_:openURLContexts:)` when your app opens a URL while running or suspended in memory.
	///
	func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) -> Void {
		log.trace("scene(_:openURLContexts:)")
		
		if let context = URLContexts.first {
			handleExternalURL(context.url)
		}
	}
	
	func handleExternalURL(_ url: URL) {
		log.trace("handleExternalURL")
		
		var urlStr = url.absoluteString
		
		// We support phoenix-specific URLs:
		// phoenix:lightning:lnbc10u1pwjqwkkp...
		//
		// As per issue #186:
		// > Anyone with more than one lightning wallet on iOS has a hard time
		// > having the URI open the correct wallet...
		//
		let trimMatchingPrefix = {(prefix: String) -> Void in
			
			if urlStr.lowercased().starts(with: prefix.lowercased()) {
				
				let startIdx = urlStr.index(urlStr.startIndex, offsetBy: prefix.count)
				let substr = urlStr[startIdx ..< urlStr.endIndex]
				urlStr = String(substr)
			}
		}
		
		trimMatchingPrefix("phoenix://")
		trimMatchingPrefix("phoenix:")
		
		AppDelegate.get().externalLightningUrlPublisher.send(urlStr)
	}
	
	// --------------------------------------------------
	// MARK: App Launch
	// --------------------------------------------------
	
	private func onAppLaunch() -> Void {
		log.trace("onAppLaunch()")
		
		// List for changes to the themePublisher,
		// and update the windows accordingly.
		//
		Prefs.shared.themePublisher.sink {[weak self](theme: Theme) in
			
			guard let self = self else {
				return
			}
			self.window?.overrideUserInterfaceStyle = theme.toInterfaceStyle()
			self.window?.tintColor = UIColor.appAccent // appAccent color might be customized for theme
			
		}.store(in: &cancellables)
		
		// Note that we cannot use `LockState.shared.objectWillChange.sink`,
		// because the callback is invoked before the `isUnlocked` value is changed,
		// and doesn't provide us with the new value.
		//
		LockState.shared.$isUnlocked.sink {[weak self] isUnlocked in
			
			log.debug("LockState.shared.isUnlocked = \(isUnlocked)")
			if isUnlocked {
				self?.hideLockWindow()
			}
		}.store(in: &cancellables)
		
		// Attempting to access the keychain before `protectedDataAvailable` is true
		// will result in the keychain returning an item-not-found error.
		//
		// So we need to wait until the keychain is ready.
		//
		LockState.shared.$protectedDataAvailable.sink {[weak self] protectedDataAvailable in
			
			log.debug("LockState.shared.protectedDataAvailable = \(protectedDataAvailable)")
			if protectedDataAvailable {
				self?.tryFirstUnlock()
			}
		}.store(in: &cancellables)
		
		// We delay our work until any needed migration has been completed.
		//
		LockState.shared.$migrationStepsCompleted.sink {[weak self] migrationStepsCompleted in
			
			log.debug("LockState.shared.migrationStepsCompleted = \(migrationStepsCompleted)")
			if migrationStepsCompleted {
				self?.checkProtectedDataAvailable()
			}
		}.store(in: &cancellables)
	}
	
	private func checkProtectedDataAvailable() {
		log.trace("checkProtectedDataAvailable()")
		assertMainThread()
		
		if UIApplication.shared.isProtectedDataAvailable {
			log.debug("UIApplication.shared.isProtectedDataAvailable == true")
	
			LockState.shared.protectedDataAvailable = true
			
		} else {
			log.debug("UIApplication.shared.isProtectedDataAvailable == false (waiting for notification)")
			
			let nc = NotificationCenter.default
			nc.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification).sink { _ in
				
				// Apple doesn't specify which thread this notification is posted on.
				// Should be the main thread, but just in case, let's be safe.
				if Thread.isMainThread {
					LockState.shared.protectedDataAvailable = true
				} else {
					DispatchQueue.main.async {
						LockState.shared.protectedDataAvailable = true
					}
				}
			}.store(in: &cancellables)
		}
	}
	
	private func tryFirstUnlock() {
		log.trace("tryFirstUnlock()")
		assertMainThread()
		
		guard !firstUnlockAttempted else {
			log.debug("tryFirstUnlock() - ignoring, task already completed")
			return
		}
		firstUnlockAttempted = true
		
		AppSecurity.shared.tryUnlockWithKeychain {
			(recoveryPhrase: RecoveryPhrase?, enabledSecurity: EnabledSecurity, error: UnlockError?) in

			// There are multiple potential configurations:
			//
			// - no wallet         => recoveryPhrase == nil, enabledSecurity is empty
			// - no security       => recoveryPhrase != nil, enabledSecurity is empty
			// - standard security => recoveryPhrase != nil, enabledSecurity is non-empty
			// - advanced security => recoveryPhrase == nil, enabledSecurity is non-empty
			//
			// Another way to think about it:
			// - standard security => biometrics only protect the UI, wallet can immediately be loaded
			// - advanced security => biometrics required to unlock both the UI and the seed

			if recoveryPhrase == nil && enabledSecurity.isEmpty && error == nil {
				// The user doesn't have a wallet yet.
				LockState.shared.walletExistence = .doesNotExist
				
				// Issue #282 - Face ID remains enabled between app installs.
				// Items stored in the iOS keychain remain persisted between iOS installs.
				// So we need to clear the flag here.
				//
				// We have the same problem with the Custom PIN.
				// And also the Bip353Address.
				// So we perform a standard wallet reset (which clears all values).
				AppSecurity.shared.resetWallet()
				
			} else {
				// The user has a wallet. (UI may or may not be locked.)
				LockState.shared.walletExistence = .exists
			}
			
			if let recoveryPhrase {
				// Load wallet into memory. (UI may or may not be locked.)
				Biz.loadWallet(recoveryPhrase: recoveryPhrase)
			}
		
			if let error = error {
				self.showErrorWindow(error)
			} else if enabledSecurity.isEmpty {
				LockState.shared.isUnlocked = true
			} else {
				self.showLockWindow()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Window Transitions
	// --------------------------------------------------
	
	public func transitionToResetWalletWindow(
		deleteTransactionHistory: Bool,
		deleteSeedBackup: Bool
	) {
		log.trace("transitionToResetWalletWindow()")
		assertMainThread()
		
		guard window != nil && resetWalletWindow == nil else {
			return
		}
		
		// We have to be careful to start the close-wallet process *after* we've closed the RootView.
		// This is because we might crash if:
		//
		// - the close-wallet process deletes something
		// - a subview re-renders, and assumes that something still exists
		//
		// For example:
		//
		// - the close-wallet process sets Biz.syncManager to nil
		// - a subview calls `Biz.syncManager!
		//
		// NB: Using a `DispatchQueue.main.asyncAfter` here doesn't seem to be enough to prevent crashes on iPad.
		// We really need to give it time to fully unload the rootWindow.
		
		showResetWalletWindow(
			deleteTransactionHistory: deleteTransactionHistory,
			deleteSeedBackup: deleteSeedBackup,
			startDelay: 0.4
		)
		hideRootWindow()
	}
	
	public func transitionBackToMainWindow() -> Bool {
		log.trace("transitionBackToMainWindow()")
		assertMainThread()
		
		guard window == nil && resetWalletWindow != nil else {
			return false
		}
		guard let windowScene = findWindowScene() else {
			return false
		}
		
		showRootWindow(windowScene)
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
			self.hideResetWalletWindow()
		}
		
		return true
	}
	
	// --------------------------------------------------
	// MARK: Root Window
	// --------------------------------------------------
	
	private func showRootWindow(_ windowScene: UIWindowScene) {
		log.trace("showRootWindow()")
		assertMainThread()
		
		if window == nil {
			
			let view = RootView()
			
			let controller = UIHostingController(rootView: view)
			
			window = UIWindow(windowScene: windowScene)
			window?.rootViewController = controller
			window?.windowLevel = .normal
			
			// Set the app-wide tint/accent color scheme.
			//
			// Credit for bug fix:
			// https://adampaxton.com/how-to-globally-set-a-tint-or-accent-color-in-swiftui/
			//
			window?.tintColor = UIColor.appAccent
			
			// Set the app-wide color scheme.
			//
			// There are other ways to accomplish this, but they are buggy.
			//
			// SwiftUI offers 2 similar methods:
			// - .colorScheme()
			// - .preferredColorScheme()
			//
			// I used multiple variations to make those work, but all variations had bugs.
			// If you use only colorScheme(), certain UI elements like the statusBar don't adapt properly.
			// If you use only the preferredColorScheme, then passing it a non-nil value once will
			// prevent your UI from supporting the system color prior to app re-launch.
			// There may be other variations I didn't try, but this solution is currently the most stable.
			//
			window?.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
		}
		
		window?.makeKeyAndVisible()
	}
	
	private func hideRootWindow() {
		log.trace("hideRootWindow()")
		assertMainThread()
		
		window?.isHidden = true
		window = nil
	}
	
	// --------------------------------------------------
	// MARK: Lock Window
	// --------------------------------------------------
	
	func lockWallet() {
		log.trace("lockWallet")
		
		LockState.shared.isUnlocked = false
		showLockWindow()
	}
	
	private func showLockWindow() {
		log.trace("showLockWindow()")
		assertMainThread()
		
		guard errorWindow == nil else {
			return
		}
		guard let windowScene = findWindowScene() else {
			return
		}
		
		if lockWindow == nil {
			
			let view = LockView()
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			lockWindow = UIWindow(windowScene: windowScene)
			lockWindow?.rootViewController = controller
			lockWindow?.tintColor = UIColor.appAccent
			lockWindow?.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
			lockWindow?.windowLevel = .alert + 1
		}
		
		lockWindow?.makeKeyAndVisible()
	}
	
	private func hideLockWindow() {
		log.trace("hideLockWindow()")
		assertMainThread()
		
		guard errorWindow == nil else {
			return
		}
		
		// Make window the responder for touch events & other input.
		window?.makeKey()
		
		// The LockView uses an animation to dismiss the lock screen.
		// So we wait until after the animation has completed,
		// and then we cleanup our resources (if still needed).
		//
		DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {[weak self] in
			
			guard let self = self else {
				return
			}
			if LockState.shared.isUnlocked {
				
				log.debug("Cleaning up lockWindow resources...")
				
				self.lockWindow?.isHidden = true
				self.lockWindow = nil
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: ResetWallet Window
	// --------------------------------------------------
	
	private func showResetWalletWindow(
		deleteTransactionHistory: Bool,
		deleteSeedBackup: Bool,
		startDelay: TimeInterval
	) {
		log.trace("showResetWalletWindow()")
		assertMainThread()
		
		guard let windowScene = findWindowScene() else {
			return
		}
		
		if resetWalletWindow == nil {
			
			let view = ResetWalletView_Action(
				deleteTransactionHistory: deleteTransactionHistory,
				deleteSeedBackup: deleteSeedBackup,
				startDelay: startDelay
			)
			
			let controller = UIHostingController(rootView: view)
			
			resetWalletWindow = UIWindow(windowScene: windowScene)
			resetWalletWindow?.rootViewController = controller
			resetWalletWindow?.tintColor = UIColor.appAccent
			resetWalletWindow?.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
			resetWalletWindow?.windowLevel = .normal + 1
		}
		
		resetWalletWindow?.makeKeyAndVisible()
	}
	
	private func hideResetWalletWindow() {
		log.trace("hideResetWalletWindow()")
		assertMainThread()
		
		resetWalletWindow?.isHidden = true
		resetWalletWindow = nil
	}
	
	// --------------------------------------------------
	// MARK: Error Window
	// --------------------------------------------------
	
	private func showErrorWindow(_ error: UnlockError) {
		log.trace("showErrorWindow()")
		assertMainThread()
		
		guard let windowScene = findWindowScene() else {
			return
		}
		
		if errorWindow == nil {
			
			let view = UnlockErrorView(danger: error)
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			errorWindow = UIWindow(windowScene: windowScene)
			errorWindow?.rootViewController = controller
			errorWindow?.tintColor = UIColor.appAccent
			errorWindow?.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
			errorWindow?.windowLevel = .alert + 1
		}
		
		errorWindow?.makeKeyAndVisible()
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func findWindowScene() -> UIWindowScene? {
		
		return window?.windowScene ??
			lockWindow?.windowScene ??
			resetWalletWindow?.windowScene ??
			errorWindow?.windowScene ??
			nil
	}
}

