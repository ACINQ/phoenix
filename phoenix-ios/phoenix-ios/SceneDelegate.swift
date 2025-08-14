import UIKit
import SwiftUI
import Combine

fileprivate let filename = "SceneDelegate"
#if DEBUG && true
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

// The default animation duration on iOS is 0.35 seconds.
// But there is a slight delay before the animation starts,
// while the UI state is updated, and the animation is initiated.
// Thus if we only wait 0.35 seconds there is a slight flash at the end
// while window layers disappear before becoming completely invisible on screen.
// So we use a conservative delay to ensure the animation has completed.
//
fileprivate let ANIMATION_DELAY: TimeInterval = 0.4

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
	
	var mainWindow        : UIWindow? = nil
	var lockWindow        : UIWindow? = nil
	var introWindow       : UIWindow? = nil
	var errorWindow       : UIWindow? = nil
	var loadingWindow     : UIWindow? = nil
	var resetWalletWindow : UIWindow? = nil
	
	let loadingWindowLevel_B   : UIWindow.Level = .normal
	let mainWindowLevel        : UIWindow.Level = .normal + 1
	let resetWalletWindowLevel : UIWindow.Level = .normal + 2
	let introWindowLevel       : UIWindow.Level = .normal + 3
	let loadingWindowLevel_A   : UIWindow.Level = .normal + 4
	let lockWindowLevel        : UIWindow.Level = .alert + 1
	let errorWindowLevel       : UIWindow.Level = .alert + 2
	
	var allWindows: [UIWindow?] {
		return [mainWindow, lockWindow, introWindow, errorWindow, loadingWindow, resetWalletWindow]
	}
	
	var isAppLaunch = true
	var cancellables = Set<AnyCancellable>()
	
	static func get() -> SceneDelegate {
		
		return UIApplication.shared.connectedScenes.first!.delegate as! SceneDelegate
	}
	
	// --------------------------------------------------
	// MARK: UIWindowSceneDelegate
	// --------------------------------------------------
	
	func scene(
		_ scene: UIScene,
		willConnectTo session: UISceneSession,
		options connectionOptions: UIScene.ConnectionOptions
	) {
		log.trace(#function)
		
		if let windowScene = scene as? UIWindowScene {
			showLoadingWindow(windowScene)
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
		log.trace(#function)
		
		// Called as the scene is being released by the system.
		// This occurs shortly after the scene enters the background, or when its session is discarded.
		// Release any resources associated with this scene that can be re-created the next time the
		// scene connects.
		// The scene may re-connect later, as its session was not neccessarily discarded
		// (see `application:didDiscardSceneSessions` instead).
	}

	func sceneDidBecomeActive(_ scene: UIScene) {
		log.trace(#function)
		
		// Called when the scene has moved from an inactive state to an active state.
		// Use this method to restart any tasks that were paused (or not yet started)
		// when the scene was inactive.
	}

	func sceneWillResignActive(_ scene: UIScene) {
		log.trace(#function)
		
		// Called when the scene will move from an active state to an inactive state.
		// This may occur due to temporary interruptions (ex. an incoming phone call).
	}

	func sceneWillEnterForeground(_ scene: UIScene) {
		log.trace(#function)
	}

	func sceneDidEnterBackground(_ scene: UIScene) {
		log.trace(#function)
		
		let currentSecurity = Keychain.current.enabledSecurity
		if currentSecurity.hasAppLock() {
			
			AppState.shared.isUnlocked = false
			showLockWindow(target: .automatic)
			
			// Shortly after this method returns:
			//
			// > UIKit takes a snapshot of your app’s current user interface.
			// > The system displays the resulting image in the app switcher.
			// > It also displays the image temporarily when bringing your app
			// > back to the foreground.
			//
			// We've requested a UI update (via @State change to AppState.shared).
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
		log.trace(#function)
		
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
		log.trace(#function)
		
		// List for changes to the themePublisher,
		// and update the windows accordingly.
		//
		Prefs.global.themePublisher.sink {(theme: Theme) in
			
			let interfaceStyle = theme.toInterfaceStyle()
			let tintColor = UIColor.appAccent // appAccent color might be customized for theme
			
			for window in self.allWindows {
				window?.overrideUserInterfaceStyle = interfaceStyle
				window?.tintColor = tintColor
			}
			
		}.store(in: &cancellables)
		
		// Note that we cannot use `AppState.shared.objectWillChange.sink`,
		// because the callback is invoked before the `isUnlocked` value is changed,
		// and doesn't provide us with the new value.
		//
		AppState.shared.$isUnlocked.sink {(isUnlocked: Bool) in
			if isUnlocked {
				log.debug("AppState.isUnlocked = true")
				if self.lockWindow != nil {
					self.hideLockWindow()
				}
			}
		}.store(in: &cancellables)
		
		// Attempting to access the keychain before `protectedDataAvailable` is true
		// will result in the keychain returning an item-not-found error.
		//
		// So we need to wait until the keychain is ready.
		//
		AppState.shared.$protectedDataAvailable.sink {(protectedDataAvailable: Bool) in
			if protectedDataAvailable {
				log.debug("AppState.protectedDataAvailable = true")
				self.readSecurityFile()
			}
		}.store(in: &cancellables)
		
		// We delay our work until any needed migration has been completed.
		//
		AppState.shared.$migrationStepsCompleted.sink {(migrationStepsCompleted: Bool) in
			if migrationStepsCompleted {
				log.debug("AppState.migrationStepsCompleted = true")
				self.checkProtectedDataAvailable()
			}
		}.store(in: &cancellables)
	}
	
	private func checkProtectedDataAvailable() {
		log.trace(#function)
		assertMainThread()
		
		if UIApplication.shared.isProtectedDataAvailable {
			log.debug("UIApplication.shared.isProtectedDataAvailable == true")
	
			AppState.shared.protectedDataAvailable = true
			
		} else {
			log.debug("UIApplication.shared.isProtectedDataAvailable == false (waiting for notification)")
			
			let nc = NotificationCenter.default
			nc.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification).sink { _ in
				
				// Apple doesn't specify which thread this notification is posted on.
				// Should be the main thread, but just in case, let's be safe.
				runOnMainThread {
					AppState.shared.protectedDataAvailable = true
				}
			}.store(in: &cancellables)
		}
	}
	
	private func readSecurityFile() {
		log.trace(#function)
		assertMainThread()
		
		SecurityFileManager.shared.asyncReadFromDisk { result in
			
			switch result {
			case .failure(let reason):
				
				switch reason {
				case .errorReadingFile(_): fallthrough
				case .errorDecodingFile(_):
					self.showErrorWindow(UnlockError.readSecurityFileError(underlying: reason))
					
				case .fileNotFound:
					self.noWalletsAvailable()
					
				} // <switch reason>

			case .success(let securityFile):
				
				switch securityFile {
				case .v0(let v0):
					if v0.keychain != nil || v0.biometrics != nil {
						self.tryWalletUnlock(KEYCHAIN_DEFAULT_ID)
					} else {
						self.noWalletsAvailable()
					}
					
				case .v1(let v1):
					
					if v1.wallets.isEmpty {
						self.noWalletsAvailable()
					} else if let defaultKey = v1.defaultKey, let _ = v1.defaultWallet() {
						self.tryWalletUnlock(defaultKey)
					} else {
						self.showLockWindow(target: .automatic)
						self.hideLoadingWindow()
					}
					
				} // </switch securityFile>
			} // </switch result>
		} // </asyncReadFromDisk>
	}
	
	private func noWalletsAvailable() {
		log.trace(#function)
		assertMainThread()
		
		// The user doesn't have a wallet yet.
		showIntroWindow(animateIn: false, isCancellable: false)
		hideLoadingWindow()
		
		// Items stored in the iOS keychain remain persisted between iOS installs.
		// So we should clear them here.
		//
		// Note that we're clearing the KEYCHAIN_DEFAULT_ID account.
		// If we don't, then when the user creates/restores a wallet,
		// these old default values will get copied into their new account.
		//
		// Related issues:
		// Issue #282 - Face ID remains enabled between app installs.
		// 
		Keychain.current.resetWallet()
	}
	
	private func tryWalletUnlock(_ id: String) {
		log.trace(#function)
		assertMainThread()
		
		Keychain.wallet(id).firstUnlockWithKeychain {
			(recoveryPhrase: RecoveryPhrase?, enabledSecurity: EnabledSecurity, error: UnlockError?) in
			
			if let recoveryPhrase {
				// The user has a wallet.
				// Load it into memory immediately.
				// The UI may or may not be locked.
				Biz.loadWallet(trigger: .appLaunch, recoveryPhrase: recoveryPhrase)
			}
		
			if let error {
				self.showErrorWindow(error)
			} else if enabledSecurity.hasAppLock() {
				AppState.shared.isUnlocked = false
				self.showLockWindow(target: .automatic)
				self.hideLoadingWindow()
			} else {
				AppState.shared.isUnlocked = true
				self.showMainWindow()
				self.hideLoadingWindow()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Window Transitions
	// --------------------------------------------------
	
	func switchToAnotherWallet() {
		log.trace(#function)
		assertMainThread()
		
		AppState.shared.isUnlocked = false
		showLockWindow(target: .walletSelector)
		lockWindow?.isUserInteractionEnabled = false
		
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			self.lockWindow?.isUserInteractionEnabled = true
			self.hideMainWindow()
			MBiz.reset()
		}
	}
	
	func addAnotherWallet() {
		log.trace(#function)
		assertMainThread()
		
		AppState.shared.isUnlocked = false
		showIntroWindow(animateIn: true, isCancellable: true)
		introWindow?.isUserInteractionEnabled = false
		
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			self.introWindow?.isUserInteractionEnabled = true
			self.hideMainWindow()
			MBiz.reset()
		}
	}
	
	func selectWallet(_ wallet: WalletMetadata) {
		log.trace(#function)
		
		tryWalletUnlock(wallet.keychainKeyId)
	}
	
	func lockWallet() {
		log.trace(#function)
		
		AppState.shared.isUnlocked = false
		showLockWindow(target: .automatic)
	}
	
	func cancelIntroWindow() {
		log.trace(#function)
		
		guard introWindow != nil else {
			log.warning("\(#function): ignoring: invalid state")
			return
		}
		
		AppState.shared.isUnlocked = false
		showLockWindow(target: .walletSelector)
		lockWindow?.isUserInteractionEnabled = false
		
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			self.lockWindow?.isUserInteractionEnabled = true
			self.hideIntroWindow()
		}
	}
	
	func finishIntroWindow() {
		log.trace(#function)
		
		guard introWindow != nil else {
			log.warning("\(#function): ignoring: invalid state")
			return
		}
		
		showMainWindow()
		hideIntroWindow()
	}
	
	func startResetWallet(
		_ options: ResetWalletOptions
	) {
		log.trace(#function)
		assertMainThread()
		
		guard mainWindow != nil && resetWalletWindow == nil else {
			log.warning("\(#function): ignoring: invalid state")
			return
		}
		
		// We have to be careful to start the close-wallet process *after* we've closed the MainView.
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
		
		showResetWalletWindow(options, startDelay: (ANIMATION_DELAY * 2))
		resetWalletWindow?.isUserInteractionEnabled = false
		
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			self.resetWalletWindow?.isUserInteractionEnabled = true
			self.hideMainWindow()
		}
	}
	
	func finishResetWallet() {
		log.trace(#function)
		assertMainThread()
		
		guard resetWalletWindow != nil else {
			log.warning("\(#function): ignoring: invalid state")
			return
		}
		
		if SecurityFileManager.shared.hasZeroWallets() {
			
			// We need to transition to the Intro screen,
			// because the user needs to create/restore a wallet to continue.
			
			AppState.shared.isUnlocked = false
			showIntroWindow(animateIn: true, isCancellable: false)
			introWindow?.isUserInteractionEnabled = false
			
			DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
				self.introWindow?.isUserInteractionEnabled = true
				self.hideResetWalletWindow()
			}
			
		} else {
			
			// There are still 1 or more wallets in the system,
			// so we transition to the WalletSelector (LockScreen).
			
			AppState.shared.isUnlocked = false
			showLockWindow(target: .walletSelector)
			lockWindow?.isUserInteractionEnabled = false
		
			DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
				self.lockWindow?.isUserInteractionEnabled = true
				self.hideResetWalletWindow()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Main Window
	// --------------------------------------------------
	
	private func showMainWindow() {
		log.trace(#function)
		assertMainThread()
		
		if mainWindow == nil {
			
			let view = MainView()
			
			let controller = UIHostingController(rootView: view)
			
			mainWindow = UIWindow(windowScene: self.windowScene)
			mainWindow?.rootViewController = controller
			
			// Set the app-wide tint/accent color scheme.
			//
			// Credit for bug fix:
			// https://adampaxton.com/how-to-globally-set-a-tint-or-accent-color-in-swiftui/
			//
			mainWindow?.tintColor = UIColor.appAccent
			
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
			mainWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			
			mainWindow?.windowLevel = mainWindowLevel
			log.debug("mainWindowLevel = \(mainWindowLevel.rawValue)")
		}
		
		mainWindow?.makeKeyAndVisible()
	}
	
	private func hideMainWindow() {
		log.trace(#function)
		assertMainThread()
		
		mainWindow?.isHidden = true
		mainWindow = nil
	}
	
	// --------------------------------------------------
	// MARK: Lock Window
	// --------------------------------------------------
	
	private func showLockWindow(target: LockViewTarget) {
		log.trace(#function)
		assertMainThread()
		
		guard errorWindow == nil else {
			return
		}
		
		if lockWindow == nil {
			
			let view = LockContainer(target: target)
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			lockWindow = UIWindow(windowScene: self.windowScene)
			lockWindow?.rootViewController = controller
			lockWindow?.tintColor = UIColor.appAccent
			lockWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			lockWindow?.windowLevel = lockWindowLevel
			log.debug("lockWindowLevel = \(lockWindowLevel.rawValue)")
		}
		
		lockWindow?.makeKeyAndVisible()
	}
	
	private func hideLockWindow() {
		log.trace(#function)
		assertMainThread()
		
		guard errorWindow == nil else {
			return
		}
		guard lockWindow != nil else {
			return
		}
		
		// The view has a built-in animation to hide it's content.
		// So we wait until after the animation has completed,
		// and then we cleanup our resources (if still needed).
		//
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			if AppState.shared.isUnlocked && self.lockWindow != nil {
				log.debug("Cleaning up lockWindow resources...")
				self.lockWindow?.isHidden = true
				self.lockWindow = nil
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Intro Window
	// --------------------------------------------------
	
	private func showIntroWindow(
		animateIn: Bool,
		isCancellable: Bool
	) {
		log.trace(#function)
		assertMainThread()
		
		if introWindow == nil {
			
			let view = IntroContainer(animateIn: animateIn, isCancellable: isCancellable)
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			introWindow = UIWindow(windowScene: self.windowScene)
			introWindow?.rootViewController = controller
			introWindow?.tintColor = UIColor.appAccent
			introWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			introWindow?.windowLevel = introWindowLevel
			log.debug("introWindowLevel = \(introWindowLevel.rawValue)")
		}
		
		introWindow?.makeKeyAndVisible()
	}
	
	private func hideIntroWindow() {
		log.trace(#function)
		assertMainThread()
		
		guard introWindow != nil else {
			return
		}
		
		// The view has a built-in animation to hide it's content.
		// So we wait until after the animation has completed,
		// and then we cleanup our resources (if still needed).
		//
		DispatchQueue.main.asyncAfter(deadline: .now() + ANIMATION_DELAY) {
			if self.introWindow != nil {
				log.debug("Cleaning up introWindow resources...")
				self.introWindow?.isHidden = true
				self.introWindow = nil
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Error Window
	// --------------------------------------------------
	
	private func showErrorWindow(_ error: UnlockError) {
		log.trace(#function)
		assertMainThread()
		
		if errorWindow == nil {
			
			let view = UnlockErrorView(danger: error)
			
			let controller = UIHostingController(rootView: view)
			
			errorWindow = UIWindow(windowScene: self.windowScene)
			errorWindow?.rootViewController = controller
			errorWindow?.tintColor = UIColor.appAccent
			errorWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			errorWindow?.windowLevel = errorWindowLevel
			log.debug("errorWindowLevel = \(errorWindowLevel.rawValue)")
		}
		
		errorWindow?.makeKeyAndVisible()
	}
	
	// --------------------------------------------------
	// MARK: Loading Window
	// --------------------------------------------------
	
	private func showLoadingWindow(
		_ newWindowScene: UIWindowScene
	) {
		log.trace(#function)
		assertMainThread()
		
		if loadingWindow == nil {
			
			let view = LoadingView()
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			loadingWindow = UIWindow(windowScene: newWindowScene)
			loadingWindow?.rootViewController = controller
			loadingWindow?.tintColor = UIColor.appAccent
			loadingWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			loadingWindow?.windowLevel = loadingWindowLevel_A
			log.debug("loadinloadingWindowLevel_A = \(loadingWindowLevel_A.rawValue)")
		}
		
		loadingWindow?.makeKeyAndVisible()
	}
	
	private func hideLoadingWindow() {
		log.trace(#function)
		assertMainThread()
		
		AppState.shared.isLoading = false
		
		// The loadingWindow manages GlobalEnvironment.deviceInfo.
		// So we never remove it from the view hierarchy.
		// We just move it into the background.
		//
		DispatchQueue.main.asyncAfter(deadline: .now() + LOADING_VIEW_ANIMATION_DURATION + 0.05) {
	
			self.loadingWindow?.windowLevel = self.loadingWindowLevel_B
			log.debug("loadingWindowLevel_B = \(self.loadingWindowLevel_B.rawValue)")
		}
	}
	
	// --------------------------------------------------
	// MARK: ResetWallet Window
	// --------------------------------------------------
	
	private func showResetWalletWindow(_ options: ResetWalletOptions, startDelay: TimeInterval) {
		log.trace(#function)
		assertMainThread()
		
		if resetWalletWindow == nil {
			
			let view = ResetWalletView_Action(
				deleteTransactionHistory: options.deleteTransactionHistory,
				deleteSeedBackup: options.deleteSeedBackup,
				startDelay: startDelay
			)
			
			let controller = UIHostingController(rootView: view)
			controller.view.backgroundColor = .clear
			
			resetWalletWindow = UIWindow(windowScene: self.windowScene)
			resetWalletWindow?.rootViewController = controller
			resetWalletWindow?.tintColor = UIColor.appAccent
			resetWalletWindow?.overrideUserInterfaceStyle = Prefs.global.theme.toInterfaceStyle()
			resetWalletWindow?.windowLevel = resetWalletWindowLevel
			log.debug("resetWalletWindowLevel = \(resetWalletWindowLevel.rawValue)")
		}
		
		resetWalletWindow?.makeKeyAndVisible()
	}
	
	private func hideResetWalletWindow() {
		log.trace(#function)
		assertMainThread()
		
		resetWalletWindow?.isHidden = true
		resetWalletWindow = nil
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	private var windowScene: UIWindowScene {
		
		for window in allWindows {
			if let scene = window?.windowScene { return scene }
		}
		
		fatalError("Unabled to find UIWindowScene")
	}
}

