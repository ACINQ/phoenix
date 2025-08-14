import Foundation
import Combine

fileprivate let filename = "MultiBusinessManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// Short-hand for `MultiBusinessManager.shared`
///
let MBiz = MultiBusinessManager.shared

/// Short-hand for `MBiz.current`
///
var Biz: BusinessManager {
	return MBiz.current
}

class MultiBusinessManager {
	
	/// Singleton instance
	public static let shared = MultiBusinessManager()
	
	public let currentBizPublisher: CurrentValueSubject<BusinessManager, Never>
	
	public var current: BusinessManager {
		return currentBizPublisher.value
	}
	
	private init() { // must use shared instance
		log.trace(#function)
		
		let biz = BusinessManager()
		currentBizPublisher = CurrentValueSubject(biz)
	}
	
	func reset() {
		log.trace(#function)
		
		self.current.stop()
		let newBiz = BusinessManager()
		currentBizPublisher.send(newBiz)
	}
}
