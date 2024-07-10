import SwiftUI
import PhoenixShared

fileprivate let filename = "SummaryInfoGrid"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SummaryInfoGrid: InfoGridView { // See InfoGridView for architecture discussion
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	
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
	
	@State var popoverPresent_standardFees = false
	@State var popoverPresent_minerFees = false
	@State var popoverPresent_serviceFees = false
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			
			// Splitting this up into separate ViewBuilders,
			// because the compiler will sometimes choke while processing this method.
			
			paymentServiceRow()
			paymentDescriptionRow()
			paymentMessageRow()
			customNotesRow()
			attachedMessageRow()
			paymentTypeRow()
			channelClosingRow()
			
			paymentFeesRow_StandardFees()
			paymentFeesRow_MinerFees()
			paymentFeesRow_ServiceFees()
			paymentDurationRow()
			
			paymentErrorRow()
		}
		.padding([.leading, .trailing])
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func keyColumn(verbatim title: String) -> some View {
		
		Text(title).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func paymentServiceRow() -> some View {
		let identifier: String = #function
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Service")
				
			} valueColumn: {
				
				Text(lnurlPay.initialUrl.host)
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentDescriptionRow() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			
			keyColumn("Desc")
				.accessibilityLabel("Description")
			
		} valueColumn: {
			
			let description = paymentInfo.paymentDescription() ?? paymentInfo.defaultPaymentDescription()
			Text(description)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = description
					}) {
						Text("Copy")
					}
				}
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func paymentMessageRow() -> some View {
		let identifier: String = #function
		let successAction = paymentInfo.metadata.lnurl?.successAction
		
		if let sa_message = successAction as? LnurlPay.Invoice_SuccessAction_Message {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				Text(sa_message.message)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = sa_message.message
						}) {
							Text("Copy")
						}
					}
				
			} // </InfoGridRow>
			
		} else if let sa_url = successAction as? LnurlPay.Invoice_SuccessAction_Url {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					
					Text(sa_url.description_)
					
					if let url = URL(string: sa_url.url.description()) {
						Button {
							openURL(url)
						} label: {
							Text("open link")
						}
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = url.absoluteString
							}) {
								Text("Copy link")
							}
						}
					}
				} // </VStack>
				
			} // </InfoGridRow>
		
		} else if let sa_aes = successAction as? LnurlPay.Invoice_SuccessAction_Aes {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					
					Text(sa_aes.description_)
					
					if let sa_aes_decrypted = decrypt(aes: sa_aes) {
					
						if let url = URL(string: sa_aes_decrypted.plaintext) {
							Button {
								openURL(url)
							} label: {
								Text("open link")
							}
							.contextMenu {
								Button(action: {
									UIPasteboard.general.string = url.absoluteString
								}) {
									Text("Copy link")
								}
							}
						} else {
							Text(sa_aes_decrypted.plaintext)
								.contextMenu {
									Button(action: {
										UIPasteboard.general.string = sa_aes_decrypted.plaintext
									}) {
										Text("Copy")
									}
								}
						}
						
					} else {
						Text("<decryption error>")
					}
				}
			} // </InfoGridRow>
			
		} // </else if let sa_aes>
	}
	
	@ViewBuilder
	func customNotesRow() -> some View {
		let identifier: String = #function
		
		if let notes = paymentInfo.metadata.userNotes, notes.count > 0 {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Notes")
				
			} valueColumn: {
				
				Text(notes)
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func attachedMessageRow() -> some View {
		
		let identifier: String = #function
		
		if let msg = paymentInfo.attachedMessage() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(msg)
					if paymentInfo.payment.isIncoming() {
						Text("Be careful with messages from unknown sources")
							.foregroundColor(.secondary)
							.font(.subheadline)
					}
				}
				
				
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentTypeRow() -> some View {
		let identifier: String = #function
		
		if let paymentTypeTuple = paymentInfo.payment.paymentType() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Type")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					let (type, explanation) = paymentTypeTuple
					Text(type)
					+ Text(verbatim: " (\(explanation))")
						.font(.footnote)
						.foregroundColor(.secondary)
					
					if let link = paymentInfo.payment.paymentLink() {
						Button {
							openURL(link)
						} label: {
							Text("view on blockchain")
						}
					}
				}
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func channelClosingRow() -> some View {
		let identifier: String = #function
		
		if let pClosingInfo = paymentInfo.payment.channelClosing() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Output")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					// Bitcoin address (copyable)
					Text(pClosingInfo.address)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = pClosingInfo.address
							}) {
								Text("Copy")
							}
						}
					
					if pClosingInfo.isSentToDefaultAddress {
						Text("(This is your address - derived from your seed. You alone possess your seed.)")
							.font(.footnote)
							.foregroundColor(.secondary)
							.padding(.top, 4)
					}
				}
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_StandardFees() -> some View {
		
		if let standardFees = paymentInfo.payment.standardFees() {
			paymentFeesRow(
				msat: standardFees.0,
				title: standardFees.1,
				explanation: standardFees.2,
				binding: $popoverPresent_standardFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_MinerFees() -> some View {
		
		if let minerFees = paymentInfo.payment.minerFees() {
			paymentFeesRow(
				msat: minerFees.0,
				title: minerFees.1,
				explanation: minerFees.2,
				binding: $popoverPresent_minerFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_ServiceFees() -> some View {
		
		if let serviceFees = paymentInfo.payment.serviceFees() {
			paymentFeesRow(
				msat: serviceFees.0,
				title: serviceFees.1,
				explanation: serviceFees.2,
				binding: $popoverPresent_serviceFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow(
		msat: Int64,
		title: String,
		explanation: String,
		binding: Binding<Bool>
	) -> some View {
		let identifier: String = "paymentFeesRow:\(title)"
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			
			keyColumn(verbatim: title)
			
		} valueColumn: {
				
			HStack(alignment: VerticalAlignment.center, spacing: 6) {
				
				let amount = formattedAmount(msat: msat)
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					if amount.hasSubFractionDigits {
						
						// We're showing sub-fractional values.
						// For example, we're showing millisatoshis.
						//
						// It's helpful to downplay the sub-fractional part visually.
						
						let hasStdFractionDigits = amount.hasStdFractionDigits
						
						Text(verbatim: amount.integerDigits)
						+	Text(verbatim: amount.decimalSeparator)
							.foregroundColor(hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: amount.stdFractionDigits)
						+	Text(verbatim: amount.subFractionDigits)
							.foregroundColor(.secondary)
							.font(.callout)
							.fontWeight(.light)
						
					} else {
						Text(amount.digits)
					}
					
					Text(verbatim: " ")
					Text_CurrencyName(currency: amount.currency, fontTextStyle: .body)
					
				} // </HStack>
				.onTapGesture { toggleCurrencyType() }
				.accessibilityLabel("\(amount.string)")
				
				if !explanation.isEmpty {
					
					Button {
						binding.wrappedValue.toggle()
					} label: {
						Image(systemName: "questionmark.circle")
							.renderingMode(.template)
							.foregroundColor(.secondary)
							.font(.body)
					}
					.popover(present: binding) {
						InfoPopoverWindow {
							Text(verbatim: explanation)
						}
					}
				}
			} // </HStack>
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func paymentDurationRow() -> some View {
		let identifier: String = #function
		
		if let _ = paymentInfo.payment as? Lightning_kmpInboundLiquidityOutgoingPayment {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Duration")
				
			} valueColumn: {
				
				Text("1 year")
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentErrorRow() -> some View {
		let identifier: String = #function
		
		if let pError = paymentInfo.payment.paymentFinalError() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Error")
				
			} valueColumn: {
				
				Text(pError)
				
			} // </InfoGridRow>
		}
	}
	
	func formattedAmount(msat: Int64) -> FormattedAmount {
		
		if showOriginalFiatValue && currencyPrefs.currencyType == .fiat {
			
			if let originalExchangeRate = paymentInfo.metadata.originalFiat {
				return Utils.formatFiat(msat: msat, exchangeRate: originalExchangeRate)
			} else {
				return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			}
			
		} else {
			
			return Utils.format(currencyPrefs, msat: msat, policy: .showMsatsIfNonZero)
		}
	}

	func decrypt(aes sa_aes: LnurlPay.Invoice_SuccessAction_Aes) -> LnurlPay.Invoice_SuccessAction_Aes_Decrypted? {
		
		guard
			let outgoingPayment = paymentInfo.payment as? Lightning_kmpLightningOutgoingPayment,
			let offchainSuccess = outgoingPayment.status.asOffChain()
		else {
			return nil
		}
		
		do {
			let aes = try AES256(
				key: offchainSuccess.preimage.toSwiftData(),
				iv: sa_aes.iv.toSwiftData()
			)
			
			let plaintext_data = try aes.decrypt(sa_aes.ciphertext.toSwiftData(), padding: .PKCS7)
			if let plaintext_str = String(bytes: plaintext_data, encoding: .utf8) {
				
				return LnurlPay.Invoice_SuccessAction_Aes_Decrypted(
					description: sa_aes.description_,
					plaintext: plaintext_str
				)
			}
			
		} catch {
			log.error("Error decrypting LnurlPay.Invoice_SuccessAction_Aes: \(String(describing: error))")
		}
		
		return nil
	}

	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}
