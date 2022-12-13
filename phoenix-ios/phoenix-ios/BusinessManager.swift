import UIKit
import PhoenixShared
import BackgroundTasks
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BusinessManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum WalletRestoreType {
	case fromManualEntry
	case fromCloudBackup(name: String?)
}

/// Short-hand for `BusinessManager.shared`
/// 
let Biz = BusinessManager.shared

/// Manages the `PhoenixBusiness` instance, which is the shared logic written in Kotlin Multiplatform.
///
class BusinessManager {
	
	/// Singleton instance
	public static let shared = BusinessManager()
	
	/// There are some places in the code where we need to access the testnet state from a background thread.
	/// This is problematic because calling into Kotlin via `business.chain.isTestnet()`
	/// from a background thread will throw an exception.
	///
	/// So we're caching this value here for background access.
	/// Also, this makes it easier to test mainnet UI & colors.
	///
	private static var _isTestnet: Bool? = nil
	public static let isTestnet = _isTestnet!
	public static let showTestnetBackground = _isTestnet!
	
	/// The current business instance.
	///
	/// Always fetch this on demand - don't cache it.
	/// Because it might change if the user closes his/her wallet.
	///
	public var business: PhoenixBusiness
	
	/// The current SyncManager instance.
	///
	/// Always fetch this on demand - don't cache it.
	/// Because it might change if the user closes his/her wallet.
	///
	public private(set) var syncManager: SyncManager? = nil

	private var walletInfo: WalletManager.WalletInfo? = nil
	private var pushToken: String? = nil
	private var fcmToken: String? = nil
	private var peerConnectionState: Lightning_kmpConnection? = nil
	
	private var longLivedTask: UIBackgroundTaskIdentifier = .invalid
	
	private var paymentsPageFetchers = [String: PaymentsPageFetcher]()
	private var cancellables = Set<AnyCancellable>()
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------

	private init() { // must use shared instance
		
		business = PhoenixBusiness(ctx: PlatformContext())
		BusinessManager._isTestnet = business.chain.isTestnet()
	}
	
	// --------------------------------------------------
	// MARK: Lifecycle
	// --------------------------------------------------

	public func start() {
		
		let electrumConfig = GroupPrefs.shared.electrumConfig
		business.appConfigurationManager.updateElectrumConfig(server: electrumConfig?.serverAddress)
		
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: GroupPrefs.shared.fiatCurrency,
			others: GroupPrefs.shared.preferredFiatCurrencies
		)
		business.appConfigurationManager.updatePreferredFiatCurrencies(current: preferredFiatCurrencies)

		let startupParams = StartupParams(
			requestCheckLegacyChannels: false,
			isTorEnabled: GroupPrefs.shared.isTorEnabled
		)
		business.start(startupParams: startupParams)
		
		registerForNotifications()
	}

	public func stop() {

		cancellables.removeAll()
		business.stop()
		syncManager?.shutdown()
	}

	public func reset() {

		business = PhoenixBusiness(ctx: PlatformContext())
		syncManager = nil
		walletInfo = nil
		peerConnectionState = nil
		paymentsPageFetchers.removeAll()

		start()
		registerForNotifications()
	}
	
	// --------------------------------------------------
	// MARK: Notification Registration
	// --------------------------------------------------
	
	private func registerForNotifications() {
		
		// Connection status observer
		business.connectionsManager.publisher.sink { (connections: Connections) in
			self.connectionsChanged(connections)
		}
		.store(in: &cancellables)
		
		// In-flight payments observer
		business.paymentsManager.inFlightOutgoingPaymentsPublisher().sink { (count: Int) in
			log.debug("inFlightOutgoingPaymentsPublisher: count = \(count)")
			if count > 0 {
				self.beginLongLivedTask()
			} else {
				self.endLongLivedTask()
			}
		}
		.store(in: &cancellables)
		
		// Tor configuration observer
		GroupPrefs.shared.isTorEnabledPublisher.sink { (isTorEnabled: Bool) in
			self.business.appConfigurationManager.updateTorUsage(enabled: isTorEnabled)
		}
		.store(in: &cancellables)
		
		// PreferredFiatCurrenies observers
		Publishers.CombineLatest(
			GroupPrefs.shared.fiatCurrencyPublisher,
			GroupPrefs.shared.currencyConverterListPublisher
		).sink { _ in
			let current = AppConfigurationManager.PreferredFiatCurrencies(
				primary: GroupPrefs.shared.fiatCurrency,
				others: GroupPrefs.shared.preferredFiatCurrencies
			)
			self.business.appConfigurationManager.updatePreferredFiatCurrencies(current: current)
		}
		.store(in: &cancellables)
	}
	
	// --------------------------------------------------
	// MARK: Wallet
	// --------------------------------------------------
	
	/// Loads the given wallet, and starts the Lightning node.
	///
	/// - Parameters:
	///   - mnemonics: The 12-word recovery phrase
	///   - seed: The seed is extracted from the mnemonics. If you've already performed this
	///           step (i.e. during verification), then pass it here to avoid the duplicate effort.
	///   - walletRestoreType: If restoring a wallet from a backup, pass the type here.
	///
	@discardableResult
	func loadWallet(
		mnemonics: [String],
		seed knownSeed: KotlinByteArray? = nil,
		walletRestoreType: WalletRestoreType? = nil
	) -> Bool {
		
		log.trace("loadWallet()")
		assertMainThread()
		
		guard walletInfo == nil else {
			return false
		}
		
		let seed = knownSeed ?? business.walletManager.mnemonicsToSeed(mnemonics: mnemonics, passphrase: "")
		guard let _walletInfo = business.walletManager.loadWallet(seed: seed) else {
			return false
		}
		
		self.walletInfo = _walletInfo
		maybeRegisterFcmToken()
		
		let cloudKey = _walletInfo.cloudKey
		let encryptedNodeId = _walletInfo.cloudKeyHash as String
		
		if let walletRestoreType = walletRestoreType {
			switch walletRestoreType {
			case .fromManualEntry:
				//
				// User is restoring wallet after manually typing in the recovery phrase.
				// So we can mark the manual_backup task as completed.
				//
				Prefs.shared.backupSeed.manualBackup_setTaskDone(true, encryptedNodeId: encryptedNodeId)
				//
				// And ensure cloud backup is disabled for the wallet.
				//
				Prefs.shared.backupSeed.isEnabled = false
				Prefs.shared.backupSeed.setName(nil, encryptedNodeId: encryptedNodeId)
				Prefs.shared.backupSeed.setHasUploadedSeed(false, encryptedNodeId: encryptedNodeId)
				
			case .fromCloudBackup(let name):
				//
				// User is restoring wallet from an existing iCloud backup.
				// So we can mark the iCloud backpu as completed.
				//
				Prefs.shared.backupSeed.isEnabled = true
				Prefs.shared.backupSeed.setName(name, encryptedNodeId: encryptedNodeId)
				Prefs.shared.backupSeed.setHasUploadedSeed(true, encryptedNodeId: encryptedNodeId)
				//
				// And ensure manual backup is diabled for the wallet.
				//
				Prefs.shared.backupSeed.manualBackup_setTaskDone(false, encryptedNodeId: encryptedNodeId)
			}
		}

		self.syncManager = SyncManager(
			chain: business.chain,
			mnemonics: mnemonics,
			cloudKey: cloudKey,
			encryptedNodeId: encryptedNodeId
		)

		if LockState.shared.walletExistence == .doesNotExist {
			LockState.shared.walletExistence = .exists
		}
		return true
	}
	
	/// The current encryptedNodeId (from the current unlocked wallet).
	///
	/// Always fetch this on demand - don't cache it.
	/// Because it might change if the user closes his/her wallet.
	///
	public var encryptedNodeId: String? {

		// For historical reasons, this is the cloudKeyHash, and NOT the nodeIdHash.
		return walletInfo?.cloudKeyHash
	}

	/// The current nodeIdHash (from the current unlocked wallet).
	///
	/// Always fetch this on demand - don't cache it.
	/// Because it might change if the user closes his/her wallet.
	///
	public var nodeIdHash: String? {
		return walletInfo?.nodeIdHash
	}

	// --------------------------------------------------
	// MARK: Push Token
	// --------------------------------------------------
	
	public func setPushToken(_ value: String) {
		log.trace("setPushToken()")
		assertMainThread()
		
		self.pushToken = value
		maybeRegisterFcmToken()
	}
	
	public func setFcmToken(_ value: String) {
		log.trace("setFcmToken()")
		assertMainThread()
		
		self.fcmToken = value
		maybeRegisterFcmToken()
	}
	
	private func connectionsChanged(_ connections: Connections) -> Void {
		log.trace("connectionsChanged()")
		
		let prvPeerConnectionState = peerConnectionState
		peerConnectionState = connections.peer
		
		if !(prvPeerConnectionState is Lightning_kmpConnection.ESTABLISHED) &&
			(peerConnectionState is Lightning_kmpConnection.ESTABLISHED)
		{
			maybeRegisterFcmToken()
		}
	}
	
	private func maybeRegisterFcmToken() -> Void {
		log.trace("maybeRegisterFcmToken()")
		assertMainThread()
		
		if walletInfo == nil {
			log.debug("maybeRegisterFcmToken: walletInfo is nil")
			return
		}
		if fcmToken == nil {
			log.debug("maybeRegisterFcmToken: fcmToken is nil")
			return
		}
		if !(peerConnectionState is Lightning_kmpConnection.ESTABLISHED) {
			log.debug("maybeRegisterFcmToken: peerConnection not established")
			return
		}
		
		// It's possible for the user to disable "background app refresh".
		// This is done via:
		// Settings -> General -> Background App Refresh
		//
		// If the user turns this off for Phoenix,
		// then the OS won't deliver silent push notifications.
		// So in this case, we want to register a "null" with the server.
		
		var token = self.fcmToken
		if UIApplication.shared.backgroundRefreshStatus != .available {
			token = nil
		}

		log.debug("registering fcm token: \(token?.description ?? "<nil>")")
		business.registerFcmToken(token: token) { result, error in
			if let e = error {
				log.error("failed to register fcm token: \(e.localizedDescription)")
			}
		}
		
		// Future optimization:
		//
		// Technically, we only need to register the (node_id, fcm_token) with the server once.
		// So we could store this tuple in the UserDefaults system,
		// and then only register with the server if the tuple changes.
		// We even have some code in place to support this.
		// But the problem we currently have is:
		//
		// When do we know for sure that the server has registered our fcm_token ?
		//
		// Currently we send off the request to lightning-kmp, and it will perform the registration
		// at some point. If the connection is currently established, it will send the
		// LightningMessage right away. Otherwise, it will send the LightningMessage after
		// establishing the connection.
		//
		// The ideal solution would be to have the server send some kind of Ack for the
		// registration. Which we could then use to trigger a storage in UserDefaults.
	}
	
	// --------------------------------------------------
	// MARK: Long-Lived Tasks
	// --------------------------------------------------
	
	// A long-lived task is for:
	//
	// > when leaving a task unfinished may cause a bad user experience in your app.
	// > For example: to complete disk writes, finish user-initiated requests, network calls, ...
	//
	// For historical reasons, this is also called a "background task".
	// However, in order to differentiate from the new BGTask's introduced in iOS 13,
	// we're now calling these "long-lived tasks".
	
	func beginLongLivedTask() {
		log.trace("beginLongLivedTask()")
		assertMainThread()
		
		if longLivedTask == .invalid {
			longLivedTask = UIApplication.shared.beginBackgroundTask { [weak self] in
				self?.endLongLivedTask()
			}
			log.debug("Invoking: business.decrementDisconnectCount()")
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	func endLongLivedTask() {
		log.trace("endLongLivedTask()")
		assertMainThread()
		
		if longLivedTask != .invalid {
			
			let task = longLivedTask
			longLivedTask = .invalid
			
			UIApplication.shared.endBackgroundTask(task)
			log.debug("Invoking: business.incrementDisconnectCount()")
			business.appConnectionsDaemon?.incrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}

	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------

	func getPaymentsPageFetcher(name: String) -> PaymentsPageFetcher {

		if let cached = paymentsPageFetchers[name] {
			return cached
		}

		let ppf = business.paymentsManager.makePageFetcher()
		paymentsPageFetchers[name] = ppf

		return ppf
	}
}

