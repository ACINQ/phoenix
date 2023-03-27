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

struct PaymentView : View {

	let type: PaymentViewType
	let paymentInfo: WalletPaymentInfo
	
	@ViewBuilder
	var body: some View {
		
		switch type {
		case .sheet:
			NavigationWrapper {
				SummaryView(type: type, paymentInfo: paymentInfo)
			}
			.edgesIgnoringSafeArea(.all)
			
		case .embedded:
			SummaryView(type: type, paymentInfo: paymentInfo)
		}
	}
}
