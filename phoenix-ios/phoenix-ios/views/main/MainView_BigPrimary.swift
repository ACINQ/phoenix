import SwiftUI
import PhoenixShared

fileprivate let filename = "MainView_BigPrimary"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MainView_BigPrimary: View {
	
	enum NavLinkTag: String {
		case ReceiveView
		case SendView
	}
	
	@State var didAppear = false
	
	@State var canMergeChannelsForSplicing = Biz.canMergeChannelsForSplicingPublisher.value
	@State var showingMergeChannelsView = false
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	@State var externalLightningRequest: AppScanController? = nil
	
	enum FooterButtonWidth: Preference {}
	let footerButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var footerButtonWidth: CGFloat? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@ScaledMetric var sendImageSize: CGFloat = 22
	@ScaledMetric var receiveImageSize: CGFloat = 22
	
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		NavigationStack(path: $navCoordinator.path) {
			layers()
				.navigationTitle("")
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
				.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
					navLinkView()
				}
				.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
					navLinkView(tag)
				}
		}
		.environmentObject(navCoordinator)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			
			Color.primaryBackground
				.ignoresSafeArea()

			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea()
			}
			
			content()
		
		} // <ZStack>
		.onAppear {
			onAppear()
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
		.onReceive(Biz.canMergeChannelsForSplicingPublisher) {
			canMergeChannelsForSplicingChanged($0)
		}
		.onReceive(externalLightningUrlPublisher) {
			didReceiveExternalLightningUrl($0)
		}
		.sheet(isPresented: $showingMergeChannelsView) {
			MergeChannelsView(location: .sheet)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HomeView(
				showSwapInWallet: showSwapInWallet,
				showLiquidityAds: showLiquidityAds
			)
			.padding(.bottom, 15)
			footer()
		}
		.padding(.bottom, 60)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 20) {
			
			Button {
				didTapReceiveButton()
			} label: {
				Label {
					Text("Receive")
						.font(.title2.weight(.medium))
						.foregroundColor(canMergeChannelsForSplicing ? .appNegative : .white)
				} icon: {
					Image("ic_receive_resized")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.foregroundColor(.white)
						.frame(width: receiveImageSize, height: receiveImageSize, alignment: .center)
				}
				.padding(.leading, 16)
				.padding(.trailing, 18)
				.padding(.top, 9)
				.padding(.bottom, 9)
			} // </Button>
			.buttonStyle(ScaleButtonStyle(
				cornerRadius: 100,
				backgroundFill: Color.appAccent
			))
			.frame(minWidth: footerButtonWidth, alignment: Alignment.center)
			.read(footerButtonWidthReader)

			Button {
				didTapSendButton()
			} label: {
				Label {
					Text("Send")
						.font(.title2.weight(.medium))
						.foregroundColor(canMergeChannelsForSplicing ? .appNegative : .white)
				} icon: {
					Image("ic_scan_resized")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.foregroundColor(.white)
						.frame(width: sendImageSize, height: sendImageSize, alignment: .center)
				}
				.padding(.leading, 26)
				.padding(.trailing, 28)
				.padding(.top, 9)
				.padding(.bottom, 9)
			} // </Button>
			.buttonStyle(ScaleButtonStyle(
				cornerRadius: 100,
				backgroundFill: Color.appAccent
			))
			.frame(minWidth: footerButtonWidth, alignment: Alignment.center)
			.read(footerButtonWidthReader)
		
		} // </HStack>
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .ReceiveView : ReceiveView()
			case .SendView    : SendView(location: .MainView, controller: externalLightningRequest)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	private func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// Careful: this function may be called when returning from the Receive/Send view
		if !didAppear {
			didAppear = true
			
			// Reserved for future use
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func canMergeChannelsForSplicingChanged(_ value: Bool) {
		log.trace("canMergeChannelsForSplicingChanged()")
		
		canMergeChannelsForSplicing = value
	}
	
	func didReceiveExternalLightningUrl(_ urlStr: String) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
		
		if #available(iOS 17.0, *) {
			// If the SendView is visible, it will simply be replaced
		} else { // iOS 16
			if navLinkTag == .SendView {
				log.debug("Ignoring: handled by SendView")
				return
			}
		}
		
		MainViewHelper.shared.processExternalLightningUrl(urlStr) { scanController in
			
			externalLightningRequest = scanController
			if #available(iOS 17, *) {
				navCoordinator.path.removeAll()
			}
			navigateTo(.SendView)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func didTapSendButton() {
		log.trace("didTapSendButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			withAnimation {
				navigateTo(.SendView)
			}
		}
	}
	
	func didTapReceiveButton() {
		log.trace("didTapReceiveButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			withAnimation {
				navigateTo(.ReceiveView)
			}
		}
	}
	
	func showSwapInWallet() {
		log.trace("showSwapInWallet()")
		
		popoverState.display(dismissable: true) {
			SwapInWalletDetails(location: .popover, popTo: popTo)
				.frame(maxHeight: 600)
		}
	}
	
	func showLiquidityAds() {
		log.trace("showLiquidityAds()")
		
		popoverState.display(dismissable: true) {
			LiquidityAdsView(location: .popover)
				.frame(maxHeight: 600)
		}
	}
	
	// --------------------------------------------------
	// MARK: Navigation
	// --------------------------------------------------
	
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
			
			if tag == nil {
				// If we pushed the SendView, triggered by an external lightning url,
				// then we can nil out the associated controller now (since we handed off to SendView).
				self.externalLightningRequest = nil
			}
		}
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else {
			popoverState.close {
				if let deepLink = destination.followedBy {
					deepLinkManager.broadcast(deepLink)
				}
			}
		}
	}
}
