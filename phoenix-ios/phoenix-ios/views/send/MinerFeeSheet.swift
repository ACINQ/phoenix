import SwiftUI
import PhoenixShared

fileprivate let filename = "MinerFeeSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MinerFeeSheet: View {
	
	let amount: Bitcoin_kmpSatoshi
	let btcAddress: String
	
	@Binding var minerFeeInfo: MinerFeeInfo?
	@Binding var satsPerByte: String
	@Binding var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError>
	@Binding var mempoolRecommendedResponse: MempoolRecommendedResponse?
	
	@State var explicitlySelectedPriority: MinerFeePriority? = nil
	@State var feeBelowMinimum: Bool = false
	@State var showLowMinerFeeWarning: Bool = false
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	enum Field: Hashable {
		case satsPerByteTextField
	}
	@FocusState private var focusedField: Field?
	
	enum PriorityBoxWidth: Preference {}
	let priorityBoxWidthReader = GeometryPreferenceReader(
		key: AppendValue<PriorityBoxWidth>.self,
		value: { [$0.size.width] }
	)
	@State var priorityBoxWidth: CGFloat? = nil
	
	enum FooterContentHeight: Preference {}
	let footerContentHeightReader = GeometryPreferenceReader(
		key: AppendValue<FooterContentHeight>.self,
		value: { [$0.size.height] }
	)
	@State var footerContentHeight: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		if showLowMinerFeeWarning {
			LowMinerFeeWarning(showLowMinerFeeWarning: $showLowMinerFeeWarning)
				.onAppear {
					smartModalState.dismissable = false
				}
		} else {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				header()
				content()
				footer()
			}
			.onChange(of: satsPerByte) { _ in
				satsPerByteChanged()
			}
			.onChange(of: mempoolRecommendedResponse) { _ in
				mempoolRecommendedResponseChanged()
			}
			.onAppear {
				smartModalState.dismissable = true
			}
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Miner fee")
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
			.accessibilityHidden(smartModalState.dismissable)
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
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			priorityBoxes()
				.padding(.horizontal, 8)
				.padding(.top)
				.padding(.bottom, 30)
			minerFeeFormula()
				.padding(.horizontal)
		}
	}
	
	@ViewBuilder
	func priorityBoxes() -> some View {
		
		if #available(iOS 16.0, *) {
			priorityBoxes_ios16()
		} else {
			priorityBoxes_ios15()
		}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func priorityBoxes_ios16() -> some View {
		
		ViewThatFits {
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					
				}
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBoxes_ios15() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_economy()
				priorityBox_low()
			}
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_medium()
				priorityBox_high()
			}
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBox_economy() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.none)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 1+ days")
			}
		} label: {
			Text("No Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.none),
			tapped: { priorityTapped(.none) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_low() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.low)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 1 hour")
			}
		} label: {
			Text("Low Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.low),
			tapped: { priorityTapped(.low) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_medium() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.medium)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 30 minutes")
			}
		} label: {
			Text("Medium Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.medium),
			tapped: { priorityTapped(.medium) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_high() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.high)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 10 minutes")
			}
		} label: {
			Text("High Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.high),
			tapped: { priorityTapped(.high) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func minerFeeFormula() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 12) {
			satsPerByteTextField()
			minerFeeAmounts()
		}
	}
	
	@ViewBuilder
	func satsPerByteTextField() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("", text: satsPerByteStyler().amountProxy)
					.keyboardType(.numberPad)
					.focused($focusedField, equals: .satsPerByteTextField)
					.frame(maxWidth: 40)
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(isInvalidSatsPerByte ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
			)
			
			Text(verbatim: "sats/vByte")
				.font(.callout)
				.foregroundColor(.secondary)
				.padding(.leading, 4)
		}
	}
	
	@ViewBuilder
	func minerFeeAmounts() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			let (btc, fiat) = minerFeeStrings()
			
			Text(verbatim: "= \(btc.string)")
			Text(verbatim: "≈ \(fiat.string)")
		}
		.font(.callout)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		ZStack {
			
			Button {
				reviewTransactionButtonTapped()
			} label: {
				Text("Review Transaction")
			}
			.font(.title3)
			.read(footerContentHeightReader)
			.frame(height: footerContentHeight, alignment: .bottom)
			.disabled(minerFeeInfo == nil)
			.isHidden(feeBelowMinimum)
			
			Group {
				if let mrr = mempoolRecommendedResponse {
					Text("Feerate below minimum allowed by mempool: \(satsPerByteString(mrr.minimumFee)) sats/vByte")
				} else {
					Text("Feerate below minimum allowed by mempool.")
				}
			}
			.font(.callout)
			.foregroundColor(.appNegative)
			.multilineTextAlignment(.center)
			.read(footerContentHeightReader)
			.frame(height: footerContentHeight, alignment: .bottom)
			.isHidden(!feeBelowMinimum)
		}
		.padding()
		.padding(.top)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isInvalidSatsPerByte: Bool {
		switch parsedSatsPerByte {
		case .success(_):
			return false
			
		case .failure(let reason):
			switch reason {
				case .emptyInput   : return false
				case .invalidInput : return true
			}
		}
	}
	
	func satsPerByteStyler() -> TextFieldNumberStyler {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		
		return TextFieldNumberStyler(
			formatter: formatter,
			amount: $satsPerByte,
			parsedAmount: $parsedSatsPerByte,
			userDidEdit: userDidEditSatsPerByteField
		)
	}
	
	func satsPerByte(_ priority: MinerFeePriority) -> (Double, String)? {
		
		guard let mempoolRecommendedResponse else {
			return nil
		}
		
		let doubleValue = mempoolRecommendedResponse.feeForPriority(priority)
		let stringValue = satsPerByteString(doubleValue)
		
		return (doubleValue, stringValue)
	}
	
	func satsPerByteString(_ value: Double) -> String {
		
		let nf = NumberFormatter()
		nf.numberStyle = .decimal
		nf.minimumFractionDigits = 0
		nf.maximumFractionDigits = 1
		
		return nf.string(from: NSNumber(value: value)) ?? "?"
	}
	
	func satsPerByteString(_ priority: MinerFeePriority) -> String {
		
		let tuple = satsPerByte(priority)
		return tuple?.1 ?? "?"
	}
	
	func isPriorityDisabled() -> Bool {
		
		return (mempoolRecommendedResponse == nil)
	}
	
	func isPrioritySelected(_ priority: MinerFeePriority) -> Bool {
		
		guard let mempoolRecommendedResponse else {
			return false
		}
		
		if let explicitlySelectedPriority {
			return explicitlySelectedPriority == priority
		}
			
		guard let amount = try? parsedSatsPerByte.get() else {
			return false
		}
		
		switch priority {
			case .none:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.none)
			
			case .low:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.low) &&
				       amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.none)
			
			case .medium:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.medium) &&
				       amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.high)
			
			case .high:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.high) &&
				       amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.medium)
		}
	}
	
	func minerFeeStrings() -> (FormattedAmount, FormattedAmount) {
		
		guard let minerFeeInfo else {
			let btc = Utils.unknownBitcoinAmount(bitcoinUnit: currencyPrefs.bitcoinUnit)
			let fiat = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			return (btc, fiat)
		}
		
		let btc = Utils.formatBitcoin(currencyPrefs, sat: minerFeeInfo.minerFee)
		let fiat = Utils.formatFiat(currencyPrefs, sat: minerFeeInfo.minerFee)
		return (btc, fiat)
	}
	
	func requiresLowMinerFeeWarning() -> Bool {
		
		guard
			let satsPerByte_number = try? parsedSatsPerByte.get(),
			let mrr = mempoolRecommendedResponse
		else {
			return false
		}
		
		return satsPerByte_number.doubleValue < mrr.economyFee
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func priorityTapped(_ priority: MinerFeePriority) {
		log.trace("priorityTapped()")
		
		guard let tuple = satsPerByte(priority) else {
			return
		}
		
		explicitlySelectedPriority = priority
		parsedSatsPerByte = .success(NSNumber(value: tuple.0))
		satsPerByte = tuple.1
	}
	
	func userDidEditSatsPerByteField() {
		log.trace("userDidEditSatsPerByteField()")
		
		explicitlySelectedPriority = nil
	}
	
	func satsPerByteChanged() {
		log.trace("satsPerByteChanged(): \(satsPerByte)")
		
		minerFeeInfo = nil
		guard
			let satsPerByte_number = try? parsedSatsPerByte.get(),
			let peer = Biz.business.peerManager.peerStateValue(),
			let scriptVector = Parser.shared.addressToPublicKeyScriptOrNull(chain: Biz.business.chain, address: btcAddress)
		else {
			return
		}
		
		if let mrr = mempoolRecommendedResponse, mrr.minimumFee > satsPerByte_number.doubleValue {
			feeBelowMinimum = true
			return
		} else {
			feeBelowMinimum = false
		}
		
		let originalSatsPerByte = satsPerByte
		
		let satsPerByte_satoshi = Bitcoin_kmpSatoshi(sat: satsPerByte_number.int64Value)
		let feePerByte = Lightning_kmpFeeratePerByte(feerate: satsPerByte_satoshi)
		let feePerKw = Lightning_kmpFeeratePerKw(feeratePerByte: feePerByte)
		
		Task { @MainActor in
			do {
				let pair = try await peer.estimateFeeForSpliceOut(
					amount: amount,
					scriptPubKey: scriptVector,
					targetFeerate: feePerKw
				)
				
				if let pair = pair,
				   let updatedFeePerKw: Lightning_kmpFeeratePerKw = pair.first,
				   let fees: Lightning_kmpChannelCommand.CommitmentSpliceFees = pair.second
				{
					if self.satsPerByte == originalSatsPerByte {
						self.minerFeeInfo = MinerFeeInfo(
							pubKeyScript: scriptVector,
							feerate: updatedFeePerKw,
							minerFee: fees.miningFee
						)
					}
				} else {
					log.error("Error: peer.estimateFeeForSpliceOut() == nil")
				}
				
			} catch {
				log.error("Error: \(error)")
			}
			
		} // </Task>
	}
	
	func mempoolRecommendedResponseChanged() {
		log.trace("mempoolRecommendedResponseChanged()")
		
		// The UI will change, so we need to reset the geometry measurements
		priorityBoxWidth = nil
		
		// Might need to display an error (if minimumFee increased)
		satsPerByteChanged()
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		showWarningOrClose()
	}
	
	func reviewTransactionButtonTapped() {
		log.trace("reviewTransactionButtonTapped()")
		showWarningOrClose()
	}
	
	func showWarningOrClose() {
		log.trace("showWarningOrClose()")
		
		if requiresLowMinerFeeWarning() {
			showLowMinerFeeWarning = true
		} else {
			smartModalState.close()
		}
	}
}

// MARK: -

struct LowMinerFeeWarning: View {
	
	@Binding var showLowMinerFeeWarning: Bool
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
			footer()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Low feerate!")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
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
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text(
			"""
			Transactions with insufficient feerate may linger for days or weeks without confirming.
			
			Choosing the feerate is your responsibility. \
			Once sent, this transaction cannot be cancelled, only accelerated with higer fees.
			
			Are you sure you want to proceed?
			"""
			)
		}
		.padding(.horizontal)
		.padding(.top)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: .center, spacing: 0) {
			Spacer()
			
			Button("Back") {
				cancelButtonTapped()
			}
			.padding(.trailing)
				
			Button("I understand") {
				confirmButtonTapped()
			}
		}
		.font(.title3)
		.padding()
		.padding(.top)
	}
	
	func cancelButtonTapped() {
		log.trace("[LowMinerFeeWarning] cancelButtonTapped()")
		showLowMinerFeeWarning = false
	}
	
	func confirmButtonTapped() {
		log.trace("[LowMinerFeeWarning] confirmButtonTapped()")
		smartModalState.close()
	}
}

// MARK: -

struct PriorityBoxStyle: GroupBoxStyle {
	
	let width: CGFloat?
	let disabled: Bool
	let selected: Bool
	let tapped: () -> Void
	
	func makeBody(configuration: GroupBoxStyleConfiguration) -> some View {
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			configuration.label
				.font(.headline)
			configuration.content
		}
		.frame(width: width?.advanced(by: -16.0))
		.padding(.all, 8)
		.background(RoundedRectangle(cornerRadius: 8, style: .continuous)
			.fill(Color(UIColor.quaternarySystemFill)))
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(selected ? Color.appAccent : Color(UIColor.quaternarySystemFill), lineWidth: 1)
		)
		.onTapGesture {
			tapped()
		}
	}
}
