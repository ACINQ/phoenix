import SwiftUI
import PhoenixShared
import Popovers
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "WalletInfoView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct WalletInfoView: View {
	
	@State var popoverPresent_swapInWallet = false
	@State var popoverPresent_finalWallet = false
	
	@State var swapIn_mpk_truncationDetected = false
	@State var final_mpk_truncationDetected = false
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			content()
			toast.view()
		}
		.navigationTitle(NSLocalizedString("Wallet info", comment: "Navigation Bar Title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_lightning()
			section_swapInWallet()
			section_finalWallet()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_lightning() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				let nodeId = Biz.nodeId ?? "?"
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text("Node id").font(.headline.bold())
					Spacer()
					copyButton(nodeId)
				}
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text(verbatim: nodeId)
						.font(.callout.weight(.light))
						.foregroundColor(.secondary)
					Spacer(minLength: 0)
					invisibleImage()
				}
				
			} // </VStack>
			
		} /*Section.*/header: {
			
			Text("Lightning")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_swapInWallet() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				let balances_confirmed = swapInBalance_confirmed()
				
				Text(balances_confirmed.0.string) +
				Text(verbatim: " ≈ \(balances_confirmed.1.string)").foregroundColor(.secondary)
				
				if let balances_unconfirmed = swapInBalance_unconfirmed() {
					
					Text("+ \(balances_unconfirmed.0.string) unconfirmed") +
					Text(verbatim: " ≈ \(balances_unconfirmed.1.string)").foregroundColor(.secondary)
				}
			} // </VStack>
			
		//	let swapInWallet = Biz.business.walletManager.getKeyManager()?.swapInOnChainWallet
			masterPublicKey(
				keyPath: "?", // swapInOnChainWallet?.path ?? "?",
				xpub: "?",    // swapInOnChainWallet?.xpub ?? "?",
				truncationDetected: $swapIn_mpk_truncationDetected
			)
			
		} /*Section.*/header: {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Swap-in wallet")
				Spacer()
				Button {
					popoverPresent_swapInWallet = true
				} label: {
					Image(systemName: "info.circle")
				}
				.foregroundColor(.secondary)
				.popover(present: $popoverPresent_swapInWallet) {
					InfoPopoverWindow {
						Text(
							"""
							An on-chain wallet derived from your seed.
							
							The swap-in wallet is a bridge to Lightning. \
							Funds on this wallet will automatically be moved to Lightning \
							according to your liquidity policy setting.
							"""
						)
					}
				}
			} // </HStack>
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_finalWallet() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				
				let balances_confirmed = finalBalance_confirmed()
				
				Text(balances_confirmed.0.string) +
				Text(verbatim: " ≈ \(balances_confirmed.1.string)").foregroundColor(.secondary)
			
				if let balances_unconfirmed = finalBalance_unconfirmed() {
					
					Text("+ \(balances_unconfirmed.0.string) unconfirmed") +
					Text(verbatim: " ≈ \(balances_unconfirmed.1.string)").foregroundColor(.secondary)
				}
			} // </VStack>
			
			let keyManager = Biz.business.walletManager.getKeyManager()
			masterPublicKey(
				keyPath: keyManager?.finalOnChainWalletPath ?? "?",
				xpub: keyManager?.finalOnChainWallet.xpub ?? "?",
				truncationDetected: $final_mpk_truncationDetected
			)
			
		} /*Section.*/header: {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Final wallet")
				Spacer()
				Button {
					popoverPresent_finalWallet = true
				} label: {
					Image(systemName: "info.circle")
				}
				.foregroundColor(.secondary)
				.popover(present: $popoverPresent_finalWallet) {
					InfoPopoverWindow {
						Text(
							"""
							An on-chain wallet derived from your seed.
							
							The final wallet is where funds are sent by default when \
							your lightning channels are closed.
							"""
						)
					}
				}
			} // </HStack>
		}
	}
	
	@ViewBuilder
	func masterPublicKey(
		keyPath: String,
		xpub: String,
		truncationDetected: Binding<Bool>
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			if !truncationDetected.wrappedValue {
				
				// All on one line:
				// Master public key (Path m/x/y/z)  <img>
				
				TruncatableView(fixedHorizontal: true, fixedVertical: true) {
					
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text("Master public key")
							.font(.headline.bold())
						Text(" (Path \(keyPath))")
							.font(.subheadline)
							.foregroundColor(.secondary)
						Spacer()
						copyButton(xpub)
					} // </HStack>
					
				} wasTruncated: {
					truncationDetected.wrappedValue = true
					
				} // </TruncatableView>
				
			} else /* if truncationDetected */ {
				
				// Too big to fit on one line => switch to two lines:
				// Master public key   <img>
				// Path: m/x/y/z
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text("Master public key")
						.font(.headline.bold())
					Spacer()
					copyButton(xpub)
				}
				
				Text("Path: \(keyPath)")
					.font(.callout)
					.foregroundColor(.secondary)
				
			} // </else>
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(xpub)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func copyButton(_ str: String) -> some View {
		
		Button {
			copyToPasteboard(str)
		} label: {
			Image(systemName: "square.on.square")
		}
		.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "square.on.square")
			.foregroundColor(.clear)
			.accessibilityHidden(true)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func swapInBalance_confirmed() -> (FormattedAmount, FormattedAmount) {
		
		let sats = Biz.business.balanceManager.swapInWalletBalanceValue().confirmed
		return formattedBalances(sats)
	}
	
	func swapInBalance_unconfirmed() -> (FormattedAmount, FormattedAmount)? {
		
		let sats = Biz.business.balanceManager.swapInWalletBalanceValue().unconfirmed
		if sats.toLong() > 0 {
			return formattedBalances(sats)
		} else {
			return nil
		}
	}
	
	func finalBalance_confirmed() -> (FormattedAmount, FormattedAmount) {
		
		let sats = Biz.business.peerManager.finalWalletBalance().confirmedBalance
		return formattedBalances(sats)
	}
	
	func finalBalance_unconfirmed() -> (FormattedAmount, FormattedAmount)? {
		
		let sats = Biz.business.peerManager.finalWalletBalance().unconfirmedBalance
		if sats.toLong() > 0 {
			return formattedBalances(sats)
		} else {
			return nil
		}
	}
	
	func formattedBalances(_ sats: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount) {
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (btcAmt, fiatAmt)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func copyToPasteboard(_ str: String) {
		log.trace("copyToPasteboard()")
		
		UIPasteboard.general.string = str
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
}
