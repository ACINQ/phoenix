import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentDetails"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentDetails: View {
	
	let parent: ValidateView
	
	private let valueFont: Font = .subheadline
	
	@ViewBuilder
	var body: some View {
		
		if #available(iOS 16.0, *) {
			details_ios16()
		} else {
			details_ios15()
		}
	}
	
	@ViewBuilder
	func details_ios15() -> some View {
		
		PaymentDetails_Grid(parent: self)
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func details_ios16() -> some View {
		
		Grid(horizontalSpacing: 8, verticalSpacing: 12) {
			
			if let model = parent.mvi.model as? Scan.Model_OnChainFlow {
				gridRows_onChain(model)
			} else {
				gridRows_description()
			}
			if let model = parent.mvi.model as? Scan.Model_OfferFlow {
				gridRows_offer(model)
			}
			if let paymentSummary = parent.paymentStrings() {
				gridRows_paymentSummary(paymentSummary)
			}
		}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_onChain(_ model: Scan.Model_OnChainFlow) -> some View {
		
		let message = bitcoinUriMessage()
		if message != nil {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				titleColumn("Type")
				
				Text("On-Chain Payment")
					.font(valueFont)
					.gridColumnAlignment(.leading)
				
			} // </GridRow>
		}
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			titleColumn("Desc")
			
			Group {
				if let message {
					Text(message)
				} else {
					Text("On-Chain Payment")
				}
			}
			.font(valueFont)
			.gridCellColumns(2)
			.gridColumnAlignment(.leading)
			.gridCellAnchor(.leading) // bug workaround
			
		} // </GridRow>
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			titleColumn("Send To")
			
			let btcAddr = model.uri.address
			Text(btcAddr)
				.lineLimit(2)
				.truncationMode(.middle)
				.font(valueFont)
				.gridCellColumns(2)
				.gridColumnAlignment(.leading)
				.contextMenu {
					Button {
						UIPasteboard.general.string = btcAddr
					} label: {
						Text("Copy")
					}
				}
		} // </GridRow>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_description() -> some View {
		
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			titleColumn("Desc")
			
			Text(requestDescription())
				.lineLimit(3)
				.truncationMode(.tail)
				.font(valueFont)
				.gridCellColumns(2)
				.gridColumnAlignment(.leading)
				.gridCellAnchor(.leading) // bug workaround
			
		} // </GridRow>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_offer(_ model: Scan.Model_OfferFlow) -> some View {
		
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			titleColumn("Send To")
			
			let offer: String = model.offer.encode()
			Text(offer)
				.lineLimit(2)
				.truncationMode(.middle)
				.font(valueFont)
				.gridCellColumns(2)
				.gridColumnAlignment(.leading)
				.contextMenu {
					Button {
						UIPasteboard.general.string = offer
					} label: {
						Text("Copy")
					}
				}
		} // </GridRow>
		
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			titleColumn("Message")
			
			Group {
				let comment = parent.comment
				if comment.isEmpty {
					Button {
						parent.showCommentSheet()
					} label: {
						Text("Attach a message")
					}
				} else {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
						Text(comment)
							.lineLimit(3)
							.truncationMode(.tail)
						Button {
							parent.showCommentSheet()
						} label: {
							Image(systemName: "square.and.pencil").font(.body)
						}
					}
				}
			} // </Group>
			.font(valueFont)
			.gridCellColumns(2)
			.gridColumnAlignment(.leading)
			.gridCellAnchor(.leading)
			
		} // </GridRow>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_paymentSummary(_ info: PaymentSummaryStrings) -> some View {
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		if info.hasTip {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				titleColumn("tip", titleColor)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_tip.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_tip.string)")
						.foregroundColor(fiatColor)
				}
				.font(valueFont)
				.gridColumnAlignment(.leading)
				
				Text(verbatim: info.percent_tip)
					.font(valueFont)
					.foregroundColor(percentColor)
					.gridColumnAlignment(.leading)
				
			} // </GridRow>
			.accessibilityLabel(accessibilityLabel_tipAmount(info))
			
		} // </hasTip>
		
		if info.hasLightningFee || info.isEmpty {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				titleColumn("lightning fee", titleColor)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_lightningFee.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_lightningFee.string)")
						.foregroundColor(fiatColor)
				}
				.font(valueFont)
				.gridColumnAlignment(.leading)
				
				Text(verbatim: info.percent_lightningFee)
					.font(valueFont)
					.foregroundColor(percentColor)
					.gridColumnAlignment(.leading)
				
			} // </GridRow>
			.accessibilityLabel(accessibilityLabel_lightningFeeAmount(info))
			
		} // </hasLightningFee>
		
		if info.hasMinerFee {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				titleColumn("miner fee", titleColor)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_minerFee.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_minerFee.string)")
						.foregroundColor(fiatColor)
				}
				.font(valueFont)
				.gridColumnAlignment(.leading)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.percent_minerFee)
						.font(valueFont)
						.foregroundColor(percentColor)
					
					Button {
						parent.showMinerFeeSheet()
					} label: {
						Image(systemName: "square.and.pencil").font(.body)
					}
				}
				.gridColumnAlignment(.leading)
				
			} // </GridRow>
			.accessibilityLabel(accessibilityLabel_minerFeeAmount(info))
			
		} // </hasMinerFee>
		
		if info.hasTip || info.hasLightningFee || info.hasMinerFee || info.isEmpty {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				titleColumn("total", titleColor)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_total.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_total.string)")
						.foregroundColor(fiatColor)
				}
				.font(valueFont)
				.gridColumnAlignment(.leading)
				
			} // </GridRow>
			.accessibilityLabel(accessibilityLabel_totalAmount(info))
			
		} // </total>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func titleColumn(_ title: LocalizedStringKey, _ color: Color = Color.secondary) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.font(.subheadline)
			.foregroundColor(color)
			.gridColumnAlignment(.trailing)
			.frame(minWidth: 125, alignment: .trailing) // Todo: Replace hard-coded value
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func bitcoinUriMessage() -> String? {
		
		guard let model = parent.mvi.model as? Scan.Model_OnChainFlow,
		      let message = model.uri.message
		else {
			return nil
		}
		
		let trimmedMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
		return trimmedMessage.isEmpty ? nil : trimmedMessage
	}
	
	func requestDescription() -> String {
		
		var desc: String? = nil
		if let invoice = parent.bolt11Invoice() {
			desc = invoice.desc_()
			
		} else if let offer = parent.bolt12Offer() {
			desc = offer.description_
			
		} else if let lnurlPay = parent.lnurlPay() {
			desc = lnurlPay.metadata.plainText
			
		} else if let lnurlWithdraw = parent.lnurlWithdraw() {
			desc = lnurlWithdraw.defaultDescription
		}
		
		return desc ?? String(localized: "No description")
	}
	
	func accessibilityLabel_tipAmount(_ info: PaymentSummaryStrings) -> String {

		let percent       = info.percent_tip
		let amountBitcoin = info.bitcoin_tip.string
		let amountFiat    = info.fiat_tip.string
		
		return NSLocalizedString(
			"Tip amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_lightningFeeAmount(_ info: PaymentSummaryStrings) -> String {
		
		let percent       = info.percent_lightningFee
		let amountBitcoin = info.bitcoin_lightningFee.string
		let amountFiat    = info.fiat_lightningFee.string
		
		return NSLocalizedString(
			"Lightning fee amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_minerFeeAmount(_ info: PaymentSummaryStrings) -> String {
		
		let percent       = info.percent_minerFee
		let amountBitcoin = info.bitcoin_minerFee.string
		let amountFiat    = info.fiat_minerFee.string
		
		return NSLocalizedString(
			"Miner fee amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_totalAmount(_ info: PaymentSummaryStrings) -> String {
		
		let amountBitcoin = info.bitcoin_total.string
		let amountFiat    = info.fiat_total.string
		
		return NSLocalizedString(
			"Total amount: \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

fileprivate struct PaymentDetails_Grid: InfoGridView {
	
	let parent: PaymentDetails
	var grandparent: ValidateView {
		return parent.parent
	}
	
	// <InfoGridView Protocol>
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	@State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) {
		keyColumnSizes = value
	}
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size] {
		return keyColumnSizes
	}
	
	@State var rowSizes: [InfoGridRow_Size] = []
	func setRowSizes(_ sizes: [InfoGridRow_Size]) {
		rowSizes = sizes
	}
	func getRowSizes() -> [InfoGridRow_Size] {
		return rowSizes
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.font(.subheadline)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			if let model = grandparent.mvi.model as? Scan.Model_OnChainFlow {
				rows_onChain(model)
			}
		}
	}
	
	@ViewBuilder
	func rows_onChain(_ model: Scan.Model_OnChainFlow) -> some View {
		
		if parent.bitcoinUriMessage() != nil {
			onChain_type()
		}
		onChain_description()
		onChain_sendTo(model)
	}
	
	@ViewBuilder
	func onChain_type() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Type")
		} valueColumn: {
			
			Text("On-Chain Payment")
				.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func onChain_description() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Desc")
		} valueColumn: {
			
			if let message = parent.bitcoinUriMessage() {
				Text(message)
					.font(.subheadline)
			} else {
				Text("On-Chain Payment")
					.font(.subheadline)
			}
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func onChain_sendTo(_ model: Scan.Model_OnChainFlow) -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Send To")
		} valueColumn: {
			
			let btcAddr = model.uri.address
			Text(btcAddr)
				.font(.subheadline)
				.contextMenu {
					Button {
						UIPasteboard.general.string = btcAddr
					} label: {
						Text("Copy")
					}
				}
			
		} // </InfoGridRow>
	}
}
