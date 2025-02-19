import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Incoming_Bolt11"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Incoming_Bolt11: DetailsInfoGrid {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
	let switchToPayment: (_ paymentId: Lightning_kmpUUID) -> Void
	
	@State var showBlockchainExplorerOptions = false
	
	// <InfoGridView Protocol>
	@StateObject var infoGridViewState = InfoGridViewState()
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 140
	// </InfoGridView Protocol>
	
	// <DetailsInfoGrid Protocol>
	@StateObject var detailsInfoGridState = DetailsInfoGridState()
	// </DetailsInfoGrid Protocol>
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
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
				section_incoming()
				section_lightningParts(payment)
			}
		}
		.background(Color.primaryBackground)
	}
	
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
	func section_incoming() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			incoming_amountReceived()
			subsection_bolt11Invoice(payment.paymentRequest, preimage: payment.paymentPreimage)
		}
	}
	
	@ViewBuilder
	func general_type() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Type of payment",
			valueColumnText: "Incoming Lightning payment (bolt11)"
		)
	}
	
	@ViewBuilder
	func general_status() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Payment status"
		) {
			if payment.completedAt == nil {
				Text("Pending")
			} else {
				Text("Successful")
			}
		}
	}
	
	@ViewBuilder
	func incoming_amountReceived() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "amount received"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: payment.amountReceived,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var payment: Lightning_kmpBolt11IncomingPayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpBolt11IncomingPayment else {
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
