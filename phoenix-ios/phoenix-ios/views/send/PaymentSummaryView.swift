import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentSummaryView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

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
}

struct PaymentSummaryView: View {
	
	@Binding var problem: Problem?
	
	let paymentNumbers: PaymentNumbers?
	let showMinerFeeSheet: () -> Void

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
		
		// UI Design:
		//
		//        amount  1,000 sat
		//                 0.57 usd
		//
		//           tip     30 sat  3%
		//                 0.01 usd
		//
		// lightning fee     20 sat  2%
		//                 0.01 usd
		//
		//     miner fee     20 sat  2% [bttn]
		//                 0.01 usd
		//                ---------
		//         total  1,070 sat
		//                 0.59 usd
		//
		// ^^^^^^^^^^^^^  ^^^^^^^^^  ^^^^^^^^^
		// column 1       col 2      col 3
		//
		// Note that we want column 2 to be centered on screen.
		// To achieve this, we're making column1.width == column3.width
		
		let labelColor   = info.isEmpty ? Color.clear : Color.secondary
		let bitcoinColor = info.isEmpty ? Color.clear : Color.primary
		let fiatColor    = info.isEmpty ? Color.clear : Color(UIColor.systemGray2)
		let percentColor = info.isEmpty ? Color.clear : Color.secondary
		let dividerColor = info.isEmpty ? Color.clear : Color.secondary
		
		HStack(alignment: VerticalAlignment.center, spacing: 32) {
			
			// ===== COLUMN 1 =====
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text("amount")
					.foregroundColor(labelColor)
					.fontWeight(.thin)
					.read(maxLabelWidthReader)
					.accessibilityLabel(accessibilityLabel_baseAmount(info))
					.accessibilityHidden(info.isEmpty)
				Text(verbatim: "")
					.padding(.bottom, 4)
					.accessibilityHidden(true)
				
				if info.hasTip || info.isEmpty {
					Text("tip")
						.foregroundColor(labelColor)
						.fontWeight(.thin)
						.read(maxLabelWidthReader)
						.accessibilityLabel(accessibilityLabel_tipAmount(info))
						.accessibilityHidden(info.isEmpty)
					Text(verbatim: "")
						.padding(.bottom, 4)
						.accessibilityHidden(true)
				}
				
				if info.hasLightningFee {
					Text("lightning fee")
						.foregroundColor(labelColor)
						.fontWeight(.thin)
						.read(maxLabelWidthReader)
						.accessibilityLabel(accessibilityLabel_lightningFeeAmount(info))
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
						.accessibilityLabel(accessibilityLabel_minerFeeAmount(info))
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
					.accessibilityLabel(accessibilityLabel_totalAmount(info))
					.accessibilityHidden(info.isEmpty)
				Text(verbatim: "")
					.accessibilityHidden(true)
			}
			
			// ===== COLUMN 2 =====
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
				
				if info.hasLightningFee {
					Text(verbatim: info.bitcoin_lightningFee.string)
						.foregroundColor(bitcoinColor)
						.read(maxNumberWidthReader)
					Text(verbatim: info.fiat_lightningFee.string)
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
			
			// ===== COLUMN 3 =====
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
				
				if info.hasLightningFee {
					Text(verbatim: info.percent_lightningFee)
						.foregroundColor(percentColor)
						.frame(minWidth: maxLabelWidth, alignment: .leading)
					Text(verbatim: "")
						.padding(.bottom, 4)
				}
				
				if info.hasMinerFee {
					HStack(alignment: VerticalAlignment.top, spacing: 4) {
						VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
							Text(verbatim: info.percent_minerFee)
								.foregroundColor(percentColor)
							Text(verbatim: "")
								.padding(.bottom, 4)
						} // </VStack>
						Button {
							showMinerFeeSheet()
						} label: {
							Image(systemName: "square.and.pencil").font(.body)
						}
					} // </HStack>
					.frame(minWidth: maxLabelWidth, alignment: .leading)
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
		
		// Note: All these calculations are done here, and not in init(),
		// because we need access to `currencyPrefs`.
		
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
		
		guard shouldDisplay, let nums = paymentNumbers else {
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
	
	func generalPercentString(_ value: Double) -> String {

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
	
	func lightningPercentString(_ value: Double) -> String {

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
	
	func accessibilityLabel_baseAmount(_ info: PaymentSummaryStrings) -> String {
		
		let amountBitcoin = info.bitcoin_base.string
		let amountFiat    = info.fiat_base.string
		
		return NSLocalizedString(
			"Base amount: \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_tipAmount(_ info: PaymentSummaryStrings) -> String {

		let percent       = info.percent_tip
		let amountBitcoin = info.bitcoin_tip.string
		let amountFiat    = info.fiat_tip.string
		
		return NSLocalizedString(
			"Tip amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_lightningFeeAmount(_ info: PaymentSummaryStrings) -> String {
		
		let percent       = info.percent_lightningFee
		let amountBitcoin = info.bitcoin_lightningFee.string
		let amountFiat    = info.fiat_lightningFee.string
		
		return NSLocalizedString(
			"Lightning fee amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_minerFeeAmount(_ info: PaymentSummaryStrings) -> String {
		
		let percent       = info.percent_minerFee
		let amountBitcoin = info.bitcoin_minerFee.string
		let amountFiat    = info.fiat_minerFee.string
		
		return NSLocalizedString(
			"Miner fee amount: \(percent), \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
	
	func accessibilityLabel_totalAmount(_ info: PaymentSummaryStrings) -> String {
		
		let amountBitcoin = info.bitcoin_total.string
		let amountFiat    = info.fiat_total.string
		
		return NSLocalizedString(
			"Total amount: \(amountBitcoin), ≈\(amountFiat)",
			comment: "VoiceOver label: PaymentSummaryView"
		)
	}
}
