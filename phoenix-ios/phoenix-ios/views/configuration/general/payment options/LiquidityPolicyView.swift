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
	
	@State var maxFeeAmt: String
	@State var parsedMaxFeeAmt: Result<NSNumber, TextFieldNumberStylerError>
	
	@State var maxFeePrcnt: String
	@State var parsedMaxFeePrcnt: Result<NSNumber, TextFieldNumberStylerError>
	
	@State var mode_automatic: Bool = true
	@State var mode_manual: Bool = false
	
	@State var showHelpSheet = false
	
	init() {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		let userLp = Prefs.shared.liquidityPolicy
		
		let sats = userLp.maxFeeSats ?? defaultLp.maxAbsoluteFee.sat
		maxFeeAmt = LiquidityPolicyView.formattedMaxFeeAmt(sat: sats)
		parsedMaxFeeAmt = .success(NSNumber(value: sats))
		
		let basisPoints = userLp.maxFeeBasisPoints ?? defaultLp.maxRelativeFeeBasisPoints
		let percent = Double(basisPoints) / Double(100)
		maxFeePrcnt = LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
		parsedMaxFeePrcnt = .success(NSNumber(value: percent))
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Miner Fee Policy", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_explanation()
			section_fees()
			section_mode()
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
	}
	
	@ViewBuilder
	func section_explanation() -> some View {
		
		Section {
			
			Text(styled: NSLocalizedString(
				"""
				Incoming payments sometimes require on-chain transactions. \
				This does not always happen, only when needed.
				
				All fees go to **bitcoin miners**.
				""",
				comment: "liquidity policy screen"
			))
		}
	}
	
	@ViewBuilder
	func section_fees() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				subsection_maxFeeAmount()
				subsection_maxFeePercent()
			}
			
		} /* Section.*/header: {
			
			Text("Fees")
		}
	}
	
	@ViewBuilder
	func subsection_maxFeeAmount() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			Text("Max fee amount: ")
				.padding(.trailing, 8)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					defaultMaxFeeAmt(),
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
						.foregroundColor(maxFeeAmt.isEmpty ? Color(UIColor.quaternaryLabel) : .secondary)
				}
				.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				.disabled(maxFeeAmt.isEmpty)
				
			} // </HStack>
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(maxFeeAmtHasError() ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
			)
			
		} // </HStack>
	}
	
	@ViewBuilder
	func subsection_maxFeePercent() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			Text("Max fee percent: ")
				.padding(.trailing, 8)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					defaultMaxFeePrcnt(),
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
						.foregroundColor(maxFeePrcnt.isEmpty ? Color(UIColor.quaternaryLabel) : .secondary)
				}
				.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				.disabled(maxFeePrcnt.isEmpty)
				
			} // </HStack>
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(maxFeePrcntHasError() ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
			)
			
		} // </HStack>
	}
	
	@ViewBuilder
	func section_mode() -> some View {
		
		Section {
			
			Toggle(isOn: $mode_automatic) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Automatic")
					Text(
						"""
						Incoming payments are automatically accepted as long as \
						the fee doesn't exceed the settings above.
						"""
					)
					.font(.subheadline)
					.foregroundColor(.secondary)
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: radioOnImage(),
				offImage: radioOffImage(),
				action: automaticModeOptionTapped
			))
			
			Toggle(isOn: $mode_manual) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Manual")
					Text(
						"""
						Incoming ***lightning*** payments are rejected when liquidity is insufficient. \
						You must manually manage your liquidity.
						"""
					)
					.font(.subheadline)
					.foregroundColor(.secondary)
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: radioOnImage(),
				offImage: radioOffImage(),
				action: manualModeOptionTapped
			))
			
		} /* Section.*/header: {
			Text("Mode")
		}
	}
	
	@ViewBuilder
	func checkboxOnImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
		
	@ViewBuilder
	func checkboxOffImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func radioOnImage() -> some View {
		Image(systemName: "record.circle")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func radioOffImage() -> some View {
		Image(systemName: "circle")
			.imageScale(.large)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func defaultMaxFeeSats() -> Int64 {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		return defaultLp.maxAbsoluteFee.sat
	}
	
	func defaultMaxFeeBasisPoints() -> Int32 {
		
		let defaultLp = NodeParamsManager.companion.defaultLiquidityPolicy
		return defaultLp.maxRelativeFeeBasisPoints
	}
	
	func defaultMaxFeeAmt() -> String {
		
		let sats = defaultMaxFeeSats()
		return LiquidityPolicyView.formattedMaxFeeAmt(sat: sats)
	}
	
	func defaultMaxFeePrcnt() -> String {
		
		let basisPoints = defaultMaxFeeBasisPoints()
		let percent = Double(basisPoints) / Double(100)
		return LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
	}
					 
	func currentFeeAmt() -> String {
		
		let sats: Int64
		switch parsedMaxFeeAmt {
		case .success(let number):
			sats = number.int64Value
		case .failure(_):
			sats = defaultMaxFeeSats()
		}
		
		return Utils.formatBitcoin(sat: sats, bitcoinUnit: .sat).string
	}
	
	func currentFeePrcnt() -> String {
		
		switch parsedMaxFeePrcnt {
		case .success(let number):
			let percent = number.doubleValue / 100.0
			return LiquidityPolicyView.formattedMaxFeePrcnt(percent: percent)
			
		case .failure(_):
			return defaultMaxFeePrcnt()
		}
	}
	
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
	
	func maxFeeAmtHasError() -> Bool {
		
		switch parsedMaxFeeAmt {
			case .success(_):
				return false
			case .failure(let reason):
				switch reason {
					case .emptyInput   : return false
					case .invalidInput : return true
				}
		}
	}
	
	func maxFeePrcntHasError() -> Bool {
		
		switch parsedMaxFeePrcnt {
			case .success(_):
				return false
			case .failure(let reason):
				switch reason {
					case .emptyInput   : return false
					case .invalidInput : return true
				}
		}
	}
	
	// --------------------------------------------------
	// MARK: Static Helpers
	// --------------------------------------------------
	
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
	
	func automaticModeOptionTapped() {
		log.trace("automaticModeOptionTapped()")
		
		if mode_automatic {
			mode_manual = false
		} else {
			mode_automatic = true
		}
	}
	
	func manualModeOptionTapped() {
		log.trace("manualModeOptionTapped()")
		
		if mode_manual {
			mode_automatic = false
		} else {
			mode_manual = true
		}
	}
	
	func onDisappear() {
		log.trace("onDisappear()")
		
	//	let defaultSats = defaultMaxFeeSats()
	//	let defaultBasisPoints = defaultMaxFeeBasisPoints()
		
		// Todo...
	}
}
