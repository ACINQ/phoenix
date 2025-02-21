import SwiftUI
import PhoenixShared

fileprivate let filename = "Details_Incoming_LegacyPayToOpen"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Details_Incoming_LegacyPayToOpen: DetailsInfoGrid {
	
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
				section_incoming()
				section_parts()
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
			valueColumnText: "Incoming on-chain payment (legacy pay-to-open)"
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
			switch onEnum(of: payment.origin) {
			case .invoice(let origin):
				subsection_bolt11Invoice(origin.paymentRequest, preimage: payment.paymentPreimage)
			case .offer(let origin):
				subsection_incomingBolt12Details(origin.metadata)
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
	// MARK: Section: Parts
	// --------------------------------------------------
	
	@ViewBuilder
	func section_parts() -> some View {
		
		let sortedParts: [Lightning_kmpLegacyPayToOpenIncomingPayment.Part] =
			payment.parts.sorted { $0.hash < $1.hash }
		
		ForEach(sortedParts.indices, id: \.self) { idx in
			
			let part = sortedParts[idx]

			InlineSection {
				header("Payment Part #\(idx+1)")
			} content: {
				part_receivedVia(part)
				switch onEnum(of: part) {
				case .onChain(let onChainPart):
					part_channelId(onChainPart.channelId)
					part_txid(onChainPart.txId)
				
				case .lightning(let lightningPart):
					part_channelId(lightningPart.channelId)
				}
				part_amountReceived(part)
			}
		}
	}
	
	@ViewBuilder
	func part_receivedVia(
		_ part: Lightning_kmpLegacyPayToOpenIncomingPayment.Part
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Received via"
		) {
			switch onEnum(of: part) {
			case .onChain(_):
				Text("Channel operation (new or splice-in)")
			case .lightning(_):
				Text("Lightning payment")
			}
		}
	}
	
	@ViewBuilder
	func part_channelId(
		_ channelId: Bitcoin_kmpByteVector32
	) -> some View {
		
		if channelId != Bitcoin_kmpByteVector32.companion.Zeroes {
			
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Channel id",
				valueColumnVerbatim: channelId.toHex()
			)
		}
	}
	
	@ViewBuilder
	func part_txid(
		_ txid: Bitcoin_kmpTxId
	) -> some View {
		
		if txid.value != Bitcoin_kmpByteVector32.companion.Zeroes {
			
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Transaction"
			) {
				commonValue_btcTxid(txid, $showBlockchainExplorerOptions)
			}
		}
	}
	
	@ViewBuilder
	func part_amountReceived(
		_ part: Lightning_kmpLegacyPayToOpenIncomingPayment.Part
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Amount received"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: part.amountReceived,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var payment: Lightning_kmpLegacyPayToOpenIncomingPayment {
		
		guard let required = paymentInfo.payment as? Lightning_kmpLegacyPayToOpenIncomingPayment else {
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
	
	func serviceFees() -> Lightning_kmpMilliSatoshi? {
		
		let msat = payment.parts.compactMap {
			$0 as? Lightning_kmpLegacyPayToOpenIncomingPayment.PartOnChain
		}.map {
			$0.serviceFee.msat
		}.sum()
		
		return (msat > 0) ? Lightning_kmpMilliSatoshi(msat: msat) : nil
	}
	
	func miningFees() -> Bitcoin_kmpSatoshi? {
		
		let sat = payment.parts.compactMap {
			$0 as? Lightning_kmpLegacyPayToOpenIncomingPayment.PartOnChain
		}.map {
			$0.miningFee.sat
		}.sum()
		
		return (sat > 0) ? Bitcoin_kmpSatoshi(sat: sat) : nil
	}
}
