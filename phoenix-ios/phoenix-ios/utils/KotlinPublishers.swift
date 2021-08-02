import Foundation
import Combine
import PhoenixShared


/// Architecture Design:
///
/// In a standard app (with 100% Swift code), a Publisher instance would be created and stored in
/// some singleton manager-class somewhere. Meaning that the Publisher is created exactly once.
///
/// It is then referenced in various Views thru-out the app, which subscribe to the singleton publisher.
/// However, in our app, we're mixing Kotlin & Swift. And our singleton is often in the Kotlin layer
/// (exposed as StateFlow). And our Swift-layer Publishers are wrappers around the StateFlow in Kotlin.
/// Because of this, we run the risk of creating the Publisher instances multiple times.
///
/// This can be troublesome for SwiftUI, as the Views get created multiple times.
/// And if the View creates the Publisher each time,
/// then the Publisher may fire the current value on each tree render.
///
/// For example:
/// ```
/// struct MyView: View {
///   let publisher: AnyPublisher<String, Never> = OnTheFlyCreatedPublisher()
///   var body: some View {
///     VStack {}
///       .onReceive(publisher) {
///         // !!! This could fire on every tree-render !!!
///       }
///   }
/// }
/// ```
///
/// There are 2 solutions to this problem that I can think of:
/// - Remember this edge case everytime you use the on-the-fly-created publisher,
///   and store the publisher in a `@State var`
/// - Fix the problem once by storing the publisher in a (lazy) `static var`
///
/// In this file, we choose the later solution.


extension PhoenixBusiness {
	
	fileprivate struct _Lazy {
		
		/// Transforming from Kotlin:
		/// ```
		/// peerState: StateFlow<Peer?>
		/// ```
		static var peerPublisher: AnyPublisher<Lightning_kmpPeer, Never> =
			KotlinCurrentValueSubject<Lightning_kmpPeer, Lightning_kmpPeer>(
				AppDelegate.get().business.peerState()
			).eraseToAnyPublisher()
	}
	
	func peerPublisher() -> AnyPublisher<Lightning_kmpPeer, Never> {
		return _Lazy.peerPublisher
	}
	
	func getPeer() -> Lightning_kmpPeer? {
		
		let wrapper = KotlinCurrentValueSubject<Lightning_kmpPeer, Lightning_kmpPeer?>(
			AppDelegate.get().business.peerState()
		)
		
		return wrapper.value
	}
}

extension PaymentsManager {
	
	fileprivate struct _Lazy {
		
		/// Transforming from Kotlin:
		/// ```
		/// paymentsPage: StateFlow<PaymentsPage>
		/// ```
		static var paymentsPagePublisher: AnyPublisher<PaymentsPage, Never> =
			KotlinCurrentValueSubject<PaymentsPage, PaymentsPage>(
				AppDelegate.get().business.paymentsManager.paymentsPage
			).eraseToAnyPublisher()
		
		/// Transforming from Kotlin:
		/// ```
		/// incomingSwaps: StateFlow<Map<String, MilliSatoshi>>
		/// ```
		static var incomingSwapsPublisher: AnyPublisher<[String: Lightning_kmpMilliSatoshi], Never> =
			KotlinCurrentValueSubject<NSDictionary, [String: Lightning_kmpMilliSatoshi]>(
				AppDelegate.get().business.paymentsManager.incomingSwaps
			)
			.eraseToAnyPublisher()
		
		/// Transforming from Kotlin:
		/// ```
		/// lastCompletedPayment: StateFlow<WalletPayment?>
		/// ```
		static var lastCompletedPaymentPublisher: AnyPublisher<Lightning_kmpWalletPayment, Never> =
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
				AppDelegate.get().business.paymentsManager.lastCompletedPayment
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
		
		/// Transforming from Kotlin:
		/// ```
		/// lastCompletedPayment: StateFlow<WalletPayment?>
		/// ```
		static var lastIncomingPaymentPublisher: AnyPublisher<Lightning_kmpIncomingPayment, Never> =
			KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
				AppDelegate.get().business.paymentsManager.lastCompletedPayment
			)
			.compactMap {
				return $0 as? Lightning_kmpIncomingPayment
			}
			.eraseToAnyPublisher()
		
		/// Transforming from Kotlin:
		/// ```
		/// inFlightOutgoingPayments: StateFlow<Set<UUID>>
		/// ```
		static var inFlightOutgoingPaymentsPublisher: AnyPublisher<Int, Never> =
			KotlinCurrentValueSubject<NSSet, Set<Lightning_kmpUUID>>(
				AppDelegate.get().business.paymentsManager.inFlightOutgoingPayments
			)
			.map {
				return $0.count
			}
			.eraseToAnyPublisher()
	}
	
	func paymentsPagePublisher() -> AnyPublisher<PaymentsPage, Never> {
		return _Lazy.paymentsPagePublisher
	}
	
	func incomingSwapsPublisher() -> AnyPublisher<[String: Lightning_kmpMilliSatoshi], Never> {
		return _Lazy.incomingSwapsPublisher
	}
	
	func lastCompletedPaymentPublisher() -> AnyPublisher<Lightning_kmpWalletPayment, Never> {
		return _Lazy.lastCompletedPaymentPublisher
	}
	
	func lastIncomingPaymentPublisher() -> AnyPublisher<Lightning_kmpIncomingPayment, Never> {
		return _Lazy.lastIncomingPaymentPublisher
	}
	
	func inFlightOutgoingPaymentsPublisher() -> AnyPublisher<Int, Never> {
		
		return _Lazy.inFlightOutgoingPaymentsPublisher
	}
}

extension AppConfigurationManager {
	
	fileprivate struct _Lazy {
		
		/// Transforming from Kotlin:
		/// ```
		/// chainContext: StateFlow<WalletContext.V0.ChainContext?>
		/// ```
		static var chainContextPublisher: AnyPublisher<WalletContext.V0ChainContext, Never> =
			KotlinCurrentValueSubject<WalletContext.V0ChainContext, WalletContext.V0ChainContext?>(
				AppDelegate.get().business.appConfigurationManager.chainContext
			)
			.compactMap { $0 }
			.eraseToAnyPublisher()
	}
	
	func chainContextPublisher() -> AnyPublisher<WalletContext.V0ChainContext, Never> {
		return _Lazy.chainContextPublisher
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
