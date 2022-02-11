import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ModifyInvoiceSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ModifyInvoiceSheet: View {

	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>

	let initialAmount: Lightning_kmpMilliSatoshi?
	
	@State var desc: String
	
	@Binding var unit: Currency
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isEmptyAmount: Bool = false
	
	@EnvironmentObject private var currencyPrefs: CurrencyPrefs
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	// Workaround for SwiftUI bug
	enum TextHeight: Preference {}
	let textHeightReader = GeometryPreferenceReader(
		key: AppendValue<TextHeight>.self,
		value: { [$0.size.height] }
	)
	@State var textHeight: CGFloat? = nil
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}

	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Edit payment request")
				.font(.title3)
				.padding([.top, .bottom])

			HStack {
				TextField(
					NSLocalizedString("Amount (optional)", comment: "TextField placeholder"),
					text: currencyStyler().amountProxy
				)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
				.read(textHeightReader)
				.padding([.top, .bottom], 8)
				.padding(.leading, 16)
				.padding(.trailing, 0)
				
				Picker(
					selection: $unit,
					label: Text(unit.abbrev).frame(minWidth: 40, alignment: Alignment.trailing)
				) {
					let options = Currency.displayable(currencyPrefs: currencyPrefs)
					ForEach(0 ..< options.count) {
						let option = options[$0]
						Text(option.abbrev).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.frame(height: textHeight) // workaround for SwiftUI bug
				.padding(.trailing, 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))

			Text(altAmount)
				.font(.caption)
				.foregroundColor(isInvalidAmount && !isEmptyAmount ? Color.appNegative : .secondary)
				.padding(.top, 0)
				.padding(.leading, 16)
				.padding(.bottom, 4)

			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					NSLocalizedString("Description (optional)", comment: "TextField placeholder"),
					text: $desc
				)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					desc = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(desc == "")
			}
			.padding([.top, .bottom], 8)
			.padding(.leading, 16)
			.padding(.trailing, 8)
			.background(
				Capsule()
					.strokeBorder(Color(UIColor.separator))
			)
			.padding(.bottom)
			
			HStack {
				Spacer()
				Button("Save") {
					didTapSaveButton()
				}
				.font(.title2)
				.disabled(isInvalidAmount && !isEmptyAmount)
			}
			.padding(.bottom)

		} // </VStack>
		.padding([.leading, .trailing])
		.assignMaxPreference(for: textHeightReader.key, to: $textHeight)
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: unit) { _  in
			unitDidChange()
		}
		
	} // </body>
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		let msat: Int64? = initialAmount?.msat
		
		switch unit {
		case .fiat(let fiatCurrency):
			
			if let msat = msat, let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				
				let formattedAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				parsedAmount = Result.success(formattedAmt.amount)
				amount = formattedAmt.digits
				
			} else {
				refreshAltAmount()
			}
			
		case .bitcoin(let bitcoinUnit):
			
			if let msat = msat {
				
				let formattedAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
				parsedAmount = Result.success(formattedAmt.amount)
				amount = formattedAmt.digits
				
			} else {
				refreshAltAmount()
			}
		}
	}
	
	func amountDidChange() -> Void {
		log.trace("amountDidChange()")
		
		refreshAltAmount()
	}
	
	func unitDidChange() -> Void {
		log.trace("unitDidChange()")
		
		// We might want to apply a different formatter
		let result = TextFieldCurrencyStyler.format(input: amount, currency: unit, hideMsats: false)
		parsedAmount = result.1
		amount = result.0
		
		refreshAltAmount()
	}
	
	func refreshAltAmount() -> Void {
		log.trace("refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			isInvalidAmount = true
			isEmptyAmount = error == .emptyInput
			
			switch error {
			case .emptyInput:
				altAmount = NSLocalizedString("Any amount", comment: "displayed when user doesn't specify an amount")
			case .invalidInput:
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			isInvalidAmount = false
			isEmptyAmount = false
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					
					let msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					altAmount = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate).string
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
				
			case .fiat(let fiatCurrency):
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					altAmount = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit).string
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since Currency.displayable() already filters for this.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
			}
		}
	}
	
	func didTapSaveButton() -> Void {
		log.trace("didTapSaveButton()")
		
		var msat: Lightning_kmpMilliSatoshi? = nil
		let trimmedDesc = desc.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
							
				msat = Lightning_kmpMilliSatoshi(msat:
					Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				)
				
			case .fiat(let fiatCurrency):
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					msat = Lightning_kmpMilliSatoshi(msat:
						Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					)
				}
			}
		}
		
		shortSheetState.close {
			
			mvi.intent(Receive.IntentAsk(
				amount: msat,
				desc: trimmedDesc,
				expirySeconds: Int64(60 * 60 * 24 * Prefs.shared.invoiceExpirationDays)
			))
		}
	}	
}
