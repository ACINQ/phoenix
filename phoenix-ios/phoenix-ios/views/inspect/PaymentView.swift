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


struct PaymentView : View {

	let paymentInfo: WalletPaymentInfo
	
	// We need an explicit close operation here because:
	// - we're going to use a NavigationView
	// - we need to programmatically close the sheet from any layer in the navigation stack
	// - the general API to pop a view from the nav stack is `presentationMode.wrappedValue.dismiss()`
	// - the general API to dismiss a sheet is `presentationMode.wrappedValue.dismiss()`
	// - thus we cannot use the general API
	//
	let closeSheet: () -> Void
	
	var body: some View {
		
		NavigationView {
			
			SummaryView(paymentInfo: paymentInfo, closeSheet: closeSheet)
				.navigationBarTitle("", displayMode: .inline)
				.navigationBarHidden(true)
		}
		.edgesIgnoringSafeArea(.all)
	}
}
