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
	// MARK: Bitcoin Formatting
	// --------------------------------------------------
	
	static func formatBitcoin(
		_ currencyPrefs : CurrencyPrefs,
		sat             : Bitcoin_kmpSatoshi
	) -> FormattedAmount {
		
		return formatBitcoin(sat: sat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	static func formatBitcoin(
		_ currencyPrefs : CurrencyPrefs,
		sat             : Int64
	) -> FormattedAmount {
		
		return formatBitcoin(sat: sat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	// --------------------------------------------------
	// MARK: Fiat Formatting
	// --------------------------------------------------
	
	static func formatFiat(
		_ currencyPrefs : CurrencyPrefs,
		sat             : Bitcoin_kmpSatoshi
	) -> FormattedAmount {
		
		return formatFiat(currencyPrefs, sat: sat.toLong())
	}
	
	static func formatFiat(
		_ currencyPrefs : CurrencyPrefs,
		sat             : Int64
	) -> FormattedAmount {
		
		let msat = toMsat(sat: sat)
		return formatFiat(currencyPrefs, msat: msat)
	}
	
	static func formatFiat(
		_ currencyPrefs : CurrencyPrefs,
		msat            : Lightning_kmpMilliSatoshi
	) -> FormattedAmount {
		
		return formatFiat(currencyPrefs, msat: msat.toLong())
	}
	
	static func formatFiat(
		_ currencyPrefs : CurrencyPrefs,
		msat            : Int64
	) -> FormattedAmount {
		
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			return formatFiat(msat: msat, exchangeRate: exchangeRate)
		} else {
			return unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
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
		
		let digits = "\(hiddenCharacter)\(hiddenCharacter)\(hiddenCharacter)"
		
		let formatter = bitcoinFormatter(bitcoinUnit: currencyPrefs.bitcoinUnit)
		let decimalSeparator: String = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
		
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.bitcoin(currencyPrefs.bitcoinUnit),
			digits: digits,
			decimalSeparator: decimalSeparator
		)
	}
	
	static func hiddenFiatAmount(_ currencyPrefs: CurrencyPrefs) -> FormattedAmount {
		
		let digits = "\(hiddenCharacter)\(hiddenCharacter)\(hiddenCharacter)"
		
		let formatter = fiatFormatter(fiatCurrency: currencyPrefs.fiatCurrency)
		let decimalSeparator: String = formatter.currencyDecimalSeparator ?? formatter.decimalSeparator ?? "."
		
		return FormattedAmount(
			amount: 0.0,
			currency: Currency.fiat(currencyPrefs.fiatCurrency),
			digits: digits,
			decimalSeparator: decimalSeparator
		)
	}
}
