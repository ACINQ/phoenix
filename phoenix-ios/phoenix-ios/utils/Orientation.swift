import SwiftUI

/// Credit: Alexander Volkov
/// https://stackoverflow.com/a/67720493/43522
///
/// Basically, `UIDevice.current.orientation` appears to be unreliable.
/// So this workaround is required.
///
struct Orientation {
	 
	/// true - if landscape orientation, false - else
	static var isLandscape: Bool {
		orientation?.isLandscape ?? window?.windowScene?.interfaceOrientation.isLandscape ?? false
	}
	
	/// true - if portrait orientation, false - else
	static var isPortrait: Bool {
		orientation?.isPortrait ?? window?.windowScene?.interfaceOrientation.isPortrait ?? false
	}
	 
	/// valid orientation or nil
	private static var orientation: UIDeviceOrientation? {
		let orientation = UIDevice.current.orientation
		return orientation.isValidInterfaceOrientation ? orientation : nil
	}
	
	/// Current window (for both SwiftUI and storyboard based app)
	private static var window: UIWindow? {
		if let scene = UIApplication.shared.connectedScenes.first {
			if let windowScene = scene as? UIWindowScene,
				let window = windowScene.windows.first {
				return window
			}
			if let windowSceneDelegate = scene.delegate as? UIWindowSceneDelegate,
				let window = windowSceneDelegate.window {
				return window
			}
		}
		return nil
	}
}
