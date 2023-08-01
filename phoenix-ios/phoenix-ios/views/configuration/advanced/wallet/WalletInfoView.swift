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
	
	let popTo: (PopToDestination) -> Void
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@State var popoverPresent_swapInWallet = false
	@State var popoverPresent_finalWallet = false
	
	@State var final_mpk_truncationDetected = false
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
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
		.onAppear() {
			onAppear()
		}
		.onReceive(swapInWalletPublisher) {
			swapInWalletChanged($0)
		}
		.onReceive(finalWalletPublisher) {
			finalWalletChanged($0)
		}
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
	
	// --------------------------------------------------
	// MARK: View Builders: SwapInWallet
	// --------------------------------------------------
	
	@ViewBuilder
	func section_swapInWallet() -> some View {
		
		Section {
			
			NavigationLink(destination: SwapInWalletDetails(popTo: popToWrapper)) {
				subsection_swapInWallet_balance()
			}
			subsection_swapInWallet_descriptor()
			
		} header: {
			subsection_swapInWallet_header()
			
		} // </Section>
	}
	
	@ViewBuilder
	func subsection_swapInWallet_header() -> some View {
		
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
	}
	
	@ViewBuilder
	func subsection_swapInWallet_balance() -> some View {
		
		balances(
			confirmed: swapInBalance_confirmed(),
			unconfirmed: swapInBalance_unconfirmed()
		)
	}
	
	@ViewBuilder
	func subsection_swapInWallet_descriptor() -> some View {
		
		let keyManager = Biz.business.walletManager.getKeyManager()
		let descriptor = keyManager?.swapInOnChainWallet.descriptor ?? "?"
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Descriptor")
					.font(.headline.bold())
				Spacer()
				copyButton(descriptor)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(descriptor)
					.lineLimit(2)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	// --------------------------------------------------
	// MARK: View Builders: FinalWallet
	// --------------------------------------------------
	
	@ViewBuilder
	func section_finalWallet() -> some View {
		
		Section {
			
			NavigationLink(destination: FinalWalletDetails()) {
				subsection_finalWallet_balance()
			}
			subsection_finalWallet_masterPublicKey()
			
		} header: {
			subsection_finalWallet_header()
			
		} // </Section>
	}
	
	@ViewBuilder
	func subsection_finalWallet_header() -> some View {
		
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
	
	@ViewBuilder
	func subsection_finalWallet_balance() -> some View {
		
		balances(
			confirmed: finalBalance_confirmed(),
			unconfirmed: finalBalance_unconfirmed()
		)
	}
	
	@ViewBuilder
	func subsection_finalWallet_masterPublicKey() -> some View {
		
		let keyManager = Biz.business.walletManager.getKeyManager()
		let keyPath = keyManager?.finalOnChainWalletPath ?? "?"
		let xpub = keyManager?.finalOnChainWallet.xpub ?? "?"
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			if !final_mpk_truncationDetected {
				
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
					final_mpk_truncationDetected = true
					
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
					.lineLimit(2)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	// --------------------------------------------------
	// MARK: View Builders: Utils
	// --------------------------------------------------
	
	@ViewBuilder
	func balances(
		confirmed   : Bitcoin_kmpSatoshi,
		unconfirmed : Bitcoin_kmpSatoshi
	) -> some View {
		
		let hasPositiveConfirmed = confirmed.sat > 0
		let hasPositiveUnconfirmed = unconfirmed.sat > 0
		
		if hasPositiveConfirmed && hasPositiveUnconfirmed {
		
		/*	This looks a bit crowded...
			I think it looks cleaner if we simply display the total in this scenario.
			If the user wants more information, there's the SwapInWalletDetails screen.
		 
			if #available(iOS 16, *) {
				Grid(horizontalSpacing: 8, verticalSpacing: 12) {
					GridRow(alignment: VerticalAlignment.firstTextBaseline) {
						Text("confirmed")
							.textCase(.lowercase)
							.font(.subheadline)
							.foregroundColor(.secondary)
							.gridColumnAlignment(.trailing)
						
						Text(verbatim: confirmed.0.string) +
						Text(verbatim: " ≈ \(confirmed.1.string)").foregroundColor(.secondary)
					}
					GridRow(alignment: VerticalAlignment.firstTextBaseline) {
						Text("unconfirmed")
							.textCase(.lowercase)
							.font(.subheadline)
							.foregroundColor(.secondary)
							.gridColumnAlignment(.trailing)
						
						Text(verbatim: unconfirmed.0.string) +
						Text(verbatim: " ≈ \(unconfirmed.1.string)").foregroundColor(.secondary)
					}
				} // </Grid>
			} else {
				VStack(alignment: HorizontalAlignment.leading, spacing: 12) {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 8) {
						Text("confirmed")
							.textCase(.lowercase)
							.font(.subheadline)
							.foregroundColor(.secondary)
						
						Text(verbatim: confirmed.0.string) +
						Text(verbatim: " ≈ \(confirmed.1.string)").foregroundColor(.secondary)
					}
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 8) {
						Text("unconfirmed")
							.textCase(.lowercase)
							.font(.subheadline)
							.foregroundColor(.secondary)
						
						Text(verbatim: unconfirmed.0.string) +
						Text(verbatim: " ≈ \(unconfirmed.1.string)").foregroundColor(.secondary)
					}
				} // </VStack>
			}
		*/
			
			let total = confirmed.sat + unconfirmed.sat
			let (btcAmt, fiatAmt) = formattedBalances(total)
			
			Text(verbatim: "\(btcAmt.string) ") +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
			
		} else if hasPositiveUnconfirmed {
			
			let (btcAmt, fiatAmt) = formattedBalances(unconfirmed)
			
			Text(verbatim: "\(btcAmt.string) ") +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
			
		} else {
			
			let (btcAmt, fiatAmt) = formattedBalances(confirmed)
			
			Text(verbatim: btcAmt.string) +
			Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
		}
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
	
	func swapInBalance_confirmed() -> Bitcoin_kmpSatoshi {
		
		return swapInWallet.deeplyConfirmedBalance
	}
	
	func swapInBalance_unconfirmed() -> Bitcoin_kmpSatoshi {
		
		// In the context of the swap-in wallet, any tx that is weakly confirmed (< 3 confirmations),
		// is still considered "pending" for the purposes of swapping it to lightning.
		
		let sats = swapInWallet.weaklyConfirmedBalance.sat + swapInWallet.unconfirmedBalance.sat
		return Bitcoin_kmpSatoshi(sat: sats)
	}
	
	func finalBalance_confirmed() -> Bitcoin_kmpSatoshi {
		
		return finalWallet.anyConfirmedBalance
	}
	
	func finalBalance_unconfirmed() -> Bitcoin_kmpSatoshi {
		
		return finalWallet.unconfirmedBalance
	}
	
	func formattedBalances(_ sats: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount) {
		
		return formattedBalances(sats.toLong())
	}
	
	func formattedBalances(_ sats: Int64) -> (FormattedAmount, FormattedAmount) {
		
		let btcAmt = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sats)
		
		return (btcAmt, fiatAmt)
	}
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper()")
		
		popToDestination = destination
		popTo(destination)
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
		} else {
			
			if let destination = popToDestination {
				log.debug("popToDestination: \(destination)")
				popToDestination = nil
				presentationMode.wrappedValue.dismiss()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func swapInWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
		swapInWallet = newValue
	}
	
	func finalWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("finalWalletChanged()")
		
		finalWallet = newValue
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
