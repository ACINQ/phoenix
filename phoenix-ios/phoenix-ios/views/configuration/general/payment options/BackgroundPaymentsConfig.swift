import UIKit


enum BackgroundPaymentsConfig: Equatable, CustomStringConvertible {
	
	/// Default settings for provisional authorization
	case receiveQuietly(discreet: Bool)
	
	/// Fully visible settings (user has customized something)
	case fullVisibility(discreet: Bool)
	
	/// Non-default settings (user has customized something)
	case customized(discreet: Bool)
	
	/// Disabled settings
	case disabled
	
	
	var description: String {
		switch self {
			case .receiveQuietly(let discreet) : return "receiveQuietly(discreet = \(discreet)"
			case .fullVisibility(let discreet) : return "fullVisibility(discreet = \(discreet)"
			case .customized(let discreet)     : return "customized(discreet = \(discreet)"
			case .disabled                     : return "disabled"
		}
	}
	
	static func fromSettings(_ settings: UNNotificationSettings?) -> BackgroundPaymentsConfig {
		
		guard let settings = settings else {
			return .receiveQuietly(discreet: false)
		}
		
		let perms = NotificationPermissions.fromSettings(settings)
		if perms == .disabled {
			return .disabled
		}
		
		let discreet = GroupPrefs.shared.discreetNotifications
		
		if settings.lockScreenSetting         != .enabled &&
			settings.notificationCenterSetting == .enabled &&
			settings.alertSetting              != .enabled &&
			settings.badgeSetting              != .enabled
		{
			return .receiveQuietly(discreet: discreet)
		}
		
		if settings.lockScreenSetting         == .enabled &&
			settings.notificationCenterSetting == .enabled &&
			settings.alertSetting              == .enabled &&
			settings.badgeSetting              == .enabled
		{
			return .fullVisibility(discreet: discreet)
		}
		
		return .customized(discreet: discreet)
	}
}
