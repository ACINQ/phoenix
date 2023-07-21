import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentOptionsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct LiquidityPolicyView: View {
	
	@State var isEnabled: Bool
	
	@State var maxFeeAmt: String
	@State var parsedMaxFeeAmt: Result<NSNumber, TextFieldNumberStylerError>
	
	@State var maxFeePrcnt: String
	@State var parsedMaxFeePrcnt: Result<NSNumber, TextFieldNumberStylerError>
	
	@State var showAdvanced = false
	@State var showHelpSheet = false
	
	let examplePayments: [Int64] = [2_000, 8_000, 20_000, 50_000]
	@State var examplePaymentsIdx = 1
	
	@State var lightningOverride = false
	
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	enum MaxFeeAmountError: Error {
		case tooLow(sats: Int64)
		case tooHigh(sats: Int64)
		case invalidInput
	}
	
	enum MaxFeeAmtFiatHeight: Preference {}
	let maxFeeAmtFiatHeightReader = GeometryPreferenceReader(
		key: AppendValue<MaxFeeAmtFiatHeight>.self,
		value: { [$0.size.height] }
	)
	@State var maxFeeAmtFiatHeight: CGFloat? = nil
	
	enum ExampleHeight: Preference {}
	let exampleHeightReader = GeometryPreferenceReader(
		key: AppendValue<ExampleHeight>.self,
		value: { [$0.size.height] }
	)
	@State var exampleHeight: CGFloat? = nil
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	init() {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		let userLp = Prefs.shared.liquidityPolicy
		
		let __isEnabled = userLp.enabled
		
		let sats = userLp.maxFeeSats ?? defaultLp.maxAbsoluteFee.sat
		let __maxFeeAmt = LiquidityPolicyView.formattedMaxFeeAmt(sat: sats)
		let __parsedMaxFeeAmt: Result<NSNumber, TextFieldNumberStylerError> = .success(NSNumber(value: sats))
		
		let basisPoints = userLp.maxFeeBasisPoints ?? defaultLp.maxRelativeFeeBasisPoints
		let percent = Double(basisPoints) / Double(100)
		let __maxFeePrcnt = LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
		let __parsedMaxFeePrcnt: Result<NSNumber, TextFieldNumberStylerError> = .success(NSNumber(value: percent))
		
		self._isEnabled = State(initialValue: __isEnabled)
		self._maxFeeAmt = State(initialValue: __maxFeeAmt)
		self._parsedMaxFeeAmt = State(initialValue: __parsedMaxFeeAmt)
		self._maxFeePrcnt = State(initialValue: __maxFeePrcnt)
		self._parsedMaxFeePrcnt = State(initialValue: __parsedMaxFeePrcnt)
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Channel management", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_explanation()
			section_general()
			if let mempoolRecommendedResponse {
				section_estimate(mempoolRecommendedResponse)
			}
			if isEnabled {
				if showAdvanced {
					section_percentageCheck()
					section_policyOverride()
				} else {
					section_showAdvancedButton()
				}
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.toolbar {
			ToolbarItem(placement: .navigationBarTrailing) {
				Button {
					showHelpSheet = true
				} label: {
					Image(systemName: "questionmark.circle") //.imageScale(.large)
				}
			}
		}
		.sheet(isPresented: $showHelpSheet) {
			
			LiquidityPolicyHelp(isShowing: $showHelpSheet)
		}
		.onDisappear {
			onDisappear()
		}
		.task {
			await fetchMempoolRecommendedFees()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders: Sections
	// --------------------------------------------------
	
	@ViewBuilder
	func section_explanation() -> some View {
		
		Section {
			
			Text(styled: NSLocalizedString(
				"""
				Incoming payments sometimes require on-chain transactions. \
				This does not always happen, only when needed.
				""",
				comment: "liquidity policy screen"
			))
		}
	}
	
	@ViewBuilder
	func section_general() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				subsection_enabled()
				if isEnabled {
					subsection_maxFeeAmount()
				}
			}
			
		} /* Section.*/header: {
			
			Text("General")
		}
	}
	
	@ViewBuilder
	func section_estimate(_ mrr: MempoolRecommendedResponse) -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				let sat = mrr.swapEstimationFee(hasNoChannels: true)
				let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: sat)
				let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sat)
				
				Text(styled: String(format: NSLocalizedString(
					"Fees are currently estimated at around **%@** (≈ %@).",
					comment:	"Fee estimate"
				), btcAmt.string, fiatAmt.string))
			}
			
		} /* Section.*/header: {
			
			Text("Mempool")
		}
	}
	
	@ViewBuilder
	func section_showAdvancedButton() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer(minLength: 0)
				
				Button {
					showAdvanced = true
				} label: {
					HStack {
						Image(systemName: "list.bullet.clipboard")
							.imageScale(.medium)
						Text("Show advanced options")
							.font(.headline)
					}
				}
				.padding(.vertical, 5)
				
				Spacer(minLength: 0)
			} // </HStack>
		} // </Section>
	}
	
	@ViewBuilder
	func section_percentageCheck() -> some View {
		
		Section {
			subsection_maxFeePercent()
			
		} /* Section.*/header: {
			
			Text("Percentage Check")
		}
	}

	@ViewBuilder
	func section_policyOverride() -> some View {
		
		Section {
			subsection_skipFeeCheck()
			
		} /* Section.*/header: {
			
			Text("Policy Override")
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders: SubSections
	// --------------------------------------------------
	
	@ViewBuilder
	func subsection_enabled() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Toggle(isOn: $isEnabled) {
				Text("Automated channel management")
			}
			
			if !isEnabled {
				Text("Incoming payments that require on-chain operations will be rejected.")
					.font(.subheadline)
					.foregroundColor(.secondary)
					.padding(.top, 16)
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_maxFeeAmount() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("Max fee amount")
					.padding(.trailing, 16)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					TextField(
						defaultMaxFeeAmountString(),
						text: maxFeeAmtStyler().amountProxy
					)
					.keyboardType(.numberPad)
					
					Text("sat")
						.padding(.leading, 4)
						.padding(.trailing, 8)
					
					// Clear button
					Button {
						clearMaxFeeAmt()
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(
								maxFeeAmt.isEmpty ? Color(UIColor.quaternaryLabel) : Color(UIColor.tertiaryLabel)
							)
					}
					.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
					.disabled(maxFeeAmt.isEmpty)
					
				} // </HStack>
				.padding(.horizontal, 8)
				.padding(.top, 8)
				.padding(.bottom, 8.0 + ((maxFeeAmtFiatHeight ?? 12.0) / 2.0))
				.background(
					RoundedRectangle(cornerRadius: 8)
						.stroke(
							maxFeeAmountHasError() ? Color.appNegative :
							maxFeeAmountHasWarning() ? Color.appWarn : Color.textFieldBorder,
							lineWidth: 1
						)
				)
				.padding(.bottom, ((maxFeeAmtFiatHeight ?? 12.0) / 2.0))
				.overlay {
					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						Spacer(minLength: 0)
						HStack(alignment: VerticalAlignment.bottom, spacing: 0) {
							Text(verbatim: "≈ \(maxFeeAmountFiat().string)")
								.font(.footnote)
								.foregroundColor(.secondary)
								.padding(.horizontal, 4)
								.background(Color(UIColor.secondarySystemGroupedBackground))
								.padding(.leading, 8)
								.read(maxFeeAmtFiatHeightReader)
							Spacer(minLength: 0)
						} // </HStack>
					} // </VStack>
				} // </overlay>
				
			} // </HStack>
			
			Group {
				switch maxFeeAmountSats() {
				case .success(_):
					if maxFeeAmountHasWarning() {
						Text("Below the expected fee. Some payments may be rejected.")
							.foregroundColor(.secondary) // because yellow text is too hard to read
					} else {
						Text("Incoming payments whose fees exceed this value will be rejected.")
							.foregroundColor(.secondary)
					}
					
				case .failure(let reason):
					switch reason {
					case .invalidInput:
						Text("Please enter a valid amount.")
							.foregroundColor(.appNegative)
					case .tooLow(_):
						Text("Amount is too low.")
							.foregroundColor(.appNegative)
					case .tooHigh(_):
						Text("Amount is too high.")
							.foregroundColor(.appNegative)
					}
				}
			}
			.font(.subheadline)
			.padding(.top, 12)
			
		} // </VStack>
		.padding(.top, 20)
		.assignMaxPreference(for: maxFeeAmtFiatHeightReader.key, to: $maxFeeAmtFiatHeight)
	}
	
	@ViewBuilder
	func subsection_maxFeePercent() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("Max fee percent")
					.padding(.trailing, 16)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					TextField(
						defaultMaxFeePercentString(),
						text: maxFeePrcntStyler().amountProxy
					)
					.keyboardType(.numberPad)
					
					Text("%")
						.padding(.leading, 4)
						.padding(.trailing, 8)
					
					// Clear button
					Button {
						clearMaxFeePrcnt()
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(
								maxFeePrcnt.isEmpty ? Color(UIColor.quaternaryLabel) : Color(UIColor.tertiaryLabel)
							)
					}
					.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
					.disabled(maxFeePrcnt.isEmpty)
					
				} // </HStack>
				.padding(.all, 8)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(maxFeePercentHasError() ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
				)
				
			} // </HStack>
			
			Text(
				"""
				Checks the fee relative to the incoming payment amount. \
				This is helpful for small payments.
				"""
			)
			.font(.subheadline)
			.foregroundColor(.secondary)
			.padding(.top, 12)
			
			subsection_example()
				.padding(.top, 36)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_example() -> some View {
		
		// |    example payment :   10,000 sat     - + |
		// |       max absolute :    5,000 sat         |
		// |        max percent :    3,000 sat         |
		// | ----------------------------------------- |
		// |            max fee :    3,000 sat         |
		
		ZStack(alignment: Alignment.topTrailing) {
			
			GeometryReader { geometry in
				
				let column0Width: CGFloat = geometry.size.width / 4.0 * 2.0
				let column1Width: CGFloat = geometry.size.width / 4.0 * 1.25
				let column2Width: CGFloat = geometry.size.width / 4.0 * 0.75
				
				let columns: [GridItem] = [
					GridItem(.fixed(column0Width), spacing: 0, alignment: .trailing),
					GridItem(.fixed(column1Width), spacing: 0, alignment: .trailing),
					GridItem(.fixed(column2Width), spacing: 0, alignment: .leading)
				]
				
				VStack(alignment: HorizontalAlignment.center, spacing: 8) {
					
					LazyVGrid(columns: columns, spacing: 8) {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("example payment")
								.multilineTextAlignment(.trailing)
							Text(verbatim: " : ")
						}
						Text(examplePaymentAmountString())
						Text(verbatim: " ")
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("max absolute")
								.multilineTextAlignment(.trailing)
							Text(verbatim: " : ")
						}
						Text(exampleMaxAbsoluteString())
						Text(verbatim: " ")
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("max percent")
								.multilineTextAlignment(.trailing)
							Text(verbatim: " : ")
						}
						Text(exampleMaxPercentString())
						Text(verbatim: " ")
					
					} // </LazyVGrid>
					.font(.footnote)
					.foregroundColor(.secondary)
					
					Divider().frame(height: 2)
					
					LazyVGrid(columns: columns, spacing: 8) {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("max fee")
								.multilineTextAlignment(.trailing)
							Text(verbatim: " : ")
						}
						Text(exampleMaxFeeString())
						Text(verbatim: " ")
						
					} // </LazyVGrid>
					.font(.footnote)
					.foregroundColor(.secondary)
					
				} // </VStack>
				.read(exampleHeightReader)
				
			} // </GeometryReader>
			.assignMaxPreference(for: exampleHeightReader.key, to: $exampleHeight)
			.frame(height: exampleHeight)
			
			HStack(alignment: VerticalAlignment.top, /*horizontal-*/spacing: 25) {
				Button {
					decrementExamplePaymentsIdx()
				} label: {
					Text(verbatim: "-")
				}
				.buttonStyle(.borderless) // SwiftUI workaround: see explanation below
				.disabled(examplePaymentsIdx == 0)
				
				Button {
					incrementExamplePaymentsIdx()
				} label: {
					Text(verbatim: "+")
				}
				.buttonStyle(.borderless) // SwiftUI workaround: see explanation below
				.disabled(examplePaymentsIdx+1 == examplePayments.count)
				
				// Within a List, if a RowItem has a button, the default interaction is
				// a tap within any area within the RowItem will trigger the button's action.
				// If there are multiple buttons, then a tap anywhere within the RowItem
				// will trigger EVERY button's action.
				//
				// This is obviously not what we want here.
				// So we can override the default interaction by explicitly setting
				// a buttonStyle.
				
				
			} // </HStack>
			.font(.headline)
			
		} // </ZStack>
	}
	
	@ViewBuilder
	func subsection_skipFeeCheck() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Toggle(isOn: $lightningOverride) {
				Text("Skip absolute fee check for Lightning")
			}
			
			Text(
				"""
				When enabled, incoming Lightning payments will ignore the absolute max fee limit. \
				Only the percentage check will apply.
				"""
			)
			.font(.subheadline)
			.foregroundColor(.secondary)
			.padding(.top, 16)
			
			Text(
				"""
				Attention: if the Bitcoin mempool feerate is high, incoming LN payments requiring \
				an on-chain operation could be expensive.
				"""
			)
			.font(.subheadline)
			.foregroundColor(.secondary)
			.padding(.top, 16)
			
		} // </VStack>
	}
	
	// --------------------------------------------------
	// MARK: Helpers: maxFeeAmount
	// --------------------------------------------------
	
	func defaultMaxFeeAmountSats() -> Int64 {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		return defaultLp.maxAbsoluteFee.sat
	}
	
	func defaultMaxFeeAmountString() -> String {
		
		let sats = defaultMaxFeeAmountSats()
		return LiquidityPolicyView.formattedMaxFeeAmt(sat: sats)
	}
	
	func maxFeeAmountSats() -> Result<Int64, MaxFeeAmountError> {
		
		switch parsedMaxFeeAmt {
		case .success(let number):
			let sats = number.int64Value
			if sats < 0       { return .failure(.invalidInput) }
			if sats < 150     { return .failure(.tooLow(sats: sats)) }
			if sats > 500_000 { return .failure(.tooHigh(sats: sats)) }
			else              { return .success(sats) }
			
		case .failure(let reason):
			switch reason {
				case .emptyInput   : return .success(defaultMaxFeeAmountSats())
				case .invalidInput : return .failure(.invalidInput)
			}
		}
	}
	
	func maxFeeAmountHasError() -> Bool {
		
		switch maxFeeAmountSats() {
			case .success(_) : return false
			case .failure(_) : return true
		}
	}
	
	func maxFeeAmountHasWarning() -> Bool {
		
		guard let mrr = mempoolRecommendedResponse else {
			return false
		}
		
		let minSat = mrr.swapEstimationFee(hasNoChannels: true).sat
		
		switch maxFeeAmountSats() {
			case .success(let sats) : return sats < minSat
			case .failure(_)        : return false
		}
	}
	
	func maxFeeAmountFiat() -> FormattedAmount {
		
		switch maxFeeAmountSats() {
		case .success(let sats):
			return Utils.formatFiat(currencyPrefs, sat: sats)
			
		case .failure(let reason):
			switch reason {
			case .tooLow(let sats):
				// I think it makes sense to display the fiat currency amount in this situation.
				// Because we're forcing the user to enter an amount in sats...
				// And they might not be familiar with the exchange rate, so they're kinda entering values blindly.
				// If the amount is too low, it's good that they can see why (because the fiat value is tiny).
				return Utils.formatFiat(currencyPrefs, sat: sats)
				
			case .tooHigh(_):
				// We could display the fiat amount here too, but the danger is that the value may be huge.
				// Which means the UI text could easily overflow.
				// So we're going to display unknown amount instead.
				return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
				
			case .invalidInput:
				return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			}
		}
	}
	
	func effectiveMaxFeeAmountSats() -> Int64 {
		
		switch maxFeeAmountSats() {
			case .success(let sats) : return sats
			case .failure(_)        : return defaultMaxFeeAmountSats()
		}
	}
	
	func effectiveMaxFeeAmountString() -> String {
		
		let sats = effectiveMaxFeeAmountSats()
		return Utils.formatBitcoin(sat: sats, bitcoinUnit: .sat).string
	}
	
	// --------------------------------------------------
	// MARK: Helpers: maxFeePercent
	// --------------------------------------------------
	
	func defaultMaxFeeBasisPoints() -> Int32 {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		return defaultLp.maxRelativeFeeBasisPoints
	}
	
	func defaultMaxFeePercent() -> Double {
		
		let basisPoints = defaultMaxFeeBasisPoints()
		return Double(basisPoints) / Double(100)
	}
	
	func defaultMaxFeePercentString() -> String {
		
		let percent = defaultMaxFeePercent()
		return LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
	}
	
	func maxFeePercentIsValid(_ percent: Double) -> Bool {
		
		return percent > 0 && percent <= 100.0
	}
	
	func maxFeePercent() -> Double? {
		
		if case .success(let number) = parsedMaxFeePrcnt {
			let percent = number.doubleValue
			if maxFeePercentIsValid(percent) {
				return percent
			}
		}
		
		return nil
	}
	
	func maxFeeBasisPoints() -> Int32? {
		
		if let percent = maxFeePercent() {
			return Int32(percent * Double(100))
		} else {
			return nil
		}
	}
	
	func maxFeePercentHasError() -> Bool {
		
		switch parsedMaxFeePrcnt {
		case .success(let number):
			let percent = number.doubleValue
			return !maxFeePercentIsValid(percent)
			
		case .failure(let reason):
			switch reason {
				case .emptyInput   : return false
				case .invalidInput : return true
			}
		}
	}
	
	func effectiveMaxFeeBasisPoints() -> Int32 {
		
		if let basisPoints = maxFeeBasisPoints() {
			return basisPoints
		} else {
			return defaultMaxFeeBasisPoints()
		}
	}
	
	func effectiveMaxFeePercent() -> Double {
		
		return maxFeePercent() ?? defaultMaxFeePercent()
	}
	
	func effectiveMaxFeePercentString() -> String {
		
		let percent = effectiveMaxFeePercent()
		return LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
	}
	
	// --------------------------------------------------
	// MARK: Helpers: Example
	// --------------------------------------------------
	
	func examplePaymentAmountMsat() -> Lightning_kmpMilliSatoshi {
		
		let sat = examplePayments[examplePaymentsIdx]
		return Lightning_kmpMilliSatoshi(sat: Bitcoin_kmpSatoshi(sat: sat))
	}
	
	func examplePaymentAmountString() -> String {
		return Utils.formatBitcoin(msat: examplePaymentAmountMsat(), bitcoinUnit: .sat, policy: .hideMsats).string
	}
	
	func exampleMaxAbsoluteMsat() -> Lightning_kmpMilliSatoshi {
		
		let sat = effectiveMaxFeeAmountSats()
		return Lightning_kmpMilliSatoshi(sat: Bitcoin_kmpSatoshi(sat: sat))
	}
	
	func exampleMaxAbsoluteString() -> String {
		return Utils.formatBitcoin(msat: exampleMaxAbsoluteMsat(), bitcoinUnit: .sat, policy: .hideMsats).string
	}
	
	func exampleMaxPercentMsat() -> Lightning_kmpMilliSatoshi {
		
		let paymentAmount = Double(examplePaymentAmountMsat().msat)
		let percent = effectiveMaxFeePercent() / Double(100)
		
		let msat = Int64(paymentAmount * percent)
		return Lightning_kmpMilliSatoshi(msat: msat)
	}
	
	func exampleMaxPercentString() -> String {
		return Utils.formatBitcoin(msat: exampleMaxPercentMsat(), bitcoinUnit: .sat, policy: .hideMsats).string
	}
	
	func exampleMaxFee() -> Lightning_kmpMilliSatoshi {
		
		let msat1 = exampleMaxAbsoluteMsat().msat
		let msat2 = exampleMaxPercentMsat().msat
		
		let msat = min(msat1, msat2)
		return Lightning_kmpMilliSatoshi(msat: msat)
	}
	
	func exampleMaxFeeString() -> String {
		return Utils.formatBitcoin(msat: exampleMaxFee(), bitcoinUnit: .sat, policy: .hideMsats).string
	}
	
	// --------------------------------------------------
	// MARK: Helpers: Formatting
	// --------------------------------------------------
	
	func maxFeeAmtStyler() -> TextFieldNumberStyler {
		return TextFieldNumberStyler(
			formatter: LiquidityPolicyView.maxFeeAmtFormater(),
			amount: $maxFeeAmt,
			parsedAmount: $parsedMaxFeeAmt
		)
	}
	
	func maxFeePrcntStyler() -> TextFieldNumberStyler {
		return TextFieldNumberStyler(
			formatter: LiquidityPolicyView.maxFeePrcntFormater(),
			amount: $maxFeePrcnt,
			parsedAmount: $parsedMaxFeePrcnt
		)
	}
	
	static func maxFeeAmtFormater() -> NumberFormatter {
		
		let nf = NumberFormatter()
		nf.numberStyle = .decimal
		
		return nf
	}
	
	static func maxFeePrcntFormater() -> NumberFormatter {
		
		let nf = NumberFormatter()
		nf.numberStyle = .decimal
		
		return nf
	}
	
	static func formattedMaxFeeAmt(sat: Int64) -> String {
		
		let nf = maxFeeAmtFormater()
		return nf.string(from: NSNumber(value: sat)) ?? "?"
	}
	
	static func formattedMaxFeePrcnt(percent: Double) -> String {
		
		let nf = maxFeePrcntFormater()
		return nf.string(from: NSNumber(value: percent)) ?? "?"
	}
	
	// --------------------------------------------------
	// MARK: Helpers: Mempool
	// --------------------------------------------------
	
	func swapEstimationFee() -> Bitcoin_kmpSatoshi? {
		
		guard let mempoolRecommendedResponse else {
			return nil
		}
		
		return mempoolRecommendedResponse.swapEstimationFee(hasNoChannels: true)
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func fetchMempoolRecommendedFees() async {
		
		for try await response in MempoolMonitor.shared.stream() {
			mempoolRecommendedResponse = response
			if Task.isCancelled {
				return
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func clearMaxFeeAmt() {
		log.trace("clearMaxFeeAmt()")
		
		maxFeeAmt = ""
		parsedMaxFeeAmt = .failure(.emptyInput)
	}
	
	func clearMaxFeePrcnt() {
		log.trace("clearMaxFeePrcnt()")
		
		maxFeePrcnt = ""
		parsedMaxFeePrcnt = .failure(.emptyInput)
	}
	
	func decrementExamplePaymentsIdx() {
		log.trace("decrementExamplePaymentsIdx")
		
		guard examplePaymentsIdx > 0 else {
			return
		}
		examplePaymentsIdx -= 1
	}
	
	func incrementExamplePaymentsIdx() {
		log.trace("incrementExamplePaymentsIdx")
		
		guard examplePaymentsIdx + 1 < examplePayments.count else {
			return
		}
		examplePaymentsIdx += 1
	}
	
	func discardChanges() {
		log.trace("discardChanges()")
		
		// Todo...
	}
	
	func onDisappear() {
		log.trace("onDisappear()")
		
		if isEnabled {
			
			let defaultSats = defaultMaxFeeAmountSats()
			let defaultBasisPoints = defaultMaxFeeBasisPoints()
			
			let currentSats = effectiveMaxFeeAmountSats()
			let currentBasisPoints = effectiveMaxFeeBasisPoints()
			
			let sats: Int64? = (currentSats == defaultSats) ? nil : currentSats
			let basisPoints: Int32? = (currentBasisPoints == defaultBasisPoints) ? nil : currentBasisPoints
			
			log.info("updated.maxFeeSats: \(sats?.description ?? "nil")")
			log.info("updated.maxFeeBasisPoints: \(basisPoints?.description ?? "nil")")
					
			Prefs.shared.liquidityPolicy = LiquidityPolicy(
				enabled: true,
				maxFeeSats: sats,
				maxFeeBasisPoints: basisPoints,
				skipAbsoluteFeeCheck: lightningOverride
			)
			
		} else {
			
			Prefs.shared.liquidityPolicy = LiquidityPolicy(
				enabled: false,
				maxFeeSats: nil,
				maxFeeBasisPoints: nil,
				skipAbsoluteFeeCheck: LiquidityPolicy.defaultPolicy().effectiveSkipAbsoluteFeeCheck
			)
		}
	}
}
