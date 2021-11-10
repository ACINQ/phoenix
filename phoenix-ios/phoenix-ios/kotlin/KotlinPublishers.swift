import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "KotlinPublishers"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

extension PhoenixBusiness {
	
	fileprivate struct _Key {
		static var peerPublisher = 0
	}

	func peerPublisher() -> AnyPublisher<Lightning_kmpPeer, Never> {

		executeOnce(storageKey: &_Key.peerPublisher) {
			
			// Transforming from Kotlin:
			// ```
			// peerState: StateFlow<Peer?>
			// ```
			KotlinCurrentValueSubject<Lightning_kmpPeer, Lightning_kmpPeer?>(
				self.peerState()
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

extension CurrencyManager {
	
	fileprivate struct _Key {
		static var ratesPublisher = 0
		static var refreshPublisher = 0
	}
	
	func ratesPubliser() -> AnyPublisher<[ExchangeRate], Never> {
		
		executeOnce(storageKey: &_Key.ratesPublisher) {
			
			// Transforming from Kotlin:
			// `ratesFlow: Flow<List<ExchangeRate>>`
			//
			KotlinPassthroughSubject<NSArray, [ExchangeRate]>(
				self.ratesFlow
			)
			.eraseToAnyPublisher()
		}
	}
	
	func refreshPublisher() -> AnyPublisher<Bool, Never> {
		
		executeOnce(storageKey: &_Key.refreshPublisher) {
			
			// Transforming from Kotlin:
			// `refreshFlow: StateFlow<Target>`
			//
			KotlinCurrentValueSubject<Target, Target>(
				self.refreshFlow
			)
			.map { (target: Target) -> Bool in
				return target != CurrencyManager.Target.companion.None
			}
			.eraseToAnyPublisher()
		}
	}
}

extension PaymentsManager {
	
	fileprivate struct _Key {
		static var paymentsPagePublisher = 0
		static var incomingSwapsPublisher = 0
		static var lastCompletedPaymentPublisher = 0
		static var lastIncomingPaymentPublisher = 0
		static var inFlightOutgoingPaymentsPublisher = 0
	}
	
	func paymentsPagePublisher() -> AnyPublisher<PaymentsPage, Never> {
		
		executeOnce(storageKey: &_Key.paymentsPagePublisher) {
			
			// Transforming from Kotlin:
			// `paymentsPage: StateFlow<PaymentsPage>`
			//
			KotlinCurrentValueSubject<PaymentsPage, PaymentsPage>(
				self.paymentsPage
			).eraseToAnyPublisher()
		}
	}
	
	func incomingSwapsPublisher() -> AnyPublisher<[String: Lightning_kmpMilliSatoshi], Never> {
		
		executeOnce(storageKey: &_Key.incomingSwapsPublisher) {
			
			// Transforming from Kotlin:
			// `incomingSwaps: StateFlow<Map<String, MilliSatoshi>>`
			//
			KotlinCurrentValueSubject<NSDictionary, [String: Lightning_kmpMilliSatoshi]>(
				self.incomingSwaps
			)
			.eraseToAnyPublisher()
		}
	}
	
	func lastCompletedPaymentPublisher() -> AnyPublisher<Lightning_kmpWalletPayment, Never> {
		
		executeOnce(storageKey: &_Key.lastCompletedPaymentPublisher) {
			
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
				self.lastCompletedPayment
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
	
	func lastIncomingPaymentPublisher() -> AnyPublisher<Lightning_kmpIncomingPayment, Never> {
		
		executeOnce(storageKey: &_Key.lastIncomingPaymentPublisher) {
		
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
				self.lastCompletedPayment
			)
			.compactMap {
				return $0 as? Lightning_kmpIncomingPayment
			}
			.eraseToAnyPublisher()
		}
	}
	
	func inFlightOutgoingPaymentsPublisher() -> AnyPublisher<Int, Never> {
		
		executeOnce(storageKey: &_Key.inFlightOutgoingPaymentsPublisher) {
			
			// Transforming from Kotlin:
			// `inFlightOutgoingPayments: StateFlow<Set<UUID>>`
			//
			KotlinCurrentValueSubject<NSSet, Set<Lightning_kmpUUID>>(
				self.inFlightOutgoingPayments
			)
			.map {
				return $0.count
			}
			.eraseToAnyPublisher()
		}
	}
}

extension AppConfigurationManager {
	
	fileprivate struct _Key {
		static var chainContextPublisher = 0
	}
	
	func chainContextPublisher() -> AnyPublisher<WalletContext.V0ChainContext, Never> {
		
		executeOnce(storageKey: &_Key.chainContextPublisher) {
			
			// Transforming from Kotlin:
			// `chainContext: StateFlow<WalletContext.V0.ChainContext?>`
			//
			KotlinCurrentValueSubject<WalletContext.V0ChainContext, WalletContext.V0ChainContext?>(
				self.chainContext
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
		
		executeOnce(storageKey: &_Key.upToDatePublisher) {
			
			/// Transforming from Kotlin:
			/// `openUpToDateFlow(): Flow<Long>`
			///
			KotlinPassthroughSubject<KotlinLong, Int64>(
				self.openUpToDateFlow()
			)
			.eraseToAnyPublisher()
		}
	}
}

extension Lightning_kmpPeer {
	
	fileprivate struct _Key {
		static var channelsPublisher = 0
	}
	
	typealias ChannelsMap = [Bitcoin_kmpByteVector32: Lightning_kmpChannelState]
	
	func channelsPublisher() -> AnyPublisher<ChannelsMap, Never> {
		
		executeOnce(storageKey: &_Key.channelsPublisher) {
			
			/// Transforming from Kotlin:
			/// `channelsFlow: StateFlow<Map<ByteVector32, ChannelState>>`
			///
			KotlinCurrentValueSubject<NSDictionary, ChannelsMap>(
				self.channelsFlow
			)
			.eraseToAnyPublisher()
		}
	}
}

extension CloudKitDb {
	
	fileprivate struct _Key {
		static var fetchQueueCountPublisher = 0
	}
	
	func fetchQueueCountPublisher() -> AnyPublisher<Int64, Never> {
		
		executeOnce(storageKey: &_Key.fetchQueueCountPublisher) {
			
			/// Transforming from Kotlin:
			/// `queueCount: StateFlow<Long>`
			///
			KotlinCurrentValueSubject<KotlinLong, Int64>(
				self.queueCount
			).eraseToAnyPublisher()
		}
	}
}
