import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentDetails"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let grid_horizontalSpacing: CGFloat = 8
fileprivate let grid_verticalSpacing: CGFloat = 12

struct PaymentDetails: View {
	
	let parent: ValidateView
	
	enum GridWidth: Preference {}
	let gridWidthReader = GeometryPreferenceReader(
		key: AppendValue<GridWidth>.self,
		value: { [$0.size.width] }
	)
	@State var gridWidth: CGFloat? = nil
	
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
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			grid()
			Spacer(minLength: 0)
		}
		.read(gridWidthReader)
		.assignMaxPreference(for: gridWidthReader.key, to: $gridWidth)
	}

	@ViewBuilder
	@available(iOS 16.0, *)
	func grid() -> some View {
		
		Grid(horizontalSpacing: grid_horizontalSpacing, verticalSpacing: grid_verticalSpacing) {
			
			if let model = parent.mvi.model as? Scan.Model_OnChainFlow {
				gridRows_onChain(model)
			} else if requestDescription() != nil {
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
	func gridRows_onChain(
		_ model: Scan.Model_OnChainFlow
	) -> some View {
		
		let message = bitcoinUriMessage()
		if message != nil {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("Type")
			} valueColumn: {
				Text("On-Chain Payment")
			} // </GridRowWrapper>
		}
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Desc")
		} valueColumn: {
			Group {
				if let message {
					Text(message)
				} else {
					Text("On-Chain Payment")
				}
			}
		} // </GridRowWrapper>
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Send To")
		} valueColumn: {
			let btcAddr = model.uri.address
			Text(btcAddr)
				.lineLimit(2)
				.truncationMode(.middle)
				.contextMenu {
					Button {
						UIPasteboard.general.string = btcAddr
					} label: {
						Text("Copy")
					}
				}
		} // </GridRowWrapper>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_description() -> some View {
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Desc")
		} valueColumn: {
			Text(requestDescription() ?? "")
				.lineLimit(3)
				.truncationMode(.tail)
		} // </GridRowWrapper>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_offer(
		_ model: Scan.Model_OfferFlow
	) -> some View {
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Send To")
		} valueColumn: {
			valueColumn_offer_sendTo(model)
		} // </GridRowWrapper>
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Message")
		} valueColumn: {
			valueColumn_offer_message(model)
		} // </GridRowWrapper>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func gridRows_paymentSummary(
		_ info: PaymentSummaryStrings
	) -> some View {
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		if info.hasTip {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("tip", titleColor)
			} valueColumn: {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_tip.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_tip.string)")
						.foregroundColor(fiatColor)
					Text(verbatim: info.percent_tip)
						.foregroundColor(percentColor)
				}
			} // </GridRowWrapper>
			.accessibilityLabel(accessibilityLabel_tipAmount(info))
			
		} // </hasTip>
		
		if info.hasLightningFee || info.isEmpty {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("lightning fee", titleColor)
			} valueColumn: {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_lightningFee.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_lightningFee.string)")
						.foregroundColor(fiatColor)
					Text(verbatim: info.percent_lightningFee)
						.foregroundColor(percentColor)
				}
			} // </GridRowWrapper>
			.accessibilityLabel(accessibilityLabel_lightningFeeAmount(info))
			
		} // </hasLightningFee>
		
		if info.hasMinerFee {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("miner fee", titleColor)
			} valueColumn: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					
					VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
						Text(verbatim: info.bitcoin_minerFee.string)
							.foregroundColor(bitcoinColor)
						Text(verbatim: " ≈\(info.fiat_minerFee.string)")
							.foregroundColor(fiatColor)
						Text(verbatim: info.percent_minerFee)
							.foregroundColor(percentColor)
					} // </VStack>
					
					Button {
						parent.showMinerFeeSheet()
					} label: {
						Image(systemName: "square.and.pencil").font(.body)
					}
					
				} // </HStack>
			} // </GridRowWrapper>
			.accessibilityLabel(accessibilityLabel_minerFeeAmount(info))
			
		} // </hasMinerFee>
		
		if info.hasTip || info.hasLightningFee || info.hasMinerFee || info.isEmpty {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("total", titleColor)
			} valueColumn: {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_total.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_total.string)")
						.foregroundColor(fiatColor)
				}
			} // </GridRowWrapper>
			.accessibilityLabel(accessibilityLabel_totalAmount(info))
			
		} // </total>
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func titleColumn(
		_ title: LocalizedStringKey,
		_ color: Color = Color.secondary
	) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.foregroundColor(color)
	}
	
	@ViewBuilder
	func valueColumn_offer_sendTo(
		_ model: Scan.Model_OfferFlow
	) -> some View {
		
		if CONTACTS_ENABLED, let contact = parent.contact {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					ContactPhoto(fileName: contact.photoUri, size: 32)
					
					Text(contact.name)
				} // <HStack>
				
				Text(contact.photoUri ?? "<nil>")
				
			} // </VStack>
			.onTapGesture {
				parent.showManageContactSheet()
			}
			
		} else {
			
			let offer: String = model.offer.encode()
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text(offer)
					.lineLimit(2)
					.truncationMode(.middle)
					.contextMenu {
						Button {
							UIPasteboard.general.string = offer
						} label: {
							Text("Copy")
						}
					}
				if CONTACTS_ENABLED {
					Button {
						parent.showManageContactSheet()
					} label: {
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
							Image(systemName: "person")
							Text("Add contact")
						}
					}
				}
			} // </VStack>
		}
	}
	
	@ViewBuilder
	func valueColumn_offer_message(
		_ model: Scan.Model_OfferFlow
	) -> some View {
		
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
	
	func requestDescription() -> String? {
		
		let sanitize = { (input: String?) -> String? in
			
			if let trimmedInput = input?.trimmingCharacters(in: .whitespacesAndNewlines) {
				if !trimmedInput.isEmpty {
					return trimmedInput
				}
			}
			return nil
		}
		
		if let invoice = parent.bolt11Invoice() {
			return sanitize(invoice.desc_())
			
		} else if let offer = parent.bolt12Offer() {
			return sanitize(offer.description_)
			
		} else if let lnurlPay = parent.lnurlPay() {
			return sanitize(lnurlPay.metadata.plainText)
			
		} else if let lnurlWithdraw = parent.lnurlWithdraw() {
			return sanitize(lnurlWithdraw.defaultDescription)
		}
		
		return nil
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

@available(iOS 16.0, *)
struct GridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
	
	let gridWidth: CGFloat?
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	init(
		gridWidth: CGFloat?,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
		self.gridWidth = gridWidth
		self.keyColumn = keyColumnBuilder()
		self.valueColumn = valueColumnBuilder()
	}
	
	@ViewBuilder
	var body: some View {
		
		GridRow(alignment: VerticalAlignment.firstTextBaseline) {
			keyColumn
				.font(.subheadline)
				.frame(maxWidth: columnWidth, alignment: Alignment.topTrailing)
				.gridCellAnchor(.topTrailing)
			
			valueColumn
				.font(.subheadline)
				.frame(minWidth: columnWidth, alignment: Alignment.leading)
				.gridCellAnchor(.leading)
		}
	}
	
	var columnWidth: CGFloat? {
		guard let gridWidth else {
			return nil
		}
		return (gridWidth / 2.0) - (grid_horizontalSpacing / 2.0)
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

/// Workaround for iOS 15, where `Grid` isn't available.
///
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
	func keyColumn(
		_ title: LocalizedStringKey,
		_ color: Color = Color.secondary
	) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.font(.subheadline)
			.foregroundColor(color)
	}
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			if let model = grandparent.mvi.model as? Scan.Model_OnChainFlow {
				rows_onChain(model)
			} else if parent.requestDescription() != nil {
				row_description()
			}
			if let model = grandparent.mvi.model as? Scan.Model_OfferFlow {
				rows_offer(model)
			}
			if let paymentSummary = grandparent.paymentStrings() {
				rows_paymentSummary(paymentSummary)
			}
		}
	}
	
	@ViewBuilder
	func rows_onChain(_ model: Scan.Model_OnChainFlow) -> some View {
		
		if parent.bitcoinUriMessage() != nil {
			row_onChain_type()
		}
		row_onChain_description()
		row_onChain_sendTo(model)
	}
	
	@ViewBuilder
	func rows_offer(
		_ model: Scan.Model_OfferFlow
	) -> some View {
		
		row_offer_sendTo(model)
		row_offer_message(model)
	}
	
	@ViewBuilder
	func rows_paymentSummary(
		_ info: PaymentSummaryStrings
	) -> some View {
		
		if info.hasTip {
			row_paymentSummary_tip(info)
		}
		if info.hasLightningFee || info.isEmpty {
			row_paymentSummary_lightningFee(info)
		}
		if info.hasMinerFee {
			row_paymentSummary_minerFee(info)
		}
		if info.hasTip || info.hasLightningFee || info.hasMinerFee || info.isEmpty {
			row_paymentSummary_total(info)
		}
	}
	
	@ViewBuilder
	func row_description() -> some View {
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
			
			Text(parent.requestDescription() ?? "")
				.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func row_onChain_type() -> some View {
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
	func row_onChain_description() -> some View {
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
	func row_onChain_sendTo(_ model: Scan.Model_OnChainFlow) -> some View {
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
	
	@ViewBuilder
	func row_offer_sendTo(_ model: Scan.Model_OfferFlow) -> some View {
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
			parent.valueColumn_offer_sendTo(model)
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func row_offer_message(_ model: Scan.Model_OfferFlow) -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Message")
		} valueColumn: {
			parent.valueColumn_offer_message(model)
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func row_paymentSummary_tip(_ info: PaymentSummaryStrings) -> some View {
		let identifier: String = #function
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("tip", titleColor)
		} valueColumn: {
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text(verbatim: info.bitcoin_tip.string)
					.foregroundColor(bitcoinColor)
				Text(verbatim: " ≈\(info.fiat_tip.string)")
					.foregroundColor(fiatColor)
				Text(verbatim: info.percent_tip)
					.foregroundColor(percentColor)
			}
		} // </InfoGridRow>
		.accessibilityLabel(parent.accessibilityLabel_tipAmount(info))
	}
	
	@ViewBuilder
	func row_paymentSummary_lightningFee(_ info: PaymentSummaryStrings) -> some View {
		let identifier: String = #function
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("lightning fee", titleColor)
		} valueColumn: {
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text(verbatim: info.bitcoin_lightningFee.string)
					.foregroundColor(bitcoinColor)
				Text(verbatim: " ≈\(info.fiat_lightningFee.string)")
					.foregroundColor(fiatColor)
				Text(verbatim: info.percent_lightningFee)
					.foregroundColor(percentColor)
			}
		} // </InfoGridRow>
		.accessibilityLabel(parent.accessibilityLabel_lightningFeeAmount(info))
	}
	
	@ViewBuilder
	func row_paymentSummary_minerFee(_ info: PaymentSummaryStrings) -> some View {
		let identifier: String = #function
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("miner fee", titleColor)
		} valueColumn: {
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(verbatim: info.bitcoin_minerFee.string)
						.foregroundColor(bitcoinColor)
					Text(verbatim: " ≈\(info.fiat_minerFee.string)")
						.foregroundColor(fiatColor)
					Text(verbatim: info.percent_minerFee)
						.foregroundColor(percentColor)
				} // </VStack>
				
				Button {
					grandparent.showMinerFeeSheet()
				} label: {
					Image(systemName: "square.and.pencil").font(.body)
				}
				
			} // </HStack>
		} // </InfoGridRow>
		.accessibilityLabel(parent.accessibilityLabel_minerFeeAmount(info))
	}
	
	@ViewBuilder
	func row_paymentSummary_total(_ info: PaymentSummaryStrings) -> some View {
		let identifier: String = #function
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("total", titleColor)
		} valueColumn: {
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text(verbatim: info.bitcoin_total.string)
					.foregroundColor(bitcoinColor)
				Text(verbatim: " ≈\(info.fiat_total.string)")
					.foregroundColor(fiatColor)
			}
		} // </InfoGridRow>
		.accessibilityLabel(parent.accessibilityLabel_totalAmount(info))
	}
}
