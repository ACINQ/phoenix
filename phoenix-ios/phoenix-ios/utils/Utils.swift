import Foundation
import PhoenixShared

class Utils {
	
	public static let Millisatoshis_Per_Satoshi      =           1_000.0
	public static let Millisatoshis_Per_Bit          =         100_000.0
	public static let Millisatoshis_Per_Millibitcoin =     100_000_000.0
	public static let Millisatoshis_Per_Bitcoin      = 100_000_000_000.0
	
	/// Converts to millisatoshi, the preferred unit for performing conversions.
	///
	static func toMsat(fromFiat amount: Double, exchangeRate: BitcoinPriceRate) -> Int64 {
		
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
	
	static func convertToFiat(msat: Int64, exchangeRate: BitcoinPriceRate) -> Double {
		
		// exchangeRate.fiatCurrency: FiatCurrency { get }
		// exchangeRate.price: Double { get }
		//
		// exchangeRate.price => value of 1.0 BTC in fiat
		
		let btc = Double(msat) / Millisatoshis_Per_Bitcoin
		let fiat = btc * exchangeRate.price
		
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
		
		return format(currencyPrefs, msat: (sat * 1_000))
	}
	
	/// Formats the given amount of millisatoshis into either a bitcoin or fiat amount,
	/// depending on the configuration of the given currencyPrefs.
	///
	/// - Returns: a FormattedAmount struct, which contains the various string values needed for display.
	///
	static func format(
		_ currencyPrefs : CurrencyPrefs,
		msat            : Lightning_kmpMilliSatoshi,
		hideMsats       : Bool = true
	) -> FormattedAmount {
		
		return format(currencyPrefs, msat: msat.toLong(), hideMsats: hideMsats)
	}
	
	/// Formats the given amount of millisatoshis into either a bitcoin or fiat amount,
	/// depending on the configuration of the given currencyPrefs.
	///
	/// - Returns: a FormattedAmount struct, which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs, msat: Int64, hideMsats: Bool = true) -> FormattedAmount {
		
		if currencyPrefs.currencyType == .bitcoin {
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit, hideMsats: hideMsats)
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
		hideMsats   : Bool = true
	) -> FormattedAmount {
		
		return formatBitcoin(msat: msat.toLong(), bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
	}
	
	/// Converts from millisatoshis to the given BitcoinUnit.
	/// By default sub-satoshi units are truncated, but this can be controlled with the `hideMsats` parameter.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatBitcoin(
		msat        : Int64,
		bitcoinUnit : BitcoinUnit,
		hideMsats   : Bool = true
	) -> FormattedAmount {
		
		let targetAmount: Double = convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
		let formatter = bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
		
		var digits = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		
		// Zero edge-case check
		let positiveZeroDigits = formatter.string(from: NSNumber(value: 0.0)) ?? "0"
		let negativeZeroDigits = formatter.string(from: NSNumber(value: -0.0)) ?? "-0"
		
		if (digits == positiveZeroDigits || digits == negativeZeroDigits) && targetAmount != 0.0 {
			
			formatter.roundingMode = .up // Round away from zero
			digits = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		}
		
		let formattedAmount = FormattedAmount(
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
	static func fiatFormatter() -> NumberFormatter {
		
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
		
		return formatter
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(sat: Bitcoin_kmpSatoshi, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		return formatFiat(sat: sat.toLong(), exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(sat: Int64, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatFiat(msat: msat, exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(msat: Lightning_kmpMilliSatoshi, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		return formatFiat(msat: msat.toLong(), exchangeRate: exchangeRate)
	}
	
	/// Converts from millisatoshi to a fiat amount, using the given exchange rate.
	///
	/// - Returns: A FormattedAmount struct, which contains the various string values needed for display.
	///
	static func formatFiat(msat: Int64, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		let fiatAmount = convertToFiat(msat: msat, exchangeRate: exchangeRate)
		let formatter = fiatFormatter()
		
		var digits = formatter.string(from: NSNumber(value: fiatAmount)) ?? fiatAmount.description
		
		// Zero edge-case check
		let positiveZeroDigits = formatter.string(from: NSNumber(value: 0.0)) ?? "0.00"
		let negativeZeroDigits = formatter.string(from: NSNumber(value: -0.0)) ?? "-0.00"
		
		if (digits == positiveZeroDigits || digits == negativeZeroDigits) && fiatAmount != 0.0 {
			
			formatter.roundingMode = .up // Round away from zero
			digits = formatter.string(from: NSNumber(value: fiatAmount)) ?? fiatAmount.description
		}
		
		return FormattedAmount(
			currency: Currency.fiat(exchangeRate.fiatCurrency),
			digits: digits,
			decimalSeparator: formatter.currencyDecimalSeparator
		)
	}
}
