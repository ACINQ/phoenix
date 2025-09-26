import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentView: View {
	
	enum Location {
	 
		/// We need an explicit close operation here because:
		/// - we're going to use a NavigationView
		/// - we need to programmatically close the sheet from any layer in the navigation stack
		/// - the general API to pop a view from the nav stack is `presentationMode.wrappedValue.dismiss()`
		/// - the general API to dismiss a sheet is `presentationMode.wrappedValue.dismiss()`
		/// - thus we cannot use the general API
		case sheet(closeSheet: () -> Void)
		
		case embedded(popTo: (PopToDestination) -> Void)
				
		var isSheet: Bool {
			switch self {
				case .sheet(_)    : return true
				case .embedded(_) : return false
			}
		}
	}
	
	let location: Location
	let paymentInfo: WalletPaymentInfo
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			PaymentViewSheet(location: location, paymentInfo: paymentInfo)
			
		case .embedded:
			SummaryView(location: location, paymentInfo: paymentInfo)
		}
	}
}

fileprivate struct PaymentViewSheet: View {
	
	let location: PaymentView.Location
	let paymentInfo: WalletPaymentInfo
	
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@ViewBuilder
	var body: some View {
		
		NavigationStack(path: $navCoordinator.path) {
			SummaryView(location: location, paymentInfo: paymentInfo)
		}
		.environmentObject(navCoordinator)
		.edgesIgnoringSafeArea(.all)
	}
}
