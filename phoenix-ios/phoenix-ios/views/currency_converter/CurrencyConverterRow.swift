import SwiftUI

fileprivate let filename = "CurrencyConverterRow"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct CurrencyConverterRow: View, ViewName {
	
	let currency: Currency
	
	@Binding var parsedRow: ParsedRow?
	@Binding var currencyTextWidth: CGFloat?
	@Binding var flagWidth: CGFloat?
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var isInvalidAmount: Bool = false
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	enum Field: Hashable {
		case amountTextfield
	}
	
	@FocusState private var focusedField: Field?
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("amount", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.focused($focusedField, equals: .amountTextfield)
					.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					clearTextField()
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(Color(UIColor.tertiaryLabel))
				}
				.isHidden(amount == "")
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			
			Text_CurrencyName(currency: currency, fontTextStyle: .body)
				.frame(width: currencyTextWidth, alignment: .leading)
				.padding(.leading, 10)
			
			switch currency {
			case .bitcoin:
				let fontHeight = UIFont.preferredFont(forTextStyle: .body).pointSize
				Image("bitcoin")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: flagWidth, height: fontHeight, alignment: .center)
					.padding(.leading, 3)
					.offset(x: 0, y: 2)
				
			case .fiat(let fiatCurrency):
				Text(fiatCurrency.flag)
					.frame(width: flagWidth, alignment: .center)
					.padding(.leading, 3)
			}
		}
		.padding()
		.onAppear {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: parsedRow) { _ in
			parsedRowDidChange()
		}
		.onChange(of: currencyPrefs.fiatExchangeRates) { _ in
			exchangeRatesDidChange()
		}
	}
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: currency,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}
	
	func onAppear() {
		log.trace("[\(currency)] onAppear()")
		
		parsedRowDidChange(forceRefresh: true)
	}
	
	func exchangeRatesDidChange() {
		log.trace("[\(currency)] exchangeRatesDidChange()")
		
		if focusedField != .amountTextfield {
			// The exchangeRates changed, and the user is modifying the value of some other currency.
			// Which means we may need to recalculate and update our amount.
			parsedRowDidChange(forceRefresh: true)
		}
	}
	
	func clearTextField() {
		log.trace("[\(currency)] clearTextField()")
		
		focusedField = .amountTextfield
		parsedAmount = Result.failure(.emptyInput)
		amount = ""
		parsedRow = ParsedRow(currency: currency, parsedAmount: Result.failure(.emptyInput))
	}
	
	func amountDidChange() {
		log.trace("[\(currency)] amountDidChange()")
		
		if focusedField == .amountTextfield {
			switch parsedAmount {
				case .failure(_): isInvalidAmount = true
				case .success(_): isInvalidAmount = false
			}
			parsedRow = ParsedRow(currency: currency, parsedAmount: parsedAmount)
		} else {
			log.trace("[\(currency)] ignoring self-triggered event")
		}
	}
	
	func parsedRowDidChange(forceRefresh: Bool = false) {
		log.trace("[\(currency)] parsedRowDidChange()")
		
		guard let parsedRow = parsedRow else {
			return
		}
		
		if !forceRefresh && parsedRow.currency == currency {
			log.trace("[\(currency)] ignoring self-triggered event")
			return
		}
		
		var srcMsat: Int64? = nil
		var newParsedAmount: Result<Double, TextFieldCurrencyStylerError>? = nil
		var newAmount: String? = nil
		
		if case .success(let srcAmt) = parsedRow.parsedAmount {
			
			switch parsedRow.currency {
			case .bitcoin(let srcBitcoinUnit):
				srcMsat = Utils.toMsat(from: srcAmt, bitcoinUnit: srcBitcoinUnit)
				
			case .fiat(let srcFiatCurrency):
				if let srcExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: srcFiatCurrency) {
					srcMsat = Utils.toMsat(fromFiat: srcAmt, exchangeRate: srcExchangeRate)
				}
			}
		}
		
		if let srcMsat = srcMsat {
			
			switch currency {
			case .bitcoin(let dstBitcoinUnit):
					
				let dstFormattedAmt = Utils.formatBitcoin(msat: srcMsat, bitcoinUnit: dstBitcoinUnit, policy: .hideMsats)
				newParsedAmount = Result.success(dstFormattedAmt.amount)
				newAmount = dstFormattedAmt.digits
			
			case .fiat(let dstFiatCurrency):
				
				if let dstExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: dstFiatCurrency) {
					
					let dstFormattedAmt = Utils.formatFiat(msat: srcMsat, exchangeRate: dstExchangeRate)
					newParsedAmount = Result.success(dstFormattedAmt.amount)
					newAmount = dstFormattedAmt.digits
				}
			}
		}
		
		if let newParsedAmount = newParsedAmount, let newAmount = newAmount {
			isInvalidAmount = false
			parsedAmount = newParsedAmount
			amount = newAmount
		} else {
			isInvalidAmount = true
			parsedAmount = Result.failure(.emptyInput)
			amount = ""
		}
	}
}
