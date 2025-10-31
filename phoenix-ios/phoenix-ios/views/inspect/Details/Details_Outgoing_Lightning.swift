import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Outgoing_Lightning"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Outgoing_Lightning: DetailsInfoGrid {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
	@State var showBlockchainExplorerOptions = false
	
	@StateObject var infoGridState = InfoGridState()
	@StateObject var detailsInfoGridState = DetailsInfoGridState()
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var infoGridRows: some View {
		
		// Architecture:
		//
		// It would be nice to simply use a List here.
		// Except that a List is lazy-loaded,
		// which means we cannot properly calculate the KeyColumnWidth.
		// That is, the KeyColumnWidth changes as you scroll, which is not what we want.
		//
		// So instead we're forced to make a fake List using a VStack.
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				section_general()
				section_timestamps()
				section_outgoing()
			#if DEBUG
				if let invReq = payment.outgoingInvoiceRequest() {
					section_blip42(invReq.contactSecret, invReq.payerOffer, invReq.payerAddress)
				}
			#endif
			}
		}
		.background(Color.primaryBackground)
	}
	
	// --------------------------------------------------
	// MARK: Section: General
	// --------------------------------------------------
	
	@ViewBuilder
	func section_general() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			general_type()
			general_status()
		}
	}
	
	@ViewBuilder
	func general_type() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Type of payment"
		) {
			switch onEnum(of: payment.details) {
				case .normal(_)  : Text("Outgoing Lightning payment (bolt11)")
				case .blinded(_) : Text("Outgoing Lightning payment (bolt12)")
				case .swapOut(_) : Text("Outgoing on-chain payment (legacy swap)")
			}
		}
	}
	
	@ViewBuilder
	func general_status() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Payment status"
		) {
			switch onEnum(of: payment.status) {
			case .pending(_):
				Text("Pending")
			case .completed(let completed):
				switch onEnum(of: completed) {
				case .failed(_):
					Text("Failed")
				case .succeeded(_):
					Text("Successful")
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Outgoing
	// --------------------------------------------------
	
	@ViewBuilder
	func section_outgoing() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			subsection_outgoingAmounts(payment)
			outgoing_details()
		}
	}
	
	@ViewBuilder
	func outgoing_details() -> some View {
		
		switch onEnum(of: payment.details) {
		case .normal(let normal):
			outgoing_details_normal(normal)
			
		case .blinded(let blinded):
			outgoing_details_blinded(blinded)
			
		case .swapOut(let swapOut):
			outgoing_details_swapOut(swapOut)
		}
	}
	
	@ViewBuilder
	func outgoing_details_normal(
		_ normal: Lightning_kmpLightningOutgoingPayment.DetailsNormal
	) -> some View {
		
		let preimage = (payment.status as? Lightning_kmpLightningOutgoingPayment.StatusSucceeded)?.preimage
		subsection_bolt11Invoice(normal.paymentRequest, preimage: preimage)
		ougoing_details_normal_pubKey(normal)
	}
	
	@ViewBuilder
	func ougoing_details_normal_pubKey(
		_ normal: Lightning_kmpLightningOutgoingPayment.DetailsNormal
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Target public key",
			valueColumnText: payment.recipient.toHex()
		)
	}
	
	@ViewBuilder
	func outgoing_details_blinded(
		_ blinded: Lightning_kmpLightningOutgoingPayment.DetailsBlinded
	) -> some View {
		
		let preimage = (payment.status as? Lightning_kmpLightningOutgoingPayment.StatusSucceeded)?.preimage
		subsection_bolt12Invoice(
			blinded.paymentRequest,
			payerKey : blinded.payerKey,
			preimage : preimage
		)
	}
	
	@ViewBuilder
	func outgoing_details_swapOut(
		_ swapOut: Lightning_kmpLightningOutgoingPayment.DetailsSwapOut
	) -> some View {
		
		outgoing_details_swapOut_btcAddress(swapOut)
		outgoing_details_swapOut_paymentHash(swapOut)
		if let success = payment.status as? Lightning_kmpLightningOutgoingPayment.StatusSucceeded {
			outgoing_details_swapOut_preimage(success.preimage)
		}
	}
	
	@ViewBuilder
	func outgoing_details_swapOut_btcAddress(
		_ swapOut: Lightning_kmpLightningOutgoingPayment.DetailsSwapOut
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Bitcoin address",
			valueColumnText: swapOut.address
		)
	}
	
	@ViewBuilder
	func outgoing_details_swapOut_paymentHash(
		_ swapOut: Lightning_kmpLightningOutgoingPayment.DetailsSwapOut
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Payment Hash",
			valueColumnText: swapOut.paymentHash.toHex()
		)
	}
	
	@ViewBuilder
	func outgoing_details_swapOut_preimage(
		_ preimage: Bitcoin_kmpByteVector32
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Preimage",
			valueColumnText: preimage.toHex()
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var payment: Lightning_kmpLightningOutgoingPayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpLightningOutgoingPayment else {
			fatalError("Invalid payment type")
		}
		return required
	}
	
	func clockStateBinding() -> Binding<AnimatedClock.ClockState> {
		
		return Binding {
			showOriginalFiatValue ? .past : .present
		} set: { value in
			switch value {
				case .past    : showOriginalFiatValue = true
				case .present : showOriginalFiatValue = false
			}
			showFiatValueExplanation = true
		}
	}
}
