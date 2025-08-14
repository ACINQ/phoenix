import UIKit
import BackgroundTasks
import Combine
import PhoenixShared


fileprivate let filename = "WatchTower"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

// The taskID must match the value in Info.plist
fileprivate let taskId_watchTower = "co.acinq.phoenix.WatchTower"


class WatchTower {
	
	/// Singleton instance
	public static let shared = WatchTower()
	
	private var appCancellables = Set<AnyCancellable>()
	private var cancellables = Set<AnyCancellable>()
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	private init() { // must use shared instance
		
		let nc = NotificationCenter.default
		
		nc.publisher(for: UIApplication.didFinishLaunchingNotification).sink { _ in
			self.applicationDidFinishLaunching()
		}.store(in: &appCancellables)
		
		nc.publisher(for: UIApplication.didEnterBackgroundNotification).sink { _ in
			self.applicationDidEnterBackground()
		}.store(in: &appCancellables)
	}
	
	func prepare() { /* Stub function */ }
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func applicationDidFinishLaunching() {
		log.trace(#function)
		
		registerBackgroundTask()
	}
	
	func applicationDidEnterBackground() {
		log.trace(#function)
		
		scheduleBackgroundTask()
	}
	
	// --------------------------------------------------
	// MARK: Calculations
	// --------------------------------------------------
	
	private func hasInFlightTransactions(_ channels: [LocalChannelInfo]) -> Bool {
		return channels.contains(where: { $0.inFlightPaymentsCount > 0 })
	}
	
	private func calculateRevokedChannelIds(
		oldChannels: [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState],
		newChannels: [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState]
	) -> Set<Bitcoin_kmpByteVector32> {
		
		var revokedChannelIds = Set<Bitcoin_kmpByteVector32>()
		
		for (channelId, oldChannel) in oldChannels {
			if let newChannel = newChannels[channelId] {

				var oldHasRevokedCommit = false
				do {
					var oldClosing: Lightning_kmpClosing? = oldChannel.asClosing()
					if oldClosing == nil {
						oldClosing = oldChannel.asOffline()?.state.asClosing()
					}

					if let oldClosing {
						oldHasRevokedCommit = !oldClosing.revokedCommitPublished.isEmpty
					}
				}

				var newHasRevokedCommit = false
				do {
					var newClosing: Lightning_kmpClosing? = newChannel.asClosing()
					if newClosing == nil {
						newClosing = newChannel.asOffline()?.state.asClosing()
					}

					if let newClosing = newChannel.asClosing() {
						newHasRevokedCommit = !newClosing.revokedCommitPublished.isEmpty
					}
				}

				if !oldHasRevokedCommit && newHasRevokedCommit {
					revokedChannelIds.insert(channelId)
				}
			}
		}
		
		return revokedChannelIds
	}
	
	// --------------------------------------------------
	// MARK: Ordering
	// --------------------------------------------------
	
	private func currentBizHasInFlightTransactions() -> Bool {
		let channels = Biz.business.peerManager.channelsValue()
		return hasInFlightTransactions(channels)
	}
	
	private func nextWalletToCheck() -> WatchTowerTarget? {
		
		guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
			log.warning("SecurityFile.current(): nil found")
			return nil
		}
		guard case .v1(let v1) = securityFile else {
			log.warning("SecurityFile.current(): v0 found")
			return nil
		}
		
		let currentWalletId = Biz.walletId
		let allTargets: [WatchTowerTarget] = v1.allKeys().map { keyInfo in
			
			let prefs = Prefs_Wallet(id: keyInfo.keychainKeyId)
			
			let lastAttemptDate = prefs.watchTower_lastAttemptDate
			let lastAttemptFailed = prefs.watchTower_lastAttemptFailed
			
			let nextAttemptDate: Date
			if lastAttemptFailed {
				nextAttemptDate = lastAttemptDate.addingTimeInterval(2.hours())
			} else {
				nextAttemptDate = lastAttemptDate.addingTimeInterval(2.days())
			}
			
			var isCurrent: Bool = false
			if let current = currentWalletId {
				isCurrent = (current.nodeId == keyInfo.nodeId) && (current.chain == keyInfo.chain)
			}
			
			return WatchTowerTarget(
				keyInfo: keyInfo,
				nextAttemptDate: nextAttemptDate,
				lastAttemptDate: lastAttemptDate,
				lastAttemptFailed: lastAttemptFailed,
				isCurrent: isCurrent
			)
		}
		
		let sortedTargets = allTargets.sorted { (targetA, targetB) in
			
			// Return true if the first argument should be ordered before the second argument.
			// Otherwise, return false.
			
			return targetA.nextAttemptDate < targetB.nextAttemptDate
		}
		
	#if DEBUG && true
		// Test running WatchTower task on non-current wallet
		return sortedTargets.filter { !$0.isCurrent }.first
	#else
		return sortedTargets.first
	#endif
	}
	
	// --------------------------------------------------
	// MARK: Task Management
	// --------------------------------------------------
	
	private func registerBackgroundTask() {
		log.trace(#function)
		
		BGTaskScheduler.shared.register(
			forTaskWithIdentifier: taskId_watchTower,
			using: DispatchQueue.main
		) { (task) in
			
			log.debug("BGTaskScheduler.executeTask()")
						 
			if let task = task as? BGAppRefreshTask {
				self.performTask(task)
			}
		}
	}
	
	private func scheduleBackgroundTask() {
		log.trace(#function)
		
		// As per the docs:
		// > There can be a total of 1 refresh task and 10 processing tasks scheduled at any time.
		// > Trying to schedule more tasks returns BGTaskScheduler.Error.Code.tooManyPendingTaskRequests.
		
		let task = BGAppRefreshTaskRequest(identifier: taskId_watchTower)
		
		if currentBizHasInFlightTransactions() {
			log.debug("currentBizHasInFlightTransactions: true")
			task.earliestBeginDate = Date(timeIntervalSinceNow: 4.hours())
			
		} else if let target = nextWalletToCheck() {
			log.debug("nextWalletToCheck(): non-nil")
			
			let earliestRequest = Date(timeIntervalSinceNow: 4.hours())
			task.earliestBeginDate = max(earliestRequest, target.nextAttemptDate)
			
			log.debug("earliestRequest: \(earliestRequest)")
			log.debug("target.nextAttemptDate: \(target.nextAttemptDate)")
			
		} else {
			log.debug("nextWalletToCheck(): nil")
			
			// As per WWDC talk (https://developer.apple.com/videos/play/wwdc2019/707):
			// It's recommended that this value be a week or less.
			task.earliestBeginDate = Date(timeIntervalSinceNow: 2.days())
		}
		
	#if !targetEnvironment(simulator) // background tasks not available in simulator
		do {
			try BGTaskScheduler.shared.submit(task)
			log.debug("BGTaskScheduler.submit: success")
		} catch {
			log.error("BGTaskScheduler.submit: \(error.localizedDescription)")
		}
	#endif
	}
	
	// --------------------------------------------------
	// MARK: Task Execution
	// --------------------------------------------------
	
	/// How to debug this:
	/// https://www.andyibanez.com/posts/modern-background-tasks-ios13/
	///
	private func performTask(_ task: BGAppRefreshTask) {
		log.trace(#function)
		
		// Kotlin will crash below if we attempt to run this code on non-main thread
		assertMainThread()
		
		if let target = nextWalletToCheck(), !target.isCurrent {
			performTask(task, target)
			
		} else { // check the current wallet
			if let walletId = Biz.walletId {
				performTask(task, Biz.business, walletId.keychainKeyId)
			} else {
				completeTask(task, success: true)
			}
		}
	}
	
	private func performTask(_ task: BGAppRefreshTask, _ target: WatchTowerTarget) {
		log.trace(#function)
		
		let id = target.keyInfo.keychainKeyId
		let business = PhoenixBusiness(ctx: PlatformContext.default)

		business.currencyManager.disableAutoRefresh()
		
		guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
			log.warning("SecurityFile.current(): nil found")
			return completeTask(task, success: true)
		}
		guard case .v1(let v1) = securityFile else {
			log.warning("SecurityFile.current(): v0 found")
			return completeTask(task, success: true)
		}
		guard let keyInfo = v1.wallets[id]?.keychain else {
			log.warning("SecurityFile.current().getWallet(): nil found")
			return completeTask(task, success: true)
		}
		
		let keychainResult = SharedSecurity.shared.readKeychainEntry(id, keyInfo)
		guard case .success(let cleartextData) = keychainResult else {
			log.warning("readKeychainEntry(): failed")
			return completeTask(task, success: true)
		}

		let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
		guard case .success(let recoveryPhrase) = decodeResult else {
			log.warning("decodeRecoveryPhrase(): failed")
			return completeTask(task, success: true)
		}
		guard let language = recoveryPhrase.language else {
			log.warning("recoveryPhrase.language == nil")
			return completeTask(task, success: true)
		}

		let seed = business.walletManager.mnemonicsToSeed(
			mnemonics: recoveryPhrase.mnemonicsArray,
			wordList: language.wordlist(),
			passphrase: ""
		)
		let walletInfo = business.walletManager.loadWallet(seed: seed)
		let wid = WalletIdentifier(chain: business.chain, walletInfo: walletInfo)
		
		let groupPrefs = GroupPrefs.wallet(wid)
		
		if let electrumConfigPrefs = groupPrefs.electrumConfig {
			business.appConfigurationManager.updateElectrumConfig(config: electrumConfigPrefs.customConfig)
		} else {
			business.appConfigurationManager.updateElectrumConfig(config: nil)
		}
		
		let primaryFiatCurrency = groupPrefs.fiatCurrency
		let preferredFiatCurrencies = AppConfigurationManager.PreferredFiatCurrencies(
			primary: primaryFiatCurrency,
			others: groupPrefs.preferredFiatCurrencies
		)
		business.appConfigurationManager.updatePreferredFiatCurrencies(
			current: preferredFiatCurrencies
		)

		let startupParams = StartupParams(
			isTorEnabled: groupPrefs.isTorEnabled,
			liquidityPolicy: groupPrefs.liquidityPolicy.toKotlin()
		)
		business.start(startupParams: startupParams)

		business.appConnectionsDaemon?.incrementDisconnectCount(
			target: AppConnectionsDaemon.ControlTarget.companion.All
		)
		business.currencyManager.refreshAll(targets: [primaryFiatCurrency], force: false)
		
		performTask(task, business, id)
	}
	
	private func performTask(_ task: BGAppRefreshTask, _ business: PhoenixBusiness, _ id: String) {
		log.trace(#function)
		
		// There are 2 tasks we may need to perform:
		//
		// 1) WatchTower task
		//
		//    If our channel partner attempts to cheat, and broadcasts a revoked transaction,
		//    then our WatchTower task will spot the TX, issue a penalty TX, and collect
		//    all the funds in the channel.
		//
		//    Since we use a relatively long `to_self_delay` (2016 blocks ≈ 14 days),
		//    this gives us plenty of time to catch a cheater.
		//
		//    For more information, see (in lightning-kmp project):
		//    - NodeParams.toRemoteDelayBlocks
		//    - NodeParams.maxToLocalDelayBlocks
		//
		// 2) PendingTxHandler task
		//
		//    Transactions may become stuck in the network, and we want to ensure that our node
		//    comes online to properly cancel the TX before it times out and forces a channel to close.
		//
		//    So when we have pending Tx's, we need to connect to the server, and update our state(s).
		
		let _true = Date.distantPast < Date.now
		// ^^^^^^ dear compiler,
		// stop emitting warnings for variables that might be changed for debugging, thanks
		
	#if DEBUG
		let performWatchTowerTask = _true // only disable for debugging purposes
	#else
		let performWatchTowerTask = _true // always perform this task
	#endif
		
		let channels = business.peerManager.channelsValue()
		let performPendingTxTask = hasInFlightTransactions(channels)
		
		let appConnectionsDaemon = business.appConnectionsDaemon
		
		let target: AppConnectionsDaemon.ControlTarget
		if performWatchTowerTask && performPendingTxTask {
			target = AppConnectionsDaemon.ControlTarget.companion.All
		} else if performWatchTowerTask {
			target = AppConnectionsDaemon.ControlTarget.companion.ElectrumPlusTor
		} else {
			target = AppConnectionsDaemon.ControlTarget.companion.AllMinusElectrum
		}
		
		var didDecrement = false
		var setupTask: Task<Void, Error>? = nil
		var watchTowerTask: Task<Void, Error>? = nil
		var pendingTxTask: Task<Void, Error>? = nil
		
		var peer: Lightning_kmpPeer? = nil
		var oldChannels = [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState]()
		
		let cleanup = {(didTimeout: Bool) in
			log.debug("cleanup(didTimeout: \(didTimeout))")
			
			if didDecrement { // need to balance decrement call
				if let daemon = appConnectionsDaemon {
					log.error("appConnectionsDaemon.incrementDisconnectCount()....")
					business.appConnectionsDaemon?.incrementDisconnectCount(target: target)
				} else {
					log.error("appConnectionsDaemon is nil !!!")
				}
			} else {
				log.debug("!didDecrement (disconnect count)")
			}
			
			setupTask?.cancel()
			watchTowerTask?.cancel()
			pendingTxTask?.cancel()

			let prefs = Prefs_Wallet(id: id)
			prefs.watchTower_lastAttemptDate = Date.now
			prefs.watchTower_lastAttemptFailed = didTimeout
			
			self.scheduleBackgroundTask()
			
			if performWatchTowerTask {
				
				let newChannels = peer?.channels ?? [:]
				let revokedChannelIds = self.calculateRevokedChannelIds(
					oldChannels: oldChannels,
					newChannels: newChannels
				)
				
				if !revokedChannelIds.isEmpty {
					// One or more channels were force-closed, and we discovered the revoked commit(s) !
					
					NotificationsManager.shared.displayLocalNotification_revokedCommit()
					
					let outcome = WatchTowerOutcome.RevokedFound(channels: revokedChannelIds)
					business.notificationsManager.saveWatchTowerOutcome(outcome: outcome) { _ in
						self.completeTask(task, success: true)
					}
					
				} else if !didTimeout {
					// WatchTower completed successfully, and no cheating by the other party was found.
					
					let outcome = WatchTowerOutcome.Nominal(channelsWatchedCount: Int32(newChannels.count))
					business.notificationsManager.saveWatchTowerOutcome(outcome: outcome) { _ in
						self.completeTask(task, success: true)
					}
					
				} else {
					// The BGAppRefreshTask timed out (iOS only gives us ~30 seconds)
					
					let outcome = WatchTowerOutcome.Unknown()
					business.notificationsManager.saveWatchTowerOutcome(outcome: outcome) { _ in
						self.completeTask(task, success: false)
					}
				}
				
			} else {
				
				self.completeTask(task, success: !didTimeout)
			}
			
			if business != Biz.business {
				business.stop(closeDatabases: true)
			}
		}
		
		var finishedWatchTowerTask = performWatchTowerTask ? false : true
		var finishedPendingTxTask = performPendingTxTask ? false : true
		
		let maybeCleanup = {(didTimeout: Bool) in
			log.debug("maybeCleanup(didTimeout: \(didTimeout))")
			if (finishedWatchTowerTask && finishedPendingTxTask) {
				cleanup(didTimeout)
			}
		}
		
		let finishWatchTowerTask = {(didTimeout: Bool) in
			DispatchQueue.main.async {
				if !finishedWatchTowerTask {
					finishedWatchTowerTask = true
					log.debug("finishWatchTowerTask(didTimeout: \(didTimeout))")
					maybeCleanup(didTimeout)
				}
			}
		}
		
		let finishPendingTxTask = {(didTimeout: Bool) in
			DispatchQueue.main.async {
				if !finishedPendingTxTask {
					finishedPendingTxTask = true
					log.debug("finishPendingTxTask(didTimeout: \(didTimeout)")
					maybeCleanup(didTimeout)
				}
			}
		}
		
		let abortTasks = {(didTimeout: Bool) in
			log.debug("abortTasks(didTimeout: \(didTimeout)")
			finishWatchTowerTask(didTimeout)
			finishPendingTxTask(didTimeout)
		}
		
		task.expirationHandler = {
			log.debug("task.expirationHandler(): fired")
			abortTasks(/* didTimeout: */ true)
		}
		
		guard (performWatchTowerTask || performPendingTxTask) else {
			log.debug("aborting: no tasks to perform")
			return abortTasks(/* didTimeout: */ false)
		}
		
		let startWatchTowerTask = {
			watchTowerTask = Task { @MainActor in
				
				// We setup a listener so we know when the WatchTower task has completed.
				// I.e. when the channel subscriptions are considered up-to-date.
				
				let minMillis = Date.now.toMilliseconds()
				
				log.debug("watchTowerTask: waiting for electrum up-to-date signal...")
				for try await millis in business.electrumWatcher.upToDatePublisher().values {
					// millis => timestamp of when electrum watch was marked up-to-date
					if millis > minMillis {
						log.debug("watchTowerTask: done")
						finishWatchTowerTask(/* didTimeout: */ false)
					}
				}
			}
		}
		
		let startPendingTxTask = {
			pendingTxTask = Task { @MainActor in
				
				// Wait until we're connected
				log.debug("pendingTxTask: waiting for connections...")
				for try await connections in business.connectionsManager.asyncStream() {
					if connections.targetsEstablished(target) {
						log.debug("pendingTxTask: connections established")
						break
					}
				}
				
				// Give the peer a max of 10 seconds to perform any needed tasks
				async let subtask1 = Task { @MainActor in
					try await Task.sleep(seconds: 10)
					log.debug("pendingTxTask: timed out")
					finishPendingTxTask(/* didTimeout: */ false)
				}
				
				// Check to see if the peer clears its pending tx's
				async let subtask2 = Task { @MainActor in
					log.debug("pendingTxTask: waiting for pending tx's to clear...")
					for try await channels in business.peerManager.channelsPublisher().values {
						if !self.hasInFlightTransactions(channels) {
							log.debug("pendingTxTask: pending tx's have cleared")
							break
						}
					}
					
					log.debug("pendingTxTask: waiting 2 seconds for cleanup...")
					try await Task.sleep(seconds: 2) // a bit of cleanup time
					
					log.debug("pendingTxTask: done")
					finishPendingTxTask(/* didTimeout: */ false)
				}
				
				let _ = await [subtask1, subtask2]
			}
		}
		
		setupTask = Task { @MainActor in
			
			log.debug("setupTask: waiting for peer...")
			for try await value in business.peerManager.peerStatePublisher().values {
				peer = value
				break
			}
			
			guard let peer else {
				log.debug("aborting: peer is nil")
				// If there's not a peer, then there's nothing to do
				return abortTasks(/* didTimeout: */ false)
			}
			
			// peerManager.channelsPublisher() will fire when either:
			// - peer.bootChannelsFlow is non-nil
			// - peer.channelsFlow is non-nil
			//
			log.debug("setupTask: waiting for channels...")
			for try await value in business.peerManager.channelsPublisher().values {
				break
			}
			
			oldChannels = peer.channels
			if oldChannels.isEmpty {
				oldChannels = peer.bootChannelsFlowValue
			}
			
			guard !oldChannels.isEmpty else {
				log.debug("aborting: no channels")
				// If we don't have any channels, then there's nothing to do
				return abortTasks(/* didTimeout: */ false)
			}
			
			appConnectionsDaemon?.decrementDisconnectCount(target: target)
			didDecrement = true
			
			if performWatchTowerTask {
				startWatchTowerTask()
			}
			if performPendingTxTask {
				startPendingTxTask()
			}
		}
	}
	
	private func completeTask(_ task: BGAppRefreshTask, success: Bool) {
		log.trace("completeTask(_, success: \(success))")
		
		task.setTaskCompleted(success: success)
	}
}

struct WatchTowerTarget {
	let keyInfo: SecurityFile.V1.KeyInfo
	let nextAttemptDate: Date
	let lastAttemptDate: Date
	let lastAttemptFailed: Bool
	let isCurrent: Bool
}
