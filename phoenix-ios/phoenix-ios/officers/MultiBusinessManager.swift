import Foundation
import Combine

fileprivate let filename = "BusinessManager"
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
let Biz = MBiz.current


class MultiBusinessManager {
	
	/// Singleton instance
	public static let shared = MultiBusinessManager()
	
	public let currentBizPublisher: CurrentValueSubject<BusinessManager, Never>
	
	public let bizListPublisher: CurrentValueSubject<[BusinessManager], Never>
	
	public var current: BusinessManager {
		return currentBizPublisher.value
	}
	
	public var bizList: [BusinessManager] {
		return bizListPublisher.value
	}
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	private init() { // must use shared instance
		
		let biz = BusinessManager()
		currentBizPublisher = CurrentValueSubject(biz)
		bizListPublisher = CurrentValueSubject([biz])
	}
}
