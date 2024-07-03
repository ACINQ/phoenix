import Foundation
import PhoenixShared


struct PaymentSummary {
	let baseMsat: Int64
	let tipMsat: Int64
	let lightningFeeMsat: Int64
	let minerFeeMsat: Int64
	let totalMsat: Int64
	let tipPercent: Double
	let lightningFeePercent: Double
	let minerFeePercent: Double
}

struct PaymentSummaryStrings {
	let bitcoin_base: FormattedAmount
	let bitcoin_tip: FormattedAmount
	let bitcoin_lightningFee: FormattedAmount
	let bitcoin_minerFee: FormattedAmount
	let bitcoin_total: FormattedAmount
	let fiat_base: FormattedAmount
	let fiat_tip: FormattedAmount
	let fiat_lightningFee: FormattedAmount
	let fiat_minerFee: FormattedAmount
	let fiat_total: FormattedAmount
	let percent_tip: String
	let percent_lightningFee: String
	let percent_minerFee: String
	let isEmpty: Bool
	let hasTip: Bool
	let hasLightningFee: Bool
	let hasMinerFee: Bool
	
	static func create(
		from source: PaymentSummary?,
		currencyPrefs: CurrencyPrefs,
		problem: Problem?
	) -> PaymentSummaryStrings {
		
		let bitcoinUnit = currencyPrefs.bitcoinUnit
		let fiatCurrency = currencyPrefs.fiatCurrency
		
		let shouldDisplay: Bool
		if let problem = problem {
			switch problem {
				case .emptyInput: shouldDisplay = false
				case .invalidInput: shouldDisplay = false
				case .amountOutOfRange: shouldDisplay = true // problem might be the tip
				case .amountExceedsBalance: shouldDisplay = true // problem might be the tip
				case .finalAmountExceedsBalance: shouldDisplay = true // problem is miner fee
			}
		} else {
			shouldDisplay = true
		}
		
		guard shouldDisplay, let nums = source else {
			let zeroBitcoin = Utils.formatBitcoin(msat: 0, bitcoinUnit: bitcoinUnit)
			let exchangeRate =  ExchangeRate.BitcoinPriceRate(
				fiatCurrency: fiatCurrency,
				price: 0.0,
				source: "",
				timestampMillis: 0
			)
			let zeroFiat = Utils.formatFiat(msat: 0, exchangeRate: exchangeRate)
			return PaymentSummaryStrings(
				bitcoin_base: zeroBitcoin,
				bitcoin_tip: zeroBitcoin,
				bitcoin_lightningFee: zeroBitcoin,
				bitcoin_minerFee: zeroBitcoin,
				bitcoin_total: zeroBitcoin,
				fiat_base: zeroFiat,
				fiat_tip: zeroFiat,
				fiat_lightningFee: zeroFiat,
				fiat_minerFee: zeroFiat,
				fiat_total: zeroFiat,
				percent_tip: "",
				percent_lightningFee: "",
				percent_minerFee: "",
				isEmpty: true,
				hasTip: false,
				hasLightningFee: false,
				hasMinerFee: false
			)
		} // </guard>
		
		let bitcoin_base = Utils.formatBitcoin(msat: nums.baseMsat, bitcoinUnit: bitcoinUnit)
		let bitcoin_tip = Utils.formatBitcoin(
			msat: nums.tipMsat,
			bitcoinUnit: bitcoinUnit,
			policy: .showMsatsIfZeroSats // tip can be tiny if amount is small
		)
		let bitcoin_lightningFee = Utils.formatBitcoin(
			msat: nums.lightningFeeMsat,
			bitcoinUnit: bitcoinUnit,
			policy: .showMsatsIfZeroSats // fee can be tiny if amount is small
		)
		let bitcoin_minerFee = Utils.formatBitcoin(msat: nums.minerFeeMsat, bitcoinUnit: bitcoinUnit)
		let bitcoin_total = Utils.formatBitcoin(msat: nums.totalMsat, bitcoinUnit: bitcoinUnit)
		
		let fiat_base: FormattedAmount
		let fiat_tip: FormattedAmount
		let fiat_lightningFee: FormattedAmount
		let fiat_minerFee: FormattedAmount
		let fiat_total: FormattedAmount
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
			
			fiat_base = Utils.formatFiat(msat: nums.baseMsat, exchangeRate: exchangeRate)
			fiat_tip = Utils.formatFiat(msat: nums.tipMsat, exchangeRate: exchangeRate)
			fiat_lightningFee = Utils.formatFiat(msat: nums.lightningFeeMsat, exchangeRate: exchangeRate)
			fiat_minerFee = Utils.formatFiat(msat: nums.minerFeeMsat, exchangeRate: exchangeRate)
			fiat_total = Utils.formatFiat(msat: nums.totalMsat, exchangeRate: exchangeRate)
		} else {
			let unknownFiatAmount = Utils.unknownFiatAmount(fiatCurrency: fiatCurrency)
			fiat_base = unknownFiatAmount
			fiat_tip = unknownFiatAmount
			fiat_lightningFee = unknownFiatAmount
			fiat_minerFee = unknownFiatAmount
			fiat_total = unknownFiatAmount
		}
		
		let percent_tip = generalPercentString(nums.tipPercent)
		let percent_lightningFee = lightningPercentString(nums.lightningFeePercent)
		let percent_minerFee = generalPercentString(nums.minerFeePercent)
		
		return PaymentSummaryStrings(
			bitcoin_base         : bitcoin_base,
			bitcoin_tip          : bitcoin_tip,
			bitcoin_lightningFee : bitcoin_lightningFee,
			bitcoin_minerFee     : bitcoin_minerFee,
			bitcoin_total        : bitcoin_total,
			fiat_base            : fiat_base,
			fiat_tip             : fiat_tip,
			fiat_lightningFee    : fiat_lightningFee,
			fiat_minerFee        : fiat_minerFee,
			fiat_total           : fiat_total,
			percent_tip          : percent_tip,
			percent_lightningFee : percent_lightningFee,
			percent_minerFee     : percent_minerFee,
			isEmpty              : false,
			hasTip               : nums.tipMsat > 0,
			hasLightningFee      : nums.lightningFeeMsat > 0,
			hasMinerFee          : nums.minerFeeMsat > 0
		)
	}
	
	private static func generalPercentString(_ value: Double) -> String {

		let formatter = NumberFormatter()
		formatter.numberStyle = .percent

		// When the value is small, we show a fraction digit for better accuracy.
		// This also avoids showing a value such as "0%", and instead showing "0.4%".
		if value < 0.0095 {
			formatter.minimumFractionDigits = 1
			formatter.maximumFractionDigits = 1
			formatter.roundingMode = .up
		}
		
		return formatter.string(from: NSNumber(value: value)) ?? ""
	}
	
	private static func lightningPercentString(_ value: Double) -> String {

		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 1
		formatter.maximumFractionDigits = 1
		formatter.roundingMode = .halfUp
		
		// The actual lightning fee is: 4 sats + 0.4%
		// But this often gets communicated as simply "0.4%"...
		// So we want to be more specific when the payment amount is smaller.
		
		if value < 0.0041 { // if amount > 40,000 sats
			return formatter.string(from: NSNumber(value: value)) ?? ""
			
		} else {
			let percentStr = formatter.string(from: NSNumber(value: 0.004)) ?? ""
			let flatStr = Utils.formatBitcoin(sat: 4, bitcoinUnit: .sat)
			return "\(percentStr) + \(flatStr.string)"
		}
	}
}
