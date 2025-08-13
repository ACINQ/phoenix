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
			KotlinPassthroughSubject<Lightning_kmpPeerEvent>(
				self.eventsFlow
			)
			.compactMap { $0 }
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
			KotlinPassthroughSubject<Lightning_kmpElectrumSubscriptionResponse>(
				self.notifications
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

extension Lightning_kmpElectrumMiniWallet {
	
	fileprivate struct _Key {
		static var walletStatePublisher = 0
	}
	
	func walletStatePublisher() -> AnyPublisher<Lightning_kmpWalletState, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.walletStatePublisher) {
			
			/// Transforming from Kotlin:
			/// `walletStateFlow: StateFlow<WalletState>`
			///
			KotlinCurrentValueSubject<Lightning_kmpWalletState>(
				self.walletStateFlow
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

extension Lightning_kmpElectrumWatcher {
	
	fileprivate struct _Key {
		static var upToDatePublisher = 0
	}
	
	func upToDatePublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.upToDatePublisher) {
			
			/// Transforming from Kotlin:
			/// `openUpToDateFlow(): Flow<Long>`
			///
			KotlinPassthroughSubject<KotlinLong>(
				self.openUpToDateFlow()
			)
			.compactMap { $0?.int64Value }
			.eraseToAnyPublisher()
		}
	}
}

extension Lightning_kmpNodeParams {
	
	fileprivate struct _Key {
		static var nodeEventsPublisher = 0
	}
	
	func nodeEventsPublisher() -> AnyPublisher<Lightning_kmpNodeEvents, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.nodeEventsPublisher) {
			
			/// Transforming from Kotlin:
			/// `nodeEvents: SharedFlow<NodeEvents>`
			///
			KotlinPassthroughSubject<Lightning_kmpNodeEvents>(
				self.nodeEvents
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
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
	
	func swapInAddressPublisher() -> AnyPublisher<SwapInAddressInfo?, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.swapInAddressPublisher) {
			
			/// Transforming from Kotlin:
			/// `MutableStateFlow<Pair<String, Int>?>`
			KotlinCurrentValueSubject<KotlinPair<NSString, KotlinInt>>(
				self.swapInAddressFlow
			)
			.map {
				if let pair = $0,
				   let addr = pair.first as? String,
				   let index = pair.second
				{
					return SwapInAddressInfo(addr: addr, index: index.intValue)
				} else {
					return nil
				}
			}
			.eraseToAnyPublisher()
		}
	}
}
