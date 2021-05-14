import Foundation
import Combine
import PhoenixShared

extension PaymentsManager {
	
	/// In a standard app (with 100% Swift code), the Publisher would be created and stored in
	/// some singleton manager-class somewhere. Meaning that the Publisher is created exactly once.
	///
	/// It is then referenced in various Views thru-out the app, which subscribe to the singleton publisher.
	/// However, because our singleton is actually in the Kotlin layer (exposed as StateFlow),
	/// we run the risk of creating the Publishers multiple times.
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
	///   and store it in a `@State var`
	/// - Fix the problem once by storing the publisher in a (lazy) `static var`
	/// 
	fileprivate struct _Lazy {
		
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
