import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RangeSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
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
			.accessibilityHidden(smartModalState.currentItem?.dismissable ?? false)
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
		
		if #available(iOS 16.0, *) {
			content_ios16()
		} else {
			content_pre16()
		}
	}

	@ViewBuilder
	@available(iOS 16.0, *)
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

	@ViewBuilder
	func content_pre16() -> some View {
		
		GeometryReader { geometry in
			
			let column0Width: CGFloat = geometry.size.width / 5.0 * 1.0
			let column1Width: CGFloat = geometry.size.width / 5.0 * 2.0
			let column2Width: CGFloat = geometry.size.width / 5.0 * 2.0
			
			let columns: [GridItem] = [
				GridItem(.fixed(column0Width), spacing: 0, alignment: .trailing),
				GridItem(.fixed(column1Width), spacing: 0, alignment: .trailing),
				GridItem(.fixed(column2Width), spacing: 0, alignment: .trailing)
			]
			
			LazyVGrid(columns: columns, spacing: 0) {
				
				Text("min:").bold()
				Button {
					minAmountTapped()
				} label: {
					Text(verbatim: minBitcoinAmount().string)
				}
				if let minFiatAmount = minFiatAmount() {
					Text(verbatim: "≈\(minFiatAmount.string)")
						.padding(.leading, 4)
				} else {
					Text(verbatim: " ")
				}
				
				Text("max:").bold()
				Button {
					maxAmountTapped()
				} label: {
					Text(verbatim: maxBitcoinAmount().string)
				}
				if let maxFiatAmount = maxFiatAmount() {
					Text(verbatim: "≈\(maxFiatAmount.string)")
						.padding(.leading, 4)
				} else {
					Text(verbatim: " ")
				}
			}
			.read(exampleHeightReader)
			
		} // </GeometryReader>
		.assignMaxPreference(for: exampleHeightReader.key, to: $exampleHeight)
		.frame(height: exampleHeight)
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
