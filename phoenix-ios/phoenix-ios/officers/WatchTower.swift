import Foundation
import BackgroundTasks
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "WatchTower"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

class WatchTower {
	
	// The taskID must match the value in Info.plist
	private static let taskId_watchTower = "co.acinq.phoenix.WatchTower"
	
	public static func registerBackgroundTasks() -> Void {
		log.trace("registerWatchTowerTask()")
		
		BGTaskScheduler.shared.register(
			forTaskWithIdentifier: taskId_watchTower,
			using: DispatchQueue.main
		) { (task) in
			
			if let task = task as? BGAppRefreshTask {
				log.debug("BGTaskScheduler.executeTask: WatchTower")
				
				self.performWatchTowerTask(task)
			}
		}
	}
	
	public static func scheduleBackgroundTasks(soon: Bool = false) {
		
		// As per the docs:
		// > There can be a total of 1 refresh task and 10 processing tasks scheduled at any time.
		// > Trying to schedule more tasks returns BGTaskScheduler.Error.Code.tooManyPendingTaskRequests.
		
		let task = BGAppRefreshTaskRequest(identifier: taskId_watchTower)
		
		// As per WWDC talk (https://developer.apple.com/videos/play/wwdc2019/707):
		// It's recommended this value be a week or less.
		//
		if soon { // last attempt failed
			task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 4)) // 4 hours
			
		} else { // last attempt succeeded
			task.earliestBeginDate = Date(timeIntervalSinceNow: (60 * 60 * 24 * 2)) // 2 days
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
	
	/// How to debug this:
	/// https://www.andyibanez.com/posts/modern-background-tasks-ios13/
	///
	private static func performWatchTowerTask(_ task: BGAppRefreshTask) -> Void {
		log.trace("performWatchTowerTask()")
		
		// kotlin will crash below if we attempt to run this code on non-main thread
		assertMainThread()
		
		let business = Biz.business
		let appConnectionsDaemon = business.appConnectionsDaemon
		let electrumTarget = AppConnectionsDaemon.ControlTarget.companion.Electrum
		
		var didDecrement = false
		var upToDateListener: AnyCancellable? = nil
		
		var peer: Lightning_kmpPeer? = nil
		var oldChannels = [Bitcoin_kmpByteVector32 : Lightning_kmpChannelState]()
		
		let cleanup = {(success: Bool) in
			
			if didDecrement { // need to balance decrement call
				appConnectionsDaemon?.incrementDisconnectCount(target: electrumTarget)
			}
			upToDateListener?.cancel()

			var notifyRevokedCommit = false
			let newChannels = peer?.channels ?? [:]

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
						notifyRevokedCommit = true
					}
				}
			}

			if notifyRevokedCommit {
				NotificationsManager.shared.displayLocalNotification_revokedCommit()
			}

			self.scheduleBackgroundTasks(soon: success ? false : true)
			task.setTaskCompleted(success: false)
		}
		
		var isFinished = false
		let finishTask = {(success: Bool) in
			
			DispatchQueue.main.async {
				if !isFinished {
					isFinished = true
					cleanup(success)
				}
			}
		}
		
		task.expirationHandler = {
			finishTask(false)
		}
		
		peer = business.getPeer()
		guard let _peer = peer else {
			// If there's not a peer, then the wallet is locked.
			return finishTask(true)
		}
		
		oldChannels = _peer.channels
		guard oldChannels.count > 0 else {
			// We don't have any channels, so there's nothing to watch.
			return finishTask(true)
		}
		
		appConnectionsDaemon?.decrementDisconnectCount(target: electrumTarget)
		didDecrement = true
		
		// We setup a handler so we know when the WatchTower task has completed.
		// I.e. when the channel subscriptions are considered up-to-date.
		
		upToDateListener = _peer.watcher.upToDatePublisher().sink { (millis: Int64) in
			finishTask(true)
		}
	}
}
