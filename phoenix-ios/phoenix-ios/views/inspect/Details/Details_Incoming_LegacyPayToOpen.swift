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
				section_parts()
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
			switch onEnum(of: payment.origin) {
			case .invoice(let origin):
				subsection_bolt11Invoice(origin.paymentRequest, preimage: payment.paymentPreimage)
			case .offer(let origin):
				subsection_bolt12Offer(origin.metadata)
			}
		}
	}
	
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
	func general_type() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Type of payment",
			valueColumnText: "Incoming on-chain payment (legacy swap-in)"
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
	
	@ViewBuilder
	func incoming_serviceFees() -> some View {
		
		if let msat = serviceFees() {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Service fees"
			) {
				commonValue_amounts(
					identifier: #function,
					displayAmounts: displayAmounts(
						msat: msat,
						originalFiat: paymentInfo.metadata.originalFiat
					)
				)
			}
		}
	}
	
	@ViewBuilder
	func incoming_miningFees() -> some View {
		
		if let sat = miningFees() {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Miner fees"
			) {
				commonValue_amounts(
					identifier: #function,
					displayAmounts: displayAmounts(
						sat: sat,
						originalFiat: paymentInfo.metadata.originalFiat
					)
				)
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
