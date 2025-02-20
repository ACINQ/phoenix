import SwiftUI
import PhoenixShared

fileprivate let filename = "DetailsInfoGrid"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension DetailsInfoGrid {
	
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
}
