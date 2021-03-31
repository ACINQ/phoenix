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


class SceneDelegate: UIResponder, UIWindowSceneDelegate {

	var window: UIWindow?
	var cancellables = Set<AnyCancellable>()

	func scene(
		_ scene: UIScene,
		willConnectTo session: UISceneSession,
		options connectionOptions: UIScene.ConnectionOptions
	) {
		log.trace("scene(_:willConnectTo:options:)")
		
		let contentView = ContentView()
		
		if let windowScene = scene as? UIWindowScene {
			
			let window = UIWindow(windowScene: windowScene)
		   window.rootViewController = UIHostingController(rootView: contentView)
			self.window = window
			window.makeKeyAndVisible()
			window.overrideUserInterfaceStyle = Prefs.shared.theme.toInterfaceStyle() // see note below
			
			Prefs.shared.themePublisher.sink {[weak self](theme: Theme) in
				self?.window?.overrideUserInterfaceStyle = theme.toInterfaceStyle()
			}.store(in: &cancellables)
			
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
	}

	func sceneDidDisconnect(_ scene: UIScene) {
		// Called as the scene is being released by the system.
		// This occurs shortly after the scene enters the background, or when its session is discarded.
		// Release any resources associated with this scene that can be re-created the next time the
		// scene connects.
		// The scene may re-connect later, as its session was not neccessarily discarded
		// (see `application:didDiscardSceneSessions` instead).
	}

	func sceneDidBecomeActive(_ scene: UIScene) {
		// Called when the scene has moved from an inactive state to an active state.
		// Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
	}

	func sceneWillResignActive(_ scene: UIScene) {
		// Called when the scene will move from an active state to an inactive state.
		// This may occur due to temporary interruptions (ex. an incoming phone call).
	}

	func sceneWillEnterForeground(_ scene: UIScene) {
		// Called as the scene transitions from the background to the foreground.
		// Use this method to undo the changes made on entering the background.
	}

	func sceneDidEnterBackground(_ scene: UIScene) {
        
		// Shortly after this method returns:
		//
		// > UIKit takes a snapshot of your app’s current user interface.
		// > The system displays the resulting image in the app switcher.
		// > It also displays the image temporarily when bringing your app
		// > back to the foreground.
		//
		// ContentView.onDidEnterBackground() will request a UI update (via a State change).
		// But it appears that the update will run AFTER the OS takes the screenshot.
		// So we have to explicitly notify the system than an update is needed / pending.
		//
		if let viewController = window?.rootViewController {
			viewController.view.setNeedsLayout()
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
}

