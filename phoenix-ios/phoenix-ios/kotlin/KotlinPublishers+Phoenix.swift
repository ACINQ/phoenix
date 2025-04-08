import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "KotlinPublishers+Phoenix"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
		static var walletNoticePublisher = 0
		static var isTorEnabledPublisher = 0
		static var electrumConfigPublisher = 0
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
	
	func walletNoticePublisher() -> AnyPublisher<WalletNotice, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.walletNoticePublisher) {
			
			// Transforming from Kotlin:
			// `walletNotice: StateFlow<WalletNotice?>`
			//
			KotlinCurrentValueSubject<WalletNotice>(
				self.walletNotice
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
	
	func isTorEnabledPublisher() -> AnyPublisher<Bool, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.isTorEnabledPublisher) {
			
			// Transforming from Kotlin:
			// `isTorEnabled = StateFlow<Boolean?>`
			//
			KotlinCurrentValueSubject<NSNumber>(
				self.isTorEnabled
			)
			.compactMap { $0?.boolValue }
			.eraseToAnyPublisher()
		}
	}
	
	func electrumConfigPublisher() -> AnyPublisher<ElectrumConfig, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.electrumConfigPublisher) {
			
			// Transforming from Kotlin:
			// `electrumConfig: StateFlow<ElectrumConfig?>`
			//
			KotlinCurrentValueSubject<ElectrumConfig>(
				self.electrumConfig
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

extension DatabaseManager {
	
	fileprivate struct _Key {
		static var databasesPublisher = 0
		static var contactsListPublisher = 0
	}
	
	func databasesPublisher() -> AnyPublisher<PhoenixDatabases, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.databasesPublisher) {
			
			// Transforming from Kotlin:
			// `databases: StateFlow<PhoenixDatabases?>`
			//
			KotlinCurrentValueSubject<PhoenixDatabases>(
				self.databases
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
	
	func contactsListPublisher() -> AnyPublisher<[ContactInfo], Never> {
		
		return databasesPublisher()
			.flatMap { $0.payments.contacts.contactsListPublisher() }
			.eraseToAnyPublisher()
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
extension SqliteContactsDb {
	
	fileprivate struct _Key {
		static var contactsListPublisher = 0
	}
	
	func contactsListPublisher() -> AnyPublisher<[ContactInfo], Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.contactsListPublisher) {
			
			// Transforming from Kotlin:
			// `contactsList: StateFlow<List<ContactInfo>>`
			//
			KotlinCurrentValueSubject<NSArray>(
				self.contactsList
			)
			.compactMap { $0 as? [ContactInfo] }
			.eraseToAnyPublisher()
		}
	}
}


// MARK: -
extension CloudKitContactsDb {
	
	fileprivate struct _Key {
		static var queueCountPublisher = 0
	}
	
	func queueCountPublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.queueCountPublisher) {
			
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

extension CloudKitPaymentsDb {
	
	fileprivate struct _Key {
		static var queueCountPublisher = 0
	}
	
	func queueCountPublisher() -> AnyPublisher<Int64, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.queueCountPublisher) {
			
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
