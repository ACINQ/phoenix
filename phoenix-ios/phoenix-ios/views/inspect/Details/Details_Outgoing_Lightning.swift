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
			//	section_outgoing()
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
