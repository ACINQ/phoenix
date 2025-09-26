import SwiftUI
import Combine
import PhoenixShared

fileprivate let filename = "NoticeMonitor"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class NoticeMonitor: ObservableObject {
	
	@Published private var isNewWallet = Prefs.current.isNewWallet
	@Published private var backupSeed_enabled = Prefs.current.backupSeed.isEnabled
	@Published private var manualBackup_taskDone = Prefs.current.backupSeed.manualBackupDone
	
	@Published private var walletContext: WalletContext? = nil
	
	@Published private var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	
	@Published private var notificationPermissions = NotificationsManager.shared.permissions.value
	@Published private var bgRefreshStatus = NotificationsManager.shared.backgroundRefreshStatus.value
	
	@Published private var torNetworkIssue = false
	
	@NestedObservableObject private var customElectrumServerObserver = CustomElectrumServerObserver()
	
	private var isTorEnabled: Bool? = nil
	private var connections: Connections? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		
		Prefs.current.isNewWalletPublisher
			.sink {[weak self](value: Bool) in
				self?.isNewWallet = value
			}
			.store(in: &cancellables)
		
		Prefs.current.backupSeed.isEnabledPublisher
			.sink {[weak self](enabled: Bool) in
				self?.backupSeed_enabled = enabled
			}
			.store(in: &cancellables)
		
		Prefs.current.backupSeed.manualBackupDonePublisher
			.sink {[weak self] _ in
				self?.manualBackup_taskDone = Prefs.current.backupSeed.manualBackupDone
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
		
		Publishers.CombineLatest(
			Biz.business.appConfigurationManager.isTorEnabledPublisher(),
			Biz.business.connectionsManager.connectionsPublisher()
		).sink {[weak self](enabled: Bool, connections: Connections) in
			if let self {
				self.isTorEnabled = enabled
				self.connections = connections
				self.checkForTorIssues()
			}
		}.store(in: &cancellables)
	}
	
	var hasNotice: Bool {
		
		if hasNotice_backupSeed { return true }
		if hasNotice_electrumServer { return true }
		if hasNotice_swapInExpiration { return true }
		if hasNotice_mempoolFull { return true }
		if hasNotice_backgroundPayments { return true }
		if hasNotice_watchTower { return true }
		if hasNotice_torNetworkIssue { return true }
		
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
	
	var hasNotice_torNetworkIssue: Bool {
		return torNetworkIssue
	}
	
	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------
	
	func checkForTorIssues(needsDelay: Bool = true) {
		
		guard let isTorEnabled, let connections else {
			return
		}
		
		if isTorEnabled && !connections.peer.isEstablished() {
			if needsDelay {
				Task { @MainActor in
					try await Task.sleep(seconds: 3.0)
					checkForTorIssues(needsDelay: false)
				}
			} else {
				torNetworkIssue = true
			}
		} else {
			torNetworkIssue = false
		}
	}
}
