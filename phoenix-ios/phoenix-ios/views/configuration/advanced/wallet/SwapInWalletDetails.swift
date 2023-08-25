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
	
	@State var liquidityPolicy: LiquidityPolicy = Prefs.shared.liquidityPolicy
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	@State var blockchainExplorerTxid: String? = nil
	
	enum NavBarButtonWidth: Preference {}
	let navBarButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<NavBarButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var navBarButtonWidth: CGFloat? = nil
	
	enum IconWidth: Preference {}
	let iconWidthReader = GeometryPreferenceReader(
		key: AppendValue<IconWidth>.self,
		value: { [$0.size.width] }
	)
	@State var iconWidth: CGFloat? = nil
	
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
	}
	
	@ViewBuilder
	func header() -> some View {
		
		if location == .popover {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Image(systemName: "xmark")
					.imageScale(.medium)
					.font(.title3)
					.foregroundColor(.clear)
					.accessibilityHidden(true)
					.frame(width: navBarButtonWidth)
				
				Spacer(minLength: 0)
				Text("Swap-in wallet")
					.font(.headline)
					.fontWeight(.medium)
					.lineLimit(1)
				Spacer(minLength: 0)
				
				Button {
					closePopover()
				} label: {
					Image(systemName: "xmark") // must match size of chevron.backward above
						.imageScale(.medium)
						.font(.title3)
				}
				.read(navBarButtonWidthReader)
				.frame(width: navBarButtonWidth)
				
			} // </HStack>
			.padding()
			.assignMaxPreference(for: navBarButtonWidthReader.key, to: $navBarButtonWidth)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_confirmed()
			section_unconfirmed()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(Prefs.shared.liquidityPolicyPublisher) {
			liquidityPolicyChanged($0)
		}
		.onReceive(swapInWalletPublisher) {
			swapInWalletChanged($0)
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 20) {
				
				let maxFee = maxSwapInFee()
				Text(styled: NSLocalizedString(
					"""
					On-chain funds will automatically be swapped to Lightning if the \
					fee is **less than \(maxFee.string)**.
					""",
					comment: "Swap-in wallet details"
				))
				
				Button {
					navigateToLiquiditySettings()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 5) {
						Image(systemName: "gearshape.fill")
							.frame(minWidth: iconWidth, alignment: Alignment.leadingFirstTextBaseline)
							.read(iconWidthReader)
						Text("Configure fee settings")
					}
				}
				
			} // </VStack>
			.padding(.bottom, 5)

		} // </Section>
		.assignMaxPreference(for: iconWidthReader.key, to: $iconWidth)
	}
	
	@ViewBuilder
	func section_confirmed() -> some View {
		
		Section {
			
			let confirmed = confirmedBalance()
			Text(verbatim: "\(confirmed.0.string)") +
			Text(verbatim: " ≈ \(confirmed.1.string)").foregroundColor(.secondary)
			
		} header: {
			Text("Ready For Swap")
			
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
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func maxSwapInFee() -> FormattedAmount {
		
		let effectiveMax: Int64
		let absoluteMax: Int64 = liquidityPolicy.effectiveMaxFeeSats
		
		let swapInBalance: Int64 = swapInWallet.totalBalance.sat
		if swapInBalance > 0 {
			
			let maxPercent: Double = Double(liquidityPolicy.effectiveMaxFeeBasisPoints) / Double(10_000)
			let percentMax: Int64 = Int64(Double(swapInBalance) * maxPercent)
			
			effectiveMax = min(absoluteMax, percentMax)
			
		} else {
			
			effectiveMax = absoluteMax
		}
		
		return Utils.formatBitcoin(currencyPrefs, sat: effectiveMax)
	}
	
	func confirmedBalance() -> (FormattedAmount, FormattedAmount) {
		
		let confirmed = swapInWallet.deeplyConfirmedBalance
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: confirmed)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: confirmed)
		
		return (btcAmt, fiatAmt)
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
	
	func liquidityPolicyChanged(_ newValue: LiquidityPolicy) {
		log.trace("liquidityPolicyChanged()")
		
		self.liquidityPolicy = newValue
	}
	
	func swapInWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
		swapInWallet = newValue
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
