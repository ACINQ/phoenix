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

fileprivate enum NavLinkTag: String {
	case SwapInWalletDetails
	case FinalWalletDetails
}


struct WalletInfoView: View {
	
	let popTo: (PopToDestination) -> Void
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@State var popoverPresent_swapInWallet = false
	@State var popoverPresent_finalWallet = false
	
	@State var final_mpk_truncationDetected = false
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
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
			
			navLink(.SwapInWalletDetails) {
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
			
			navLink(.FinalWalletDetails) {
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
		
		let total = confirmed.sat + unconfirmed.sat
		let (btcAmt, fiatAmt) = formattedBalances(total)
		
		Text(verbatim: "\(btcAmt.string) ") +
		Text(verbatim: " ≈ \(fiatAmt.string)").foregroundColor(.secondary)
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
	
	@ViewBuilder
	private func navLink<Content>(
		_ tag: NavLinkTag,
		label: () -> Content
	) -> some View where Content: View {
		
		NavigationLink(
			destination: navLinkView(tag),
			tag: tag,
			selection: $navLinkTag,
			label: label
		)
	}
	
	@ViewBuilder
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .SwapInWalletDetails : SwapInWalletDetails(location: .embedded, popTo: popToWrapper)
			case .FinalWalletDetails  : FinalWalletDetails()
		}
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
		log.trace("popToWrapper(\(destination))")
		
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
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkChanged(deepLink)
				}
			}
			
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
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		// This is a hack, courtesy of bugs in Apple's NavigationLink:
		// https://developer.apple.com/forums/thread/677333
		//
		// Summary:
		// There's some quirky code in SwiftUI that is resetting our navLinkTag.
		// Several bizarre workarounds have been proposed.
		// I've tried every one of them, and none of them work (at least, without bad side-effects).
		//
		// The only clean solution I've found is to listen for SwiftUI's bad behaviour,
		// and forcibly undo it.
		
		if let value = value {
			
			// Navigate towards deep link (if needed)
			var newNavLinkTag: NavLinkTag? = nil
			switch value {
				case .paymentHistory     : break
				case .backup             : break
				case .drainWallet        : break
				case .electrum           : break
				case .backgroundPayments : break
				case .liquiditySettings  : break
				case .forceCloseChannels : break
				case .swapInWallet       : newNavLinkTag = NavLinkTag.SwapInWalletDetails
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: 1.5)
				
				self.navLinkTag = newNavLinkTag // Trigger/push the view
			}
			
		} else {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 0.0)
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
			log.debug("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
		}
	}
	
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
	
	// --------------------------------------------------
	// MARK: Workarounds
	// --------------------------------------------------
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				log.trace("swiftUiBugWorkaround = nil")
				self.swiftUiBugWorkaround = nil
			}
		}
	}
}
