import UIKit
import PhoenixShared
import BackgroundTasks
import Combine


fileprivate let filename = "BusinessManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
	
	/// Reports the most recent `LiquidityEvents.Rejected` event.
	/// An incoming `LiquidityEvents.Accepted` automatically cancels the most recent rejected event.
	/// This only includes events in which source == onchain
	///
	public let swapInRejectedPublisher = CurrentValueSubject<Lightning_kmpLiquidityEventsRejected?, Never>(nil)

	/// Reports the most recent state of `IosMigrationHelper.shouldMigrateChannels()`.
	/// When true, the (blocking) upgrade mechanism must be re-run.
	///
	public let canMergeChannelsForSplicingPublisher = CurrentValueSubject<Bool, Never>(false)
	
	/// Reports whether or not the notifySrvExt process is running, and connected to the Peer.
	/// When this is true, we (the main app process) are prevented from connecting to the Peer
	/// (otherwise doing so would force-disconnect the notifySrvExt, which may be processing an incoming payment)
	///
	public let srvExtConnectedToPeer = CurrentValueSubject<Bool, Never>(false)

	/// For creating a new wallet
	/// 
	public let mnemonicLanguagePublisher = CurrentValueSubject<MnemonicLanguage, Never>(MnemonicLanguage.english)
	
	private var isInBackground = false
	
	private var walletInfo: WalletManager.WalletInfo? = nil
	private var pushToken: String? = nil
	private var fcmToken: String? = nil
	private var peerConnectionState: Lightning_kmpConnection? = nil
	
	private var longLivedTasks = [String: UIBackgroundTaskIdentifier]()
	
	private var paymentsPageFetchers = [String: PaymentsPageFetcher]()
	private var appCancellables = Set<AnyCancellable>()
	private var cancellables = Set<AnyCancellable>()
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	private init() { // must use shared instance
		
		business = PhoenixBusiness(ctx: PlatformContext.default)
		BusinessManager._isTestnet = business.chain.isTestnet()
		
		let nc = NotificationCenter.default
		
		nc.publisher(for: UIApplication.didEnterBackgroundNotification).sink { _ in
			self.applicationDidEnterBackground()
		}.store(in: &appCancellables)
		
		nc.publisher(for: UIApplication.willEnterForegroundNotification).sink { _ in
			self.applicationWillEnterForeground()
		}.store(in: &appCancellables)
		
		WatchTower.shared.prepare()
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
			isTorEnabled: GroupPrefs.shared.isTorEnabled,
			liquidityPolicy: GroupPrefs.shared.liquidityPolicy.toKotlin(),
			trustedSwapInTxs: Set()
		)
		business.start(startupParams: startupParams)
		
		setup()
		startTasks()
	}

	public func stop() {

		cancellables.removeAll()
		business.stop()
		syncManager?.shutdown()
	}

	public func reset() {

		business = PhoenixBusiness(ctx: PlatformContext.default)
		syncManager = nil
		swapInRejectedPublisher.send(nil)
		canMergeChannelsForSplicingPublisher.send(false)
		walletInfo = nil
		peerConnectionState = nil
		paymentsPageFetchers.removeAll()

		start()
	}
	
	// --------------------------------------------------
	// MARK: Setup
	// --------------------------------------------------
	
	private func setup() {
		log.trace("setup()")
		
		// Connection status observer
		business.connectionsManager.connectionsPublisher()
			.sink { (connections: Connections) in
			
				self.connectionsChanged(connections)
			}
			.store(in: &cancellables)
		
		// In-flight payments observer
		business.peerManager.peerStatePublisher()
			.flatMap { $0.eventsFlowPublisher() }
			.sink { (event: Lightning_kmpPeerEvent) in
				
				if let paymentProgress = event as? Lightning_kmpPaymentProgress {
					let paymentId = paymentProgress.request.paymentId.description()
					self.beginLongLivedTask(id: paymentId)
					
				} else if let paymentSent = event as? Lightning_kmpPaymentSent {
					let paymentId = paymentSent.request.paymentId.description()
					self.endLongLivedTask(id: paymentId)
					
				} else if let paymentNotSent = event as? Lightning_kmpPaymentNotSent {
					let paymentId = paymentNotSent.request.paymentId.description()
					self.endLongLivedTask(id: paymentId)
				}
			}
			.store(in: &cancellables)
		
		// Tor configuration observer
		GroupPrefs.shared.isTorEnabledPublisher
			.sink { (isTorEnabled: Bool) in
				
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
		
		// Liquidity policy
		GroupPrefs.shared.liquidityPolicyPublisher.dropFirst()
			.sink { (policy: LiquidityPolicy) in
			
				Task { @MainActor in
					do {
						log.debug("invoking peerManager.updatePeerLiquidityPolicy()...")
						try await self.business.peerManager.updatePeerLiquidityPolicy(newPolicy: policy.toKotlin())
						
						if self.swapInRejectedPublisher.value != nil {
							log.debug("Received updated liquidityPolicy: clearing swapInRejectedPublisher")
							self.swapInRejectedPublisher.value = nil
						}
					} catch {
						log.error("Error: biz.peerManager.updatePeerLiquidityPolicy: \(error)")
					}
				}
			}
			.store(in: &cancellables)
		
		// NodeEvents
		business.nodeParamsManager.nodeParamsPublisher()
			.flatMap { $0.nodeEventsPublisher() }
			.sink { (event: Lightning_kmpNodeEvents) in
				
				if let rejected = event as? Lightning_kmpLiquidityEventsRejected,
				   rejected.source == Lightning_kmpLiquidityEventsSource.onChainWallet
				{
					log.debug("Received Lightning_kmpLiquidityEventsRejected: \(rejected)")
					self.swapInRejectedPublisher.value = rejected
					
				} else if let task = event as? Lightning_kmpSensitiveTaskEvents {
					
					if let taskStarted = task as? Lightning_kmpSensitiveTaskEventsTaskStarted {
						if let taskIdentifier = taskStarted.id.asInteractiveTx() {
							self.beginLongLivedTask(id: taskIdentifier.id)
						}
						
					} else if let taskEnded = task as? Lightning_kmpSensitiveTaskEventsTaskEnded {
						if let taskIdentifier = taskEnded.id.asInteractiveTx() {
							self.endLongLivedTask(id: taskIdentifier.id)
						}
					}
				}
			}
			.store(in: &cancellables)
		
		// LiquidityEvent.Accepted is still missing.
		// So we're simulating it by monitoring the swapIn wallet balance.
		// A LiquidityEvent.Rejected occurs when:
		// - swapInWallet.deeplyConfirmedBalance > 0
		// - but the fees exceed the confirmed maxFees
		//
		// If the swapIn successfully occurs later,
		// then the entire confirmed balance is consumed,
		// and thus the confirmed balance drops to zero.
		//
		business.balanceManager.swapInWalletPublisher()
			.sink { (wallet: Lightning_kmpWalletState.WalletWithConfirmations) in
				
				if wallet.deeplyConfirmedBalance.sat == 0 {
					if self.swapInRejectedPublisher.value != nil {
						log.debug("Received Lightning_kmpLiquidityEventsAccepted: clearing swapInRejectedPublisher")
						self.swapInRejectedPublisher.value = nil
					}
				}
			}
			.store(in: &cancellables)
		
		// Monitor for unfinished "merge-channels for splicing" upgrade.
		//
		business.peerManager.channelsPublisher()
			.sink { (channels: [LocalChannelInfo]) in
				
				let shouldMigrate = IosMigrationHelper.shared.shouldMigrateChannels(channels: channels)
				self.canMergeChannelsForSplicingPublisher.send(shouldMigrate)
			}
			.store(in: &cancellables)
		
		// Monitor for notifySrvExt being active & connected to Peer
		//
		GroupPrefs.shared.srvExtConnectionPublisher
			.sink { (date: Date) in
			
				log.debug("srvExtConnectionPublisher.fire()")
				
				let elapsed = date.timeIntervalSinceNow * -1.0
				log.debug("elapsed = \(elapsed)")
				
				let isConnected = elapsed < 5.0 /* seconds */
				log.debug("isConnected = \(isConnected)")
				
				self.srvExtConnectedToPeer.send(isConnected)
				
				if isConnected {
					let delay = 5.0 - elapsed
					DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
						if GroupPrefs.shared.srvExtConnection == date {
							log.debug("srvExtConnection.clear()")
							GroupPrefs.shared.srvExtConnection = Date(timeIntervalSince1970: 0)
						}
					}
				}
			}
			.store(in: &cancellables)
		
		var srvExtWasConnectedToPeer = false
		self.srvExtConnectedToPeer
			.sink { (isConnected: Bool) in
			
				log.debug("srvExtConnectedToPeer(): isConnected = \(isConnected)")
				
				let wasConnected = srvExtWasConnectedToPeer
				srvExtWasConnectedToPeer = isConnected
				log.debug("wasConnected = \(wasConnected)")
				
				if isConnected && !wasConnected {
					log.debug("incrementDisconnectCount(target: Peer)")
					self.business.appConnectionsDaemon?.incrementDisconnectCount(
						target: AppConnectionsDaemon.ControlTarget.companion.Peer
					)
					
				} else if !isConnected && wasConnected {
					log.debug("decrementDisconnectCount(target: Peer)")
					self.business.appConnectionsDaemon?.decrementDisconnectCount(
						target: AppConnectionsDaemon.ControlTarget.companion.Peer
					)
				}
				
			}.store(in: &cancellables)
		
		// Keep Prefs.shared.swapInAddressIndex up-to-date
		business.peerManager.peerStatePublisher()
			.compactMap { $0.swapInWallet }
			.flatMap { $0.swapInAddressPublisher() }
			.sink { (newInfo: Lightning_kmpSwapInWallet.SwapInAddressInfo?) in
				
				if let newInfo {
					if Prefs.shared.swapInAddressIndex < newInfo.index {
						Prefs.shared.swapInAddressIndex = newInfo.index
					}
				}
			}
			.store(in: &cancellables)
	}
	
	func startTasks() {
		log.trace("startTasks()")
		
		Task { @MainActor in
			let channelsStream = self.business.peerManager.channelsPublisher().values
			do {
				for try await channels in channelsStream {
					let shouldMigrate = IosMigrationHelper.shared.shouldMigrateChannels(channels: channels)
					if !shouldMigrate {
						let peer = try await Biz.business.peerManager.getPeer()
						try await peer.startWatchSwapInWallet()
					}
					break
				}
			} catch {
				log.error("peer.startWatchSwapInWallet(): error: \(error)")
			}
		} // </Task>
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func applicationDidEnterBackground() {
		log.trace("### applicationDidEnterBackground()")
		
		if !isInBackground {
			business.appConnectionsDaemon?.incrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
			isInBackground = true
		}
	}
	
	func applicationWillEnterForeground() {
		log.trace("### applicationWillEnterForeground()")
		
		if isInBackground {
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
			isInBackground = false
		}
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
		recoveryPhrase: RecoveryPhrase,
		seed knownSeed: KotlinByteArray? = nil,
		walletRestoreType: WalletRestoreType? = nil
	) -> Bool {
		
		log.trace("loadWallet()")
		assertMainThread()
		
		if (business.walletManager.isLoaded()) {
			return false
		}

		guard walletInfo == nil else {
			return false
		}
		
		guard let language = recoveryPhrase.language else {
			return false
		}
		
		let seed = knownSeed ?? business.walletManager.mnemonicsToSeed(
			mnemonics  : recoveryPhrase.mnemonicsArray,
			wordList   : language.wordlist(),
			passphrase : ""
		)
		let _walletInfo = business.walletManager.loadWallet(seed: seed)
		
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
			recoveryPhrase: recoveryPhrase,
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
	
	public var nodeId: String? {
		return walletInfo?.nodeId.toHex()
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
		
		let oldPeerConnectionState = peerConnectionState ?? Lightning_kmpConnection.CLOSED(reason: nil)
		let newPeerConnectionState = connections.peer
		peerConnectionState = newPeerConnectionState
		
		if !oldPeerConnectionState.isEstablished() && newPeerConnectionState.isEstablished() {
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
		
		let token = self.fcmToken
		log.debug("registering fcm token: \(token?.description ?? "<nil>")")
		business.registerFcmToken(token: token) { error in
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
	
	func beginLongLivedTask(id: String, timeout: TimeInterval = 180) {
		log.trace("beginLongLivedTask(id: '\(id)')")
		assertMainThread()
		
		if longLivedTasks[id] == nil {
			longLivedTasks[id] = UIApplication.shared.beginBackgroundTask {
				self.endLongLivedTask(id: id)
			}
			
			DispatchQueue.main.asyncAfter(deadline: .now() + timeout) {
				self.endLongLivedTask(id: id)
			}
			
			log.debug("Invoking: business.decrementDisconnectCount()")
			business.appConnectionsDaemon?.decrementDisconnectCount(
				target: AppConnectionsDaemon.ControlTarget.companion.All
			)
		}
	}
	
	func endLongLivedTask(id: String) {
		log.trace("endLongLivedTask(id: '\(id)')")
		assertMainThread()
		
		if let task = longLivedTasks.removeValue(forKey: id) {
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

