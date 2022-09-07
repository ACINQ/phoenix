import Foundation
import PhoenixShared


extension Utils {
	
	// --------------------------------------------------
	// MARK: Standard Formatting
	// --------------------------------------------------
	
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
		
		switch currencyPrefs.currencyType {
		case .bitcoin:
			return formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit, policy: policy)
		case .fiat:
			let selectedFiat = currencyPrefs.fiatCurrency
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: selectedFiat) {
				return formatFiat(msat: msat, exchangeRate: exchangeRate)
			} else {
				return unknownFiatAmount(fiatCurrency: selectedFiat)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Alt Formatting
	// --------------------------------------------------
	
	static let hiddenCharacter = "\u{2217}" // asterisk operator
	
	static func hiddenAmount(_ currencyPrefs: CurrencyPrefs) -> FormattedAmount {
		
		switch currencyPrefs.currencyType {
			case .bitcoin : return hiddenBitcoinAmount(currencyPrefs)
			case .fiat    : return hiddenFiatAmount(currencyPrefs)
		}
	}
	
	static func hiddenBitcoinAmount(_ currencyPrefs: CurrencyPrefs) -> FormattedAmount {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		
		let decimalSeparator: String = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
		let digits = "\(hiddenCharacter)\(hiddenCharacter)\(hiddenCharacter)"
		
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.bitcoin(currencyPrefs.bitcoinUnit),
			digits: digits,
			decimalSeparator: decimalSeparator
		)
	}
	
	static func hiddenFiatAmount(_ currencyPrefs: CurrencyPrefs) -> FormattedAmount {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .currency
		
		let decimalSeparator: String = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
		let digits = "\(hiddenCharacter)\(hiddenCharacter)\(hiddenCharacter)"
		
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.fiat(currencyPrefs.fiatCurrency),
			digits: digits,
			decimalSeparator: decimalSeparator
		)
	}
}
