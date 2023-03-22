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

	@Binding var savedAmount: CurrencyAmount?
	@Binding var currencyConverterOpen: Bool
	
	let initialAmount: Lightning_kmpMilliSatoshi?
	@State var desc: String
	
	@State var currency: Currency = Currency.bitcoin(.sat)
	@State var currencyList: [Currency] = [Currency.bitcoin(.sat)]
	@State var currencyPickerChoice = CurrencyPickerOption.currency(Currency.bitcoin(.sat))
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isEmptyAmount: Bool = false
	
	@EnvironmentObject private var currencyPrefs: CurrencyPrefs
	
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	
	// Workaround for SwiftUI bug
	enum TextHeight: Preference {}
	let textHeightReader = GeometryPreferenceReader(
		key: AppendValue<TextHeight>.self,
		value: { [$0.size.height] }
	)
	@State var textHeight: CGFloat? = nil

	init(
		mvi: MVIState<Receive.Model, Receive.Intent>,
		savedAmount: Binding<CurrencyAmount?>,
		amount: Lightning_kmpMilliSatoshi?,
		desc: String,
		currencyConverterOpen: Binding<Bool>
	) {
		self.mvi = mvi
		self._savedAmount = savedAmount
		self.initialAmount = amount
		self._desc = State<String>(initialValue: desc)
		self._currencyConverterOpen = currencyConverterOpen
	}
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Edit payment request")
				.font(.title3)
				.padding([.top, .bottom])
				.accessibilityAddTraits(.isHeader)

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
					selection: $currencyPickerChoice,
					label: currencyText(currencyPickerChoice).frame(minWidth: 40, alignment: Alignment.trailing)
				) {
					ForEach(currencyPickerOptions(), id: \.self) { option in
						currencyText(option).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.frame(height: textHeight) // workaround for SwiftUI bug
				.padding(.trailing, 16)
			}
			.background(
				Capsule().stroke(Color.textFieldBorder)
			)

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
				Capsule().strokeBorder(Color.textFieldBorder)
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
		.onChange(of: currencyPickerChoice) { _ in
			currencyPickerDidChange()
		}
		
	} // </body>
	
	@ViewBuilder
	func currencyText(_ option: CurrencyPickerOption) -> some View {
		
		// From what I can tell, Apple won't let us do any formatting here.
		// Things I've tried that don't work:
		//
		// #1
		// ```
		// HStack {Text("A") Text("B")}
		// ```
		// ^ You just get "A"
		//
		// #2
		// ```
		// Text("A") + Text("B").fontWeight(.thin)
		// ```
		// ^ You just get "AB" without the formatting
		
		switch option {
		case .currency(let currency):
			Text(currency.shortName)
		case .other:
			Text(option.description)
		}
	}
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: currency,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}
	
	func currencyPickerOptions() -> [CurrencyPickerOption] {
		
		var options = [CurrencyPickerOption]()
		for currency in currencyList {
			options.append(CurrencyPickerOption.currency(currency))
		}
		
		options.append(CurrencyPickerOption.other)
		
		return options
	}
	
	func currentAmount() -> CurrencyAmount? {
		
		guard let amt = try? parsedAmount.get(), amt > 0 else {
			return nil
		}
		
		return CurrencyAmount(currency: currency, amount: amt)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		if let savedAmount = savedAmount {
			
			// We have a saved amount from a previous modification.
			// That is, from using the ModifyInvoiceSheet earlier, or from using the CurrencyConverter.
			// So we display this amount as-is.
			
			let formattedAmt: FormattedAmount
			switch savedAmount.currency {
				case .bitcoin(let bitcoinUnit):
					formattedAmt = Utils.formatBitcoin(
						amount: savedAmount.amount,
						bitcoinUnit: bitcoinUnit,
						policy: .showMsatsIfNonZero
					)
				
				case .fiat(let fiatCurrency):
					formattedAmt = Utils.formatFiat(
						amount: savedAmount.amount,
						fiatCurrency: fiatCurrency
					)
			}
			
			parsedAmount = Result.success(formattedAmt.amount)
			amount = formattedAmt.digits
			currency = savedAmount.currency
			
		} else if let initialAmount = initialAmount {
			
			// Since there's an amount in bitcoin, we use the user's preferred bitcoin unit.
			// We try to use the user's preferred currency.
			
			let formattedAmt = Utils.formatBitcoin(
				msat: initialAmount,
				bitcoinUnit: currencyPrefs.bitcoinUnit,
				policy: .showMsatsIfNonZero
			)
			parsedAmount = Result.success(formattedAmt.amount)
			amount = formattedAmt.digits
			currency = Currency.bitcoin(currencyPrefs.bitcoinUnit)
			
		} else {
			
			// There's no amount, so we default to the user's preferred currency
			
			currency = currencyPrefs.currency
			refreshAltAmount()
		}
		
		currencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: currency)
		currencyPickerChoice = CurrencyPickerOption.currency(currency)
	}
	
	func amountDidChange() -> Void {
		log.trace("amountDidChange()")
		
		refreshAltAmount()
	}
	
	func currencyPickerDidChange() -> Void {
		log.trace("currencyPickerDidChange()")
		
		switch currencyPickerChoice {
		case .currency(let newCurrency):
			
			if currency != newCurrency {
				currency = newCurrency
				
				// We might want to apply a different formatter
				let result = TextFieldCurrencyStyler.format(input: amount, currency: currency, hideMsats: false)
				parsedAmount = result.1
				amount = result.0
				
				refreshAltAmount()
			}
			
		case .other: // user selected "other"
			
			if let amt = try? parsedAmount.get(), amt > 0 {
				savedAmount = CurrencyAmount(currency: currency, amount: amt)
			} else {
				savedAmount = nil
			}
			
			currencyConverterOpen = true
			smartModalState.close()
		}
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
			
			var msat: Int64? = nil
			switch currency {
			case .bitcoin(let bitcoinUnit):
				msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				
			case .fiat(let fiatCurrency):
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
				}
			}
			
			if let msat = msat {
				
				var altBitcoinUnit: FormattedAmount? = nil
				var altFiatCurrency: FormattedAmount? = nil
				
				let preferredBitcoinUnit = currencyPrefs.bitcoinUnit
				if currency != Currency.bitcoin(preferredBitcoinUnit) {
					altBitcoinUnit = Utils.formatBitcoin(msat: msat, bitcoinUnit: preferredBitcoinUnit)
				}
				
				let preferredFiatCurrency = currencyPrefs.fiatCurrency
				if currency != Currency.fiat(preferredFiatCurrency) {
					if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: preferredFiatCurrency) {
						altFiatCurrency = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
					} else {
						altFiatCurrency = Utils.unknownFiatAmount(fiatCurrency: preferredFiatCurrency)
					}
				}
				
				if let altBitcoinUnit = altBitcoinUnit, let altFiatCurrency = altFiatCurrency {
					altAmount = "≈ \(altBitcoinUnit.string)  /  ≈ \(altFiatCurrency.string)"
					
				} else if let altBitcoinUnit = altBitcoinUnit {
					altAmount = "≈ \(altBitcoinUnit.string)"
					
				} else if let altFiatCurrency = altFiatCurrency {
					altAmount = "≈ \(altFiatCurrency.string)"
					
				} else {
					// We don't know the exchange rate
					altAmount = ""
				}
				
			} else {
				// We don't know the exchange rate
				altAmount = ""
			}
			
		} // </switch>
	}
	
	func didTapSaveButton() -> Void {
		log.trace("didTapSaveButton()")
		
		var msat: Lightning_kmpMilliSatoshi? = nil
		let trimmedDesc = desc.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			savedAmount = CurrencyAmount(currency: currency, amount: amt)
			switch currency {
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
			
		} else {
			savedAmount = nil
		}
		
		smartModalState.close {
			
			mvi.intent(Receive.IntentAsk(
				amount: msat,
				desc: trimmedDesc,
				expirySeconds: Prefs.shared.invoiceExpirationSeconds
			))
		}
	}	
}

enum CurrencyPickerOption: Hashable, Identifiable, CustomStringConvertible {
	
	case currency(Currency)
	case other
	
	var id: String {
		switch self {
		case .currency(let currency):
			return currency.id
		case .other:
			return "OtHeR"
		}
	}
	
	var description: String {
		switch self {
		case .currency(let currency):
			return currency.shortName
		case .other:
			return NSLocalizedString("other",
				comment: "Option in currency picker list. Sends user to Currency Converter"
			)
		}
	}
}
