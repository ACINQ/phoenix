import SwiftUI
import PhoenixShared

fileprivate let filename = "DetailsInfoGrid"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension DetailsInfoGrid {
	
	// --------------------------------------------------
	// MARK: Section: Timestamps
	// --------------------------------------------------
	
	@ViewBuilder
	func section_timestamps() -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			timestamps_createdAt()
			timestamps_completedAt()
			timestamps_elapsed()
		}
	}
	
	@ViewBuilder
	func timestamps_createdAt() -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Created at"
		) {
			commonValue_date(date: paymentInfo.payment.createdAtDate)
		}
	}
	
	@ViewBuilder
	func timestamps_completedAt() -> some View {
		
		if let completedAtDate = paymentInfo.payment.completedAtDate {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Completed at"
			) {
				commonValue_date(date: completedAtDate)
			}
		}
	}
	
	@ViewBuilder
	func timestamps_elapsed() -> some View {
		
		if let milliseconds = paymentInfo.payment.paymentTimeElapsed() {
			
			detailsRow(
				identifier: #function,
				keyColumnTitle: "elapsed"
			) {
				if milliseconds < 1_000 {
					Text("\(milliseconds) milliseconds")
					
				} else if milliseconds < (90 * 1_000) {
					let seconds = displayElapsedSeconds(milliseconds: milliseconds)
					Text("\(seconds) seconds")
					
				} else {
					let (minutes, seconds) = displayElapsedMinutes(milliseconds: milliseconds)
					Text("\(minutes):\(seconds) minutes")
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Lightning Parts
	// --------------------------------------------------
	
	@ViewBuilder
	func section_lightningParts(_ payment: Lightning_kmpLightningIncomingPayment) -> some View {
		
		let sortedParts: [Lightning_kmpLightningIncomingPayment.Part] =
			payment.parts.sorted { $0.hash < $1.hash }
		
		ForEach(sortedParts.indices, id: \.self) { idx in
			
			let part = sortedParts[idx]

			InlineSection {
				header("Payment Part #\(idx+1)")
			} content: {
				part_receivedVia(part)
				switch onEnum(of: part) {
				case .htlc(let htlc):
					part_htlc_amount(htlc)
					part_htlc_fees(htlc)
					
				case .feeCredit(let feeCredit):
					part_feeCredit_amount(feeCredit)
				}
				part_completedAt(part)
			}
		}
	}
	
	@ViewBuilder
	func part_receivedVia(
		_ part: Lightning_kmpLightningIncomingPayment.Part
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Received via"
		) {
			switch onEnum(of: part) {
			case .htlc(_):
				Text("Lightning payment")
				
			case .feeCredit(_):
				Text("Fee credit")
			}
		}
	}
	
	@ViewBuilder
	func part_htlc_amount(
		_ htlc: Lightning_kmpLightningIncomingPayment.PartHtlc
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Amount received"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: htlc.amountReceived,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func part_htlc_fees(
		_ htlc: Lightning_kmpLightningIncomingPayment.PartHtlc
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: htlc.fees,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func part_feeCredit_amount(
		_ feeCredit: Lightning_kmpLightningIncomingPayment.PartFeeCredit
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Amount added to fee credit"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: feeCredit.amountReceived,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func part_completedAt(
		_ part: Lightning_kmpLightningIncomingPayment.Part
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Completed at"
		) {
			commonValue_date(date: part.receivedAtDate)
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: OnChain Incoming
	// --------------------------------------------------
	
	@ViewBuilder
	func section_onChainIncoming(
		_ payment: Lightning_kmpOnChainIncomingPayment,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			onChainIncoming_amountReceived(payment)
			onChainIncoming_serviceFees(payment)
			onChainIncoming_minerFees(payment)
			onChainIncoming_channelId(payment)
			onChainIncoming_txId(payment, showBlockchainExplorerOptions)
			onChainIncoming_inputs(payment, showBlockchainExplorerOptions)
		}
	}
	
	@ViewBuilder
	func onChainIncoming_amountReceived(
		_ payment: Lightning_kmpOnChainIncomingPayment
	) -> some View {
		
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
	func onChainIncoming_serviceFees(
		_ payment: Lightning_kmpOnChainIncomingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Service fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: payment.serviceFee,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func onChainIncoming_minerFees(
		_ payment: Lightning_kmpOnChainIncomingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Miner fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					sat: payment.miningFee,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func onChainIncoming_channelId(
		_ payment: Lightning_kmpOnChainIncomingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Channel id",
			valueColumnVerbatim: payment.channelId.toHex()
		)
	}
	
	@ViewBuilder
	func onChainIncoming_txId(
		_ payment: Lightning_kmpOnChainIncomingPayment,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Transaction"
		) {
			commonValue_btcTxid(payment.txId, showBlockchainExplorerOptions)
		}
	}
	
	@ViewBuilder
	func onChainIncoming_inputs(
		_ payment: Lightning_kmpOnChainIncomingPayment,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		let sortedInputs = payment.localInputs.sorted { a, b in
			a.index < b.index
		}
		
		ForEach(sortedInputs.indices, id: \.self) { idx in
			
			let input = sortedInputs[idx]
			let identifier = "\(#function)-\(idx)"
			
			DetailsRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn(verbatim: "Input \(idx)")
				
			} valueColumn: {
				commonValue_btcTxid(input.txid, showBlockchainExplorerOptions)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Outgoing Liquidity
	// --------------------------------------------------
	
	@ViewBuilder
	func section_outgoingLiquidity(
		_ payment: Lightning_kmpOnChainOutgoingPayment,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		InlineSection {
			EmptyView()
		} content: {
			outgoingLiquidity_liquidityAmount(payment)
			outgoingLiquidity_serviceFees(payment)
			outgoingLiquidity_minerFees(payment)
			outgoingLiquidity_purchaseType(payment)
			outgoingLiquidity_channelId(payment)
			outgoingLiquidity_btcTxid(payment, showBlockchainExplorerOptions)
		}
	}
	
	@ViewBuilder
	func outgoingLiquidity_liquidityAmount(
		_ payment: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		
		if let liquidityPurchase = payment.liquidityPurchase {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Liquidity requested"
			) {
				commonValue_amounts(
					identifier: #function,
					displayAmounts: displayAmounts(
						sat: liquidityPurchase.amount,
						originalFiat: paymentInfo.metadata.originalFiat
					)
				)
			}
		}
	}
	
	@ViewBuilder
	func outgoingLiquidity_serviceFees(
		_ payment: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Service fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: payment.serviceFee,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func outgoingLiquidity_minerFees(
		_ payment: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Miner fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					sat: payment.miningFee,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func outgoingLiquidity_purchaseType(
		_ payment: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		
		if let liquidityPurchase = payment.liquidityPurchase {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Purchase type"
			) {
				switch onEnum(of: liquidityPurchase) {
				case .standard(_):
					Text("Standard")
					
				case .withFeeCredit(_):
					Text("Fee credit")
				}
			}
		}
	}
	
	@ViewBuilder
	func outgoingLiquidity_channelId(
		_ payment: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Channel id",
			valueColumnVerbatim: payment.channelId.toHex()
		)
	}
	
	@ViewBuilder
	func outgoingLiquidity_btcTxid(
		_ payment: Lightning_kmpOnChainOutgoingPayment,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Transaction"
		) {
			commonValue_btcTxid(payment.txId, showBlockchainExplorerOptions)
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: BLIP 42
	// --------------------------------------------------
	#if DEBUG
	
	@ViewBuilder
	func section_blip42(
		_ secret: Bitcoin_kmpByteVector32?,
		_ offer: Lightning_kmpOfferTypesOffer?,
		_ address: Lightning_kmp_coreUnverifiedContactAddress?
	) -> some View {
		
		InlineSection {
			header("BLIP 42 (DEBUG build only)")
		} content: {
			blip42_contactSecret(secret)
			blip42_offer(offer)
			blip42_address(address)
		}
	}
	
	@ViewBuilder
	func blip42_contactSecret(
		_ secret: Bitcoin_kmpByteVector32?
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "contact secret"
		) {
			if let str = secret?.toHex() {
				Text(str)
					.lineLimit(2)
					.truncationMode(.middle)
			} else {
				Text(verbatim: "<null>")
					.foregroundStyle(Color.secondary)
			}
		}
	}
	
	@ViewBuilder
	func blip42_offer(
		_ offer: Lightning_kmpOfferTypesOffer?
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "payer offer"
		) {
			if let str = offer?.encode() {
				Text(str)
					.lineLimit(2)
					.truncationMode(.middle)
			} else {
				Text(verbatim: "<null>")
					.foregroundStyle(Color.secondary)
			}
		}
	}

	@ViewBuilder
	func blip42_address(
		_ address: Lightning_kmp_coreUnverifiedContactAddress?
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "payer address"
		) {
			if let str = address?.description() {
				Text(str)
					.lineLimit(2)
					.truncationMode(.middle)
			} else {
				Text(verbatim: "<null>")
					.foregroundStyle(Color.secondary)
			}
		}
	}
	
	#endif
	// --------------------------------------------------
	// MARK: SubSection: Bolt11 Invoice
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_bolt11Invoice(
		_ bolt11Invoice : Lightning_kmpBolt11Invoice,
		preimage        : Bitcoin_kmpByteVector32?
	) -> some View {
		
		bolt11Invoice_amountRequested(bolt11Invoice)
		bolt11Invoice_description(bolt11Invoice)
		bolt11Invoice_paymentHash(bolt11Invoice)
		if let preimage {
			bolt11Invoice_preimage(preimage)
		}
		bolt11Invoice_invoice(bolt11Invoice)
	}
	
	@ViewBuilder
	func bolt11Invoice_amountRequested(
		_ bolt11Invoice: Lightning_kmpBolt11Invoice
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "amount requested"
		) {
			if let msat = bolt11Invoice.amount {
				commonValue_amounts(
					identifier: #function,
					displayAmounts: displayAmounts(
						msat: msat,
						originalFiat: nil // we don't have this info (at time of invoice generation)
					)
				)
			} else {
				Text("Any amount")
			}
		}
	}
	
	@ViewBuilder
	func bolt11Invoice_description(
		_ bolt11Invoice: Lightning_kmpBolt11Invoice
	) -> some View {
		
		if let rawDescription = bolt11Invoice.invoiceDescription_ {
			
			detailsRow(
				identifier: #function,
				keyColumnTitle: "invoice description"
			) {
				let description = rawDescription.trimmingCharacters(in: .whitespacesAndNewlines)
				if description.isEmpty {
					Text("empty").foregroundColor(.secondary)
				} else {
					Text(description)
				}
			}
		}
	}
	
	@ViewBuilder
	func bolt11Invoice_paymentHash(
		_ bolt11Invoice: Lightning_kmpBolt11Invoice
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment hash",
			valueColumnText: bolt11Invoice.paymentHash.toHex()
		)
	}
	
	@ViewBuilder
	func bolt11Invoice_preimage(
		_ preimage: Bitcoin_kmpByteVector32
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment preimage",
			valueColumnText: preimage.toHex()
		)
	}
	
	@ViewBuilder
	func bolt11Invoice_invoice(
		_ bolt11Invoice: Lightning_kmpBolt11Invoice
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "invoice"
		) {
			let invoice = bolt11Invoice.write()
			Text(invoice)
				.lineLimit(5)
				.truncationMode(.tail)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = invoice
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	// --------------------------------------------------
	// MARK: SubSection: Bolt12 Invoice
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_bolt12Invoice(
		_ invoice : Lightning_kmpBolt12Invoice,
		payerKey  : Bitcoin_kmpPrivateKey,
		preimage  : Bitcoin_kmpByteVector32?
	) -> some View {
		
		bolt12Invoice_amountRequested(invoice)
		bolt12Invoice_description(invoice)
		bolt12Invoice_payerKey(payerKey)
		bolt12Invoice_paymentHash(invoice)
		if let preimage {
			bolt12Invoice_preimage(preimage)
		}
		bolt12Invoice_offer(invoice)
		bolt12Invoice_invoice(invoice)
	}
	
	@ViewBuilder
	func bolt12Invoice_amountRequested(
		_ invoice: Lightning_kmpBolt12Invoice
	) -> some View {
		
		if let amount = invoice.amount {
			detailsRow(
				identifier: #function,
				keyColumnTitle: "Amount requested"
			) {
				commonValue_amounts(
					identifier: #function,
					displayAmounts: displayAmounts(
						msat: amount,
						originalFiat: paymentInfo.metadata.originalFiat
					)
				)
			}
		}
	}
	
	@ViewBuilder
	func bolt12Invoice_description(
		_ invoice: Lightning_kmpBolt12Invoice
	) -> some View {
		
		if let desc = invoice.description_?.trimmingCharacters(in: .whitespacesAndNewlines), !desc.isEmpty {
			detailsRowCopyable(
				identifier: #function,
				keyColumnTitle: "Invoice description",
				valueColumnText: desc
			)
		}
	}
	
	@ViewBuilder
	func bolt12Invoice_payerKey(
		_ payerKey  : Bitcoin_kmpPrivateKey
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Payer key",
			valueColumnText: payerKey.toHex()
		)
	}
	
	@ViewBuilder
	func bolt12Invoice_paymentHash(
		_ invoice: Lightning_kmpBolt12Invoice
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Payment Hash",
			valueColumnText: invoice.paymentHash.toHex()
		)
	}
	
	@ViewBuilder
	func bolt12Invoice_preimage(
		_ preimage: Bitcoin_kmpByteVector32
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "Preimage",
			valueColumnText: preimage.toHex()
		)
	}
	
	@ViewBuilder
	func bolt12Invoice_offer(
		_ invoice: Lightning_kmpBolt12Invoice
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Offer"
		) {
			let text = invoice.invoiceRequest.offer.encode()
			Text(text)
				.lineLimit(5)
				.truncationMode(.tail)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = text
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func bolt12Invoice_invoice(
		_ invoice: Lightning_kmpBolt12Invoice
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Bolt12 invoice"
		) {
			let text = invoice.write()
			Text(text)
				.lineLimit(5)
				.truncationMode(.tail)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = text
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	// --------------------------------------------------
	// MARK: SubSection: Incoming Bolt12 Details
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_incomingBolt12Details(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		incomingBolt12Details_amountRequested(metadata)
		incomingBolt12Details_paymentHash(metadata)
		incomingBolt12Details_preimage(metadata)
		incomingBolt12Details_metadata(metadata)
		incomingBolt12Details_payerKey(metadata)
	}
	
	@ViewBuilder
	func incomingBolt12Details_amountRequested(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "amount requested"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: metadata.amount,
					originalFiat: nil // we don't have this info (at time of invoice generation)
				)
			)
		}
	}
	
	@ViewBuilder
	func incomingBolt12Details_paymentHash(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment hash",
			valueColumnText: metadata.paymentHash.toHex()
		)
	}
	
	@ViewBuilder
	func incomingBolt12Details_preimage(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment preimage",
			valueColumnText: metadata.preimage.toHex()
		)
	}
	
	@ViewBuilder
	func incomingBolt12Details_metadata(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Metadata"
		) {
			let offer = metadata.encode().toHex()
			Text(offer)
				.lineLimit(5)
				.truncationMode(.tail)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = offer
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func incomingBolt12Details_payerKey(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		if let metadataV1 = metadata as? Lightning_kmpOfferPaymentMetadata.V1 {
			
			detailsRowCopyable(
				identifier: #function,
				keyColumnTitle: "Payer key",
				valueColumnText: metadataV1.payerKey.toHex()
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: SubSection: Outgoing Amount
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_outgoingAmounts(
		_ payment: Lightning_kmpOutgoingPayment
	) -> some View {
		
		outgoingAmounts_amountSent(payment)
		outgoingAmounts_fees(payment)
	}
	
	@ViewBuilder
	func outgoingAmounts_amountSent(
		_ payment: Lightning_kmpOutgoingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Amount sent (fees included)"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: payment.amount,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func outgoingAmounts_fees(
		_ payment: Lightning_kmpOutgoingPayment
	) -> some View {
		
		detailsRow(
			identifier: #function,
			keyColumnTitle: "Fees"
		) {
			commonValue_amounts(
				identifier: #function,
				displayAmounts: displayAmounts(
					msat: payment.fees,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func exploreTx(_ txId: Bitcoin_kmpTxId, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txId, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
}
