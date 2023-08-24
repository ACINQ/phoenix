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
	
	/// Reports the most recent `LiquidityEvents.Rejected` event.
	/// An incoming `LiquidityEvents.Accepted` automatically cancels the most recent rejected event.
	/// This only includes events in which source == onchain
	///
	public let swapInRejectedPublisher = CurrentValueSubject<Lightning_kmpLiquidityEventsRejected?, Never>(nil)

	/// Reports the most recent state of `ChannelsConsolidationHelper.canConsolidate()`.
	/// When true, the (blocking) upgrade mechanism must be re-run.
	///
	public let canMergeChannelsForSplicingPublisher = CurrentValueSubject<Bool, Never>(false)
	
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

		let lp = Prefs.shared.liquidityPolicy
		log.debug("lp.effectiveMaxFeeSats = \(lp.effectiveMaxFeeSats)")
		log.debug("lp.effectiveMaxFeeBasisPoints = \(lp.effectiveMaxFeeBasisPoints)")
		
		let startupParams = StartupParams(
			requestCheckLegacyChannels: false,
			isTorEnabled: GroupPrefs.shared.isTorEnabled,
			liquidityPolicy: lp.toKotlin(),
			trustedSwapInTxs: Set()
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
		swapInRejectedPublisher.send(nil)
		walletInfo = nil
		peerConnectionState = nil
		paymentsPageFetchers.removeAll()

		start()
	}
	
	// --------------------------------------------------
	// MARK: Notification Registration
	// --------------------------------------------------
	
	private func registerForNotifications() {
		
		// Connection status observer
		business.connectionsManager.publisher
			.sink { (connections: Connections) in
			
				self.connectionsChanged(connections)
			}
			.store(in: &cancellables)
		
		// In-flight payments observer
		business.paymentsManager.inFlightOutgoingPaymentsPublisher()
			.sink { (count: Int) in
				
				log.debug("inFlightOutgoingPaymentsPublisher: count = \(count)")
				if count > 0 {
					self.beginLongLivedTask()
				} else {
					self.endLongLivedTask()
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
		Prefs.shared.liquidityPolicyPublisher.dropFirst()
			.sink { (policy: LiquidityPolicy) in
			
				Task { @MainActor in
					do {
						try await self.business.peerManager.updatePeerLiquidityPolicy(newPolicy: policy.toKotlin())
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
				   rejected.source == Lightning_kmpLiquidityEventsSource.onchainwallet
				{
					log.debug("Received Lightning_kmpLiquidityEventsRejected: \(rejected)")
					self.swapInRejectedPublisher.value = rejected
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
						log.debug("Received Lightning_kmpLiquidityEventsAccepted")
						self.swapInRejectedPublisher.value = nil
					}
				}
			}
			.store(in: &cancellables)
		
		// Monitor for unfinished "merge-channels for splicing" upgrade.
		//
		business.peerManager.channelsPublisher()
			.sink { (channels: [LocalChannelInfo]) in
				
				let canConsolidate = ChannelsConsolidationHelper.shared.canConsolidate(channels: channels)
				self.canMergeChannelsForSplicingPublisher.send(canConsolidate)
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
		
		if (business.walletManager.isLoaded()) {
			return false
		}

		guard walletInfo == nil else {
			return false
		}
		
		let seed = knownSeed ?? business.walletManager.mnemonicsToSeed(mnemonics: mnemonics, passphrase: "")
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
	// MARK: Push Notifications
	// --------------------------------------------------
	
	func processPushNotification(
		_ userInfo: [AnyHashable : Any],
		_ completionHandler: @escaping (UIBackgroundFetchResult) -> Void
	) {
		
		log.debug("Received remote notification: \(userInfo)")
		assertMainThread()
		
		// allow network connection, even if app in background

		let appConnectionsDaemon = business.appConnectionsDaemon
		let targets =
			AppConnectionsDaemon.ControlTarget.companion.Peer.plus(
				other: AppConnectionsDaemon.ControlTarget.companion.Electrum
			)
		appConnectionsDaemon?.decrementDisconnectCount(target: targets)
		
		var didReceivePayment = false
		var totalTimer: Timer? = nil
		var postPaymentTimer: Timer? = nil
		var publisher: AnyPublisher<Lightning_kmpIncomingPayment, Never>? = nil
		var cancellable: AnyCancellable? = nil
		
		let pushReceivedAt = Date()
		
		var isFinished = false
		let Finish = { (_: Timer) -> Void in
			
			assertMainThread()
			
			if !isFinished {
				isFinished = true
				
				// balance previous decrement call
				appConnectionsDaemon?.incrementDisconnectCount(target: targets)
				
				totalTimer?.invalidate()
				postPaymentTimer?.invalidate()
				publisher = nil
				cancellable?.cancel()
				
				if didReceivePayment {
					log.info("Background fetch: Cleaning up")
				} else {
					log.info("Background fetch: Didn't receive payment - giving up")
				}
				completionHandler(didReceivePayment ? .newData : .noData)
			}
		}
		
		// The OS gives us 30 seconds to fetch data, and then invoke the completionHandler.
		// Failure to properly "clean up" in this way will result in the OS reprimanding us.
		// So we set a timer to ensure we stop before the max allowed.
		totalTimer = Timer.scheduledTimer(withTimeInterval: 29.0, repeats: false, block: Finish)
		
		publisher = business.paymentsManager.lastIncomingPaymentPublisher()
		cancellable = publisher!.sink(receiveValue: { (payment: Lightning_kmpIncomingPayment) in

			assertMainThread()
			
			guard
				let paymentReceivedAt = payment.received?.receivedAtDate,
				paymentReceivedAt > pushReceivedAt
			else {
				// Ignoring - this is the most recently received incomingPayment, but not a new one
				return
			}
			
			log.info("Background fetch: Payment received !")
			
			didReceivePayment = true
			postPaymentTimer?.invalidate()
			postPaymentTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false, block: Finish)
			NotificationsManager.shared.displayLocalNotification_receivedPayment(payment)
		})
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

