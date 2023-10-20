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
	
	@Published private var isNewWallet = Prefs.shared.isNewWallet
	@Published private var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	@Published private var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
		encryptedNodeId: Biz.encryptedNodeId!
	)
	
	@Published private var walletContext: WalletContext? = nil
	
	@Published private var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	
	@Published private var notificationPermissions = NotificationsManager.shared.permissions.value
	@Published private var bgRefreshStatus = NotificationsManager.shared.backgroundRefreshStatus.value
	
	@NestedObservableObject private var customElectrumServerObserver = CustomElectrumServerObserver()
	
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
		
		Biz.business.appConfigurationManager.walletContextPublisher()
			.sink {[weak self](context: WalletContext) in
				self?.walletContext = context
			}
			.store(in: &cancellables)
		
		Biz.business.balanceManager.swapInWalletPublisher()
			.sink {[weak self](wallet: Lightning_kmpWalletState.WalletWithConfirmations) in
				self?.swapInWallet = wallet
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
	
	var hasNotice: Bool {
		
		if hasNotice_backupSeed { return true }
		if hasNotice_electrumServer { return true }
		if hasNotice_swapInExpiration { return true }
		if hasNotice_mempoolFull { return true }
		if hasNotice_backgroundPayments { return true }
		if hasNotice_watchTower { return true }
		
		return false
	}
	
	var hasNotice_backupSeed: Bool {
		if isNewWallet {
			// It's a new wallet. We don't bug them about backing up their seed until
			// they've actually done something with this wallet.
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
	
	var hasNotice_swapInExpiration: Bool {
		return swapInWallet.expirationWarningInDays() != nil
	}
	
	var hasNotice_mempoolFull: Bool {
		return walletContext?.isMempoolFull ?? false
	}
	
	var hasNotice_backgroundPayments: Bool {
		return notificationPermissions == .disabled
	}
	
	var hasNotice_watchTower: Bool {
		return bgRefreshStatus != .available
	}
}
