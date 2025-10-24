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
	
	func peerStateSequence() -> AnyAsyncSequence<Lightning_kmpPeer> {
		
		return self.peerState
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
	
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
extension WalletContextManager {
	
	func walletContextSequence() -> AnyAsyncSequence<WalletContext> {
		
		return self.walletContext // StateFlow<WalletContext?>
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
	
	func walletNoticeSequence() -> AnyAsyncSequence<WalletNotice> {
		
		return self.walletNotice // StateFlow<WalletNotice?>
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension AppConfigurationManager {
	
	fileprivate struct _Key {
		static var isTorEnabledPublisher = 0
		static var electrumConfigPublisher = 0
	}
	
	func isTorEnabledSequence() -> AnyAsyncSequence<Bool> {
		
		return self.isTorEnabled // StateFlow<Bool?>
			.compactMap { $0 }
			.map { $0.boolValue }
			.eraseToAnyAsyncSequence()
	}
	
	func electrumConfigSequence() -> AnyAsyncSequence<ElectrumConfig> {
		
		return self.electrumConfig // StateFlow<ElectrumConfig?>
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
extension SqliteCardsDb {
	
	func cardsListSequence() -> AnyAsyncSequence<[BoltCardInfo]> {
		
		return self.cardsList
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
}

// MARK: -
extension PaymentsManager {
	
	func lastCompletedPaymentSequence() -> AnyAsyncSequence<Lightning_kmpWalletPayment> {
			
		return self.lastCompletedPayment
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
	
	func lastIncomingPaymentSequence() -> AnyAsyncSequence<Lightning_kmpIncomingPayment> {
			
		return self.lastCompletedPayment
			.compactMap { $0 }
			.compactMap { $0 as? Lightning_kmpIncomingPayment }
			.eraseToAnyAsyncSequence()
	}
}

// MARK: -
extension PaymentsPageFetcher {
	
	func paymentsPageSequence() -> AnyAsyncSequence<PaymentsPage> {
			
		return self.paymentsPage
			.compactMap { $0 }
			.eraseToAnyAsyncSequence()
	}
}


// MARK: -
extension CloudKitCardsDb {
	
	func queueCountSequence() -> AnyAsyncSequence<Int64> {
		
		return self.queueCount
			.map { $0.int64Value }
			.eraseToAnyAsyncSequence()
	}
}

extension CloudKitContactsDb {
	
	func queueCountSequence() -> AnyAsyncSequence<Int64> {
		
		return self.queueCount
			.map { $0.int64Value }
			.eraseToAnyAsyncSequence()
	}
}

extension CloudKitPaymentsDb {
	
	func queueCountSequence() -> AnyAsyncSequence<Int64> {
		
		return self.queueCount
			.map { $0.int64Value }
			.eraseToAnyAsyncSequence()
	}
}
