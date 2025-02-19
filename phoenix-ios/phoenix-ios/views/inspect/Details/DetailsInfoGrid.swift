import SwiftUI
import PhoenixShared

fileprivate let filename = "DetailsInfoGrid"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class DetailsInfoGridState: ObservableObject {
	@Published var truncatedText: [String: Bool] = [:]
}

protocol DetailsInfoGrid: InfoGridView {
	var detailsInfoGridState: DetailsInfoGridState { get }
	
	var paymentInfo: WalletPaymentInfo { get }
	var showOriginalFiatValue: Bool { get }
	var showFiatValueExplanation: Bool { get }
	var currencyPrefs: CurrencyPrefs { get }
	
	func clockStateBinding() -> Binding<AnimatedClock.ClockState>
}

extension DetailsInfoGrid {
	
	// --------------------------------------------------
	// MARK: Styling
	// --------------------------------------------------
	
	@ViewBuilder
	func header(_ title: LocalizedStringKey) -> some View {
		
		HStack {
			Spacer()
			Text(title)
				.textCase(.uppercase)
				.lineLimit(1)
				.minimumScaleFactor(0.5)
				.font(.headline)
				.foregroundColor(Color(UIColor.systemGray))
			Spacer()
		}
		.padding(.bottom, 12)
		.accessibilityAddTraits(.isHeader)
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func keyColumn(verbatim title: String) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	// --------------------------------------------------
	// MARK: Row Builders
	// --------------------------------------------------
	
	@ViewBuilder
	func detailsRow(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnText: LocalizedStringKey
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(valueColumnText)
		}
	}
	
	@ViewBuilder
	func detailsRow(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnVerbatim: String
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(verbatim: valueColumnVerbatim)
		}
	}
	
	@ViewBuilder
	func detailsRowCopyable(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnText: String
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(valueColumnText)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = valueColumnText
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func detailsRow<Content: View>(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		@ViewBuilder valueBuilder: () -> Content
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			valueBuilder()
		}
	}
	
	// --------------------------------------------------
	// MARK: Common Values
	// --------------------------------------------------
	
	@ViewBuilder
	func commonValue_date(date: Date) -> some View {
		
		let (dayMonthYear, timeOfDay) = displayTimes(date: date)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			Text(dayMonthYear)
			Text(timeOfDay)
		}
	}
	
	@ViewBuilder
	func commonValue_amounts(
		identifier: String,
		displayAmounts: DisplayAmounts,
		displayFeePercent: String? = nil
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			let display_bitcoin = displayAmounts.bitcoin
			if display_bitcoin.hasSubFractionDigits {
				
				// We're showing sub-fractional values.
				// For example, we're showing millisatoshis.
				//
				// It's helpful to downplay the sub-fractional part visually.
				
				let hasStdFractionDigits = display_bitcoin.hasStdFractionDigits
				
				Text(verbatim: display_bitcoin.integerDigits)
				+	Text(verbatim: display_bitcoin.decimalSeparator)
						.foregroundColor(hasStdFractionDigits ? .primary : .secondary)
				+	Text(verbatim: display_bitcoin.stdFractionDigits)
				+	Text(verbatim: display_bitcoin.subFractionDigits)
						.foregroundColor(.secondary)
				+	Text(verbatim: " \(display_bitcoin.type)")
				
			} else {
				Text(verbatim: display_bitcoin.string)
			}
			
			if let display_feePercent = displayFeePercent {
				Text(verbatim: display_feePercent)
			}
			
			// If we know the original fiat exchange rate, then we can switch back and forth
			// between the original & current fiat value.
			//
			// However, if we do NOT know the original fiat value,
			// then we simply show the current fiat value (without the clock button)
			//
			let canShowOriginalFiatValue = displayAmounts.fiatOriginal != nil
			let showingOriginalFiatValue = showOriginalFiatValue && canShowOriginalFiatValue
			
			// The preferred layout is with a single row:
			// 1,234 USD (original) (clock)
			//
			// However, if the text gets truncated it looks really odd:
			// 1,23 USD (origi (clock)
			// 4        nal)
			//
			// In that case we switch to 2 rows.
			// Note that if we detect truncation for either (original) or (now),
			// then we keep the layout at 2 rows so it's not confusing when the user switches back and forth.
			
			let key = identifier
			let isTruncated = detailsInfoGridState.truncatedText[key] ?? false
			
			if isTruncated {
				
				// Two rows:
				// 1,234 USD
				//  (original) (clock)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					if showingOriginalFiatValue {
						
						let display_fiatOriginal = displayAmounts.fiatOriginal ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						Text(verbatim: "≈ \(display_fiatOriginal.digits) ")
						Text_CurrencyName(currency: display_fiatOriginal.currency, fontTextStyle: .callout)
						
					} else {
						
						let display_fiatCurrent = displayAmounts.fiatCurrent ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						Text(verbatim: "≈ \(display_fiatCurrent.digits) ")
						Text_CurrencyName(currency: display_fiatCurrent.currency, fontTextStyle: .callout)
					}
				} // </HStack>
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					if showingOriginalFiatValue {
						Text(" (original)", comment: "translate: original")
							.foregroundColor(.secondary)
					} else {
						Text(" (now)", comment: "translate: now")
							.foregroundColor(.secondary)
					}
					
					if canShowOriginalFiatValue {
						
						AnimatedClock(state: clockStateBinding(), size: 14, animationDuration: 0.0)
							.padding(.leading, 4)
							.offset(y: 1)
					}
				} // </HStack>
				
			} else {
				
				// Single row:
				// 1,234 USD (original) (clock)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					if showingOriginalFiatValue {
						
						let display_fiatOriginal = displayAmounts.fiatOriginal ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						TruncatableView(fixedHorizontal: true, fixedVertical: true) {
							Text(verbatim: "≈ \(display_fiatOriginal.digits) ")
								.layoutPriority(0)
						} wasTruncated: {
							detailsInfoGridState.truncatedText[key] = true
						}
						Text_CurrencyName(currency: display_fiatOriginal.currency, fontTextStyle: .callout)
							.layoutPriority(1)
						Text(" (original)", comment: "translate: original")
							.layoutPriority(1)
							.foregroundColor(.secondary)
						
					} else {
						
						let display_fiatCurrent = displayAmounts.fiatCurrent ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
					
						TruncatableView(fixedHorizontal: true, fixedVertical: true) {
							Text(verbatim: "≈ \(display_fiatCurrent.digits) ")
								.layoutPriority(0)
						} wasTruncated: {
							detailsInfoGridState.truncatedText[key] = true
						}
						Text_CurrencyName(currency: display_fiatCurrent.currency, fontTextStyle: .callout)
							.layoutPriority(1)
						Text(" (now)", comment: "translate: now")
							.layoutPriority(1)
							.foregroundColor(.secondary)
					}
					
					if canShowOriginalFiatValue {
						
						AnimatedClock(state: clockStateBinding(), size: 14, animationDuration: 0.0)
							.padding(.leading, 4)
							.offset(y: 1)
					}
					
				} // </HStack>
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func commonValue_btcTxid(
		_ txId  : Bitcoin_kmpTxId,
		_ showBlockchainExplorerOptions: Binding<Bool>
	) -> some View {
		
		let txIdStr = txId.toHex()
		Text(txIdStr)
			.contextMenu {
				Button(action: {
					UIPasteboard.general.string = txIdStr
				}) {
					Text("Copy")
				}
				Button {
					showBlockchainExplorerOptions.wrappedValue = true
				} label: {
					Text("Explore")
				}
			} // </contextMenu>
			.confirmationDialog("Blockchain Explorer",
				isPresented: showBlockchainExplorerOptions,
				titleVisibility: .automatic
			) {
				Button {
					exploreTx(txId, website: BlockchainExplorer.WebsiteMempoolSpace())
				} label: {
					Text(verbatim: "Mempool.space") // no localization needed
				}
				Button {
					exploreTx(txId, website: BlockchainExplorer.WebsiteBlockstreamInfo())
				} label: {
					Text(verbatim: "Blockstream.info") // no localization needed
				}
			} // </confirmationDialog>
	}
	
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
	// MARK: SubSection: Bolt11 Invoice
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_bolt11Invoice(
		_ bolt11Invoice: Lightning_kmpBolt11Invoice,
		preimage: Bitcoin_kmpByteVector32
	) -> some View {
		
		bolt11Invoice_amountRequested(bolt11Invoice)
		bolt11Invoice_description(bolt11Invoice)
		bolt11Invoice_paymentHash(bolt11Invoice)
		bolt11Invoice_preimage(preimage)
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
	// MARK: SubSection: Bolt12 Offer
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_bolt12Offer(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		bolt12Offer_amountRequested(metadata)
		bolt12Offer_paymentHash(metadata)
		bolt12Offer_preimage(metadata)
		bolt12Offer_metadata(metadata)
		bolt12Offer_payerKey(metadata)
	}
	
	@ViewBuilder
	func bolt12Offer_amountRequested(
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
	func bolt12Offer_paymentHash(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment hash",
			valueColumnText: metadata.paymentHash.toHex()
		)
	}
	
	@ViewBuilder
	func bolt12Offer_preimage(
		_ metadata: Lightning_kmp_coreOfferPaymentMetadata
	) -> some View {
		
		detailsRowCopyable(
			identifier: #function,
			keyColumnTitle: "payment preimage",
			valueColumnText: metadata.preimage.toHex()
		)
	}
	
	@ViewBuilder
	func bolt12Offer_metadata(
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
	func bolt12Offer_payerKey(
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
	// MARK: View Helpers
	// --------------------------------------------------
	
	func displayTimes(date: Date) -> (String, String) {
		
		let df = DateFormatter()
		df.dateStyle = .long
		df.timeStyle = .none
		let dayMonthYear = df.string(from: date)
		
		let tf = DateFormatter()
		tf.dateStyle = .none
		tf.timeStyle = .long
		let timeOfDay = tf.string(from: date)
		
		return (dayMonthYear, timeOfDay)
	}
	
	func displayElapsedSeconds(milliseconds: Int64) -> String {
		
		let seconds = Double(milliseconds) / Double(1_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true
		formatter.minimumFractionDigits = 3
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: seconds))!
	}
	
	func displayElapsedMinutes(milliseconds: Int64) -> (String, String) {
		
		let minutes = milliseconds / (60 * 1_000)
		let seconds = milliseconds % (60 * 1_000) % 1_000
		
		let mFormatter = NumberFormatter()
		mFormatter.numberStyle = .decimal
		mFormatter.usesGroupingSeparator = true
		
		let minutesStr = mFormatter.string(from: NSNumber(value: minutes))!
		
		let sFormatter = NumberFormatter()
		sFormatter.minimumIntegerDigits = 2
		
		let secondsStr = sFormatter.string(from: NSNumber(value: seconds))!
		
		return (minutesStr, secondsStr)
	}
	
	func displayAmounts(
		msat: Lightning_kmpMilliSatoshi,
		originalFiat: ExchangeRate.BitcoinPriceRate?
	) -> DisplayAmounts {
		
		let bitcoin = Utils.formatBitcoin(
			msat        : msat,
			bitcoinUnit : currencyPrefs.bitcoinUnit,
			policy      : .showMsatsIfNonZero
		)
		var fiatCurrent: FormattedAmount? = nil
		var fiatOriginal: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			fiatCurrent = Utils.formatFiat(msat: msat, exchangeRate: fiatExchangeRate)
		}
		if let originalFiat = originalFiat {
			fiatOriginal = Utils.formatFiat(msat: msat, exchangeRate: originalFiat)
		}
		
		return DisplayAmounts(bitcoin: bitcoin, fiatCurrent: fiatCurrent, fiatOriginal: fiatOriginal)
	}
	
	func displayAmounts(
		sat: Bitcoin_kmpSatoshi,
		originalFiat: ExchangeRate.BitcoinPriceRate?
	) -> DisplayAmounts {
		
		let bitcoin = Utils.formatBitcoin(sat: sat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var fiatCurrent: FormattedAmount? = nil
		var fiatOriginal: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			fiatCurrent = Utils.formatFiat(sat: sat, exchangeRate: fiatExchangeRate)
		}
		if let originalFiat = originalFiat {
			fiatOriginal = Utils.formatFiat(sat: sat, exchangeRate: originalFiat)
		}
		
		return DisplayAmounts(bitcoin: bitcoin, fiatCurrent: fiatCurrent, fiatOriginal: fiatOriginal)
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
