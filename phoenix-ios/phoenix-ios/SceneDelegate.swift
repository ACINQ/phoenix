import UIKit
import SwiftUI
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SceneDelegate"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

class LockState: ObservableObject {
	@Published var isUnlocked: Bool

	init(isUnlocked: Bool) {
		self.isUnlocked = isUnlocked
	}
}

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

	var window: UIWindow?
	var cancellables = Set<AnyCancellable>()
	
	var lockWindow: UIWindow?
	var errorWindow: UIWindow?
	
	let lockState = LockState(isUnlocked: false)
	
	var isAppLaunch = true

	func scene(
		_ scene: UIScene,
		willConnectTo session: UISceneSession,
		options connectionOptions: UIScene.ConnectionOptions
	) {
		log.trace("scene(_:willConnectTo:options:)")
		
		if let windowScene = scene as? UIWindowScene {
			
			let contentView = ContentView(lockState: lockState)
			
			let window = UIWindow(windowScene: windowScene)
			window.rootViewController = UIHostingController(rootView: contentView)
			window.windowLevel = .normal
			
			// Set the app-wide tint/accent color scheme.
			//
			// Credit for bug fix:
			// https://adampaxton.com/how-to-globally-set-a-tint-or-accent-color-in-swiftui/
			//
			window.tintColor = UIColor.appAccent
			
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
			window.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
			
			Prefs.shared.themePublisher.sink {[weak self](theme: Theme) in
				
				self?.window?.overrideUserInterfaceStyle = theme.toInterfaceStyle()
				self?.window?.tintColor = UIColor.appAccent // appAccent color might be customized for theme
				
			}.store(in: &cancellables)
			
			self.window = window
			window.makeKeyAndVisible()
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
				AppDelegate.get().externalLightningUrlPublisher.send(url)
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
		// Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
	}

	func sceneWillResignActive(_ scene: UIScene) {
		log.trace("sceneWillResignActive()")
		
		// Called when the scene will move from an active state to an inactive state.
		// This may occur due to temporary interruptions (ex. an incoming phone call).
	}

	func sceneWillEnterForeground(_ scene: UIScene) {
		log.trace("sceneWillEnterForeground()")
		
		// Called as the scene transitions from the background to the foreground.
		// Use this method to undo the changes made on entering the background.
	}

	func sceneDidEnterBackground(_ scene: UIScene) {
		log.trace("sceneDidEnterBackground()")
		
		let currentSecurity = AppSecurity.shared.enabledSecurity.value
		if !currentSecurity.isEmpty {
			
			lockState.isUnlocked = false
			showLockWindow()
			
			// Shortly after this method returns:
			//
			// > UIKit takes a snapshot of your app’s current user interface.
			// > The system displays the resulting image in the app switcher.
			// > It also displays the image temporarily when bringing your app
			// > back to the foreground.
			//
			// We've requested a UI update (via @State change to lockState).
			// But it appears that the update will run AFTER the OS takes the screenshot.
			// So we have to explicitly notify the system than an update is needed / pending.
			//
			lockWindow?.rootViewController?.view.setNeedsLayout()
		}
	}
	
	/// From Apple docs:
	///
	/// > If your app has opted into Scenes, and your app is not running, the system delivers the
	/// > URL to the `scene(_:willConnectTo:options:)` delegate method after launch,
	/// > and to `scene(_:openURLContexts:)` when your app opens a URL while running or suspended in memory.
	///
	func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) -> Void {
		log.trace("scene(_:openURLContexts:)")
		
		if let context = URLContexts.first {
			log.debug("openURL: \(context.url)")
			AppDelegate.get().externalLightningUrlPublisher.send(context.url)
		}
	}
	
	// --------------------------------------------------
	// MARK: App Launch
	// --------------------------------------------------
	
	private func onAppLaunch() -> Void {
		log.trace("onAppLaunch()")
		
		// Note that we cannot use `lockState.objectWillChange.sink`,
		// because the callback is invoked before the lockState is changed,
		// and doesn't provide us with the new value.
		//
		lockState.$isUnlocked.sink {[weak self] isUnlocked in
			
			log.debug("lockState.isUnlocked = \(isUnlocked)")
			if isUnlocked {
				self?.hideLockWindow()
			}
		}.store(in: &cancellables)
		
		AppSecurity.shared.tryUnlockWithKeychain {
			(mnemonics: [String]?, enabledSecurity: EnabledSecurity, danger: LossOfSeedDanger?) in

			// There are multiple potential configurations:
			//
			// - no security       => mnemonics are available, enabledSecurity is empty
			// - standard security => mnemonics are available, enabledSecurity is non-empty
			// - advanced security => mnemonics are not available, enabledSecurity is non-empty
			//
			// Another way to think about it:
			// - standard security => biometrics only protect the UI, wallet can immediately be loaded
			// - advanced security => biometrics required to unlock both the UI and the seed

			if let mnemonics = mnemonics {
				// unlock & load wallet
				AppDelegate.get().loadWallet(mnemonics: mnemonics)
			}
			
			if let danger = danger {
				self.showErrorWindow(danger)
			} else if enabledSecurity.isEmpty {
				self.lockState.isUnlocked = true
			} else {
				self.showLockWindow()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: App Switcher Privacy
	// --------------------------------------------------
	
	private func showLockWindow() {
		log.trace("showLockWindow()")
		
		guard errorWindow == nil else {
			return
		}
		guard let windowScene = self.window?.windowScene else {
			return
		}
		
		if lockWindow == nil {
			
			let lockView = LockView(lockState: lockState)
			
			let controller = UIHostingController(rootView: lockView)
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
			if self.lockState.isUnlocked {
				
				log.debug("Cleaning up lockWindow resources...")
				
				self.lockWindow?.isHidden = true
				self.lockWindow = nil
			}
		}
	}
	
	private func showErrorWindow(_ danger: LossOfSeedDanger) {
		log.trace("showErrorWindow()")
		
		guard let windowScene = self.window?.windowScene else {
			return
		}
		
		if errorWindow == nil {
			
			let errorView = ErrorView(danger: danger)
			
			let controller = UIHostingController(rootView: errorView)
			controller.view.backgroundColor = .clear
			
			errorWindow = UIWindow(windowScene: windowScene)
			errorWindow?.rootViewController = controller
			errorWindow?.tintColor = UIColor.appAccent
			errorWindow?.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle()
			errorWindow?.windowLevel = .alert + 1
		}
		
		errorWindow?.makeKeyAndVisible()
	}
}

