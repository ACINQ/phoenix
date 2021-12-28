import Foundation
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncTxManager_PendingSettings"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class SyncTxManager_PendingSettings: Equatable, CustomStringConvertible {
	
	enum EnableDisable{
	  case willEnable
	  case willDisable
	}
	
	private weak var parent: SyncTxManager?
	
	let paymentSyncing: EnableDisable
	let delay: TimeInterval
	let startDate: Date
	let fireDate: Date
	
	init(_ parent: SyncTxManager, enableSyncing delay: TimeInterval) {
		let now = Date()
		self.parent = parent
		self.paymentSyncing = .willEnable
		self.delay = delay
		self.startDate = now
		self.fireDate = now + delay
		log.trace("init()")
		startTimer()
	}
	
	init(_ parent: SyncTxManager, disableSyncing delay: TimeInterval) {
		let now = Date()
		self.parent = parent
		self.paymentSyncing = .willDisable
		self.delay = delay
		self.startDate = now
		self.fireDate = now + delay
		log.trace("init()")
		startTimer()
	}
	
	deinit {
		log.trace("deinit()")
	}
	
	private func startTimer() {
		log.trace("startTimer()")
		
		let deadline: DispatchTime = DispatchTime.now() + fireDate.timeIntervalSinceNow
		DispatchQueue.global(qos: .utility).asyncAfter(deadline: deadline) {[weak self] in
			self?.approve()
		}
	}
	
	/// Automatically invoked after the timer expires.
	/// Can also be called manually to approve before the delay.
	///
	func approve() {
		log.trace("approve()")
		
		if let parent = parent {
			parent.updateState(pending: self, approved: true)
		}
	}
	
	func cancel() {
		log.trace("cancel()")
		
		if let parent = parent {
			parent.updateState(pending: self, approved: false)
		}
	}
	
	var description: String {
		
		let dateStr = fireDate.description(with: Locale.current)
		switch paymentSyncing {
		case .willEnable:
			return "<SyncTxManager_PendingSettings: willEnable @ \(dateStr)>"
		case .willDisable:
			return "<SyncTxManager_PendingSettings: willDisable @ \(dateStr)>"
		}
	}
	
	static func == (lhs: SyncTxManager_PendingSettings, rhs: SyncTxManager_PendingSettings) -> Bool {
		
		return (lhs.paymentSyncing == rhs.paymentSyncing) && (lhs.fireDate == rhs.fireDate)
	}
}
