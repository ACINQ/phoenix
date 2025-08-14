import SwiftUI
import PhoenixShared

fileprivate let filename = "MainView_BigPrimary"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MainView_BigPrimary: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case ReceiveView
		case SendView
		case LoginView(flow: SendManager.ParseResult_Lnurl_Auth)
		case ValidateView(flow: SendManager.ParseResult)
		
		var description: String {
			switch self {
				case .ReceiveView     : return "ReceiveView"
				case .SendView        : return "SendView"
				case .LoginView(_)    : return "LoginView"
				case .ValidateView(_) : return "ValidateView"
			}
		}
	}
	
	@State var didAppear = false
	
	@State var canMergeChannelsForSplicing = Biz.canMergeChannelsForSplicingPublisher.value
	@State var showingMergeChannelsView = false
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	
	enum FooterButtonWidth: Preference {}
	let footerButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var footerButtonWidth: CGFloat? = nil
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@ScaledMetric var sendImageSize: CGFloat = 22
	@ScaledMetric var receiveImageSize: CGFloat = 22
	
	@StateObject var toast = Toast()
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
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

			if Biz.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea()
			}
			
			content()
			
			if parseProgress != nil {
				FetchActivityNotice(
					title: self.fetchActivityTitle,
					onCancel: { self.cancelParseRequest() }
				)
				.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
			}
			
			toast.view()
		
		} // <ZStack>
		.onAppear {
			onAppear()
		}
		.onReceive(Biz.canMergeChannelsForSplicingPublisher) {
			canMergeChannelsForSplicingChanged($0)
		}
		.onReceive(externalLightningUrlPublisher) {
			didReceiveExternalLightningUrl($0)
		}
		.sheet(isPresented: $showingMergeChannelsView) {
			MergeChannelsView(location: .sheet)
				.modifier(GlobalEnvironment.sheetInstance())
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HomeView(
				showLiquidityAds: showLiquidityAds,
				showSwapInWallet: showSwapInWallet,
				showFinalWallet: showFinalWallet
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
		case .ReceiveView:
			ReceiveView()
			
		case .SendView:
			SendView(location: .MainView)
			
		case .LoginView(let flow):
			LoginView(flow: flow, popTo: self.popTo)
			
		case .ValidateView(let flow):
			ValidateView(flow: flow, popTo: self.popTo)
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
	
	var fetchActivityTitle: String {
		
		if parseProgress is SendManager.ParseProgress_LnurlServiceFetch {
			return String(localized: "Fetching Lightning URL", comment: "Progress title")
		} else {
			return String(localized: "Resolving lightning address", comment: "Progress title")
		}
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
		
		parseExternalUrl(urlStr)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.description))")
		
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
	
	func showLiquidityAds() {
		log.trace("showLiquidityAds()")
		
		popoverState.display(dismissable: true) {
			LiquidityAdsView(location: .popover)
				.frame(maxHeight: 600)
		}
	}
	
	func showSwapInWallet() {
		log.trace("showSwapInWallet()")
		
		// We used to show this in a popover:
	//	popoverState.display(dismissable: true) {
	//		SwapInWalletDetails(location: .popover, popTo: popTo)
	//			.frame(maxHeight: 600)
	//	}
		
		// But on iPad, it's just as clean to jump straight to the configuration setting.
		deepLinkManager.broadcast(.swapInWallet)
	}
	
	func showFinalWallet() {
		log.trace("showFinalWallet()")
		
		// It's currently not possible to use a popover here.
		// The problem is:
		// > FinalWalletDetails > SpendOnChainFunds > showMinerFeeSheet()
		//
		// So we have a view(SpendOnChainFunds) within a popover,
		// that needs to display another popover(MinerFeeSheet).
		// So the user never returns to the SpendOnChainFunds view.
		//
		// This is fixable with a bit of work.
		// But on iPad, it's just as clean to jump straight to the configuration setting.
		
		deepLinkManager.broadcast(.finalWallet)
	}
	
	// --------------------------------------------------
	// MARK: Navigation
	// --------------------------------------------------
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else {
			if popoverState.hasCurrentItem {
				popoverState.close {
					if let deepLink = destination.followedBy {
						deepLinkManager.broadcast(deepLink)
					}
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: External URL
	// --------------------------------------------------
	
	func parseExternalUrl(_ input: String) {
		log.trace("parseExternalUrl()")
		
		guard !isParsing else {
			log.warning("parseExternalUrl: ignoring: isParsing == true")
			return
		}
		
		isParsing = true
		parseIndex += 1
		let index = parseIndex
		
		Task { @MainActor in
			do {
				let progressHandler = {(progress: SendManager.ParseProgress) -> Void in
					if index == parseIndex {
						self.parseProgress = progress
					} else {
						log.warning("parseExternalUrl: progressHandler: ignoring: cancelled")
					}
				}
				
				let result: SendManager.ParseResult = try await Biz.business.sendManager.parse(
					request: input,
					progress: progressHandler
				)
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
					handleParseResult(result)
				} else {
					log.warning("parseExternalUrl: result: ignoring: cancelled")
				}
				
			} catch {
				log.error("parseExternalUrl: error: \(error)")
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
				}
			}
		}
	}
	
	func handleParseResult(_ result: SendManager.ParseResult) {
		log.trace("handleParseResult()")
		
		if let badRequest = result as? SendManager.ParseResult_BadRequest {
			showErrorMessage(badRequest)
		} else {
			
			if #available(iOS 17.0, *) {
				navCoordinator.path.removeAll()
			}
			if let auth = result as? SendManager.ParseResult_Lnurl_Auth {
				navigateTo(.LoginView(flow: auth))
			} else {
				navigateTo(.ValidateView(flow: result))
			}
		}
	}
	
	func showErrorMessage(_ result: SendManager.ParseResult_BadRequest) {
		log.trace("showErrorMessage()")
		
		let either = ParseResultHelper.processBadRequest(result)
		switch either {
		case .Left(let msg):
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 30.0,
				alignment: .middle,
				showCloseButton: true
			)
			
		case .Right(let websiteLink):
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					didCopyLink: didCopyLink,
					didOpenLink: nil
				)
			}
		}
	}
	
	func cancelParseRequest() {
		log.trace("cancelParseRequest()")
		
		isParsing = false
		parseIndex += 1
		parseProgress = nil
	}
	
	func didCopyLink() {
		log.trace("didCopyLink()")
		
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
}
