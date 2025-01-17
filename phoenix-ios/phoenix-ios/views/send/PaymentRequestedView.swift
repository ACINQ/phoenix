import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentRequestedView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// Used after a lnurl-withdraw is completed.
///
struct PaymentRequestedView: View {
	
	let flow: SendManager.ParseResult_Lnurl_Withdraw
	let invoice: Lightning_kmpBolt11Invoice
	
	let popTo: (PopToDestination) -> Void // For iOS 16
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	let lastIncomingPaymentPublisher = Biz.business.paymentsManager.lastIncomingPaymentPublisher()
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Payment Requested", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarBackButtonHidden()
			.onReceive(lastIncomingPaymentPublisher) {
				lastIncomingPaymentChanged($0)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
			content()
		}
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			let host = paymentRequestHost()
			Text("Payment requested from \(host)")
				.multilineTextAlignment(.center)
				.font(.title)
			
			let amount = paymentAmount().string
			Text("You should soon receive a payment for \(amount)")
				.multilineTextAlignment(.center)
				.padding(.vertical, 40)
			
			doneButton()
		}
		.padding()
	}
	
	@ViewBuilder
	func doneButton() -> some View {
		
		Button {
			doneButtonTapped()
		} label: {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "checkmark.circle")
					.renderingMode(.template)
					.imageScale(.medium)
				Text("Done")
			}
			.font(.title3)
			.foregroundColor(Color.white)
			.padding(.top, 4)
			.padding(.bottom, 5)
			.padding([.leading, .trailing], 24)
		}
		.buttonStyle(ScaleButtonStyle(
			cornerRadius: 100,
			backgroundFill: Color.appAccent,
			disabledBackgroundFill: Color(UIColor.systemGray)
		))
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func paymentRequestHost() -> String {
		return flow.lnurlWithdraw.initialUrl.host
	}
	
	func paymentAmount() -> FormattedAmount {
		let msat: Int64 = invoice.amount?.msat ?? 0
		return Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("lastIncomingPaymentChanged()")
		log.debug("lastIncomingPayment.paymentHash = \(lastIncomingPayment.id.description())")
		
		let paymentState = lastIncomingPayment.state()
		if paymentState == WalletPaymentState.successOffChain {
			
			if let lightningPayment = lastIncomingPayment as? Lightning_kmpLightningIncomingPayment {
				if lightningPayment.paymentHash.toHex() == invoice.paymentHash.toHex() {
					popToRootView()
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func doneButtonTapped() {
		log.trace("doneButtonTapped()")
		
		popToRootView()
	}
	
	func popToRootView() {
		log.trace("popToRootView()")
		
		if #available(iOS 17, *) {
			navCoordinator.path.removeAll()
		} else { // iOS 16
			popTo(.RootView(followedBy: nil))
			presentationMode.wrappedValue.dismiss()
		}
	}
}

