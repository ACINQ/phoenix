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
	
	func channelsArraySequence() -> AnyAsyncSequence<[LocalChannelInfo]> {
		
		return self.channelsFlow
			.compactMap { $0 }
			.map { Array($0.values) }
			.eraseToAnyAsyncSequence()
	}
	
	func finalWalletSequence() -> AnyAsyncSequence<Lightning_kmpWalletState.WalletWithConfirmations> {
		
		return self.finalWallet
			.map { $0 ?? Lightning_kmpWalletState.WalletWithConfirmations.empty() }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension AppConfigurationManager {
	
	func walletContextSequence() -> AnyAsyncSequence<WalletContext> {
		
		return self.walletContext
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension BalanceManager {

	func balanceSequence() -> AnyAsyncSequence<Lightning_kmpMilliSatoshi?> {
		
		return self.balance
			.eraseToAnyAsyncSequence()
	}
	
	func swapInWalletSequence() -> AnyAsyncSequence<Lightning_kmpWalletState.WalletWithConfirmations> {
		
		return self.swapInWallet
			.map { $0 ?? Lightning_kmpWalletState.WalletWithConfirmations.empty() }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension ConnectionsManager {
	
	func connectionsSequence() -> AnyAsyncSequence<Connections> {
		
		return self.connections
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension CurrencyManager {
	
	func ratesSequence() -> AnyAsyncSequence<[ExchangeRate]> {
		
		return self.ratesFlow
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
	
	func refreshSequence() -> AnyAsyncSequence<Bool> {
		
		return self.refreshFlow
			.compactMap { $0 }
			.map { (targets: Set<FiatCurrency>) -> Bool in
				return !targets.isEmpty
			}
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension NodeParamsManager {
	
	func getNodeParams() async -> Lightning_kmpNodeParams {
		
		return await self.nodeParams.first { $0 != nil }!!
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

	func notificationsSequence() -> AnyAsyncSequence<[NotificationItem]> {

		return self.notifications
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
			.eraseToAnyAsyncSequence()
	}
	
	func notificationsPublisher() -> AnyPublisher<[NotificationItem], Never> {

		self.getSetAssociatedObject(storageKey: &_Key.notificationsPublisher) {

			// Transforming from Kotlin:
			// `notifications = StateFlow<List<Pair<Set<UUID>, Notification>>>`
			// 
			KotlinCurrentValueSubject<AnyObject>(
				self.notifications._bridgeToObjectiveC()
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
			KotlinCurrentValueSubject<AnyObject>(
				self.paymentsCount._bridgeToObjectiveC()
			)
			.compactMap { $0 as? KotlinLong }
			.map { $0.int64Value }
			.eraseToAnyPublisher()
		}
	}
	
	func lastCompletedPaymentPublisher() -> AnyPublisher<Lightning_kmpWalletPayment, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.lastCompletedPaymentPublisher) {
			
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<AnyObject>(
				self.lastCompletedPayment._bridgeToObjectiveC()
			)
			.compactMap { $0 as? Lightning_kmpWalletPayment }
			.eraseToAnyPublisher()
		}
	}
	
	func lastIncomingPaymentPublisher() -> AnyPublisher<Lightning_kmpIncomingPayment, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.lastIncomingPaymentPublisher) {
		
			// Transforming from Kotlin:
			// `lastCompletedPayment: StateFlow<WalletPayment?>`
			//
			KotlinCurrentValueSubject<AnyObject>(
				self.lastCompletedPayment._bridgeToObjectiveC()
			)
			.compactMap { $0 as? Lightning_kmpWalletPayment }
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
			KotlinCurrentValueSubject<AnyObject>(
				self.paymentsPage._bridgeToObjectiveC()
			)
			.compactMap { $0 as? PaymentsPage }
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
			KotlinCurrentValueSubject<AnyObject>(
				self.queueCount._bridgeToObjectiveC()
			)
			.compactMap { $0 as? KotlinLong }
			.map { $0.int64Value }
			.eraseToAnyPublisher()
		}
	}
}
