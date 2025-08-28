import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Incoming_Bolt12"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Incoming_Bolt12: DetailsInfoGrid {
	
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
				section_incoming()
				section_lightningParts(payment)
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
			keyColumnTitle: "Type of payment",
			valueColumnText: "Incoming Lightning payment (bolt12)"
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
	
	// --------------------------------------------------
	// MARK: Section: Incoming
	// --------------------------------------------------
	
	@ViewBuilder
	func section_incoming() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			incoming_amountReceived()
			subsection_incomingBolt12Details(payment.metadata)
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
	
	var payment: Lightning_kmpBolt12IncomingPayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpBolt12IncomingPayment else {
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
