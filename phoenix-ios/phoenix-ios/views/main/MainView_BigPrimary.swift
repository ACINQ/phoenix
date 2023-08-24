import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MainView_BigPrimary"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum NavLinkTag: String {
	case ReceiveView
	case SendView
}


struct MainView_BigPrimary: View {
	
	@State private var navLinkTag: NavLinkTag? = nil
	
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
	
	@ScaledMetric var sendImageSize: CGFloat = 22
	@ScaledMetric var receiveImageSize: CGFloat = 22
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		NavigationWrapper {
			layers()
				.navigationTitle("")
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
		}
		.sheet(isPresented: $showingMergeChannelsView) {
			MergeChannelsView(type: .sheet)
		}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			if #unavailable(iOS 16.0) {
				// iOS 14 & 15 have bugs when using NavigationLink.
				// The suggested workarounds include using only a single NavigationLink.
				//
				NavigationLink(
					destination: primary_navLinkView(),
					isActive: navLinkTagBinding()
				) {
					EmptyView()
				}
				.isDetailLink(false)
				
			} // else: uses.navigationStackDestination()
			
			Color.primaryBackground
				.ignoresSafeArea()

			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea()
			}
			
			primary_body()
		
		} // <ZStack>
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // For iOS 16+
			primary_navLinkView()
		}
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
	}
	
	@ViewBuilder
	func primary_body() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HomeView(showSwapInWallet: showSwapInWallet)
				.padding(.bottom, 15)
			primary_footer()
		}
		.padding(.bottom, 60)
	}
	
	@ViewBuilder
	func primary_footer() -> some View {
		
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
	func primary_navLinkView() -> some View {
		
		if let tag = navLinkTag {
			switch tag {
				case .ReceiveView : ReceiveView()
				case .SendView    : SendView(controller: externalLightningRequest)
			}
		} else {
			EmptyView()
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
	
	private func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil {
			// If we pushed the SendView, triggered by an external lightning url,
			// then we can nil out the associated controller now (since we handed off to SendView).
			self.externalLightningRequest = nil
		}
	}
	
	private func canMergeChannelsForSplicingChanged(_ value: Bool) {
		log.trace("canMergeChannelsForSplicingChanged()")
		
		canMergeChannelsForSplicing = value
	}
	
	private func didReceiveExternalLightningUrl(_ urlStr: String) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
		
		if navLinkTag == .SendView {
			log.debug("Ignoring: handled by SendView")
			return
		}
		
		MainViewHelper.shared.processExternalLightningUrl(urlStr) { scanController in
			
			self.externalLightningRequest = scanController
			self.navLinkTag = .SendView
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapSendButton() {
		log.trace("didTapSendButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			withAnimation {
				navLinkTag = .SendView
			}
		}
	}
	
	func didTapReceiveButton() {
		log.trace("didTapReceiveButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			withAnimation {
				navLinkTag = .ReceiveView
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
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		popoverState.close {
			if let deepLink = destination.followedBy {
				deepLinkManager.broadcast(deepLink)
			}
		}
	}
}
