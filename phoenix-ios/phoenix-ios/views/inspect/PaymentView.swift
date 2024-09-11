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
	
	@EnvironmentObject var shortSheetState: ShortSheetState
	@State var shortSheetItem: ShortSheetItem? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	@State var popoverItem: PopoverItem? = nil
	
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			NavigationStack(path: $navCoordinator.path) {
				SummaryView(location: location, paymentInfo: paymentInfo)
			}
			.environmentObject(navCoordinator)
			.edgesIgnoringSafeArea(.all)
			.zIndex(0) // needed for proper animation
			.accessibilityHidden(shortSheetItem != nil || popoverItem != nil)
			
			if let shortSheetItem = shortSheetItem {
				ShortSheetWrapper(dismissable: shortSheetState.dismissable) {
					shortSheetItem.view
				}
				.zIndex(1) // needed for proper animation
			}
			
			if let popoverItem = popoverItem {
				PopoverWrapper(dismissable: popoverState.dismissable) {
					popoverItem.view
				}
				.zIndex(2) // needed for proper animation
			}
			
		} // </ZStack>
		.onReceive(shortSheetState.itemPublisher) { (item: ShortSheetItem?) in
			withAnimation {
				shortSheetItem = item
			}
		}
		.onReceive(popoverState.itemPublisher) { (item: PopoverItem?) in
			withAnimation {
				popoverItem = item
			}
		}
	}
}
