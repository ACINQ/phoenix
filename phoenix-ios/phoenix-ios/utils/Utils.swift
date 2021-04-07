import Foundation
import PhoenixShared

class Utils {
	
	private static let Millisatoshis_Per_Satoshi      =           1_000.0
	private static let Millisatoshis_Per_Bit          =         100_000.0
	private static let Millisatoshis_Per_Millibitcoin =     100_000_000.0
	private static let Millisatoshis_Per_Bitcoin      = 100_000_000_000.0
	
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
		return format(currencyPrefs, msat: (sat.toLong() * 1_000))
	}
	
	/// Formats the given amount of satoshis into a FormattedAmount struct,
	/// which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs, sat: Int64) -> FormattedAmount {
		return format(currencyPrefs, msat: (sat * 1_000))
	}
	
	/// Formats the given amount of millisatoshis into a FormattedAmount struct,
	/// which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs,
	                              msat: Lightning_kmpMilliSatoshi,
	                         hideMsats: Bool = true
	) -> FormattedAmount {
		return format(currencyPrefs, msat: msat.toLong(), hideMsats: hideMsats)
	}
	
	/// Formats the given amount of millisatoshis into a FormattedAmount struct,
	/// which contains the various string values needed for display.
	///
	static func format(_ currencyPrefs: CurrencyPrefs, msat: Int64, hideMsats: Bool = true) -> FormattedAmount {
		
		if currencyPrefs.currencyType == .bitcoin {
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit, hideMsats: hideMsats)
		} else {
			let selectedFiat = currencyPrefs.fiatCurrency
			let exchangeRate = currencyPrefs.fiatExchangeRates.first { rate -> Bool in
				return (rate.fiatCurrency == selectedFiat)
			}
			
			if let exchangeRate = exchangeRate {
				return formatFiat(msat: msat, exchangeRate: exchangeRate)
			} else {
				return FormattedAmount(
					digits: "?.??",
					type: selectedFiat.shortName,
					decimalSeparator: NumberFormatter().currencyDecimalSeparator
				)
			}
		}
	}
	
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
		
		formatter.roundingMode = .floor
		
		return formatter
	}

	static func formatBitcoin(sat: Int64, bitcoinUnit: BitcoinUnit) -> FormattedAmount {
		
		let msat = sat * Int64(Millisatoshis_Per_Satoshi)
		return formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
	}
	
	static func formatBitcoin(
		msat: Int64,
		bitcoinUnit: BitcoinUnit,
		hideMsats: Bool = true
	) -> FormattedAmount {
		
		let targetAmount: Double = convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
		let formatter = bitcoinFormatter(bitcoinUnit: bitcoinUnit, hideMsats: hideMsats)
		
		let digits = formatter.string(from: NSNumber(value: targetAmount)) ?? targetAmount.description
		let formattedAmount = FormattedAmount(
			digits: digits,
			type: bitcoinUnit.shortName,
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
		
		// Fiat amount should be rounded up.
		// Otherwise 1 sat (or 1msat...) = $0.00 which is not really correct.
		// It's better to display $0.01 instead.
		formatter.roundingMode = .ceiling
		
		return formatter
	}
	
	static func formatFiat(msat: Int64, exchangeRate: BitcoinPriceRate) -> FormattedAmount {
		
		let fiatAmount = convertToFiat(msat: msat, exchangeRate: exchangeRate)
		let formatter = fiatFormatter()
		
		let digits = formatter.string(from: NSNumber(value: fiatAmount)) ?? fiatAmount.description
		return FormattedAmount(
			digits: digits,
			type: exchangeRate.fiatCurrency.shortName,
			decimalSeparator: formatter.currencyDecimalSeparator
		)
	}
}
