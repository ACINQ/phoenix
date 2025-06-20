import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Outgoing_LiquidityAuto"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Outgoing_LiquidityAuto: DetailsInfoGrid {
	
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
				if payment.incomingPaymentReceivedAt != nil {
					section_outgoingLiquidity(payment, $showBlockchainExplorerOptions)
				}
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
			valueColumnText: "Inbound liquidity request (auto)"
		)
	}
	
	@ViewBuilder
	func general_status() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Payment status"
		) {
			if payment.confirmedAt == nil {
				Text("Pending")
			} else {
				Text("Successful")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var payment: Lightning_kmpAutomaticLiquidityPurchasePayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpAutomaticLiquidityPurchasePayment else {
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
