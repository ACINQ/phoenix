import SwiftUI
import PhoenixShared
import Popovers
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LiquidityAdsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct LiquidityAdsView: View {
	
	let liquidityOptions: [Bitcoin_kmpSatoshi] = [
		Bitcoin_kmpSatoshi(sat:    100_000),
		Bitcoin_kmpSatoshi(sat:    250_000),
		Bitcoin_kmpSatoshi(sat:    500_000),
		Bitcoin_kmpSatoshi(sat:  1_000_000),
		Bitcoin_kmpSatoshi(sat:  2_000_000),
		Bitcoin_kmpSatoshi(sat: 10_000_000)
	]
		
	@State var channels: [LocalChannelInfo] = []
	@State var balanceMsat: Int64? = nil
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var sliderValue: Double = 0
	@State var feeInfo: LiquidityFeeInfo? = nil
	@State var finalResult: Lightning_kmpChannelCommand.CommitmentSpliceResponse? = nil
	
	@State var isEstimating: Bool = false
	@State var isPurchasing: Bool = false
	@State var isPurchased: Bool = false
	@State var channelsNotAvailable: Bool = false
	
	@State var showHelpSheet = false
	
	@State var popoverPresent_minerFee = false
	@State var popoverPresent_serviceFee = false
	@State var popoverPresent_duration = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	var popoverPresent: Bool {
		return popoverPresent_minerFee || popoverPresent_serviceFee || popoverPresent_duration
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Add Liquidity")
			.navigationBarTitleDisplayMode(.inline)
			.toolbar {
				ToolbarItem(placement: .navigationBarTrailing) {
					Button {
						showHelpSheet = true
					} label: {
						Image(systemName: "questionmark.circle")
					}
				}
			}
			.sheet(isPresented: $showHelpSheet) {
				LiquidityAdsHelp(isShowing: $showHelpSheet)
			}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_teaser()
			if let finalResult {
				section_result(finalResult)
			} else {
				section_settings()
				if let feeInfo {
					section_estimatedCost(feeInfo)
				} else {
					section_estimateCostButton()
				}
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(Biz.business.peerManager.channelsPublisher()) {
			channelsChanged($0)
		}
		.onReceive(Biz.business.balanceManager.balancePublisher()) {
			balanceChanged($0)
		}
		.task {
			await fetchMempoolRecommendedFees()
		}
	}
	
	@ViewBuilder
	func section_teaser() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 18) {
				
				Image("bucket")
					.resizable()
					.scaledToFit()
					.frame(maxWidth: 100, maxHeight: 100)
				
				Text("Plan ahead to save money later")
					.font(.title3)
				
			} // </VStack>
			.frame(maxWidth: .infinity)
		} // </Section>
	}
	
	@ViewBuilder
	func section_settings() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				subsection_currentLiquidity()
					.padding(.top, 4)
					.padding(.bottom, 36)
				
				subsection_addLiquidity()
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			.background {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image("bucket")
						.resizable()
						.scaledToFill()
						.frame(width: 50, height: bucketHeight(), alignment: .trailing)
						.clipped()
						Spacer()
				}
				.opacity(0.30)
			} // </Background>
		} // </Section>
	}
	
	@ViewBuilder
	func subsection_currentLiquidity() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			Text("Current inbound liquidity:")
				.font(.headline)
				.padding(.bottom, 2)
			
			Text("(space in bucket to receive)")
				.font(.footnote)
				.foregroundColor(.secondary)
				.padding(.bottom, 8)
			
			let (remoteBtc, remoteFiat) = remoteBalance()
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Text(remoteBtc.string)
					.foregroundColor(.primary)
				
				Text(verbatim: "(≈ \(remoteFiat.string))")
					.foregroundColor(.secondary)
			} // </HStack>
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_addLiquidity() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Add liquidity:")
				.font(.headline)
				.padding(.bottom, 2)
			
			Text("(additional space in bucket)")
				.font(.footnote)
				.foregroundColor(.secondary)
				.padding(.bottom, 8)
			
			let min = Double(0)
			let max = Double(liquidityOptions.count - 1)
			
			Slider(value: $sliderValue, in: min ... max, step: 1)
				.disabled(isPurchasing || isPurchased)
				.frame(maxWidth: 175)
				.padding(.bottom, 8)
				.onChange(of: sliderValue) { _ in
					sliderValueChanged()
				}
			
			let (amtBtc, amtFiat) = selectedLiquidityAmounts()
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Text(amtBtc.string)
					.foregroundColor(.primary)
				
				Text(verbatim: "(≈ \(amtFiat.string))")
					.foregroundColor(.secondary)
			} // </HStack>
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_estimateCostButton() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 8) {
				
				Button {
					estimateCost()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 8) {
						if isEstimating {
							ProgressView().progressViewStyle(CircularProgressViewStyle())
						} else {
							Image(systemName: "magnifyingglass")
						}
						Text("Estimate liquidity cost")
					}
				}
				.font(.headline)
				.disabled(isEstimating)
				
				if channelsNotAvailable {
					Text("Channels are not available, try again later")
						.font(.callout)
						.foregroundColor(.appNegative)
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_estimatedCost(_ feeInfo: LiquidityFeeInfo) -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 20) {
				subsection_costDetails(feeInfo)
				
				Divider()
					.frame(maxWidth: 175)
				
				subsection_purchaseButton()
					.padding(.bottom, 10)
			}
			.frame(maxWidth: .infinity)
		}
	}
	
	@ViewBuilder
	func subsection_costDetails(_ feeInfo: LiquidityFeeInfo) -> some View {
		
	//	if #available(iOS 16.0, *) {
	//		subsection_costDetails_ios16(feeInfo)
	//	} else {
			subsection_costDetails_ios15(feeInfo)
	//	}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func subsection_costDetails_ios16(_ feeInfo: LiquidityFeeInfo) -> some View {
		
		Grid(horizontalSpacing: 8, verticalSpacing: 12) {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				Text("Miner Fee")
					.textCase(.uppercase)
					.font(.subheadline)
					.foregroundColor(.secondary)
					.gridColumnAlignment(.trailing)
				
				let (amtBtc, amtFiat) = formattedBalances(sats: feeInfo.estimate.minerFee)
				HStack(alignment: VerticalAlignment.top, spacing: 8) {
					VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
						Text(amtBtc.string)
						Text(verbatim: "≈ \(amtFiat.string)").foregroundColor(.secondary)
					}
					helpButton {
						popoverPresent_minerFee = true
					}
					.popover(present: $popoverPresent_minerFee) {
						InfoPopoverWindow {
							Text("Mining fee contribution for the underlying on-chain transaction.")
						}
					}
				} // </HStack>
				.font(.subheadline)
				.gridColumnAlignment(.leading)
			} // </GridRow>
			
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				Text("Service Fee")
					.textCase(.uppercase)
					.font(.subheadline)
					.foregroundColor(.secondary)
					.gridColumnAlignment(.trailing)
				
				let (amtBtc, amtFiat) = formattedBalances(
					msats: feeInfo.estimate.serviceFee,
					policy: .showMsatsIfNonZero
				)
				HStack(alignment: VerticalAlignment.top, spacing: 8) {
					VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
						Text(amtBtc.string)
						Text(verbatim: "≈ \(amtFiat.string)").foregroundColor(.secondary)
					}
					helpButton {
						popoverPresent_serviceFee = true
					}
					.popover(present: $popoverPresent_serviceFee) {
						InfoPopoverWindow {
							Text("This fee goes to the service providing the liquidity.")
						}
					}
				} // </HStack>
				.font(.subheadline)
				.gridColumnAlignment(.leading)
			} // </GridRow>
			
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				Text("Duration")
					.textCase(.uppercase)
					.font(.subheadline)
					.foregroundColor(.secondary)
					.gridColumnAlignment(.trailing)
				
				HStack(alignment: VerticalAlignment.top, spacing: 8) {
					Text("1 year")
					helpButton {
						popoverPresent_duration = true
					}
					.popover(present: $popoverPresent_duration) {
						InfoPopoverWindow {
							Text("The additional capacity will be preserved for that duration.")
						}
					}
				} // </HStack>
				.font(.subheadline)
				.gridColumnAlignment(.leading)
			} // </GridRow>
		} // </Grid>
	}
	
	@ViewBuilder
	func subsection_costDetails_ios15(_ feeInfo: LiquidityFeeInfo) -> some View {
		
		FeeInfo_Grid(
			feeInfo: feeInfo,
			popoverPresent_minerFee: $popoverPresent_minerFee,
			popoverPresent_serviceFee: $popoverPresent_serviceFee,
			popoverPresent_duration: $popoverPresent_duration
		)
	}
	
	@ViewBuilder
	func subsection_purchaseButton() -> some View {
		
		let _insufficientFunds = insufficientFunds()
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			
			Button {
				purchaseLiquidity()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 8) {
					if isPurchasing {
						ProgressView().progressViewStyle(CircularProgressViewStyle())
					} else {
						Image(systemName: "checkmark.circle")
					}
					Text("Accept")
				}
			}
			.buttonStyle(.borderless) // prevents trigger when row tapped
			.disabled(isPurchasing || popoverPresent || _insufficientFunds)
			.font(.headline)
			
			if channelsNotAvailable {
				Text("Channels are not available, try again later")
					.font(.callout)
					.foregroundColor(.appNegative)
				
			} else if _insufficientFunds {
				Text("The total fees exceed your balance")
					.font(.callout)
					.foregroundColor(.appNegative)
			}
		}
	}
	
	@ViewBuilder
	func helpButton(
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			Image(systemName: "questionmark.circle")
				.renderingMode(.template)
				.foregroundColor(.secondary)
				.font(.body)
		}
		.buttonStyle(.borderless) // prevents trigger when row tapped
	}
	
	@ViewBuilder
	func section_result(
		_ finalResult: Lightning_kmpChannelCommand.CommitmentSpliceResponse
	) -> some View {
		
		Section {
			if let _ = finalResult.asCreated() {
				section_result_created()
			} else if let failure = finalResult.asFailure() {
				section_result_failure(failure)
			}
		}
	}
	
	@ViewBuilder
	func section_result_created() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 8) {
				
				Image(systemName: "checkmark.circle")
					.renderingMode(.template)
					.font(.headline)
					.imageScale(.large)
					.foregroundColor(.appPositive)
				
				Text("Liquidity successfully added!")
					.font(.headline)
			} // </HStack>
			
			if let feeInfo {
				let (amtBtc, _) = formattedBalances(sats: feeInfo.params.amount)
				Text("You added \(amtBtc.string)")
					.font(.caption)
					.foregroundColor(.secondary)
					.padding(.top, 8)
			}
			
			Divider()
				.frame(maxWidth: 175)
				.padding(.vertical, 20)
			
			Text("New inbound liquidity:")
				.font(.headline)
				.padding(.bottom, 8)
			
			let (remoteBtc, remoteFiat) = remoteBalance()
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Text(remoteBtc.string)
					.foregroundColor(.primary)
				
				Text(verbatim: "(≈ \(remoteFiat.string))")
					.foregroundColor(.secondary)
			} // </HStack>
			
		} // </VStack>
		.frame(maxWidth: .infinity)
		.padding(.vertical, 4)
	}
	
	@ViewBuilder
	func section_result_failure(
		_ failure: Lightning_kmpChannelCommand.CommitmentSpliceResponseFailure
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 8) {
				
				Image(systemName: "x.circle")
					.renderingMode(.template)
					.font(.headline)
					.imageScale(.large)
					.foregroundColor(.appNegative)
				
				Text("Add liquidity failed!")
				
			} // </HStack>
			.font(.headline)
			.padding(.bottom, 8)
			
			Text("No funds were transferred.")
				.font(.caption)
				.foregroundColor(.secondary)
				.padding(.bottom, 8)
			
			section_result_failure_details(failure)
				.font(.caption)
				.foregroundColor(.appNegative)
		
			Divider()
				.frame(maxWidth: 175)
				.padding(.vertical, 20)
			
			Button {
				resetState()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 8) {
					Image(systemName: "eraser.fill")
					Text("Start over")
				}
			}
			.buttonStyle(.borderless) // prevents trigger when row tapped
			.foregroundColor(.appNegative)
			.font(.headline)
		}
		.frame(maxWidth: .infinity)
		.padding(.vertical, 4)
	}
	
	@ViewBuilder
	func section_result_failure_details(
		_ failure: Lightning_kmpChannelCommand.CommitmentSpliceResponseFailure
	) -> some View {
		
		Group {
			if let _ = failure.asInsufficientFunds() {
				Text("Insufficient funds")
			} else if let _ = failure.asInvalidSpliceOutPubKeyScript() {
				Text("Invalid splice-out pubKeyScript")
			} else if let _ = failure.asSpliceAlreadyInProgress() {
				Text("Splice already in progress")
			} else if let _ = failure.asChannelNotIdle() {
				Text("Channel not idle")
			} else if let _ = failure.asInvalidLiquidityAds() {
				Text("Invalid liquidity ads")
			} else if let _ = failure.asFundingFailure() {
				Text("Funding failure")
			} else if let _ = failure.asCannotStartSession() {
				Text("Cannot start session")
			} else if let _ = failure.asInteractiveTxSessionFailed() {
				Text("Interactive tx session failed")
			} else if let _ = failure.asCannotCreateCommitTx() {
				Text("Cannot create commit tx")
			} else if let _ = failure.asAbortedByPeer() {
				Text("Aborted by peer")
			} else if let _ = failure.asDisconnected() {
				Text("Disconnected")
			} else {
				Text("Unknown reason")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func formattedBalances(
		sats: Bitcoin_kmpSatoshi
	) -> (FormattedAmount, FormattedAmount) {
		
		let amtBitcoin = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let amtFiat = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (amtBitcoin, amtFiat)
	}
	
	func formattedBalances(
		msats: Lightning_kmpMilliSatoshi,
		policy: MsatsPolicy = .showMsatsIfZeroSats
	) -> (FormattedAmount, FormattedAmount) {
		
		let amtBitcoin = Utils.formatBitcoin(currencyPrefs, msat: msats, policy: policy)
		let amtFiat = Utils.formatFiat(currencyPrefs, msat: msats)
		
		return (amtBitcoin, amtFiat)
	}
	
	func remoteBalance() -> (FormattedAmount, FormattedAmount) {
		
		let remoteMsats = channels.map { channel in
			channel.availableForReceive?.msat ?? Int64(0)
		}.sum()
		
		return formattedBalances(
			msats: Lightning_kmpMilliSatoshi(msat: remoteMsats),
			policy: .showMsatsIfZeroSats
		)
	}
	
	func selectedLiquidityIndex() -> Int {
		
		var index: Int = Int(sliderValue.rounded())
		index = max(index, 0)
		index = min(index, liquidityOptions.count - 1)
		
		return index
	}
	
	func selectedLiquidityAmount() -> Bitcoin_kmpSatoshi {
		
		let idx = selectedLiquidityIndex()
		return liquidityOptions[idx]
	}
	
	func selectedLiquidityAmounts() -> (FormattedAmount, FormattedAmount) {
		
		let sats = selectedLiquidityAmount()
		
		let amtBtc = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let amtFiat = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (amtBtc, amtFiat)
	}
	
	func bucketHeight() -> CGFloat {
		
		let minHeight: CGFloat = 100
		let maxHeight: CGFloat = 200
		
		let diff = maxHeight - minHeight
		
		let idx = selectedLiquidityIndex()
		let percent = CGFloat(idx) / CGFloat(liquidityOptions.count - 1)
		
		let increment = diff * percent
		
		return minHeight + increment.rounded()
	}
	
	func insufficientFunds() -> Bool {
		
		guard
			let feeInfo,
			let balanceMsat
		else {
			return false
		}
		
		let minerMsat = Utils.toMsat(sat: feeInfo.estimate.minerFee)
		let serviceMsat = feeInfo.estimate.serviceFee.msat
		
		let totalFeeMsat = minerMsat + serviceMsat
		return totalFeeMsat > balanceMsat
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
	// MARK: Notifications
	// --------------------------------------------------
	
	func channelsChanged(_ newValue: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		
		channels = newValue
	}
	
	func balanceChanged(_ balance: Lightning_kmpMilliSatoshi?) {
		log.trace("balanceDidChange()")
		
		if let balance = balance {
			balanceMsat = balance.msat
		} else {
			balanceMsat = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func sliderValueChanged() {
		log.trace("sliderValueChanged()")
		
		// Changing the slider invalidates the current estimation (and any estimation work in-flight)
		if isEstimating {
			isEstimating = false
		}
		if feeInfo != nil {
			feeInfo = nil
		}
	}
	
	func estimateCost() {
		log.trace("estimateCost()")
		
		guard
			!isEstimating,
			let mrr = mempoolRecommendedResponse,
			let peer = Biz.business.peerManager.peerStateValue()
		else {
			return
		}
		
		let amount = selectedLiquidityAmount()
		
		let satsPerByte = Bitcoin_kmpSatoshi(sat: Int64(mrr.hourFee))
		let feePerByte = Lightning_kmpFeeratePerByte(feerate: satsPerByte)
		let feePerKw = Lightning_kmpFeeratePerKw(feeratePerByte: feePerByte)
		
		let leaseRate = NodeParamsManager.companion._liquidityLeaseRate(amount: amount)
		
		isEstimating = true
		Task { @MainActor in
			
			var pair: KotlinPair<
				Lightning_kmpFeeratePerKw,
				Lightning_kmpChannelCommand.CommitmentSpliceFees>? = nil
			
			var _channelsNotAvailable = false
			do {
				pair = try await peer._estimateFeeForInboundLiquidity(
					amount: amount,
					targetFeerate: feePerKw,
					leaseRate: leaseRate
				)
				
				if pair == nil {
					log.error("peer.estimateFeeForInboundLiquidity() == nil")
					_channelsNotAvailable = true
				}
				
			} catch {
				log.error("peer.estimateFeeForInboundLiquidity(): error: \(error)")
			}
			
			let currentAmount = self.selectedLiquidityAmount()
			if currentAmount != amount {
				// User changed slider value while we were calculating the estimated fees.
				// So the estimation is now invalid, and we can ignore it.
				return
			}
			
			if let pair = pair,
			   let feerate: Lightning_kmpFeeratePerKw = pair.first,
			   let fees: Lightning_kmpChannelCommand.CommitmentSpliceFees = pair.second
			{
				feeInfo = LiquidityFeeInfo(
					params: LiquidityFeeParams(amount: amount, feerate: feerate, leaseRate: leaseRate),
					estimate: LiquidityFeeEstimate(minerFee: fees.miningFee, serviceFee: fees.serviceFee)
				)
			}
			
			channelsNotAvailable = _channelsNotAvailable
			isEstimating = false
		} // </Task>
	}
	
	func purchaseLiquidity() {
		log.trace("purchaseLiquidity()")
		
		guard
			!isPurchasing,
			let feeInfo,
			let peer = Biz.business.peerManager.peerStateValue()
		else {
			return
		}
		
		isPurchasing = true
		Task { @MainActor in
			
			var result: Lightning_kmpChannelCommand.CommitmentSpliceResponse? = nil
			var _channelsNotAvailable = false
			do {
				result = try await peer._requestInboundLiquidity(
					amount: feeInfo.params.amount,
					feerate: feeInfo.params.feerate,
					leaseRate: feeInfo.params.leaseRate
				)
				
				if result == nil {
					log.error("peer.requestInboundLiquidity() == nil")
					_channelsNotAvailable = true
				}
				
			} catch {
				log.error("peer.requestInboundLiquidity(): error: \(error)")
			}
			
			if let result {
				finalResult = result
			} else if !_channelsNotAvailable {
				finalResult = Lightning_kmpChannelCommand.CommitmentSpliceResponseFailureDisconnected()
			}
			
			channelsNotAvailable = _channelsNotAvailable
			isPurchasing = false
			isPurchased = (result?.asCreated() != nil)
			
		} // </Task>
	}
	
	func resetState() {
		log.trace("resetState()")
		
		feeInfo = nil
		finalResult = nil
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

/**
 * Workaround for iOS 15, where `Grid` isn't available.
 */
fileprivate struct FeeInfo_Grid: InfoGridView {
	
	let feeInfo: LiquidityFeeInfo
	
	@Binding var popoverPresent_minerFee: Bool
	@Binding var popoverPresent_serviceFee: Bool
	@Binding var popoverPresent_duration: Bool
	
	// <InfoGridView Protocol>
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	@State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) {
		keyColumnSizes = value
	}
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size] {
		return keyColumnSizes
	}
	
	@State var rowSizes: [InfoGridRow_Size] = []
	func setRowSizes(_ sizes: [InfoGridRow_Size]) {
		rowSizes = sizes
	}
	func getRowSizes() -> [InfoGridRow_Size] {
		return rowSizes
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			minerFee()
			serviceFee()
			duration()
		}
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.font(.subheadline)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func minerFee() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Miner Fee")
		} valueColumn: {
			
			let (amtBtc, amtFiat) = formattedBalances(sats: feeInfo.estimate.minerFee)
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(amtBtc.string)
					Text(verbatim: "≈ \(amtFiat.string)").foregroundColor(.secondary)
				}
				helpButton {
					popoverPresent_minerFee = true
				}
				.popover(present: $popoverPresent_minerFee) {
					InfoPopoverWindow {
						Text("Mining fee contribution for the underlying on-chain transaction.")
					}
				}
			} // </HStack>
			.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func serviceFee() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Service Fee")
		} valueColumn: {
			
			let (amtBtc, amtFiat) = formattedBalances(
				msats: feeInfo.estimate.serviceFee,
				policy: .showMsatsIfNonZero
			)
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(amtBtc.string)
					Text(verbatim: "≈ \(amtFiat.string)").foregroundColor(.secondary)
				}
				helpButton {
					popoverPresent_serviceFee = true
				}
				.popover(present: $popoverPresent_serviceFee) {
					InfoPopoverWindow {
						Text("This fee goes to the service providing the liquidity.")
					}
				}
			} // </HStack>
			.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func duration() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Duration")
		} valueColumn: {
			
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				Text("1 year")
				helpButton {
					popoverPresent_duration = true
				}
				.popover(present: $popoverPresent_duration) {
					InfoPopoverWindow {
						Text("The additional capacity will be preserved for that duration.")
					}
				}
			} // </HStack>
			.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func helpButton(
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			Image(systemName: "questionmark.circle")
				.renderingMode(.template)
				.foregroundColor(.secondary)
				.font(.body)
		}
		.buttonStyle(.borderless) // prevents trigger when row tapped
	}
	
	func formattedBalances(
		sats: Bitcoin_kmpSatoshi
	) -> (FormattedAmount, FormattedAmount) {
		
		let amtBitcoin = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let amtFiat = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (amtBitcoin, amtFiat)
	}
	
	func formattedBalances(
		msats: Lightning_kmpMilliSatoshi,
		policy: MsatsPolicy
	) -> (FormattedAmount, FormattedAmount) {
		
		let amtBitcoin = Utils.formatBitcoin(currencyPrefs, msat: msats, policy: policy)
		let amtFiat = Utils.formatFiat(currencyPrefs, msat: msats)
		
		return (amtBitcoin, amtFiat)
	}
}
