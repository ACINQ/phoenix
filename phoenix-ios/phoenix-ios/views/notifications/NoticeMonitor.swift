import SwiftUI
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NoticeMonitor"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class NoticeMonitor: ObservableObject {
	
	@Published var isNewWallet = Prefs.shared.isNewWallet
	@Published var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	@Published var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
		encryptedNodeId: Biz.encryptedNodeId!
	)
	
	// Raw publisher for all WalletContext settings fetched from the cloud.
	// See specific functions below for simple getters. E.g.: `hasNotice_mempoolFull`
	@Published var chainContext: WalletContext.V0ChainContext? = nil
	
	@Published var notificationPermissions = NotificationsManager.shared.permissions.value
	@Published var bgRefreshStatus = NotificationsManager.shared.backgroundRefreshStatus.value
	
	@NestedObservableObject var customElectrumServerObserver = CustomElectrumServerObserver()
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		
		Prefs.shared.isNewWalletPublisher
			.sink {[weak self](value: Bool) in
				self?.isNewWallet = value
			}
			.store(in: &cancellables)
		
		Prefs.shared.backupSeed.isEnabled_publisher
			.sink {[weak self](enabled: Bool) in
				self?.backupSeed_enabled = enabled
			}
			.store(in: &cancellables)
		
		Prefs.shared.backupSeed.manualBackup_taskDone_publisher
			.sink {[weak self] _ in
				self?.manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
					encryptedNodeId: Biz.encryptedNodeId!
				)
			}
			.store(in: &cancellables)
		
		Biz.business.appConfigurationManager.chainContextPublisher()
			.sink {[weak self](context: WalletContext.V0ChainContext) in
				self?.chainContext = context
			}
			.store(in: &cancellables)
		
		NotificationsManager.shared.permissions
			.sink {[weak self](permissions: NotificationPermissions) in
				self?.notificationPermissions = permissions
			}
			.store(in: &cancellables)
		
		NotificationsManager.shared.backgroundRefreshStatus
			.sink {[weak self](status: UIBackgroundRefreshStatus) in
				self?.bgRefreshStatus = status
			}
			.store(in: &cancellables)
	}
	
	
	var hasNotice_backupSeed: Bool {
		if isNewWallet {
			// It's a new wallet. We don't bug them about backing up their seed until
			// they've actually done something with thie wallet.
			return false
		} else if !backupSeed_enabled && !manualBackup_taskDone {
			return true
		} else {
			return false
		}
	}
	
	var hasNotice_electrumServer: Bool {
		return customElectrumServerObserver.problem == .badCertificate
	}
	
	var hasNotice_mempoolFull: Bool {
		return chainContext?.mempool.v1.highUsage ?? false
	}
	
	var hasNotice_backgroundPayments: Bool {
		return notificationPermissions == .disabled
	}
	
	var hasNotice_watchTower: Bool {
		return bgRefreshStatus != .available
	}
}
