import Foundation
import PhoenixShared

enum MsatsPolicy {
	/// Millisatoshi amounts are always shown
	case showMsats
	/// Millisatoshi amounts are never shown
	case hideMsats
	/// Millisatoshi amounts are shown if: `0 < msats < 1,000`
	case showIfZeroSats
}

class Utils {
	
	public static let Millisatoshis_Per_Satoshi      =           1_000.0
	public static let Millisatoshis_Per_Bit          =         100_000.0
	public static let Millisatoshis_Per_Millibitcoin =     100_000_000.0
	public static let Millisatoshis_Per_Bitcoin      = 100_000_000_000.0
	
	/// Converts to millisatoshi, the preferred unit for performing conversions.
	///
	static func toMsat(fromFiat amount: Double, exchangeRate: ExchangeRate.BitcoinPriceRate) -> Int64 {
		
		let btc = amount / exchangeRate.price
		return toMsat(from: btc, bitcoinUnit: .btc)
	}
	
	/// Converts to millisatoshi, the preferred unit for performing conversions.
	///
	static func toMsat(from amount: Double, bitcoinUnit: BitcoinUnit) -> Int64 {
		
		var msat: Double
		switch bitcoinUnit {
			case .sat          : msat = amount * Millisatoshis_Per_Satoshi
			case .bit          : msat = amount * Millisatoshis_Per_Bit
			case .mbtc         : msat = amount * Millisatoshis_Per_Millibitcoin
			default/*.bitcoin*/: msat = amount * Millisatoshis_Per_Bitcoin
		}
		
		if let result = Int64(exactly: msat.rounded(.towardZero)) {
			return result
		} else {
			return (msat > 0) ? Int64.max : Int64.min
		}
	}
	
	static func convertBitcoin(msat: Lightning_kmpMilliSatoshi, bitcoinUnit: BitcoinUnit) -> Double {
		return convertBitcoin(msat: msat.toLong(), bitcoinUnit: bitcoinUnit)
	}
	
	static func convertBitcoin(msat: Int64, bitcoinUnit: BitcoinUnit) -> Double {
		
		switch bitcoinUnit {
			case .sat          : return Double(msat) / Millisatoshis_Per_Satoshi
			case .bit          : return Double(msat) / Millisatoshis_Per_Bit
			case .mbtc         : return Double(msat) / Millisatoshis_Per_Millibitcoin
			default/*.bitcoin*/: return Double(msat) / Millisatoshis_Per_Bitcoin
		}
	}
	
	static func convertToFiat(msat: Int64, exchangeRate: ExchangeRate.BitcoinPriceRate) -> Double {
		
		// exchangeRate.fiatCurrency: FiatCurrency { get }
		// exchangeRate.price: Double { get }
		//
		// exchangeRate.price => value of 1.0 BTC in fiat
		
		let btc = Double(msat) / Millisatoshis_Per_Bitcoin
		let fiat = btc * exchangeRate.price
		
		return fiat
	}
	
	static func convertToFiat(msat: Int64, originalFiat: OriginalFiat) -> Double {
		
		// OriginalFiat.rate: Double { get }
		//
		// originalFiat.rate => value of 1.0 BTC in fiat
		
		let btc = Double(msat) / Millisatoshis_Per_Bitcoin
		let fiat = btc * originalFiat.rate
		
		return fiat
	}
	
	/// Formats the given amount of satoshis into a FormattedAmount struct,
	/// which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs, sat: Bitcoin_kmpSatoshi) -> FormattedAmount {
		
		return format(currencyPrefs, sat: sat.toLong())
	}
	
	/// Formats the given amount of satoshis into a FormattedAmount struct,
	/// which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs, sat: Int64) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return format(currencyPrefs, msat: msat)
	}
	
	/// Formats the given amount of millisatoshis into either a bitcoin or fiat amount,
	/// depending on the configuration of the given currencyPrefs.
	///
	/// - Returns: a FormattedAmount struct, which contains the various string values needed for display.
	///
	static func format(
		_ currencyPrefs : CurrencyPrefs,
		msat            : Lightning_kmpMilliSatoshi,
		policy          : MsatsPolicy = .hideMsats
	) -> FormattedAmount {
		
		return format(currencyPrefs, msat: msat.toLong(), policy: policy)
	}
	
	/// Formats the given amount of millisatoshis into either a bitcoin or fiat amount,
	/// depending on the configuration of the given currencyPrefs.
	///
	/// - Returns: a FormattedAmount struct, which contains the various string values needed for display.
	///
	static func format(
		_ currencyPrefs : CurrencyPrefs,
		msat            : Int64,
		policy          : MsatsPolicy = .hideMsats
	) -> FormattedAmount {
		
		if currencyPrefs.currencyType == .bitcoin {
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit, policy: policy)
		} else {
			let selectedFiat = currencyPrefs.fiatCurrency
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: selectedFiat) {
				return formatFiat(msat: msat, exchangeRate: exchangeRate)
			} else {
				return unknownFiatAmount(fiatCurrency: selectedFiat)
			}
		}
	}
	
	static func unknownFiatAmount(fiatCurrency: FiatCurrency) -> FormattedAmount {
		
		let decimalSeparator = NumberFormatter().currencyDecimalSeparator ?? "."
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.fiat(fiatCurrency),
			digits: "?\(decimalSeparator)??",
			decimalSeparator: decimalSeparator
		)
	}
	
	/// Returns a formatter that's appropriate for the given BitcoinUnit & configuration.
	///
	static func bitcoinFormatter(bitcoinUnit: BitcoinUnit, hideMsats: Bool = true) -> NumberFormatter {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true // thousands separator (US="10,000", FR="10 000")
		
		switch bitcoinUnit {
			case .sat          : formatter.maximumFractionDigits = 0
			case .bit          : formatter.maximumFractionDigits = 2
			case .mbtc         : formatter.maximumFractionDigits = 5
			default/*.bitcoin*/: formatter.maximumFractionDigits = 8
		}
		
		if !hideMsats {
			formatter.maximumFractionDigits += 3
		}
		
		// Rounding will respect our configured maximumFractionDigits.
		//
		// Rounding options:
		// - ceiling  : Round towards positive infinity
		// - floor    : Round towards negative infinity
		// - down     : Round towards zero
		// - up       : Round away from zero
		// - halfEven : Round towards the nearest integer, or towards an even number if equidistant.
		// - halfDown : Round towards the nearest integer, or towards zero if equidistant.
		// - halfUp   : Round towards the nearest integer, or away from zero if equidistant.
		//
		// Keep in mind:
		// * incoming payments are positive (i.e. +14.054)
		// * outgoing payments are negative (i.e. -14.054)
		//
		// In most situations, what we really want is halfUp.
		// For example, if we're formatting satoshis (w/ maximumFractionDigits == 0), then with halfUp:
		// * +16.001 sats => received 16 sats
		// * -16.001 sats => sent 16 sats
		//
		// The exception to this rule is when we round to zero:
		// * +0.100 sats => received 0 sats
		// * -0.100 sats => sent 0 sats
		//
		// So we handle the zero edge case below.
		//
		formatter.roundingMode = .halfUp
		
		return formatter
	}

	/// Converts from satoshis to the given BitcoinUnit.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(sat: Bitcoin_kmpSatoshi, bitcoinUnit: BitcoinUnit) -> FormattedAmount {
		
		return formatBitcoin(sat: sat.toLong(), bitcoinUnit: bitcoinUnit)
	}
	
	/// Converts from satoshis to the given BitcoinUnit.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(sat: Int64, bitcoinUnit: BitcoinUnit) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
	}
	
	/// Converts from millisatoshis to the given BitcoinUnit.
	/// By default sub-satoshi units are truncated, but this can be controlled with the `hideMsats` parameter.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		msat        : Lightning_kmpMilliSatoshi,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats
	) -> FormattedAmount {
		
		return formatBitcoin(msat: msat.toLong(), bitcoinUnit: bitcoinUnit, policy: policy)
	}
	
	/// Converts from millisatoshis to the given BitcoinUnit.
	/// By default sub-satoshi units are truncated, but this can be controlled with the `hideMsats` parameter.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		msat        : Int64,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats
	) -> FormattedAmount {
		
		let targetAmount: Double = convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
		return formatBitcoin(amount: targetAmount, bitcoinUnit: bitcoinUnit, policy: policy)
	}
	
	static func formatBitcoin(
		amount      : Double,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats
	) -> FormattedAmount {
		
		let hideMsats: Bool
		switch policy {
			case .hideMsats: hideMsats = true
			case .showMsats: hideMsats = false
			case .showIfZeroSats:
				let msats = toMsat(from: amount, bitcoinUnit: bitcoinUnit)
				if (msats > 0) && (msats < 1_000) {
					hideMsats = false
				} else {
					hideMsats = true
				}
		}
		
		let formatter = bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
		
		var digits = formatter.string(from: NSNumber(value: amount)) ?? amount.description
		
		// Zero edge-case check
		let positiveZeroDigits = formatter.string(from: NSNumber(value: 0.0)) ?? "0"
		let negativeZeroDigits = formatter.string(from: NSNumber(value: -0.0)) ?? "-0"
		
		if (digits == positiveZeroDigits || digits == negativeZeroDigits) && amount != 0.0 {
			
			formatter.roundingMode = .up // Round away from zero
			digits = formatter.string(from: NSNumber(value: amount)) ?? amount.description
		}
		
		let formattedAmount = FormattedAmount(
			amount: amount,
			currency: Currency.bitcoin(bitcoinUnit),
			digits: digits,
			decimalSeparator: formatter.decimalSeparator
		)
		
		if formatter.maximumFractionDigits > 3 {
			// The number may have a large fraction component.
			// See discussion in: FormattedAmount.withFormattedFractionDigits()
			//
			return formattedAmount.withFormattedFractionDigits()
		} else {
			return formattedAmount
		}
	}
	
	/// Returns a formatter appropriate for any fiat currency.
	///
	static func fiatFormatter(fiatCurrency: FiatCurrency) -> NumberFormatter {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .currency
		
		// The currency formatter embeds the currency symbol:
		// - "$1,234.57"
		// - "1 234,57 €"
		// - "￥1,234.57"
		//
		// We don't want this.
		// So we need to remove it, and the associated padding.
		formatter.currencySymbol = ""
		formatter.paddingCharacter = ""
		formatter.positivePrefix = "" // needed for: [nl, de_CH, es_BQ, ...]
		formatter.positiveSuffix = "" // needed for: [he, ar, ar_AE, ...]
		
		// Rounding options:
		// - ceiling  : Round towards positive infinity
		// - floor    : Round towards negative infinity
		// - down     : Round towards zero
		// - up       : Round away from zero
		// - halfEven : Round towards the nearest integer, or towards an even number if equidistant.
		// - halfDown : Round towards the nearest integer, or towards zero if equidistant.
		// - halfUp   : Round towards the nearest integer, or away from zero if equidistant.
		//
		// Keep in mind:
		// * incoming payments are positive (i.e. +14.054)
		// * outgoing payments are negative (i.e. -14.054)
		//
		// In most situations, what we really want is halfUp.
		// For example, with halfUp:
		// * +2.05123 usd => received 2.05 usd
		// * -2.05123 usd => sent 2.05 usd
		//
		// The exception to this rule is when we round to zero:
		// * +0.00123 usd => received 0.00 usd
		// * -0.00123 usd => sent 0.00 usd
		//
		// So we handle the zero edge case below.
		//
		formatter.roundingMode = .halfUp
		
		// Some currencies don't display cents.
		// For example, the Colombian Peso discountinued centavos in 1984.
		// And today the smallest coin is $5.
		//
		// So if we **DON'T** set the Locale appropriately,
		// then when a Colombian (with currentLocale==es_CO) formats US dollars,
		// the result will NOT display cents. Which is inappropriate for USD.
		//
		// And the opposite problem occurs if an American (with currentLocale=en_US) formats COP,
		// the result WILL display cents. Which is inappropriate for COP.
		
		if let locale = fiatCurrency.matchingLocales().first {
			
			// Unfortunately, we can't simply set formatter.locale.
			// To continue the example from above:
			//
			// A Colombian (with currentLocale=es_CO) would expect to see: 12.345,67 USD
			// An American (with currentLocale=en_US) would expect to see: 12,345 COP
			//
			// Changing the locale changes more than just the display of cents.
			// It could also inappropriately change the grouping separator, decimal separator, etc.
			//
			// So we're going to try to just tweak the min/max fractionDigits.
			
			let altFormatter = formatter.copy() as! NumberFormatter
			altFormatter.locale = locale
			
			if let str = altFormatter.string(from: NSNumber(value: 1.0)) {
				
				let usesCents = str.contains(altFormatter.currencyDecimalSeparator)
				if usesCents {
					formatter.minimumFractionDigits = 2
					formatter.maximumFractionDigits = 2
				} else {
					formatter.minimumFractionDigits = 0
					formatter.maximumFractionDigits = 0
				}
			}
		}
		
		return formatter
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat: Bitcoin_kmpSatoshi,
		exchangeRate: ExchangeRate.BitcoinPriceRate
	) -> FormattedAmount {
		
		return formatFiat(sat: sat.toLong(), exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat: Int64,
		exchangeRate: ExchangeRate.BitcoinPriceRate
	) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatFiat(msat: msat, exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat: Lightning_kmpMilliSatoshi,
		exchangeRate: ExchangeRate.BitcoinPriceRate
	) -> FormattedAmount {
		
		return formatFiat(msat: msat.toLong(), exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat: Int64,
		exchangeRate: ExchangeRate.BitcoinPriceRate
	) -> FormattedAmount {
		
		let fiatAmount = convertToFiat(msat: msat, exchangeRate: exchangeRate)
		return formatFiat(amount: fiatAmount, fiatCurrency: exchangeRate.fiatCurrency)
	}
	
	/// Converts from satoshi to a fiat amount, using the original exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat: Bitcoin_kmpSatoshi,
		originalFiat: OriginalFiat
	) -> FormattedAmount? {
		
		return formatFiat(sat: sat.toLong(), originalFiat: originalFiat)
	}
	
	/// Converts from satoshi to a fiat amount, using the original exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat: Int64,
		originalFiat: OriginalFiat
	) -> FormattedAmount? {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatFiat(msat: msat, originalFiat: originalFiat)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the original exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat: Lightning_kmpMilliSatoshi,
		originalFiat: OriginalFiat
	) -> FormattedAmount? {
		
		return formatFiat(msat: msat.toLong(), originalFiat: originalFiat)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the original exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat: Int64,
		originalFiat: OriginalFiat
	) -> FormattedAmount? {
		
		guard let fiatCurrency = FiatCurrency.companion.valueOfOrNull(code: originalFiat.type) else {
			return nil
		}
		
		let fiatAmount = convertToFiat(msat: msat, originalFiat: originalFiat)
		return formatFiat(amount: fiatAmount, fiatCurrency: fiatCurrency)
	}
	
	static func formatFiat(
		amount: Double,
		fiatCurrency: FiatCurrency
	) -> FormattedAmount {
		
		let formatter = fiatFormatter(fiatCurrency: fiatCurrency)
		
		var digits = formatter.string(from: NSNumber(value: amount)) ?? amount.description
		
		// Zero edge-case check
		let positiveZeroDigits = formatter.string(from: NSNumber(value: 0.0)) ?? "0.00"
		let negativeZeroDigits = formatter.string(from: NSNumber(value: -0.0)) ?? "-0.00"
		
		if (digits == positiveZeroDigits || digits == negativeZeroDigits) && amount != 0.0 {
			
			formatter.roundingMode = .up // Round away from zero
			digits = formatter.string(from: NSNumber(value: amount)) ?? amount.description
		}
		
		return FormattedAmount(
			amount: amount,
			currency: Currency.fiat(fiatCurrency),
			digits: digits,
			decimalSeparator: formatter.currencyDecimalSeparator
		)
	}
}
