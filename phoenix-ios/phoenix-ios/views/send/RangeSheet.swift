import SwiftUI
import PhoenixShared

fileprivate let filename = "RangeSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct RangeSheet: View {
	
	let range: MsatRange
	let valueChanged: (Int64) -> Void
	
	@State var exampleHeight: CGFloat? = nil
	enum ExampleHeight: Preference {}
	let exampleHeightReader = GeometryPreferenceReader(
		key: AppendValue<ExampleHeight>.self,
		value: { [$0.size.height] }
	)
	
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		let dismissable: Bool = smartModalState.dismissable
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Acceptable range")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(dismissable)
		} // <HStack>
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		content_ios16()
	}

	@ViewBuilder
	func content_ios16() -> some View {
		
		Grid(
			alignment: Alignment.trailing, // or use .gridColumnAlignment
			horizontalSpacing: 8,
			verticalSpacing: 16
		) {
			GridRow {
				Text("min:").bold()
				Button {
					minAmountTapped()
				} label: {
					Text(verbatim: minBitcoinAmount().string)
				}
				if let minFiatAmount = minFiatAmount() {
					Text(verbatim: "≈\(minFiatAmount.string)")
						.padding(.leading, 4)
				}
			}
			GridRow {
				Text("max:").bold()
				Button {
					maxAmountTapped()
				} label: {
					Text(verbatim: maxBitcoinAmount().string)
				}
				if let maxFiatAmount = maxFiatAmount() {
					Text(verbatim: "≈\(maxFiatAmount.string)")
						.padding(.leading, 4)
				}
			}
		}
		.padding()
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func minBitcoinAmount() -> FormattedAmount {
		
		return Utils.formatBitcoin(msat: range.min, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	func maxBitcoinAmount() -> FormattedAmount {
		
		return Utils.formatBitcoin(msat: range.max, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	func minFiatAmount() -> FormattedAmount? {
		
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			return Utils.formatFiat(msat: range.min, exchangeRate: exchangeRate)
		} else {
			return nil
		}
	}
	
	func maxFiatAmount() -> FormattedAmount? {
		
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			return Utils.formatFiat(msat: range.max, exchangeRate: exchangeRate)
		} else {
			return nil
		}
	}

	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func minAmountTapped() {
		log.trace("minAmountTraced()")
		
		valueChanged(range.min.msat)
	}
	
	func maxAmountTapped() {
		log.trace("maxAmountTraced()")
		
		valueChanged(range.max.msat)
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
	
		smartModalState.close()
	}

}
