import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Outgoing_Splice"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Outgoing_Splice: DetailsInfoGrid {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
	@State var showBlockchainExplorerOptions = false
	
	@StateObject var infoGridState = InfoGridState()
	@StateObject var detailsInfoGridState = DetailsInfoGridState()
	
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
				section_outgoing()
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
			valueColumnText: "Outgoing on-chain payment"
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
	// MARK: Section: Outgoing
	// --------------------------------------------------
	
	@ViewBuilder
	func section_outgoing() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			subsection_outgoingAmounts(payment)
			outgoing_channelId()
			outgoing_btcAddress()
			outgoing_btcTxid()
		}
	}
	
	@ViewBuilder
	func outgoing_channelId() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Channel id",
			valueColumnVerbatim: payment.channelId.toHex()
		)
	}
	
	@ViewBuilder
	func outgoing_btcAddress() -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Bitcoin address",
			valueColumnText: payment.address
		)
	}
	
	@ViewBuilder
	func outgoing_btcTxid() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Transaction"
		) {
			commonValue_btcTxid(payment.txId, $showBlockchainExplorerOptions)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var payment: Lightning_kmpSpliceOutgoingPayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpSpliceOutgoingPayment else {
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
