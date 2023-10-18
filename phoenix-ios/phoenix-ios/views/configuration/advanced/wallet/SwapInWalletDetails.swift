import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SwapInWalletDetails"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct SwapInWalletDetails: View {
	
	enum ViewLocation {
		case popover
		case embedded
	}
	
	let location: ViewLocation
	let popTo: (PopToDestination) -> Void
	
	@State var liquidityPolicy: LiquidityPolicy = GroupPrefs.shared.liquidityPolicy
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	let swapInRejectedPublisher = Biz.swapInRejectedPublisher
	@State var swapInRejected: Lightning_kmpLiquidityEventsRejected? = nil
	
	@State var blockchainExplorerTxid: String? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
		.navigationTitle(NSLocalizedString("Swap-in wallet", comment: "Navigation Bar Title"))
		.navigationBarTitleDisplayMode(.inline)
		.onAppear { onAppear() }
	}
	
	@ViewBuilder
	func header() -> some View {
		
		if location == .popover {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Image(systemName: "xmark")
					.imageScale(.medium)
					.font(.title3)
					.foregroundColor(.clear) // invisible
					.accessibilityHidden(true)
				
				Spacer(minLength: 0)
				Text("Swap-in wallet")
					.font(.headline)
					.fontWeight(.medium)
					.lineLimit(1)
				Spacer(minLength: 0)
				
				Button {
					closePopover()
				} label: {
					Image(systemName: "xmark")
						.imageScale(.medium)
						.font(.title3)
				}
				
			} // </HStack>
			.padding()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			if hasUnconfirmedUtxos() {
				section_unconfirmed()
			}
			section_confirmed()
			if hasTimedOutUtxos() {
				section_timedOut()
			}
			if hasCancelledUtxos() {
				section_cancelled()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(GroupPrefs.shared.liquidityPolicyPublisher) {
			liquidityPolicyChanged($0)
		}
		.onReceive(swapInWalletPublisher) {
			swapInWalletChanged($0)
		}
		.onReceive(swapInRejectedPublisher) {
			swapInRejectedStateChange($0)
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				if !liquidityPolicy.enabled {
					
					Text(
						"""
						You have **disabled** automated channel management. \
						Funds will not be swapped, and will be unavailable for spending within Phoenix.
						"""
					)
					
				} else {
					
					let (maxFee, isPercentBased) = maxSwapInFeeDetails()
					if isPercentBased {
						
						let percent = basisPointsAsPercent(liquidityPolicy.effectiveMaxFeeBasisPoints)
						Text(
							"""
							On-chain funds will automatically be swapped to Lightning if the \
							fee is **less than \(percent)** (\(maxFee.string)) of the amount.
							"""
						)
						
					} else {
						
						Text(
							"""
							On-chain funds will automatically be swapped to Lightning if the \
							fee is **less than \(maxFee.string)**.
							"""
						)
					}
				}
				
				Text("Funds not swapped **after 4 months** are recoverable on-chain")
					.font(.callout)
					.foregroundColor(.secondary)
					.padding(.bottom, 10)
				
				Button {
					navigateToLiquiditySettings()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 5) {
						Image(systemName: "gearshape.fill")
						Text("Configure fee settings")
					}
				}
				
			} // </VStack>
			.padding(.bottom, 5)

		} // </Section>
	}
	
	@ViewBuilder
	func section_unconfirmed() -> some View {
		
		Section {
			
			let utxos = unconfirmedUtxos()
			if utxos.isEmpty {
			
				Text("No pending transactions")
					.foregroundColor(.secondary)
				
			} else {
				
				ForEach(utxos) { utxo in
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						
						Text(verbatim: "\(utxo.confirmationCount) / 3")
							.monospacedDigit()
							.foregroundColor(.secondary)
							.padding(.trailing, 15)
						
						Group {
							let (btcAmt, fiatAmt) = formattedBalances(utxo.amount)
							
							Text(verbatim: "\(btcAmt.string) ") +
							Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
						}
						.padding(.trailing, 15)
						
						Spacer(minLength: 0)
						Button {
							blockchainExplorerTxid = utxo.txid
						} label: {
							Image(systemName: "link")
						}
					}
				}
			}
			
		} header: {
			Text("Waiting For 3 Confirmations")
			
		} // </Section>
		.confirmationDialog("Blockchain Explorer",
			isPresented: confirmationDialogBinding(),
			titleVisibility: .automatic
		) {
			let txid = blockchainExplorerTxid ?? ""
			Button {
				exploreTx(txid, website: BlockchainExplorer.WebsiteMempoolSpace())
			} label: {
				Text(verbatim: "Mempool.space") // no localization needed
					.textCase(.none)
			}
			Button {
				exploreTx(txid, website: BlockchainExplorer.WebsiteBlockstreamInfo())
			} label: {
				Text(verbatim: "Blockstream.info") // no localization needed
					.textCase(.none)
			}
			Button {
				copyTxId(txid)
			} label: {
				Text("Copy transaction id")
					.textCase(.none)
			}
		} // </confirmationDialog>
	}
	
	@ViewBuilder
	func section_confirmed() -> some View {
		
		Section {
			
			let (btcAmt, fiatAmt) = confirmedBalance()
			Text(verbatim: "\(btcAmt.string)") +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
			
			subsection_confirmed_lastAttempt()
			
		} header: {
			Text("Ready For Swap")
			
		} // </Section>
	}
	
	@ViewBuilder
	func subsection_confirmed_lastAttempt() -> some View {
		
		if liquidityPolicy.enabled, let lastRejection = swapInRejected {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				
				Text("Last attempt failed")
					.foregroundColor(.appWarn)
				
				if let reason = lastRejection.reason as?
						Lightning_kmpLiquidityEventsRejected.ReasonTooExpensiveOverAbsoluteFee
				{
					let actualFee = Utils.formatBitcoin(currencyPrefs, msat: lastRejection.fee)
					let maxAllowedFee = Utils.formatBitcoin(currencyPrefs, sat: reason.maxAbsoluteFee)
					
					Text("The fee was **\(actualFee.string)** but your max fee was set to **\(maxAllowedFee.string)**.")
					
				} else if let reason = lastRejection.reason as?
								Lightning_kmpLiquidityEventsRejected.ReasonTooExpensiveOverRelativeFee
				{
					let actualFee = Utils.formatBitcoin(currencyPrefs, msat: lastRejection.fee)
					let percent = basisPointsAsPercent(reason.maxRelativeFeeBasisPoints)
					
					Text("The fee was **\(actualFee.string)** which is more than **\(percent)** of the amount.")
				}
				
			} // </VStack>
		}
	}
	
	@ViewBuilder
	func section_timedOut() -> some View {
		
		Section {
			
			let (btcAmt, fiatAmt) = timedOutBalance()
			Text(verbatim: "\(btcAmt.string)") +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
			
		} header: {
			Text("Timed-Out Funds")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_cancelled() -> some View {
		
		Section {
			
			let (btcAmt, fiatAmt) = cancelledBalance()
			Text(verbatim: "\(btcAmt.string)") +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
			
			Text("These funds were not swapped in time. Use the wallet's descriptor to access them.")
				.font(.callout)
				.foregroundColor(.secondary)
			
		} header: {
			Text("Cancelled Funds")
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func maxSwapInFeeDetails() -> (FormattedAmount, Bool) {
		
		let absoluteMax: Int64 = liquidityPolicy.effectiveMaxFeeSats
		
		let swapInBalance: Int64 = swapInWallet.totalBalance.sat
		if swapInBalance > 0 {
			
			let maxPercent: Double = Double(liquidityPolicy.effectiveMaxFeeBasisPoints) / Double(10_000)
			let percentMax: Int64 = Int64(Double(swapInBalance) * maxPercent)
			
			if percentMax < absoluteMax {
				
				let formatted = Utils.formatBitcoin(currencyPrefs, sat: percentMax)
				return (formatted, true)
			}
		}
		
		let formatted = Utils.formatBitcoin(currencyPrefs, sat: absoluteMax)
		return (formatted, false)
	}
	
	func basisPointsAsPercent(_ basisPoints: Int32) -> String {
		
		// Example: 30% == 3,000 basis points
		//
		// 3,000 / 100       => 30.0 => 3000%
		// 3,000 / 100 / 100 =>  0.3 => 30%
		
		let percent = Double(basisPoints) / Double(10_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 2
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	func hasUnconfirmedUtxos() -> Bool {
		
		return !swapInWallet.weaklyConfirmed.isEmpty || !swapInWallet.unconfirmed.isEmpty
	}
	
	func hasTimedOutUtxos() -> Bool {
		
		return !swapInWallet.lockedUntilRefund.isEmpty
	}
	
	func hasCancelledUtxos() -> Bool {
		
		return !swapInWallet.readyForRefund.isEmpty
	}
	
	func unconfirmedUtxos() -> [UtxoWrapper] {
		
		let utxos = swapInWallet.weaklyConfirmed + swapInWallet.unconfirmed
		let wrappedUtxos = utxos.map { utxo in
			
			let confirmationCount = (utxo.blockHeight == 0)
			  ? 0
			  : Int64(swapInWallet.currentBlockHeight) - utxo.blockHeight + 1
			
			return UtxoWrapper(utxo: utxo, confirmationCount: confirmationCount)
		}
		
		return wrappedUtxos
	}
	
	func confirmedBalance() -> (FormattedAmount, FormattedAmount) {
		
		let sats = swapInWallet.readyForSwapWallet().deeplyConfirmedBalance
		return formattedBalances(sats)
	}
	
	func timedOutBalance() -> (FormattedAmount, FormattedAmount) {
		
		let sats = swapInWallet.lockedUntilRefundBalance
		return formattedBalances(sats)
	}
	
	func cancelledBalance() -> (FormattedAmount, FormattedAmount) {
		
		let sats = swapInWallet.readyForRefundBalance
		return formattedBalances(sats)
	}
	
	func formattedBalances(_ sats: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount) {
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (btcAmt, fiatAmt)
	}
	
	func confirmationDialogBinding() -> Binding<Bool> {
		
		return Binding( // SwiftUI only allows for 1 ".sheet"
			get: { blockchainExplorerTxid != nil },
			set: { if !$0 { blockchainExplorerTxid = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// Reserved...
	}
	
	func liquidityPolicyChanged(_ newValue: LiquidityPolicy) {
		log.trace("liquidityPolicyChanged()")
		
		self.liquidityPolicy = newValue
	}
	
	func swapInWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
	#if DEBUG
		swapInWallet = newValue.fakeBlockHeight(plus: Int32(144 * 30 * 7))
	#else
		swapInWallet = newValue
	#endif
	}
	
	func swapInRejectedStateChange(_ state: Lightning_kmpLiquidityEventsRejected?) {
		log.trace("swapInRejectedStateChange()")
		
		swapInRejected = state
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func exploreTx(_ txid: String, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txid, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copyTxId(_ txid: String) {
		log.trace("copyTxId()")
		
		UIPasteboard.general.string = txid
	}
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		popTo(.ConfigurationView(followedBy: .liquiditySettings))
		presentationMode.wrappedValue.dismiss()
	}
	
	func closePopover() {
		log.trace("closePopover")
		
		popoverState.close()
	}
}
