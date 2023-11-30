import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "KotlinPublishers+Phoenix"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

extension PeerManager {
	
	fileprivate struct _Key {
		static var peerStatePublisher = 0
		static var channelsPublisher = 0
		static var finalWalletPublisher = 0
	}
	
	func peerStatePublisher() -> AnyPublisher<Lightning_kmpPeer, Never> {

		self.getSetAssociatedObject(storageKey: &_Key.peerStatePublisher) {
			
			// Transforming from Kotlin:
			// ```
			// peerState: StateFlow<Peer?>
			// ```
			KotlinCurrentValueSubject<Lightning_kmpPeer>(
				self.peerState
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
	
	func channelsPublisher() -> AnyPublisher<[LocalChannelInfo], Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.channelsPublisher) {
			
			// Transforming from Kotlin:
			// ```
			// channelsFlow: StateFlow<Map<ByteVector32, LocalChannelInfo>?>
			// ```
			KotlinCurrentValueSubject<NSDictionary>(
				self.channelsFlow
			)
			.compactMap { $0 as? [Bitcoin_kmpByteVector32: LocalChannelInfo] }
			.map { Array($0.values) }
			.eraseToAnyPublisher()
		}
	}
	
	func finalWalletPublisher() -> AnyPublisher<Lightning_kmpWalletState.WalletWithConfirmations, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.finalWalletPublisher) {
			
			// Transforming from Kotlin:
			// ```
			// finalWallet: StateFlow<WalletState.WalletWithConfirmations?>
			// ```
			KotlinCurrentValueSubject<Lightning_kmpWalletState.WalletWithConfirmations>(
				self.finalWallet
			)
			.map { $0 ?? Lightning_kmpWalletState.WalletWithConfirmations.empty() }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension AppConfigurationManager {
	
	fileprivate struct _Key {
		static var walletContextPublisher = 0
	}
	
	func walletContextPublisher() -> AnyPublisher<WalletContext, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.walletContextPublisher) {
			
			// Transforming from Kotlin:
			// `walletContext: StateFlow<WalletContext?>`
			//
			KotlinCurrentValueSubject<WalletContext>(
				self.walletContext
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension BalanceManager {
	
	fileprivate struct _Key {
		static var balancePublisher = 0
		static var swapInWalletPublisher = 0
	}
	
	func balancePublisher() -> AnyPublisher<Lightning_kmpMilliSatoshi?, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.balancePublisher) {
			
			// Transforming from Kotlin:
			// `balance: StateFlow<MilliSatoshi?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpMilliSatoshi>(
				self.balance
			)
			.eraseToAnyPublisher()
		}
	}
	
	func swapInWalletPublisher() -> AnyPublisher<Lightning_kmpWalletState.WalletWithConfirmations, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.swapInWalletPublisher) {
			
			// Transforming from Kotlin:
			// ```
			// swapInWallet: StateFlow<WalletState.WalletWithConfirmations?>
			// ```
			KotlinCurrentValueSubject<Lightning_kmpWalletState.WalletWithConfirmations>(
				self.swapInWallet
			)
			.map { $0 ?? Lightning_kmpWalletState.WalletWithConfirmations.empty() }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension ConnectionsManager {
	
	fileprivate struct _Key {
		static var connectionsPublisher = 0
	}
	
	func connectionsPublisher() -> AnyPublisher<Connections, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.connectionsPublisher) {
			
			// Transforming from Kotlin:
			// `connections: StateFlow<Connections>`
			//
			KotlinCurrentValueSubject<Connections>(
				self.connections
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension CurrencyManager {
	
	fileprivate struct _Key {
		static var ratesPublisher = 0
		static var refreshPublisher = 0
	}
	
	func ratesPubliser() -> AnyPublisher<[ExchangeRate], Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.ratesPublisher) {
			
			// Transforming from Kotlin:
			// `ratesFlow: StateFlow<List<ExchangeRate>>`
			//
			KotlinCurrentValueSubject<NSArray>(
				self.ratesFlow
			)
			.compactMap { $0 as? [ExchangeRate] }
			.eraseToAnyPublisher()
		}
	}
	
	func refreshPublisher() -> AnyPublisher<Bool, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.refreshPublisher) {
			
			// Transforming from Kotlin:
			// `refreshFlow: StateFlow<Set<FiatCurrency>>`
			//
			KotlinCurrentValueSubject<NSSet>(
				self.refreshFlow
			)
			.compactMap { $0 as? Set<FiatCurrency> }
			.map { (targets: Set<FiatCurrency>) -> Bool in
				return !targets.isEmpty
			}
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension NodeParamsManager {
	
	fileprivate struct _Key {
		static var nodeParamsPublisher = 0
	}
	
	func nodeParamsPublisher() -> AnyPublisher<Lightning_kmpNodeParams, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.nodeParamsPublisher) {
			
			// Transforming from Kotlin:
			// `nodeParams: StateFlow<NodeParams?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpNodeParams>(
				self.nodeParams
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension PhoenixShared.NotificationsManager {

	fileprivate struct _Key {
		static var notificationsPublisher = 0
	}

	struct NotificationItem: Identifiable {
		let ids: Set<Lightning_kmpUUID>
		let notification: PhoenixShared.Notification
		
		public var id: String {
			// How do we turn this into an Identifiable string ?
			//
			// Notifications with the same content will automatically be grouped into the
			// same NotificationItem, with both ids present in the Set.
			// So all we need is:
			// - a deterministic UUID from the set (lowest in sort order)
			// - plus the number of UUIDs in the set
			//
			// If these 2 items match, you know the notification content is equal,
			// and thus the corresponding UI doesn't require a refresh.
			
			let firstUUID = ids.sorted { (a, b) in
				// true if the first argument should be ordered before the second argument; otherwise, false
				return a.compareTo(other: b) < 0
			}.first?.description() ?? UUID().uuidString
			
			return "\(firstUUID)|\(ids.count)"
		}
	}

	func notificationsPublisher() -> AnyPublisher<[NotificationItem], Never> {

		self.getSetAssociatedObject(storageKey: &_Key.notificationsPublisher) {

			// Transforming from Kotlin:
			// `notifications = StateFlow<List<Pair<Set<UUID>, Notification>>>`
			// 
			KotlinCurrentValueSubject<NSArray>(
				self.notifications
			)
			.compactMap { $0 as? Array<AnyObject> }
			.map { originalArray in
				let transformedArray: [NotificationItem] = originalArray.compactMap { value in
					guard
						let pair = value as? KotlinPair<AnyObject, AnyObject>,
						let ids = pair.first as? Set<Lightning_kmpUUID>,
						let notification = pair.second as? PhoenixShared.Notification
					else {
						return nil
					}
					return NotificationItem(ids: ids, notification: notification)
				}
				return transformedArray
			}
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension PaymentsManager {
	
	fileprivate struct _Key {
		static var paymentsCountPublisher = 0
		static var lastCompletedPaymentPublisher = 0
		static var lastIncomingPaymentPublisher = 0
	}
	
	func paymentsCountPublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.paymentsCountPublisher) {
			
			// Transforming from Kotlin:
			// `paymentsCount: StateFlow<Long>`
			//
			KotlinCurrentValueSubject<KotlinLong>(
				self.paymentsCount
			)
			.compactMap { $0?.int64Value }
			.eraseToAnyPublisher()
		}
	}
	
	func lastCompletedPaymentPublisher() -> AnyPublisher<Lightning_kmpWalletPayment, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.lastCompletedPaymentPublisher) {
			
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment>(
				self.lastCompletedPayment
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
	
	func lastIncomingPaymentPublisher() -> AnyPublisher<Lightning_kmpIncomingPayment, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.lastIncomingPaymentPublisher) {
		
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment>(
				self.lastCompletedPayment
			)
			.compactMap { $0 as? Lightning_kmpIncomingPayment }
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension PaymentsPageFetcher {
	
	fileprivate struct _Key {
		static var paymentsPagePublisher = 0
	}
	
	func paymentsPagePublisher() -> AnyPublisher<PaymentsPage, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.paymentsPagePublisher) {
			
			// Transforming from Kotlin:
			// `paymentsPage: StateFlow<PaymentsPage>`
			//
			KotlinCurrentValueSubject<PaymentsPage>(
				self.paymentsPage
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}


// MARK: -
extension CloudKitDb {
	
	fileprivate struct _Key {
		static var fetchQueueCountPublisher = 0
	}
	
	func fetchQueueCountPublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.fetchQueueCountPublisher) {
			
			/// Transforming from Kotlin:
			/// `queueCount: StateFlow<Long>`
			///
			KotlinCurrentValueSubject<KotlinLong>(
				self.queueCount
			)
			.compactMap { $0?.int64Value }
			.eraseToAnyPublisher()
		}
	}
}
