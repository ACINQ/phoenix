import Foundation

/// This file is **ONLY** for the Notify-Service-Extension (background process)
///
extension AppIdentifier {
	
	static var current: AppId {
		return .background
	}
}
