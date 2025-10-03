import Foundation

/// This file is **ONLY** for the main Phoenix app (foreground process)
///
extension AppIdentifier {
	
	static var current: AppId {
		return .foreground
	}
}

