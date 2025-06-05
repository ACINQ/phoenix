import SwiftUI
import PhoenixShared
import Popovers

fileprivate let filename = "WalletInfoView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WalletInfoView: View {
	
	enum NavLinkTag: String {
		case SwapInWalletDetails
		case SwapInAddresses
		case FinalWalletDetails
	}
	
	let popTo: (PopToDestination) -> Void
	
	@State var didAppear = false
	
	@State var popoverPresent_swapInWallet = false
	@State var popoverPresent_finalWallet = false
	
	@State var final_mpk_truncationDetected = false
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	@State var swiftUiBugWorkaround: NavLinkTag? = nil
	@State var swiftUiBugWorkaroundIdx = 0
	// </iOS_16_workarounds>
	
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
		
		layers()
			.navigationTitle(NSLocalizedString("Wallet info", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
			toast.view()
		}
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
	
	// --------------------------------------------------
	// MARK: View Builders: SwapInWallet
	// --------------------------------------------------
	
	@ViewBuilder
	func section_swapInWallet() -> some View {
		
		Section {
			
			navLink_plain(.SwapInWalletDetails) {
				subsection_swapInWallet_balance()
			}
			subsection_swapInWallet_legacyDescriptor()
			subsection_swapInWallet_descriptor()
			subsection_swapInWallet_publicKey()
			navLink_plain(.SwapInAddresses) {
				subsection_swapInWallet_swapAddresses()
			}
			
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
	func subsection_swapInWallet_legacyDescriptor() -> some View {
		
		let keyManager = Biz.business.walletManager.keyManagerValue()
		let descriptor = keyManager?.swapInOnChainWallet.legacyDescriptor ?? "?"
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Legacy descriptor")
					.font(.headline.bold())
				Spacer()
				copyButton(descriptor)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(descriptor)
					.lineLimit(1)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_swapInWallet_descriptor() -> some View {
		
		let keyManager = Biz.business.walletManager.keyManagerValue()
		let descriptor = keyManager?.swapInOnChainWallet.publicDescriptor ?? "?"
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Descriptor")
					.font(.headline.bold())
				Spacer()
				copyButton(descriptor)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(descriptor)
					.lineLimit(1)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_swapInWallet_publicKey() -> some View {
		
		let keyManager = Biz.business.walletManager.keyManagerValue()
		let pubKeyHex = keyManager?.swapInOnChainWallet.userPublicKey.toHex() ?? "?"
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Public key")
					.font(.headline.bold())
				Spacer()
				copyButton(pubKeyHex)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(pubKeyHex)
					.lineLimit(1)
					.font(.callout.weight(.light))
					.foregroundColor(.secondary)
				Spacer(minLength: 0)
				invisibleImage()
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func subsection_swapInWallet_swapAddresses() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Swap addresses")
				.font(.headline.bold())
			Image(systemName: "list.bullet")
				.padding(.leading, 8)
				.foregroundColor(.secondary)
			Spacer()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders: FinalWallet
	// --------------------------------------------------
	
	@ViewBuilder
	func section_finalWallet() -> some View {
		
		Section {
			
			navLink_plain(.FinalWalletDetails) {
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
		
		let keyManager = Biz.business.walletManager.keyManagerValue()
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
					.lineLimit(1)
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
	func navLink_plain<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .SwapInWalletDetails : SwapInWalletDetails(location: .embedded, popTo: popToWrapper)
			case .SwapInAddresses     : SwapInAddresses()
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
		
		if #available(iOS 17, *) {
			log.warning("popToWrapper(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
			popTo(destination)
		}
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
	// MARK: Navigation
	// --------------------------------------------------
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.description ?? "nil")")
		
		if #available(iOS 17, *) {
			// Nothing to do here.
			// Everything is handled in `MainView_Small` & `MainView_Big`.
			
		} else { // iOS 16
			
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
			
			if let value {
				
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
					case .finalWallet        : newNavLinkTag = NavLinkTag.FinalWalletDetails
				}
				
				if let newNavLinkTag {
					
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
	}
	
	func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if #available(iOS 17, *) {
			log.warning(
				"""
				navLinkTagChanged(): This function is for iOS 16 only ! This means there's a bug.
				The navLinkTag is being set somewhere, when the navCoordinator should be used instead.
				"""
			)
			
		} else { // iOS 16
			
			if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
				log.debug("Blocking SwiftUI's attempt to reset our navLinkTag")
				self.navLinkTag = forcedNavLinkTag
			}
		}
	}
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		if #available(iOS 17, *) {
			log.warning("clearSwiftUiBugWorkaround(): This function is for iOS 16 only !")
		} else { // iOS 16
			
			let idx = self.swiftUiBugWorkaroundIdx
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				if self.swiftUiBugWorkaroundIdx == idx {
					log.trace("swiftUiBugWorkaround = nil")
					self.swiftUiBugWorkaround = nil
				}
			}
		}
	}
}
