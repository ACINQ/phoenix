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


struct PriceSliderSheet: View {
	
	let flow: FlowType
	let valueChanged: (Int64) -> Void
	
	// The Slider family works with `BinaryFloatingPoint`, thus we're using `Double` here.
	let sliderRange: ClosedRange<Double>
	@State var sliderValue: Double
	
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
	
	// There are 4 scenarios:
	//
	// 1) This is a standard lightning invoice with an amount.
	//    The range is calculated by doubling the invoice amount
	//    (i.e. the max acceptable payment according to Bolt specs).
	//    Therefore this can be thought of as a tip.
	//
	// 2) This is an amountless lightning invoice.
	//    The user typed in an amount, and then selected the tip button.
	//    So we know this is meant to be a tip.
	//    The range is calculated by doubling the invoice amount
	//    (simply to be consistent with standard tip operations).
	//
	// 3) This is a lnurlPay operation.
	//    The range is coming from the lnurlPay options.
	//    This could be a range of things, from a payment at a restaurant to a donation.
	//
	// 4) This is a lnurlWithdraw operation.
	//    The range is coming from the lnurlWithdraw options.
	//    This is typically a withdrawl from a personal account at an exchange or service.
	//
	// The UI setup:
	//
	// We always configure the slider as a percent, from 0% to X%, where X <= 100%.
	// Why have we made this decision ?
	//
	// - The most common scenario is a tip, and so it's common to make a tip based on a percent of the bill.
	//
	// - In the case of a lnurlWithdraw operation, the percentage makes it easy to quickly withdraw
	//   a fraction of your account. If the user wants to withdraw a specific amount, then they can simply
	//   type it into the amount TextField on the ValidateView screen.
	//
	// - In the case of a lnurlPay operation, it could be a donation with a very large range.
	//   In this case, the slider is probably not helpful regardless of how you configure it.
	//   Most likely the user will simply type in the amount they want to donate in the ValidateView screen.
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(
		flow: FlowType,
		msat: Int64,
		valueChanged: @escaping (Int64) -> Void
	) {
		self.flow = flow
		self.valueChanged = valueChanged
		
		switch flow {
		case .pay(let range):
			
			let dblMin = range.min.msat + range.min.msat
			if range.max.msat == dblMin {
				
				// Standard tip
				
				self.sliderRange = Double(0)...Double(100)
				
				let percent = Double(msat - range.min.msat) / Double(range.max.msat - range.min.msat)
				self._sliderValue = State<Double>(initialValue: (percent * 100))
				
			} else if range.max.msat < dblMin {
				
				// This is similar to a standard tip, but the max percent is below 100%.
				// For example, a lnurlPay for a restaurant, where the min amount is equal to the bill,
				// and the max amount includes a 30% tip.
				// So we'll still treat it like a standard tip, but with a lower max.
				
				let maxTipPercent = Double(range.max.msat - range.min.msat) / Double(range.min.msat)
				self.sliderRange = Double(0)...Double(maxTipPercent * 100)
				
				let percent = Double(msat - range.min.msat) / Double(dblMin - range.min.msat) // different
				self._sliderValue = State<Double>(initialValue: (percent * 100))
				
			} else /* range.max.msat > dblMin */ {
				
				// This is a lnurlPay operation.
				// It might be something like a donation, where the minimum is like 1 sat,
				// and the maximum is some big number.
				
				self.sliderRange = Double(0)...Double(100)
				
				let percent = Double(msat - range.min.msat) / Double(range.max.msat - range.min.msat)
				self._sliderValue = State<Double>(initialValue: (percent * 100))
			}
			
		case .withdraw(let range):
			
			self.sliderRange = Double(0)...Double(100)
			
			let percent = Double(msat - range.min.msat) / Double(range.max.msat - range.min.msat)
			self._sliderValue = State<Double>(initialValue: (percent * 100))
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Text(maxPercentString())
				.foregroundColor(.clear)
				.read(maxPercentWidthReader)
				.accessibilityHidden(true)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				header()
				content()
				footer()
			}
		}
		.assignMaxPreference(for: maxPercentWidthReader.key, to: $maxPercentWidth)
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			if isTip() {
				Text("Customize tip")
					.font(.title3)
					.accessibilityAddTraits(.isHeader)
					.accessibilitySortPriority(100)
			} else {
				Text("Customize amount")
					.font(.title3)
					.accessibilityAddTraits(.isHeader)
					.accessibilitySortPriority(100)
			}
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
		}
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
				
				let maxBitcoin = maxBitcoinAmount()
				let minBitcoin = minBitcoinAmount()
				
				let maxFiat = maxFiatAmount()
				let minFiat = minFiatAmount()
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					// Column 0: (left)
					// - Amounts in Bitcoin
					VStack(alignment: HorizontalAlignment.trailing, spacing: 40) {
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Max: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
								.accessibilityLabel("Maximum amount is \(maxBitcoin.string), ≈\(maxFiat.string)")
								.accessibilitySortPriority(94)
								.accessibilityHidden(!announceMax())
							Text(maxBitcoin.string)
								.foregroundColor(.secondary)
								.read(maxAmountWidthReader)
								.accessibilityHidden(true)
						}
						
						Text(bitcoinAmount().string)
							.accessibilityLabel(summaryLabel())
							.accessibilitySortPriority(-1)
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Min: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
								.accessibilityLabel("Minimum amount is \(minBitcoin.string), ≈\(minFiat.string)")
								.accessibilitySortPriority(95)
								.accessibilityHidden(!announceMin())
							Text(minBitcoin.string)
								.foregroundColor(.secondary)
								.frame(width: maxAmountWidth, alignment: .trailing)
								.accessibilityHidden(true)
						}
						
					} // </VStack: column 0>
					.frame(width: columnWidth)
					.read(contentHeightReader)
					
					// Column 1: (center)
					// - Vertical slider
					VSlider(value: $sliderValue, in: sliderRange, step: 1.0)
						.frame(width: vsliderWidth, height: contentHeight, alignment: .center)
						.accessibilityRepresentation {
							Slider(value: $sliderValue, in: sliderRange, step: 1.0)
						}
					
					// Column 2: (right)
					// - Amounts in fiat
					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(maxFiat.string)
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
							Text(minFiat.string)
								.foregroundColor(.secondary)
						}
					
					} // </VStack: column 2>
					.frame(width: columnWidth, height: contentHeight)
					.accessibilityHidden(true)
				
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
				.accessibilityLabel("Decrease value")
				
				Text(percentString())
					.frame(minWidth: maxPercentWidth, alignment: Alignment.center)
				
				Button {
					plusButtonTapped()
				} label: {
					Image(systemName: "plus.circle")
						.imageScale(.large)
				}
				.accessibilityLabel("Increase value")
			
			} // </HStack>
			.accessibilityHidden(true) // duplicate functionality; available via Slider
			
		} // </VStack>
		.padding()
		.assignMaxPreference(for: maxAmountWidthReader.key, to: $maxAmountWidth)
		.assignMaxPreference(for: contentHeightReader.key, to: $contentHeight)
		.onChange(of: sliderValue) {
			sliderValueChanged($0)
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		let recentTipPercents = recentTipPercents()
		if isTip() && !recentTipPercents.isEmpty {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				ForEach(recentTipPercents.indices, id: \.self) { idx in
					
					let percent = recentTipPercents[idx]
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
					.accessibilitySortPriority(Double(85 - idx))
					.accessibilityHint("Quick tip")
					
					if percent != recentTipPercents.last {
						Spacer()
					}
				} // </ForEach>
			} // </HStack>
			.padding(.top, 8)
			.padding([.leading, .trailing, .bottom])
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	/// Calculates & returns the additional amount (i.e. above range.min)
	///
	func percentToTipMsat(_ percent: Double) -> Int64 {
		
		// Reminder: percent != sliderValue
		// 0.0 <= percent <= 1.0
		
		switch flow {
		case .pay(let range):
			
			let dblMin = range.min.msat + range.min.msat
			if range.max.msat <= dblMin {
				
				// For tips:
				// - 0 % => range.min
				// - X % => treated like a tip
				
				let msat: Double = Double(range.min.msat) * percent
				return Int64(msat.rounded())
				
			} else /* range.max.msat > dblMin */ {
				
				// For lnurlPay:
				// -   0 % => range.min
				// - 100 % => range.max
				
				let diff: Int64 = range.max.msat - range.min.msat
				let msat: Double = Double(diff) * percent
				
				return Int64(msat.rounded())
			}
			
		case .withdraw(let range):
			
			// For withdraws:
			// -   0 % => range.min
			// - 100 % => range.max
			
			let diff: Int64 = range.max.msat - range.min.msat
			let msat: Double = Double(diff) * percent
			
			return Int64(msat.rounded())
		}
	}
	
	func percentToTotalMsat(_ percent: Double) -> Int64 {
		
		// Reminder: percent != sliderValue
		// 0.0 <= percent <= 1.0
		
		return flow.range.min.msat + percentToTipMsat(percent)
	}
	
	func tipMsat() -> Int64 {
		return percentToTipMsat(sliderValue / 100.0)
	}
	
	func totalMsat() -> Int64 {
		return percentToTotalMsat(sliderValue / 100.0)
	}
	
	func recentTipPercents() -> [Int] {
		
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
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func isTip() -> Bool {
		
		// Can we treat this as a tip ?
		
		switch flow {
		case .pay(let range):
			
			// There are 3 scenarios:
			//
			// 1) This is a standard lightning invoice.
			//    The range is calculated by doubling the invoice amount.
			//    So we can treat this as a tip.
			//
			// 2) This is an amountless lightning invoice.
			//    The user typed in an amount, and then selected the tip button.
			//    The range is calculated by doubling the invoice amount.
			//    So we can treat this as a tip.
			//
			// 3) This is a lnurlPay operation.
			//    The range is coming from the lnurlPay options.
			//    We can only treat this as a tip if the max is no more than double the min.
			
			return range.max.msat <= (range.min.msat + range.min.msat)
			
		case .withdraw(_):
			
			return false
		}
	}
	
	func formatBitcoinAmount(msat: Int64) -> FormattedAmount {
		return Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	func maxBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: flow.range.max.msat)
	}
	
	func bitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: totalMsat())
	}
	
	func minBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: flow.range.min.msat)
	}
	
	func formatFiatAmount(msat: Int64) -> FormattedAmount {
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: currencyPrefs.fiatCurrency) {
			return Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
		} else {
			return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
		}
	}
	
	func maxFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: flow.range.max.msat)
	}
	
	func fiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: totalMsat())
	}
	
	func minFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: flow.range.min.msat)
	}
	
	func formatPercent(_ percent: Double) -> String {
		
		// Reminder: percent != sliderValue
		// 0.0 <= percent <= 1.0
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	func maxPercentDouble() -> Double {
		
		return sliderRange.upperBound / 100.0
	}
	
	func percentDouble() -> Double {
		
		return sliderValue / 100.0
	}
	
	func minPercentDouble() -> Double {
		
		return 0.0
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
		
		// Reminder: percent != sliderValue
		// 0.0 <= percent <= 1.0
		
		if formatPercent(percent) != percentString() {
			return true
		}
		
		let newMsat = percentToTotalMsat(percent)
		
		if formatBitcoinAmount(msat: newMsat).digits != bitcoinAmount().digits {
			return true
		}
		
		if formatFiatAmount(msat: newMsat).digits != fiatAmount().digits {
			return true
		}
		
		return false
	}
	
	// --------------------------------------------------
	// MARK: Accessibility
	// --------------------------------------------------
	
	func announceMin() -> Bool {
		
		// When VoiceOver is enabled, do we announce the minimum amount ?
		//
		// Generally this makes sense if this isn't a tip.
		// For example:
		// - lnurlPay for a donation
		// - lnurlWithdraw
		
		return !isTip()
	}
	
	func announceMax() -> Bool {
		
		// When VoiceOver is enabled, do we announce the maximum amount ?
		//
		// Generally this makes sense if this isn't a tip,
		// or if the maximum tip is less than 100%.
		
		switch flow {
		case .pay(let range):
			return (range.min.msat + range.min.msat) != range.max.msat
			
		case .withdraw(_):
			return true
		}
	}
	
	func summaryLabel() -> String {
		
		let ttlMsat = totalMsat()
		let ttlBtcn = formatBitcoinAmount(msat: ttlMsat).string
		let ttlFiat = formatFiatAmount(msat: ttlMsat).string
		
		if isTip() {
			let tipMsat = tipMsat()
			let tipBtcn = formatBitcoinAmount(msat: tipMsat).string
			let tipFiat = formatFiatAmount(msat: tipMsat).string
			
			return NSLocalizedString("tip amount: \(tipBtcn), ≈\(tipFiat), total amount: \(ttlBtcn), ≈\(ttlFiat)",
				comment: "VoiceOver information: Customize tip sheet"
			)
		} else {
			return NSLocalizedString("total amount: \(ttlBtcn) ≈\(ttlFiat)",
				comment: "VoiceOver information: Customize tip sheet"
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func sliderValueChanged(_ newValue: Double) {
		log.trace("sliderValueChanged(\(newValue))")
		
		let percent = newValue / 100.0
		let msat = percentToTotalMsat(percent)
		
		log.debug("sliderValue(\(newValue)) => percent(\(percent)) => msat(\(msat))")
		valueChanged(msat)
	}
	
	func minusButtonTapped() {
		log.trace("minusButtonTapped()")
		
		var floorPercent = sliderValue.rounded(.down)

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
		
		self.sliderValue = floorPercent
	}
	
	func plusButtonTapped() {
		log.trace("plusButtonTapped()")
		
		var ceilingPercent = sliderValue.rounded(.up)

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
		
		self.sliderValue = ceilingPercent
	}
	
	func recentButtonTapped(_ percent: Int) {
		log.trace("recentButtonTapped()")
		
		if case .pay(_) = flow {

			self.sliderValue = Double(percent)
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
