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
		
		  details_ios16()
	}
	
	@ViewBuilder
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
	func grid() -> some View {
		
		Grid(horizontalSpacing: grid_horizontalSpacing, verticalSpacing: grid_verticalSpacing) {
			
			if let model = parent.flow as? SendManager.ParseResult_Uri {
				gridRows_onChain(model)
			} else if requestDescription() != nil {
				gridRows_description()
			}
			
			if let model = parent.flow as? SendManager.ParseResult_Bolt12Offer {
				gridRows_offer(model)
			} else if let model = parent.flow as? SendManager.ParseResult_Lnurl_Pay {
				gridRows_lnurlpay(model)
			}
			
			if let paymentSummary = parent.paymentStrings() {
				gridRows_paymentSummary(paymentSummary)
			}
		}
	}
	
	@ViewBuilder
	func gridRows_onChain(
		_ model: SendManager.ParseResult_Uri
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
	func gridRows_offer(
		_ model: SendManager.ParseResult_Bolt12Offer
	) -> some View {
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Send To")
		} valueColumn: {
			if let contact = parent.contact {
				valueColumn_sendTo_contact(contact)
			} else {
				let dst: String = model.lightningAddress ?? model.offer.encode()
				valueColumn_sendTo_dst(dst)
			}
		} // </GridRowWrapper>
		
		GridRowWrapper(gridWidth: gridWidth) {
			titleColumn("Message")
		} valueColumn: {
			valueColumn_offer_message(model)
		} // </GridRowWrapper>
	}
	
	@ViewBuilder
	func gridRows_lnurlpay(
		_ model: SendManager.ParseResult_Lnurl_Pay
	) -> some View {
		
		if let contact = parent.contact {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("Send To")
			} valueColumn: {
				valueColumn_sendTo_contact(contact)
			} // </GridRowWrapper>
		} else if let address = model.lightningAddress {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("Send To")
			} valueColumn: {
				valueColumn_sendTo_dst(address)
			} // </GridRowWrapper>
		}
	}
	
	@ViewBuilder
	func gridRows_paymentSummary(
		_ info: PaymentSummaryStrings
	) -> some View {
		
		let titleColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		
		if info.hasTip {
			GridRowWrapper(gridWidth: gridWidth) {
				titleColumn("Tip", titleColor)
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
				titleColumn("Lightning fee", titleColor)
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
				titleColumn("Miner fee", titleColor)
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
				titleColumn("Total", titleColor)
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
	func titleColumn(
		_ title: LocalizedStringKey,
		_ color: Color = Color.secondary
	) -> some View {
		
		Text(title)
			.foregroundColor(color)
	}
	
	@ViewBuilder
	func valueColumn_sendTo_contact(
		_ contact: ContactInfo
	) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 4) {
			ContactPhoto(filename: contact.photoUri, size: 32)
			Text(contact.name)
		} // <HStack>
		.onTapGesture {
			parent.manageExistingContact()
		}
	}
	
	@ViewBuilder
	func valueColumn_sendTo_dst(
		_ dst: String
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			Text(dst)
				.lineLimit(2)
				.truncationMode(.middle)
				.contextMenu {
					Button {
						UIPasteboard.general.string = dst
					} label: {
						Text("Copy")
					}
				}
			Button {
				parent.addContact()
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
					Image(systemName: "person")
					Text("Add contact")
				}
			}
		} // </VStack>
	}
	
	@ViewBuilder
	func valueColumn_offer_message(
		_ model: SendManager.ParseResult_Bolt12Offer
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
		
		guard
			let model = parent.flow as? SendManager.ParseResult_Uri,
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

fileprivate struct GridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
	
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
				.frame(maxWidth: columnWidth, alignment: Alignment.trailing)
				.gridColumnAlignment(HorizontalAlignment.trailing)
			
			valueColumn
				.font(.subheadline)
				.frame(minWidth: columnWidth, alignment: Alignment.leading)
				.gridColumnAlignment(HorizontalAlignment.leading)
		}
	}
	
	var columnWidth: CGFloat? {
		guard let gridWidth else {
			return nil
		}
		return (gridWidth / 2.0) - (grid_horizontalSpacing / 2.0)
	}
}

