import Foundation
import PhoenixShared

enum MsatsPolicy {
	/// Millisatoshi amounts are never shown.
	case hideMsats
	/// Millisatoshi amounts are shown if non-zero.
	case showMsatsIfNonZero
	/// Millisatoshi amounts are only shown if: `(sat == 0) && (0 < msat < 1_000)`
	case showMsatsIfZeroSats
}

class Utils {
	
	public static let Millisatoshis_Per_Satoshi      =           1_000.0
	public static let Millisatoshis_Per_Bit          =         100_000.0
	public static let Millisatoshis_Per_Millibitcoin =     100_000_000.0
	public static let Millisatoshis_Per_Bitcoin      = 100_000_000_000.0
	
	// --------------------------------------------------
	// MARK: Conversion
	// --------------------------------------------------
	
	/// Converts to millisatoshi, the preferred unit for performing conversions.
	///
	static func toMsat(fromFiat amount: Double, exchangeRate: ExchangeRate.BitcoinPriceRate) -> Int64 {
		
		let btc = amount / exchangeRate.price
		return toMsat(from: btc, bitcoinUnit: .btc)
	}
	
	/// Converts from satoshi to millisatoshi
	///
	static func toMsat(sat: Bitcoin_kmpSatoshi) -> Int64 {
		return toMsat(sat: sat.toLong())
	}
	
	/// Converts from satoshi to millisatoshi
	///
	static func toMsat(sat: Int64) -> Int64 {
		return sat * Int64(Millisatoshis_Per_Satoshi)
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
	
	static func convertBitcoin(msat: Lightning_kmpMilliSatoshi, to bitcoinUnit: BitcoinUnit) -> Double {
		return convertBitcoin(msat: msat.toLong(), to: bitcoinUnit)
	}
	
	static func convertBitcoin(msat: Int64, to bitcoinUnit: BitcoinUnit) -> Double {
		
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
	
	/// Returns the exchangeRate for the given fiatCurrency, if available.
	///
	static func exchangeRate(
		for fiatCurrency: FiatCurrency,
		fromRates exchangeRates: [ExchangeRate]
	) -> ExchangeRate.BitcoinPriceRate? {
		
		let btcExchangeRates: [ExchangeRate.BitcoinPriceRate] = exchangeRates.compactMap { rate in
			return rate as? ExchangeRate.BitcoinPriceRate
		}
		
		if let paramToBtc = btcExchangeRates.first(where: { (rate: ExchangeRate.BitcoinPriceRate) in
			rate.fiatCurrency == fiatCurrency
		}) {
			return paramToBtc
		}
		
		let usdExchangeRates: [ExchangeRate.UsdPriceRate] = exchangeRates.compactMap { rate in
			return rate as? ExchangeRate.UsdPriceRate
		}
		
		guard let paramToUsd = usdExchangeRates.first(where: { (rate: ExchangeRate.UsdPriceRate) in
			rate.fiatCurrency == fiatCurrency
		}) else {
			return nil
		}
		
		guard let usdToBtc = btcExchangeRates.first(where: { (rate: ExchangeRate.BitcoinPriceRate) in
			rate.fiatCurrency == FiatCurrency.usd
		}) else {
			return nil
		}
		
		return ExchangeRate.BitcoinPriceRate(
			fiatCurrency: fiatCurrency,
			price: usdToBtc.price * paramToUsd.price,
			source: "\(usdToBtc.source), \(paramToUsd.source)",
			timestampMillis: min(usdToBtc.timestampMillis, paramToUsd.timestampMillis)
		)
	}
	
	// --------------------------------------------------
	// MARK: Bitcoin Formatting
	// --------------------------------------------------
	
	/// Returns a formatter that's appropriate for the given BitcoinUnit & configuration.
	///
	static func bitcoinFormatter(
		bitcoinUnit : BitcoinUnit,
		hideMsats   : Bool = true,
		locale      : Locale? = nil
	) -> NumberFormatter {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true // thousands separator (US="10,000", FR="10 000")
		
		if let locale = locale {
			formatter.locale = locale
		}
		
		switch bitcoinUnit {
			case .sat          : formatter.maximumFractionDigits = 0
			case .bit          : formatter.maximumFractionDigits = 2
			case .mbtc         : formatter.maximumFractionDigits = 5
			default/*.bitcoin*/: formatter.maximumFractionDigits = 8
		}
		
		if !hideMsats {
			formatter.maximumFractionDigits += 3
		}
		
		formatter.minimumFractionDigits = formatter.maximumFractionDigits
		
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
	static func formatBitcoin(
		sat         : Bitcoin_kmpSatoshi,
		bitcoinUnit : BitcoinUnit,
		locale      : Locale? = nil
	) -> FormattedAmount {
		
		return formatBitcoin(sat: sat.toLong(), bitcoinUnit: bitcoinUnit, locale: locale)
	}
	
	/// Converts from satoshis to the given BitcoinUnit.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		sat         : Int64,
		bitcoinUnit : BitcoinUnit,
		locale      : Locale? = nil
	) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit, locale: locale)
	}
	
	/// Converts from millisatoshis to the given BitcoinUnit.
	/// By default sub-satoshi units are truncated, but this can be controlled with the `hideMsats` parameter.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		msat        : Lightning_kmpMilliSatoshi,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats,
		locale      : Locale? = nil
	) -> FormattedAmount {
		
		return formatBitcoin(msat: msat.toLong(), bitcoinUnit: bitcoinUnit, policy: policy, locale: locale)
	}
	
	/// Converts from millisatoshis to the given BitcoinUnit.
	/// By default sub-satoshi units are truncated, but this can be controlled with the `hideMsats` parameter.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		msat        : Int64,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats,
		locale      : Locale? = nil
	) -> FormattedAmount {
		
		let targetAmount: Double = convertBitcoin(msat: msat, to: bitcoinUnit)
		return formatBitcoin(amount: targetAmount, bitcoinUnit: bitcoinUnit, policy: policy, locale: locale)
	}
	
	static func formatBitcoin(
		amount      : Double,
		bitcoinUnit : BitcoinUnit,
		policy      : MsatsPolicy = .hideMsats,
		locale      : Locale? = nil
	) -> FormattedAmount {
		
		let hideMsats: Bool
		switch policy {
		case .hideMsats:
			hideMsats = true
		case .showMsatsIfNonZero:
			let msatsRemainder = toMsat(from: amount, bitcoinUnit: bitcoinUnit) % Int64(1_000)
			if (msatsRemainder > 0) {
				hideMsats = false
			} else {
				hideMsats = true
			}
		case .showMsatsIfZeroSats:
			let totalMsats = toMsat(from: amount, bitcoinUnit: bitcoinUnit)
			if (totalMsats > 0) && (totalMsats < 1_000) {
				hideMsats = false
			} else {
				hideMsats = true
			}
		}
		
		let formatter = bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats, locale: locale)
		
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
		
		return formattedAmount.withTruncatedFractionDigits().withFormattedFractionDigits()
	}
	
	// --------------------------------------------------
	// MARK: Fiat Formatting
	// --------------------------------------------------
	
	/// Returns a formatter appropriate for any fiat currency.
	///
	static func fiatFormatter(
		fiatCurrency : FiatCurrency,
		locale       : Locale? = nil
	) -> NumberFormatter {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .currency
		
		if let locale = locale {
			formatter.locale = locale
		}
		
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
		
		if fiatCurrency.usesCents() {
			formatter.minimumFractionDigits = 2
			formatter.maximumFractionDigits = 2
		} else {
			formatter.minimumFractionDigits = 0
			formatter.maximumFractionDigits = 0
		}
		
		return formatter
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat          : Bitcoin_kmpSatoshi,
		exchangeRate : ExchangeRate.BitcoinPriceRate,
		locale       : Locale? = nil
	) -> FormattedAmount {
		
		return formatFiat(sat: sat.toLong(), exchangeRate: exchangeRate, locale: locale)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		sat          : Int64,
		exchangeRate : ExchangeRate.BitcoinPriceRate,
		locale       : Locale? = nil
	) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatFiat(msat: msat, exchangeRate: exchangeRate, locale: locale)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat         : Lightning_kmpMilliSatoshi,
		exchangeRate : ExchangeRate.BitcoinPriceRate,
		locale       : Locale? = nil
	) -> FormattedAmount {
		
		return formatFiat(msat: msat.toLong(), exchangeRate: exchangeRate, locale: locale)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(
		msat         : Int64,
		exchangeRate : ExchangeRate.BitcoinPriceRate,
		locale       : Locale? = nil
	) -> FormattedAmount {
		
		let fiatAmount = convertToFiat(msat: msat, exchangeRate: exchangeRate)
		return formatFiat(amount: fiatAmount, fiatCurrency: exchangeRate.fiatCurrency, locale: locale)
	}
	
	static func formatFiat(
		amount       : Double,
		fiatCurrency : FiatCurrency,
		locale       : Locale? = nil
	) -> FormattedAmount {
		
		let formatter = fiatFormatter(fiatCurrency: fiatCurrency, locale: locale)
		
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
	
	// --------------------------------------------------
	// MARK: Alt Formatting
	// --------------------------------------------------
	
	static func unknownFiatAmount(fiatCurrency: FiatCurrency) -> FormattedAmount {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .currency
		
		let decimalSeparator: String = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
		let digits = fiatCurrency.usesCents() ? "?\(decimalSeparator)??" : "?"
		
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.fiat(fiatCurrency),
			digits: digits,
			decimalSeparator: decimalSeparator
		)
	}
}
