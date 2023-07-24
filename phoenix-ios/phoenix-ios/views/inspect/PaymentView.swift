import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum PaymentViewType {
	
	/// We need an explicit close operation here because:
	/// - we're going to use a NavigationView
	/// - we need to programmatically close the sheet from any layer in the navigation stack
	/// - the general API to pop a view from the nav stack is `presentationMode.wrappedValue.dismiss()`
	/// - the general API to dismiss a sheet is `presentationMode.wrappedValue.dismiss()`
	/// - thus we cannot use the general API
	case sheet(closeAction: () -> Void)
	
	case embedded
}

struct PaymentView: View {

	let type: PaymentViewType
	let paymentInfo: WalletPaymentInfo
	
	@ViewBuilder
	var body: some View {
		
		switch type {
		case .sheet:
			PaymentViewSheet(type: type, paymentInfo: paymentInfo)
			
		case .embedded:
			SummaryView(type: type, paymentInfo: paymentInfo)
		}
	}
}

fileprivate struct PaymentViewSheet: View {
	
	let type: PaymentViewType
	let paymentInfo: WalletPaymentInfo
	
	@EnvironmentObject var shortSheetState: ShortSheetState
	@State var shortSheetItem: ShortSheetItem? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	@State var popoverItem: PopoverItem? = nil
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			NavigationWrapper {
				SummaryView(type: type, paymentInfo: paymentInfo)
			}
			.edgesIgnoringSafeArea(.all)
			.zIndex(0) // needed for proper animation
			.accessibilityHidden(shortSheetItem != nil || popoverItem != nil)
			
			if let shortSheetItem = shortSheetItem {
				ShortSheetWrapper(dismissable: shortSheetItem.dismissable) {
					shortSheetItem.view
				}
				.zIndex(1) // needed for proper animation
			}
			
			if let popoverItem = popoverItem {
				PopoverWrapper(dismissable: popoverItem.dismissable) {
					popoverItem.view
				}
				.zIndex(2) // needed for proper animation
			}
			
		} // </ZStack>
		.onReceive(shortSheetState.publisher) { (item: ShortSheetItem?) in
			withAnimation {
				shortSheetItem = item
			}
		}
		.onReceive(popoverState.publisher) { (item: PopoverItem?) in
			withAnimation {
				popoverItem = item
			}
		}
	}
}
