import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "KotlinPublishers+Lightning"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


extension Lightning_kmpElectrumClient {
	
	fileprivate struct _Key {
		static var notificationsPublisher = 0
	}
	
	func notificationsPublisher() -> AnyPublisher<Lightning_kmpElectrumSubscriptionResponse, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.notificationsPublisher) {
			
			/// Transforming from Kotlin:
			/// `notifications: Flow<ElectrumSubscriptionResponse>`
			///
			KotlinPassthroughSubject<
				/*obj-c:*/ Lightning_kmpElectrumSubscriptionResponse,
				/*swift:*/ Lightning_kmpElectrumSubscriptionResponse
			>(
				self.notifications
			)
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
			KotlinPassthroughSubject<KotlinLong, Int64>(
				self.openUpToDateFlow()
			)
			.eraseToAnyPublisher()
		}
	}
}

// MARK: -
extension Lightning_kmpNodeParams {
	
	fileprivate struct _Key {
		static var nodeEventsPublisher = 0
	}
	
	func nodeEventsPublisher() -> AnyPublisher<Lightning_kmpNodeEvents, Never> {
		
		self.getSetAssociatedObject(storageKey: &_Key.nodeEventsPublisher) {
			
			/// Transforming from Kotlin:
			/// `nodeEvents: SharedFlow<NodeEvents>`
			///
			KotlinPassthroughSubject<Lightning_kmpNodeEvents, Lightning_kmpNodeEvents?>(
				self.nodeEvents
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		}
	}
}
