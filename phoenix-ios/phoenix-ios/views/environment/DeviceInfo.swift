import Foundation
import UIKit
import SwiftUI
import LocalAuthentication

/// Represents the availability of Biometrics on the current device.
/// Devices either support TouchID or FaceID,
/// but the user needs to have enabled and enrolled in the service.
///
enum BiometricSupport {
	
	case touchID_available
	case touchID_notAvailable
	case touchID_notEnrolled
	
	case faceID_available
	case faceID_notAvailable
	case faceID_notEnrolled
	
	case notAvailable
	
	func isAvailable() -> Bool {
		return (self == .touchID_available) || (self == .faceID_available)
	}
}

/// An ObservableObject that provides device info & categories.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var deviceInfo: DeviceInfo
///
class DeviceInfo: ObservableObject {
	
	@Published var _windowSize: CGSize = .zero
	@Published var windowSafeArea: EdgeInsets = EdgeInsets()
	
	/// Readers should use this instead of the published `_windowSize`,
	/// because it's zero on the first layout pass.
	///
	var windowSize: CGSize {
		var windowSize = _windowSize
		if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
			if let window = windowScene.windows.first {
				windowSize = window.bounds.size
			}
		}
		return windowSize
	}
	
	var isIPhone: Bool {
		return UIDevice.current.userInterfaceIdiom == .phone
	}
	
	var isIPad: Bool {
		return UIDevice.current.userInterfaceIdiom == .pad
	}
	
	var isLandscape: Bool {
		return Orientation.isLandscape
	}
	
	var isPortrait: Bool {
		return Orientation.isPortrait
	}
	
	var isIPadLandscapeFullscreen: Bool {
		if UIDevice.current.userInterfaceIdiom != .pad {
			return false
		}
		if !self.isLandscape {
			return false
		}
		
		return self.windowSize == UIScreen.main.bounds.size
	}
	
	var isFaceID: Bool {
		switch Self.biometricSupport() {
			case .faceID_available    : return true
			case .faceID_notAvailable : return true
			case .faceID_notEnrolled  : return true
			default                   : return false
		}
	}
	
	// iPod touch (gen 5+) - 320x568 pt (@2x)
	//
	// iPhone SE (2016)    - 320x568 pt (@2x)
	// iPhone SE (2020)    - 375x667 pt (@2x)
	// iPhone SE (2022)    - 375x667 pt (@2x)
	//
	// iPhone 6s           - 375x667 pt (@2x)
	// iPhone 6s Plus      - 414x736 pt (@3x)
	//
	// iPhone 7            - 375x667 pt (@2x)
	// iPhone 7 Plus       - 414x736 pt (@3x)
	//
	// iPhone 8            - 375x667 pt (@2x)
	// iPhone 8 Plus       - 414x736 pt (@3x)
	//
	// iPhone X            - 375x812 pt (@3x)
	// iPhone XR           - 414x896 pt (@2x)
	// iPhone XS           - 375x812 pt (@3x)
	// iPhone XS Max       - 414x896 pt (@3x)
	//
	// iPhone 11           - 414x896 pt (@2x)
	// iPhone 11 Pro       - 375x812 pt (@3x)
	// iPhone 11 Pro Max   - 414x896 pt (@3x)
	//
	// iPhone 12 mini      - 375x812 pt (@3x)
	// iPhone 12           - 390x844 pt (@3x)
	// iPhone 12 Pro       - 390x844 pt (@3x)
	// iPhone 12 Pro Max   - 428x926 pt (@3x)
	//
	// iPhone 13 mini      - 375x812 pt (@3x)
	// iPhone 13           - 390x844 pt (@3x)
	// iPhone 13 Pro       - 390x844 pt (@3x)
	// iPhone 13 Pro Max   - 428x926 pt (@3x)
	//
	// iPad Pro 9.7-inch   -  768x1024 pt
	// iPad Pro 10.5-inch  - 1668x2244 pt
	// iPad Pro 11-inch    - 1668x2388 pt
	// iPad Pro 12.9-inch  - 2048x2732 pt
	//
	// iPad Mini 6th gen   - 744x1133 pt
	
	var isShortHeight: Bool {
		// - iPod touch (gen 5+)
		// - iPhone SE (2016)
		// - iPhone SE (2020)
		// - iPhone 6s
		// - iPhone 7
		// - iPhone 8
  		return UIScreen.main.bounds.height < 700
	}
	
	var isSmallWidth: Bool {
		// - iPod touch (gen 5+)
		// - iPhone SE (2016)
		return UIScreen.main.bounds.width < 350
	}
	
	var isAverageWidth: Bool {
		// - iPhone SE (2020)
		// - iPhone 6s
		// - iPhone 7
		// - iPhone 8
		// - iPhone X
		// - iPhone XS
		// - iPhone 11 Pro
		// - iPhone 12 mini
		// - iPhone 12
		// - iPhone 12 Pro
		// - iPhone 13 mini
		// - iPhone 13
		// - iPhone 13 Pro
		let width = UIScreen.main.bounds.width
		return width >= 350 && width < 400
	}
	
	/// Used to limit the width of text-heavy content on iPad.
	///
	var textColumnMaxWidth: CGFloat {
		return DeviceInfo.textColumnMaxWidth
	}
	
	static var textColumnMaxWidth: CGFloat {
		return 500
	}
	
	/// Returns the device's current status concerning biometric support.
	///
	static func biometricSupport() -> BiometricSupport {
		
		let context = LAContext()
		
		var error : NSError?
		let result = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
		
		if context.biometryType == .touchID {
			if result && (error == nil) {
				return .touchID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .touchID_notEnrolled
				} else {
					return .touchID_notAvailable
				}
			}
		}
		if context.biometryType == .faceID {
			if result && (error == nil) {
				return .faceID_available
			} else {
				if let error = error as? LAError, error.code == .biometryNotEnrolled {
					return .faceID_notEnrolled
				} else {
					return .faceID_notAvailable
				}
			}
		}
		
		return .notAvailable
	}
}
