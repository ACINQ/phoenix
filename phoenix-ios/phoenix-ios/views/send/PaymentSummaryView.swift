import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentSummaryView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct PaymentSummaryStrings {
	let bitcoin_base: FormattedAmount
	let bitcoin_tip: FormattedAmount
	let bitcoin_minerFee: FormattedAmount
	let bitcoin_total: FormattedAmount
	let fiat_base: FormattedAmount
	let fiat_tip: FormattedAmount
	let fiat_minerFee: FormattedAmount
	let fiat_total: FormattedAmount
	let percent_tip: String
	let percent_minerFee: String
	let isEmpty: Bool
	let hasTip: Bool
	let hasMinerFee: Bool
}

struct PaymentSummaryView: View {
	
	@Binding var problem: Problem?
	
	let paymentNumbers: PaymentNumbers?

	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	enum MaxLabelWidth: Preference {}
	let maxLabelWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxLabelWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxLabelWidth: CGFloat? = nil
	
	enum MaxNumberWidth: Preference {}
	let maxNumberWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxNumberWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxNumberWidth: CGFloat? = nil
	
	@ViewBuilder
	var body: some View {
		
		let info = paymentSummaryStrings()
		let labelColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		let dividerColor = info.isEmpty ? Color.clear : Color.secondary
		
		// amount   1,000 sat
		//           0.57 usd
		//
		//    tip      30 sat  3%
		//           0.01 usd
		//          ---------
		//  total   1,030 sat
		//           0.58 usd
		
		HStack(alignment: VerticalAlignment.center, spacing: 32) {
			
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text("amount")
					.foregroundColor(labelColor)
					.fontWeight(.thin)
					.read(maxLabelWidthReader)
					.accessibilityLabel(baseAmountLabel(info))
					.accessibilityHidden(info.isEmpty)
				Text(verbatim: "")
					.padding(.bottom, 4)
					.accessibilityHidden(true)
				
				if info.hasTip || info.isEmpty {
					Text("tip")
						.foregroundColor(labelColor)
						.fontWeight(.thin)
						.read(maxLabelWidthReader)
						.accessibilityLabel(tipAmountLabel(info))
						.accessibilityHidden(info.isEmpty)
					Text(verbatim: "")
						.padding(.bottom, 4)
						.accessibilityHidden(true)
				}
				
				if info.hasMinerFee {
					Text("miner fee")
						.foregroundColor(labelColor)
						.fontWeight(.thin)
						.read(maxLabelWidthReader)
						.accessibilityLabel(minerFeeAmountLabel(info))
						.accessibilityHidden(info.isEmpty)
					Text(verbatim: "")
						.padding(.bottom, 4)
						.accessibilityHidden(true)
				}
				
				Divider()
					.frame(width: 0, height: 1)
				
				Text("total")
					.fontWeight(.thin)
					.foregroundColor(labelColor)
					.read(maxLabelWidthReader)
					.accessibilityLabel(totalAmountLabel(info))
					.accessibilityHidden(info.isEmpty)
				Text(verbatim: "")
					.accessibilityHidden(true)
			}
			
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text(verbatim: info.bitcoin_base.string)
					.foregroundColor(bitcoinColor)
					.read(maxNumberWidthReader)
				Text(verbatim: info.fiat_base.string)
					.foregroundColor(fiatColor)
					.read(maxNumberWidthReader)
					.padding(.bottom, 4)
				
				if info.hasTip || info.isEmpty {
					Text(verbatim: info.bitcoin_tip.string)
						.foregroundColor(bitcoinColor)
						.read(maxNumberWidthReader)
					Text(verbatim: info.fiat_tip.string)
						.foregroundColor(fiatColor)
						.read(maxNumberWidthReader)
						.padding(.bottom, 4)
				}
				
				if info.hasMinerFee {
					Text(verbatim: info.bitcoin_minerFee.string)
						.foregroundColor(bitcoinColor)
						.read(maxNumberWidthReader)
					Text(verbatim: info.fiat_minerFee.string)
						.foregroundColor(fiatColor)
						.read(maxNumberWidthReader)
						.padding(.bottom, 4)
				}
				
				Divider()
					.foregroundColor(dividerColor)
					.frame(width: info.isEmpty ? 0 : maxNumberWidth ?? 0, height: 1)
				
				Text(verbatim: info.bitcoin_total.string)
					.foregroundColor(bitcoinColor)
					.read(maxNumberWidthReader)
				Text(verbatim: info.fiat_total.string)
					.foregroundColor(fiatColor)
					.read(maxNumberWidthReader)
			}
			.accessibilityHidden(true)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				Text(verbatim: "")
				Text(verbatim: "")
					.padding(.bottom, 4)
				
				if info.hasTip || info.isEmpty {
					Text(verbatim: info.percent_tip)
						.foregroundColor(percentColor)
						.frame(minWidth: maxLabelWidth, alignment: .leading)
					Text(verbatim: "")
						.padding(.bottom, 4)
				}
				
				if info.hasMinerFee {
					Text(verbatim: info.percent_minerFee)
						.foregroundColor(percentColor)
						.frame(minWidth: maxLabelWidth, alignment: .leading)
					Text(verbatim: "")
						.padding(.bottom, 4)
				}
				
				Divider()
					.frame(width: 0, height: 1)
				
				Text(verbatim: "")
				Text(verbatim: "")
			}
			.accessibilityHidden(true)
		}
		.assignMaxPreference(for: maxLabelWidthReader.key, to: $maxLabelWidth)
		.assignMaxPreference(for: maxNumberWidthReader.key, to: $maxNumberWidth)
		.font(.footnote)
	}
	
	func paymentSummaryStrings() -> PaymentSummaryStrings {
		
		// Note: All these calculations are done here,and not in init(),
		// because we need access to `currencyPrefs`.
		
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
		
		guard shouldDisplay, let nums = paymentNumbers else {
			let zeroBitcoin = Utils.formatBitcoin(msat: 0, bitcoinUnit: currencyPrefs.bitcoinUnit)
			let exchangeRate =  ExchangeRate.BitcoinPriceRate(
				fiatCurrency: currencyPrefs.fiatCurrency,
				price: 0.0,
				source: "",
				timestampMillis: 0
			)
			let zeroFiat = Utils.formatFiat(msat: 0, exchangeRate: exchangeRate)
			return PaymentSummaryStrings(
				bitcoin_base: zeroBitcoin,
				bitcoin_tip: zeroBitcoin,
				bitcoin_minerFee: zeroBitcoin,
				bitcoin_total: zeroBitcoin,
				fiat_base: zeroFiat,
				fiat_tip: zeroFiat,
				fiat_minerFee: zeroFiat,
				fiat_total: zeroFiat,
				percent_tip: "",
				percent_minerFee: "",
				isEmpty: true,
				hasTip: false,
				hasMinerFee: false
			)
		}
		
		let bitcoin_base = Utils.formatBitcoin(
			msat: nums.baseMsat,
			bitcoinUnit: currencyPrefs.bitcoinUnit
		)
		let bitcoin_tip = Utils.formatBitcoin(
			msat: nums.tipMsat,
			bitcoinUnit: currencyPrefs.bitcoinUnit,
			policy: .showMsatsIfZeroSats // tip can be small if amount is small
		)
		let bitcoin_minerFee = Utils.formatBitcoin(
			msat: nums.minerFeeMsat,
			bitcoinUnit: currencyPrefs.bitcoinUnit
		)
		let bitcoin_total = Utils.formatBitcoin(
			msat: nums.totalMsat,
			bitcoinUnit: currencyPrefs.bitcoinUnit
		)
		
		let fiat_base: FormattedAmount
		let fiat_tip: FormattedAmount
		let fiat_minerFee: FormattedAmount
		let fiat_total: FormattedAmount
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: currencyPrefs.fiatCurrency) {
			
			fiat_base = Utils.formatFiat(msat: nums.baseMsat, exchangeRate: exchangeRate)
			fiat_tip = Utils.formatFiat(msat: nums.tipMsat, exchangeRate: exchangeRate)
			fiat_minerFee = Utils.formatFiat(msat: nums.minerFeeMsat, exchangeRate: exchangeRate)
			fiat_total = Utils.formatFiat(msat: nums.totalMsat, exchangeRate: exchangeRate)
		} else {
			let unknownFiatAmount = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			fiat_base = unknownFiatAmount
			fiat_tip = unknownFiatAmount
			fiat_minerFee = unknownFiatAmount
			fiat_total = unknownFiatAmount
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		
		let percent_tip = formatter.string(from: NSNumber(value: nums.tipPercent)) ?? ""
		let percent_minerFee = formatter.string(from: NSNumber(value: nums.minerFeePercent)) ?? ""
		
		return PaymentSummaryStrings(
			bitcoin_base     : bitcoin_base,
			bitcoin_tip      : bitcoin_tip,
			bitcoin_minerFee : bitcoin_minerFee,
			bitcoin_total    : bitcoin_total,
			fiat_base        : fiat_base,
			fiat_tip         : fiat_tip,
			fiat_minerFee    : fiat_minerFee,
			fiat_total       : fiat_total,
			percent_tip      : percent_tip,
			percent_minerFee : percent_minerFee,
			isEmpty          : false,
			hasTip           : nums.tipMsat > 0,
			hasMinerFee      : nums.minerFeeMsat > 0
		)
	}
	
	func baseAmountLabel(_ info: PaymentSummaryStrings) -> String {
		
		let amountBitcoin = info.bitcoin_base.string
		let amountFiat    = info.fiat_base.string
		
		return NSLocalizedString(
			"Base amount: \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func tipAmountLabel(_ info: PaymentSummaryStrings) -> String {

		let percent       = info.percent_tip
		let amountBitcoin = info.bitcoin_tip.string
		let amountFiat    = info.fiat_tip.string
		
		return NSLocalizedString(
			"Tip amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func minerFeeAmountLabel(_ info: PaymentSummaryStrings) -> String {
		
		let percent       = info.percent_minerFee
		let amountBitcoin = info.bitcoin_minerFee.string
		let amountFiat    = info.fiat_minerFee.string
		
		return NSLocalizedString(
			"Miner fee amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func totalAmountLabel(_ info: PaymentSummaryStrings) -> String {
		
		let amountBitcoin = info.bitcoin_total.string
		let amountFiat    = info.fiat_total.string
		
		return NSLocalizedString(
			"Total amount: \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
}
