import UIKit
import PhoenixShared
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NotificationsManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum NotificationPermissions: CustomStringConvertible {
	
	/// We have not requested permission to display notifications (neither provisional nor standard).
	/// This means that notifications are implicitly disabled.
	case neverRequested
	
	/// We've been given provisional permission to display notifications.
	/// This means that notifications are delivered "quietly",
	/// and are only displayed in the notification center.
	case provisional
	
	/// We've been given non-provisional permission to display notifications.
	/// By default, this means notifications are displayed everywhere,
	/// and we have permission to badge the app icon.
	case enabled
	
	/// Notifications are effectively disabled.
	/// This means that iOS will not launch/run the notifySrvExt.
	/// I.e. background payments are disabled.
	case disabled
	
	
	var description: String {
		switch self {
			case .neverRequested : return "neverRequested"
			case .provisional    : return "provisional"
			case .enabled        : return "enabled"
			case .disabled       : return "disabled"
		}
	}
	
	static func fromSettings(_ settings: UNNotificationSettings) -> NotificationPermissions {
		
		switch settings.authorizationStatus {
			case .notDetermined : return .neverRequested
			case .provisional   : return .provisional
			case .denied        : return .disabled
			default             : break
		}
		
		// Within Settings > Phoenix > Notifications, there are 3 options
		// that can be enabled/disabled independently of each other:
		//
		// - Lock Screen
		// - Notification Center
		// - Banners
		//
		// However, if the user disables both "Lock Screen" & "Notification Center",
		// then iOS won't reliable launch the notifySrvExt. This is because if only
		// "Banners" are enabled, then iOS will only launch the notifySrvExt if
		// the device is unlocked.
		
		if settings.lockScreenSetting == .enabled {
			return .enabled
		}
		if settings.notificationCenterSetting == .enabled {
			return .enabled
		}
		
		return .disabled
	}
}

/// Manages the following tasks:
/// - asking iOS for permission to display notifications
/// - monitoring iOS for our permission status
/// - common handling for the display of notifications to the uer
///
class NotificationsManager {
	
	/// Singleton instance
	public static let shared = NotificationsManager()
	
	/// The current notification settings assigned to our app from iOS.
	/// This is based on the user-configurable settings within iOS,
	/// for the Phoenix app, under the "Notifications" section.
	///
	public var settings = CurrentValueSubject<UNNotificationSettings?, Never>(nil)
	
	/// Simplified permissions (parsed from `UNNotificationSettings`).
	///
	public var permissions = CurrentValueSubject<NotificationPermissions, Never>(.neverRequested)
	
	/// The current "background app refresh" permissions assigend to our app from iOS.
	/// This is based on the user-configurable settings within iOS,
	/// for the Phoenix app.
	///
	public var backgroundRefreshStatus = CurrentValueSubject<UIBackgroundRefreshStatus, Never>(.available)
	
	private var cancellables = Set<AnyCancellable>()
	
	/// Must use shared instance
	private init() {
		
		settings.sink { (settings: UNNotificationSettings?) in
			
			if let settings = settings {
				let newValue = NotificationPermissions.fromSettings(settings)
				log.debug("permissions.send(\(newValue))")
				self.permissions.send(newValue)
			}
			
		}.store(in: &cancellables)
		
		NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification).sink { _ in
			self.applicationWillEnterForeground()
		}.store(in: &cancellables)
		
		NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification).sink { _ in
			self.didBecomeActiveNotification()
		}.store(in: &cancellables)
		
		refreshSettings()
		refreshBackgroundRefreshStatus()
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func applicationWillEnterForeground() {
		log.trace("applicationWillEnterForeground()")
		
		// User may have changed notification permissions.
		refreshSettings()
	}
	
	func didBecomeActiveNotification() {
		log.trace("didBecomeActiveNotification()")
		
		// Accessing `UIApplication.shared.backgroundRefreshStatus` within
		// `applicationWillEnterForeground` results in a stale value.
		// We actually need to wait until `didBecomeActiveNotification` to get an updated value.
		refreshBackgroundRefreshStatus()
	}
	
	// --------------------------------------------------
	// MARK: Refresh
	// --------------------------------------------------
	
	private func refreshSettings() {
		log.trace("refreshSettings()")
		
		UNUserNotificationCenter.current().getNotificationSettings { (settings: UNNotificationSettings) in
			
			log.debug("UNNotificationSettings: \(settings)")
			
			// We're not on the main thread right now
			DispatchQueue.main.async {
				self.settings.send(settings)
			}
		}
	}
	
	private func refreshBackgroundRefreshStatus() {
		log.trace("refreshBackgroundRefreshStatus()")
		
		let refreshBlock = {
			// iOS displays a warning if we access `UIApplication.shared` from a background thread
			
			let newStatus = UIApplication.shared.backgroundRefreshStatus
			log.debug("backgroundRefreshStatus = \(newStatus.rawValue)")
			self.backgroundRefreshStatus.send(newStatus)
		}
		
		if Thread.isMainThread {
			refreshBlock()
		} else {
			DispatchQueue.main.async { refreshBlock() }
		}
	}
	
	// --------------------------------------------------
	// MARK: Permission
	// --------------------------------------------------
	
	/// When you request "standard" permissions, a dialogue box will appear
	/// asking the user for permission to display notifications:
	///
	/// > "Phoenix" would like to send you notifications.
	///
	/// The user can accept or deny this request.
	///
	/// If they accept then the app has permission to:
	/// - display notifications on the lock screen
	/// - display notifications in the notification center
	/// - display banners (when the screen is unlocked)
	/// - badge the app icon
	/// - run the notifySrvExt when push notifications arrive
	///
	/// If they deny then the app:
	/// - cannot display notifications
	/// - cannot run the notifySrvExt
	///
	public func requestPermissionForStandardNotifications() {
		log.trace("requestPermissionForStandardNotifications()")
		
		requestPermission([.alert, .sound, .badge])
	}
	
	/// When you request "provisional" permissions, iOS will grant them without prompting the user.
	///
	/// However, provisional permissions are much more restrictive than standard permissions.
	///
	/// With provisional permissions the app has permission to:
	/// - display notifications in the notification center
	/// - run the notifySrvExt when push notifications arrive
	///
	/// Further, when notifications are displayed, they contain a prompt:
	///
	/// > Keep receiving notifications from the "Phoenix" app?
	/// > [Keep...] [Turn off...]
	///
	/// If the user chooses "Keep", then the notifications will be delivered "silently".
	/// Which means the following restrictions:
	/// - cannot display notifications on the lock screen
	/// - cannot display banners (when the screen is unlocked)
	/// - cannot badge the app icon
	///
	public func requestPermissionForProvisionalNotifications() {
		log.trace("requestPermissionForProvisionalNotifications()")
		
		requestPermission([.alert, .sound, .badge, .provisional])
	}
	
	private func requestPermission(_ options: UNAuthorizationOptions) {
		
		UNUserNotificationCenter.current().requestAuthorization(options: options) { granted, error in
			
			log.debug("UNUserNotificationCenter.requestAuthorization(): granted = \(granted)")
			if let error = error {
				// How can an error possibly occur ?!?
				// Apple doesn't tell us...
				log.error("UNUserNotificationCenter.requestAuthorization(): \(String(describing: error))")
			}
			
			self.refreshSettings()
		}
	}
	
	// --------------------------------------------------
	// MARK: Display
	// --------------------------------------------------
	
	func displayLocalNotification_receivedPayment(_ payment: Lightning_kmpIncomingPayment) {
		log.trace("displayLocalNotification_receivedPayment()")
		
		// We are having problems interacting with the `payment` parameter outside the main thread.
		// This might have to do with the goofy Kotlin freezing stuff.
		// So let's be safe and always operate on the main thread here.
		//
		let handler = {(settings: UNNotificationSettings) -> Void in
			
			guard settings.authorizationStatus == .authorized else {
				return
			}
			
			let paymentInfos = [
				WalletPaymentInfo(
					payment: payment,
					metadata: WalletPaymentMetadata.empty(),
					fetchOptions: WalletPaymentFetchOptions.companion.None
				)
			]
			
			let currencyPrefs = GlobalEnvironment.currencyPrefs
			let bitcoinUnit = currencyPrefs.bitcoinUnit
			let fiatCurrency = currencyPrefs.fiatCurrency
			let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
			
			let content = UNMutableNotificationContent()
			content.fillForReceivedPayments(
				payments: paymentInfos,
				bitcoinUnit: bitcoinUnit,
				exchangeRate: exchangeRate
			)
			
			let request = UNNotificationRequest(
				identifier: payment.id(),
				content: content,
				trigger: nil
			)
			
			UNUserNotificationCenter.current().add(request) { error in
				if let error = error {
					log.error("NotificationCenter.add(request): error: \(String(describing: error))")
				}
			}
		}
		
		UNUserNotificationCenter.current().getNotificationSettings { settings in
			
			if Thread.isMainThread {
				handler(settings)
			} else {
				DispatchQueue.main.async { handler(settings) }
			}
		}
	}
	
	public func displayLocalNotification_revokedCommit() {
		log.trace("displayLocalNotification_revokedCommit()")
		
		let handler = {(settings: UNNotificationSettings) -> Void in
			
			guard settings.authorizationStatus == .authorized else {
				return
			}
			
			GroupPrefs.shared.badgeCount += 1
			
			let content = UNMutableNotificationContent()
			content.title = "Some of your channels have closed"
			content.body = "Please start Phoenix to review your channels."
			content.badge = NSNumber(value: GroupPrefs.shared.badgeCount)
			
			let request = UNNotificationRequest(
				identifier: "revokedCommit",
				content: content,
				trigger: nil
			)
			
			UNUserNotificationCenter.current().add(request) { error in
				if let error = error {
					log.error("NotificationCenter.add(request): error: \(String(describing: error))")
				}
			}
		}
		
		UNUserNotificationCenter.current().getNotificationSettings { settings in
			
			if Thread.isMainThread {
				handler(settings)
			} else {
				DispatchQueue.main.async { handler(settings) }
			}
		}
	}
}
