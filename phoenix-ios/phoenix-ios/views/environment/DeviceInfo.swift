import Foundation
import UIKit

/// An ObservableObject that provides device info & categories.
/// Available as an EnvironmentObject:
///
/// @EnvironmentObject var deviceInfo: DeviceInfo
///
class DeviceInfo: ObservableObject {
	
	// iPod touch (gen 5+) - 320x568 pt (640x1136 px @2x)
	//
	// iPhone SE (2016)    - 320x568 pt (640x1136 px @2x)
	// iPhone SE (2020)    - 375x667 pt (750x1334 px @2x)
	//
	// iPhone 6s           - 375x667 pt (750x1334 px @2x)
	// iPhone 6s Plus      - 414x736 pt (1080x1920 px @3x)
	//
	// iPhone 7            - 375x667 pt (750x1334 px @2x)
	// iPhone 7 Plus       - 414x736 pt (1080x1920 px @3x)
	//
	// iPhone 8            - 375x667 pt (750x1334 px @2x)
	// iPhone 8 Plus       - 414x736 pt (1080x1920 px @3x)
	//
	// iPhone X            - 375x812 pt (1125x2436 px @3x)
	// iPhone XR           - 414x896 pt (828x1792 px @2x)
	// iPhone XS           - 375x812 pt (1125x2436 px @3x)
	// iPhone XS Max       - 414x896 pt (1242x2688 px @3x)
	//
	// iPhone 11           - 414x896 pt (828x1792 px @2x)
	// iPhone 11 Pro       - 375x812 pt (1125x2436 px @3x)
	// iPhone 11 Pro Max   - 414x896 pt (1242x2688 px @3x)
	//
	// iPhone 12 mini      - 375x812 pt (1125x2436 px @3x)
	// iPhone 12           - 390x844 pt (1170x2532 px @3x)
	// iPhone 12 Pro       - 390x844 pt (1170x2532 px @3x)
	// iPhone 12 Pro Max   - 428x926 pt (1284x2778 px @3x)
	//
	// iPhone 13 mini      - 375x812 pt (1125x2436 px @3x)
	// iPhone 13           - 390x844 pt (1170x2532 px @3x)
	// iPhone 13 Pro       - 390x844 pt (1170x2532 px @3x)
	// iPhone 13 Pro Max   - 428x926 pt (1284x2778 px @3x)
	
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
}
