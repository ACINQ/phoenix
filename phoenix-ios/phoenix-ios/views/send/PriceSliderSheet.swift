import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PriceSliderSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct MsatRange {
	let min: Lightning_kmpMilliSatoshi
	let max: Lightning_kmpMilliSatoshi
	
	init(min: Lightning_kmpMilliSatoshi, max: Lightning_kmpMilliSatoshi) {
		self.min = min
		self.max = max
	}
	
	init(min: Int64, max: Int64) {
		self.min = Lightning_kmpMilliSatoshi(msat: min)
		self.max = Lightning_kmpMilliSatoshi(msat: max)
	}
}

enum FlowType {
	case pay(range: MsatRange)
	case withdraw(range: MsatRange)
}

struct PriceSliderSheet: View {
	
	let flowType: FlowType
	let valueChanged: (Int64) -> Void
	
	init(flowType: FlowType, msat: Int64, valueChanged: @escaping (Int64) -> Void) {
		self.flowType = flowType
		self.valueChanged = valueChanged
		_amountSats = State(initialValue: Utils.convertBitcoin(msat: msat, to: .sat))
	}
	
	// The Slider family works with BinaryFloatingPoint.
	// So we're going to switch to `sats: Double` for simplicity.
	
	@State var amountSats: Double
	
	var range: MsatRange {
		
		switch flowType {
		case .pay(let range):
			return range
		case .withdraw(let range):
			return range
		}
	}
	
	var rangeSats: ClosedRange<Double> {
		let range = range
		let minSat: Double = Double(range.min.msat) / Utils.Millisatoshis_Per_Satoshi
		let maxSat: Double = Double(range.max.msat) / Utils.Millisatoshis_Per_Satoshi
		
		return minSat...maxSat
	}
	
	enum MaxPercentWidth: Preference {}
	let maxPercentWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxPercentWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxPercentWidth: CGFloat? = nil
	
	enum MaxAmountWidth: Preference {}
	let maxAmountWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxAmountWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxAmountWidth: CGFloat? = nil
	
	enum ContentHeight: Preference {}
	let contentHeightReader = GeometryPreferenceReader(
		key: AppendValue<ContentHeight>.self,
		value: { [$0.size.height] }
	)
	@State var contentHeight: CGFloat? = nil
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Text(maxPercentString())
				.foregroundColor(.clear)
				.read(maxPercentWidthReader)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text("Customize amount")
						.font(.title3)
					Spacer()
					Button {
						closeButtonTapped()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
				}
				.padding(.horizontal)
				.padding(.vertical, 8)
				.background(
					Color(UIColor.secondarySystemBackground)
						.cornerRadius(15, corners: [.topLeft, .topRight])
				)
				.padding(.bottom, 4)
				
				content.padding()
				footer
			}
		}
		.assignMaxPreference(for: maxPercentWidthReader.key, to: $maxPercentWidth)
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 20) {
			
			GeometryReader { proxy in
				
				// We have 3 columns:
				//
				// | bitcoin prices | vslider | fiat prices |
				//
				// We want:
				// - column 0 & 2 to be exactly the same width
				// - column 1 to be perfectly centered
				
				let vsliderWidth = CGFloat(50)
				let columnWidth = (proxy.size.width - vsliderWidth) / CGFloat(2)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					// Column 0: (left)
					// - Amounts in Bitcoin
					VStack(alignment: HorizontalAlignment.trailing, spacing: 40) {
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Max: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(maxBitcoinAmount().string)
								.foregroundColor(.secondary)
								.read(maxAmountWidthReader)
						}
						
						Text(bitcoinAmount().string)
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Min: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(minBitcoinAmount().string)
								.foregroundColor(.secondary)
								.frame(width: maxAmountWidth, alignment: .trailing)
						}
						
					} // </VStack: column 0>
					.frame(width: columnWidth)
					.read(contentHeightReader)
					
					// Column 1: (center)
					// - Vertical slider
					VSlider(value: $amountSats, in: rangeSats) { value in
						log.debug("VSlider.onEditingChanged")
					}
					.frame(width: vsliderWidth, height: contentHeight, alignment: .center)
					
					// Column 2: (right)
					// - Amounts in fiat
					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(maxFiatAmount().string)
								.foregroundColor(.secondary)
						}
						Spacer()
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(fiatAmount().string)
						}
						Spacer()
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(minFiatAmount().string)
								.foregroundColor(.secondary)
						}
					
					} // </VStack: column 2>
					.frame(width: columnWidth, height: contentHeight)
				
				} // </HStack>
				
			} // </GeometryReader>
			.frame(height: contentHeight)
			
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				
				Button {
					minusButtonTapped()
				} label: {
					Image(systemName: "minus.circle")
						.imageScale(.large)
				}
				Text(percentString())
					.frame(minWidth: maxPercentWidth, alignment: Alignment.center)
				Button {
					plusButtonTapped()
				} label: {
					Image(systemName: "plus.circle")
						.imageScale(.large)
				}
			
			} // </HStack>
			
		} // </VStack>
		.assignMaxPreference(for: maxAmountWidthReader.key, to: $maxAmountWidth)
		.assignMaxPreference(for: contentHeightReader.key, to: $contentHeight)
		.onChange(of: amountSats) {
			valueChanged(Utils.toMsat(from: $0, bitcoinUnit: .sat))
		}
	}
	
	@ViewBuilder
	var footer: some View {
		
		let recentPercents = recentPercents()
		if case .pay(_) = flowType, !recentPercents.isEmpty {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				ForEach(recentPercents, id: \.self) { percent in
					Button {
						recentButtonTapped(percent)
					} label: {
						Text(verbatim: "\(percent)%")
							.padding(.vertical, 6)
							.padding(.horizontal, 12)
					}
					.buttonStyle(ScaleButtonStyle(
						cornerRadius: 16,
						backgroundFill: Color(UIColor.systemGroupedBackground), // secondarySystemBackground
						borderStroke: Color.appAccent
					))
					if percent != recentPercents.last {
						Spacer()
					}
				} // </ForEach>
			} // </HStack>
			.padding(.top, 8)
			.padding([.leading, .trailing, .bottom])
		}
	}
	
	func msat() -> Int64 {
		
		return Utils.toMsat(from: amountSats, bitcoinUnit: .sat)
	}
	
	func formatBitcoinAmount(msat: Int64) -> FormattedAmount {
		return Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	func maxBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: range.max.msat)
	}
	
	func bitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: msat())
	}
	
	func minBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: range.min.msat)
	}
	
	func formatFiatAmount(msat: Int64) -> FormattedAmount {
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: currencyPrefs.fiatCurrency) {
			return Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
		} else {
			return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
		}
	}
	
	func maxFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: range.max.msat)
	}
	
	func fiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: msat())
	}
	
	func minFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: range.min.msat)
	}
	
	func formatPercent(_ percent: Double) -> String {
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	func percentToMsat(_ percent: Double) -> Int64 {
		
		switch flowType {
		case .pay(_):
			
			// For outgoing payments:
			// - min => base amount
			// - anything above min is treated like a tip
			
			return Int64(Double(range.min.msat) * (1.0 + percent))
			
		case .withdraw(_):
			
			// For withdraws:
			// - max => treated like 100% of user's balance
			// - anything below min is a percent of user's balance
			
			return Int64(Double(range.max.msat) * percent)
		}
	}
	
	func maxPercentDouble() -> Double {
		
		switch flowType {
		case .pay(_):
			
			// For outgoing payments:
			// - min => base amount
			// - anything above min is treated like a tip
			
			let minMsat = range.min.msat
			let maxMsat = range.max.msat
			
			return Double(maxMsat - minMsat) / Double(minMsat)
			
		case .withdraw(_):
			
			// For withdraws:
			// - max => treated like 100% of user's balance
			// - anything below min is a percent of user's balance
			
			return 1.0
		}
	}
	
	func percentDouble() -> Double {
		
		switch flowType {
		case .pay(_):
			
			// For outgoing payments:
			// - min => base amount
			// - anything above min is treated like a tip
			
			let minMsat = range.min.msat
			let curMsat = msat()
			
			return Double(curMsat - minMsat) / Double(minMsat)
			
		case .withdraw(_):
			
			// For withdraws:
			// - max => treated like 100% of user's balance
			// - anything below min is a percent of user's balance
			
			let maxMsat = range.max.msat
			let curMsat = msat()
			
			return Double(curMsat) / Double(maxMsat)
		}
	}
	
	func minPercentDouble() -> Double {
		
		switch flowType {
		case .pay(_):
			
			// For outgoing payments:
			// - min => base amount
			// - anything above min is treated like a tip
			
			return 0.0
			
		case .withdraw(_):
			
			// For withdraws:
			// - max => treated like 100% of user's balance
			// - anything below min is a percent of user's balance
			
			let maxMsat = range.max.msat
			let minMsat = range.min.msat
			
			return Double(minMsat) / Double(maxMsat)
		}
	}
	
	func maxPercentString() -> String {
		return formatPercent(maxPercentDouble())
	}
	
	func percentString() -> String {
		return formatPercent(percentDouble())
	}
	
	func minPercentString() -> String {
		return formatPercent(minPercentDouble())
	}
	
	func willUserInterfaceChange(percent: Double) -> Bool {
		
		if formatPercent(percent) != percentString() {
			return true
		}
		
		let newMsat = percentToMsat(percent)
		
		if formatBitcoinAmount(msat: newMsat).digits != bitcoinAmount().digits {
			return true
		}
		
		if formatFiatAmount(msat: newMsat).digits != fiatAmount().digits {
			return true
		}
		
		return false
	}
	
	func minusButtonTapped() {
		log.trace("minusButtonTapped()")
		
		var floorPercent = (percentDouble() * 100.0).rounded(.down)
		
		// The previous percent may have been something like "8.7%".
		// And the new percent may be "8%".
		//
		// The question is, if we change the percent to "8%",
		// does this create any kind of change in the UI.
		//
		// If the answer is YES, then it's a valid change.
		// If the answer is NO, then we should drop another percentage point.
		
		if !willUserInterfaceChange(percent: (floorPercent / 100.0)) {
			floorPercent -= 1.0
		}
		
		let minPercent = minPercentDouble() * 100.0
		if floorPercent < minPercent {
			floorPercent = minPercent
		}
		
		let newMsat = percentToMsat(floorPercent / 100.0)
		amountSats = Utils.convertBitcoin(msat: newMsat, to: .sat)
	}
	
	func plusButtonTapped() {
		log.trace("plusButtonTapped()")
		
		var ceilingPercent = (percentDouble() * 100.0).rounded(.up)
		
		// The previous percent may have been something like "8.7%".
		// And the new percent may be "9%".
		//
		// The question is, if we change the percent to "9%",
		// does this create any kind of change in the UI.
		//
		// If the answer is YES, then it's a valid change.
		// If the answer is NO, then we should add another percentage point.
		
		if !willUserInterfaceChange(percent: (ceilingPercent / 100.0)) {
			ceilingPercent += 1.0
		}
		
		let maxPercent = maxPercentDouble() * 100.0
		if ceilingPercent > maxPercent {
			ceilingPercent = maxPercent
		}
		
		let newMsat = percentToMsat(ceilingPercent / 100.0)
		amountSats = Utils.convertBitcoin(msat: newMsat, to: .sat)
	}
	
	func recentPercents() -> [Int] {
		
		// Most recent item is at index 0
		var recents = Prefs.shared.recentTipPercents
		
		// Remove items outside the valid range
		let minPercent = Int(minPercentDouble() * 100.0)
		let maxPercent = Int(maxPercentDouble() * 100.0)
		recents = recents.filter { ($0 >= minPercent) && ($0 <= maxPercent) }
		
		// Trim to most recent 3 items
		let targetCount = 3
		recents = Array(recents.prefix(targetCount))
		
		// Add default values (if needed/possible)
		let defaults = [10, 15, 20].filter { ($0 >= minPercent) && ($0 <= maxPercent) }
		
		if recents.isEmpty {
			recents.append(contentsOf: defaults)
		} else if recents.count < targetCount {
			
			// The default list is [10, 15, 20]
			// But what if the user's first tip is 5%, what should the new list be ?
			//
			// The most helpful results will be those numbers that are
			// closest to the user's own picks.
			//
			// Thus:
			// - if the user's first pick is 5  : [5, 10, 15]
			// - if the user's first pick is 12 : [10, 12, 15]
			// - if the user's first pick is 18 : [15, 18, 20]
			// - if the user's first pick is 25 : [15, 20, 25]
			//
			// We can use a similar logic if recents.count == 2
			
			var extras = defaults
			repeat {
				
				let diffs = extras.map { defaultValue in
					recents.map { recentValue in
						return abs(defaultValue - recentValue)
					}.sum()
				}
				
				if let minDiff = diffs.min(), let minIdx = diffs.firstIndex(of: minDiff) {
					
					let defaultValue = extras.remove(at: minIdx)
					if !recents.contains(defaultValue) {
						recents.append(defaultValue)
					}
				}
				
			} while recents.count < targetCount && !extras.isEmpty
		}
		
		return recents.sorted()
	}
	
	func recentButtonTapped(_ percent: Int) {
		log.trace("recentButtonTapped()")
		
		if case .pay(_) = flowType {
			
			// For outgoing payments:
			// - min => base amount
			// - anything above min is treated like a tip
			
			let newMsat = percentToMsat(Double(percent) / 100.0)
			amountSats = Utils.convertBitcoin(msat: newMsat, to: .sat)
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
