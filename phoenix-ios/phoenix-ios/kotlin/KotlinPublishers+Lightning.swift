import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "KotlinPublishers+Lightning"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension Lightning_kmpPeer {
	
	fileprivate struct _Key {
		static var eventsFlowPublisher = 0
	}
	
	func eventsFlowPublisher() -> AnyPublisher<Lightning_kmpPeerEvent, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.eventsFlowPublisher) {
			
			/// Transforming from Kotlin:
			/// `eventsFlow: SharedFlow<PeerEvent>`
			///
			KotlinPassthroughSubject<AnyObject>(
				self.eventsFlow._bridgeToObjectiveC()
			)
			.compactMap { $0 as? Lightning_kmpPeerEvent }
			.eraseToAnyPublisher()
		}
	}
}

extension Lightning_kmpElectrumClient {
	
	fileprivate struct _Key {
		static var notificationsPublisher = 0
	}
	
	func notificationsPublisher() -> AnyPublisher<Lightning_kmpElectrumSubscriptionResponse, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.notificationsPublisher) {
			
			/// Transforming from Kotlin:
			/// `notifications: Flow<ElectrumSubscriptionResponse>`
			///
			KotlinPassthroughSubject<AnyObject>(
				self.notifications._bridgeToObjectiveC()
			)
			.compactMap { $0 as? Lightning_kmpElectrumSubscriptionResponse }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension Lightning_kmpElectrumWatcher {
	
	fileprivate struct _Key {
		static var upToDatePublisher = 0
	}
	
	func upToDatePublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.upToDatePublisher) {
			
			/// Transforming from Kotlin:
			/// `openUpToDateFlow(): Flow<Long>`
			///
			KotlinPassthroughSubject<AnyObject>(
				self.openUpToDateFlow()._bridgeToObjectiveC()
			)
			.compactMap { $0 as? KotlinLong }
			.map { $0.int64Value }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension Lightning_kmpNodeParams {
	
	func nodeEventsSequence() -> AnyAsyncSequence<Lightning_kmpNodeEvents> {
		
		return self.nodeEvents
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

extension Lightning_kmpSwapInWallet {
	
	fileprivate struct _Key {
		static var swapInAddressPublisher = 0
	}
	
	struct SwapInAddressInfo {
		let addr: String
		let index: Int
	}
	
	func swapInAddressSequence() -> AnyAsyncSequence<SwapInAddressInfo?> {
		
		return self.swapInAddressFlow
			.map {
				var result: SwapInAddressInfo? = nil
				if let pair = $0 as? KotlinPair<NSString, KotlinInt>,
					let addr = pair.first as? String,
					let index = pair.second
				{
					result = SwapInAddressInfo(addr: addr, index: index.intValue)
				}
				return result
			}
			.eraseToAnyAsyncSequence()
	}
}
