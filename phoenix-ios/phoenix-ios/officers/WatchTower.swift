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
	
	private var lastTaskFailed = false
	
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
		log.trace("### applicationDidFinishLaunching()")
		
		registerBackgroundTask()
	}
	
	func applicationDidEnterBackground() {
		log.trace("### applicationDidEnterBackground()")
		
		scheduleBackgroundTask()
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	private func hasInFlightTransactions(_ channels: [LocalChannelInfo]) -> Bool {
		return channels.contains(where: { $0.inFlightPaymentsCount > 0 })
	}
	
	private func hasInFlightTransactions() -> Bool {
		let channels = Biz.business.peerManager.channelsValue()
		return hasInFlightTransactions(channels)
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

					if let oldClosing = oldClosing {
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
	// MARK: Task Management
	// --------------------------------------------------
	
	private func registerBackgroundTask() -> Void {
		log.trace("registerBackgroundTask()")
		
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
		
		// As per the docs:
		// > There can be a total of 1 refresh task and 10 processing tasks scheduled at any time.
		// > Trying to schedule more tasks returns BGTaskScheduler.Error.Code.tooManyPendingTaskRequests.
		
		let task = BGAppRefreshTaskRequest(identifier: taskId_watchTower)
		
		if hasInFlightTransactions() {
			task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 4)) // 2 hours
			
		} else {
			
			if lastTaskFailed {
				task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 4)) // 4 hours
			} else {
				// As per WWDC talk (https://developer.apple.com/videos/play/wwdc2019/707):
				// It's recommended that this value be a week or less.
				task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 24 * 2)) // 2 days
			}
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
	private func performTask(_ task: BGAppRefreshTask) -> Void {
		log.trace("performTask()")
		
		// Kotlin will crash below if we attempt to run this code on non-main thread
		assertMainThread()
		
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
		let performPendingTxTask = hasInFlightTransactions()
		let performBothTasks = performWatchTowerTask && performPendingTxTask
		
		let business = Biz.business
		let appConnectionsDaemon = business.appConnectionsDaemon
		
		let target: AppConnectionsDaemon.ControlTarget
		if (performBothTasks || !performWatchTowerTask) {
			target = AppConnectionsDaemon.ControlTarget.companion.All
		} else if !performWatchTowerTask {
			target = AppConnectionsDaemon.ControlTarget.companion.AllMinusElectrum
		} else {
			target = AppConnectionsDaemon.ControlTarget.companion.ElectrumPlusTor
		}
		
		var didDecrement = false
		var watchTowerListener: AnyCancellable? = nil
		var pendingTxHandler: Task<Void, Error>? = nil
		
		var peer: Lightning_kmpPeer? = nil
		var oldChannels = [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState]()
		
		let cleanup = {(didTimeout: Bool) in
			log.debug("cleanup()")
			
			if didDecrement { // need to balance decrement call
				appConnectionsDaemon?.incrementDisconnectCount(target: target)
			}
			
			watchTowerListener?.cancel()
			pendingTxHandler?.cancel()

			self.lastTaskFailed = didTimeout
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
						task.setTaskCompleted(success: true)
					}
					
				} else if !didTimeout {
					// WatchTower completed successfully, and no cheating by the other party was found.
					
					let outcome = WatchTowerOutcome.Nominal(channelsWatchedCount: Int32(newChannels.count))
					business.notificationsManager.saveWatchTowerOutcome(outcome: outcome) { _ in
						task.setTaskCompleted(success: true)
					}
					
				} else {
					// The BGAppRefreshTask timed out (iOS only gives us ~30 seconds)
					
					let outcome = WatchTowerOutcome.Unknown()
					business.notificationsManager.saveWatchTowerOutcome(outcome: outcome) { _ in
						task.setTaskCompleted(success: false)
					}
				}
				
			} else {
				
				task.setTaskCompleted(success: !didTimeout)
			}
		}
		
		var finishedWatchTowerTask = performWatchTowerTask ? false : true
		var finishedPendingTxTask = performPendingTxTask ? false : true
		
		let maybeCleanup = {(didTimeout: Bool) in
			if (finishedWatchTowerTask && finishedPendingTxTask) {
				cleanup(didTimeout)
			}
		}
		
		let finishWatchTowerTask = {(didTimeout: Bool) in
			DispatchQueue.main.async {
				if !finishedWatchTowerTask {
					finishedWatchTowerTask = true
					log.debug("finishWatchTowerTask()")
					maybeCleanup(didTimeout)
				}
			}
		}
		
		let finishPendingTxTask = {(didTimeout: Bool) in
			DispatchQueue.main.async {
				if !finishedPendingTxTask {
					finishedPendingTxTask = true
					log.debug("finishPendingTxTask()")
					maybeCleanup(didTimeout)
				}
			}
		}
		
		let abortTasks = {(didTimeout: Bool) in
			finishWatchTowerTask(didTimeout)
			finishPendingTxTask(didTimeout)
		}
		
		task.expirationHandler = {
			abortTasks(/* didTimeout: */ true)
		}
		
		guard (performWatchTowerTask || performPendingTxTask) else {
			return abortTasks(/* didTimeout: */ false)
		}
		
		peer = business.peerManager.peerStateValue()
		guard let _peer = peer else {
			// If there's not a peer, then there's nothing to do
			return abortTasks(/* didTimeout: */ false)
		}
		
		oldChannels = _peer.channels
		guard oldChannels.count > 0 else {
			// If we don't have any channels, then there's nothing to do
			return abortTasks(/* didTimeout: */ false)
		}
		
		appConnectionsDaemon?.decrementDisconnectCount(target: target)
		didDecrement = true
		
		if performWatchTowerTask {
			// We setup a handler so we know when the WatchTower task has completed.
			// I.e. when the channel subscriptions are considered up-to-date.
			
			let minMillis = Date.now.toMilliseconds()
			watchTowerListener = Biz.business.electrumWatcher.upToDatePublisher().sink { (millis: Int64) in
				// millis => timestamp of when electrum watch was marked up-to-date
				if millis > minMillis {
					finishWatchTowerTask(/* didTimeout: */ false)
				}
			}
		}
		
		if performPendingTxTask {
			pendingTxHandler = Task { @MainActor in
				
				// Wait until we're connected
				for try await connections in Biz.business.connectionsManager.asyncStream() {
					if connections.targetsEstablished(target) {
						break
					}
				}
				
				// Give the peer a max of 10 seconds to perform any needed tasks
				async let subtask1 = Task { @MainActor in
					try await Task.sleep(seconds: 10)
					finishPendingTxTask(/* didTimeout: */ false)
				}
				
				// Check to see if the peer clears its pending TX's
				async let subtask2 = Task { @MainActor in
					for try await channels in Biz.business.peerManager.channelsPublisher().values {
						if !hasInFlightTransactions(channels) {
							break
						}
					}
					try await Task.sleep(seconds: 2) // a bit of cleanup time
					finishPendingTxTask(/* didTimeout: */ false)
				}
				
				let _ = await [subtask1, subtask2]
			}
		}
	}
}
