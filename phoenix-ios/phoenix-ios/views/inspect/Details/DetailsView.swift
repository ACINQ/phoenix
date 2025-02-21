import SwiftUI
import PhoenixShared

fileprivate let filename = "DetailsView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DetailsView: View {
	
	let location: PaymentView.Location
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
	let switchToPayment: (_ paymentId: Lightning_kmpUUID) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			content()
				.navigationTitle(NSLocalizedString("Details", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
			
		case .embedded:
			
			content()
				.navigationTitle(NSLocalizedString("Payment Details", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			details()
		}
		.background(Color.primaryBackground)
	}
	
	@ViewBuilder
	func header() -> some View {
		
		if case .sheet(let closeAction) = location {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.backward")
						.imageScale(.medium)
						.font(.title3.weight(.semibold))
				}
				Spacer()
				Button {
					closeAction()
				} label: {
					Image(systemName: "xmark")
						.imageScale(.medium)
						.font(.title3)
				}
			} // </VStack>
			.padding()
			
		} else {
			Spacer().frame(height: 25)
		}
	}
	
	@ViewBuilder
	func details() -> some View {
		
		switch onEnum(of: paymentInfo.payment) {
		case .incomingPayment(let incoming):
			details_incoming(incoming)
			
		case .outgoingPayment(let outgoing):
			details_outgoing(outgoing)
		}
	}
	
	@ViewBuilder
	func details_incoming(_ incomingPayment: Lightning_kmpIncomingPayment) -> some View {
		
		switch onEnum(of: incomingPayment) {
		case .lightningIncomingPayment(let lightning):
			switch onEnum(of: lightning) {
			case .bolt11IncomingPayment(_):
				Details_Incoming_Bolt11(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .bolt12IncomingPayment(_):
				Details_Incoming_Bolt12(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
			}
			
		case .onChainIncomingPayment(let onChain):
			switch onEnum(of: onChain) {
			case .newChannelIncomingPayment(_):
				Details_Incoming_NewChannel(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .spliceInIncomingPayment(_):
				Details_Incoming_SpliceIn(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
			}
			
		case .legacyPayToOpenIncomingPayment(_):
			Details_Incoming_LegacyPayToOpen(
				paymentInfo: $paymentInfo,
				showOriginalFiatValue: $showOriginalFiatValue,
				showFiatValueExplanation: $showFiatValueExplanation
			)
			
		case .legacySwapInIncomingPayment(_):
			Details_Incoming_LegacySwapIn(
				paymentInfo: $paymentInfo,
				showOriginalFiatValue: $showOriginalFiatValue,
				showFiatValueExplanation: $showFiatValueExplanation
			)
		}
	}
	
	@ViewBuilder
	func details_outgoing(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		switch onEnum(of: outgoingPayment) {
		case .lightningOutgoingPayment(_):
			Details_Outgoing_Lightning(
				paymentInfo: $paymentInfo,
				showOriginalFiatValue: $showOriginalFiatValue,
				showFiatValueExplanation: $showFiatValueExplanation
			)
			
		case .onChainOutgoingPayment(let onChain):
			switch onEnum(of: onChain) {
			case .spliceOutgoingPayment(_):
				Details_Outgoing_Splice(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .spliceCpfpOutgoingPayment(_):
				Details_Outgoing_SpliceCpfp(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .automaticLiquidityPurchasePayment(_):
				Details_Outgoing_LiquidityAuto(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .manualLiquidityPurchasePayment(_):
				Details_Outgoing_LiquidityManual(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
				
			case .channelCloseOutgoingPayment(_):
				Details_Outgoing_ChannelClose(
					paymentInfo: $paymentInfo,
					showOriginalFiatValue: $showOriginalFiatValue,
					showFiatValueExplanation: $showFiatValueExplanation
				)
			}
		}
	}
}
