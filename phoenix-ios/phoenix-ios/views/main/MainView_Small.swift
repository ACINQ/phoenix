import SwiftUI
import PhoenixShared

fileprivate let filename = "MainView_Small"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MainView_Small: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case ConfigurationView
		case TransactionsView
		case ReceiveView
		case SendView
		case CurrencyConverter
		case SwapInWalletDetails
		case FinalWalletDetails
		case LiquidityAdsView
		case LoginView(flow: SendManager.ParseResult_Lnurl_Auth)
		case ValidateView(flow: SendManager.ParseResult)
		
		var description: String {
			switch self {
				case .ConfigurationView   : return "ConfigurationView"
				case .TransactionsView    : return "TransactionsView"
				case .ReceiveView         : return "ReceiveView"
				case .SendView            : return "SendView"
				case .CurrencyConverter   : return "CurrencyConverter"
				case .SwapInWalletDetails : return "SwapInWalletDetails"
				case .FinalWalletDetails  : return "FinalWalletDetails"
				case .LiquidityAdsView    : return "LiquidityAdsView"
				case .LoginView(_)        : return "LoginView"
				case .ValidateView(_)     : return "ValidateView"
			}
		}
	}
	
	@State var canMergeChannelsForSplicing = Biz.canMergeChannelsForSplicingPublisher.value
	@State var showingMergeChannelsView = false
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	
	@ScaledMetric var sendImageSize: CGFloat = 17
	@ScaledMetric var receiveImageSize: CGFloat = 18
	
	let headerButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<HeaderButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var headerButtonHeight: CGFloat? = nil
	
	enum FooterButtonWidth: Preference {}
	let footerButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var footerButtonWidth: CGFloat? = nil
	
	enum FooterButtonHeight: Preference {}
	let footerButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var footerButtonHeight: CGFloat? = nil
	
	@State var footerTruncationDetection_standard: [DynamicTypeSize: Bool] = [:]
	@State var footerTruncationDetection_condensed: [DynamicTypeSize: Bool] = [:]
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	@State var swiftUiBugWorkaround: NavLinkTag? = nil
	@State var swiftUiBugWorkaroundIdx = 0
	// </iOS_16_workarounds>
	
	@StateObject var toast = Toast()
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var popoverState: PopoverState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	/* .accessibilitySortPriority():
	 *
	 * - Footer button: send         = 39
	 * - Footer button: receive      = 38
	 * - Header button: settings     = 23
	 * - Header button: transactions = 22
	 * - Header button: app status   = 21
	 * - Header button: tools        = 20
	 */
	
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
				.edgesIgnoringSafeArea(.all)

			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
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

		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
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
			header()
			HomeView(
				showLiquidityAds: showLiquidityAds,
				showSwapInWallet: showSwapInWallet,
				showFinalWallet: showFinalWallet
			)
			footer()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			// Leading Button 1
			header_settingsButton()
				.padding(.trailing)
				.accessibilitySortPriority(23)
			
			// Leading Button 2
			header_transactionsButton()
				.accessibilitySortPriority(22)
			
			Spacer()
			
			// Trailing Button 2
			AppStatusButton(
				headerButtonHeightReader: headerButtonHeightReader,
				headerButtonHeight: $headerButtonHeight
			)
			.padding(.trailing)
			.accessibilitySortPriority(21)
			
			// Trailing Button 1
			ToolsMenu(
				buttonHeightReader: headerButtonHeightReader,
				buttonHeight: $headerButtonHeight,
				openCurrencyConverter: { navigateTo(.CurrencyConverter) }
			)
			.accessibilitySortPriority(20)
		}
		.padding([.top, .leading, .trailing])
		.padding(.bottom, 40) // extra padding on bottom, between Header & HomeView
		.assignMaxPreference(for: headerButtonHeightReader.key, to: $headerButtonHeight)
	}
	
	@ViewBuilder
	func header_settingsButton() -> some View {
		
		Button {
			navigateTo(.ConfigurationView)
		} label: {
			Image(systemName: "gearshape.fill")
				.renderingMode(.template)
				.imageScale(.large)
				.font(.caption2)
				.foregroundColor(.primary)
				.padding(.all, 7)
				.read(headerButtonHeightReader)
				.frame(minHeight: headerButtonHeight)
				.squareFrame()
				.background(Color.buttonFill)
				.cornerRadius(30)
				.overlay(
					RoundedRectangle(cornerRadius: 30)
						.stroke(Color.borderColor, lineWidth: 1)
				)
		}
		.accessibilityLabel("Settings")
	}
	
	@ViewBuilder
	func header_transactionsButton() -> some View {
		
		Button {
			navigateTo(.TransactionsView)
		} label: {
			Image(systemName: "list.bullet")
				.renderingMode(.template)
				.imageScale(.large)
				.font(.caption2)
				.foregroundColor(.primary)
				.padding(.all, 7)
				.read(headerButtonHeightReader)
				.frame(minHeight: headerButtonHeight)
				.squareFrame()
				.background(Color.buttonFill)
				.cornerRadius(30)
				.overlay(
					RoundedRectangle(cornerRadius: 30)
						.stroke(Color.borderColor, lineWidth: 1)
				)
		}
		.accessibilityLabel("Payment history")
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		let dts = dynamicTypeSize
		let footerTruncationDetected_condensed = footerTruncationDetection_condensed[dts] ?? false
		let footerTruncationDetected_standard = footerTruncationDetection_standard[dts] ?? false
		
		let buttonTextColor = canMergeChannelsForSplicing ? Color.appNegative : Color.primaryForeground
		
		Group {
			if footerTruncationDetected_condensed {
				footer_accessibility(buttonTextColor: buttonTextColor)
			} else if footerTruncationDetected_standard {
				footer_condensed(buttonTextColor: buttonTextColor, dts: dts)
			} else {
				footer_standard(buttonTextColor: buttonTextColor, dts: dts)
			}
		}
		.padding(.top, 20)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
		.assignMaxPreference(for: footerButtonWidthReader.key, to: $footerButtonWidth)
		.assignMaxPreference(for: footerButtonHeightReader.key, to: $footerButtonHeight)
	}
	
	@ViewBuilder
	func footer_standard(buttonTextColor: Color, dts: DynamicTypeSize) -> some View {
		
		// We're trying to center the divider:
		//
		// ---------------------------------
		// | [img] Receive | [img] Send    |
		// ---------------------------------
		//                 ^ perfectly centered
		//
		// To accomplish this, we make both buttons the same width.
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer(minLength: 2)
			
			Button {
				didTapReceiveButton()
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} wasTruncated: {
						log.debug("footerTruncationDetected_standard(receive): \(dts)")
						self.footerTruncationDetection_standard[dts] = true
					}
				} icon: {
					Image("ic_receive_resized")
						.resizable()
						.frame(width: receiveImageSize, height: receiveImageSize)
						.foregroundColor(.appAccent)
				}
			}
			.frame(minWidth: footerButtonWidth, alignment: Alignment.center)
			.read(footerButtonWidthReader)
			.read(footerButtonHeightReader)
			.accessibilityLabel("Receive payment")
			.accessibilitySortPriority(38)

			Spacer(minLength: 2)
			if let footerButtonHeight = footerButtonHeight {
				Divider().frame(width: 1, height: footerButtonHeight).background(Color.borderColor)
				Spacer(minLength: 2)
			}

			Button {
				didTapSendButton()
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} wasTruncated: {
						log.debug("footerTruncationDetected_standard(send): \(dts)")
						self.footerTruncationDetection_standard[dts] = true
					}
				} icon: {
					Image("ic_scan_resized")
						.resizable()
						.frame(width: sendImageSize, height: sendImageSize)
						.foregroundColor(.appAccent)
				}
			}
			.frame(minWidth: footerButtonWidth, alignment: Alignment.center)
			.read(footerButtonWidthReader)
			.read(footerButtonHeightReader)
			.accessibilityLabel("Send payment")
			.accessibilitySortPriority(39)
			
			Spacer(minLength: 2)
		
		} // </HStack>
	}
	
	@ViewBuilder
	func footer_condensed(buttonTextColor: Color, dts: DynamicTypeSize) -> some View {
		
		// There's a large font being used, and possibly a small screen too.
		// Thus horizontal space is tight.
		//
		// So we're going to just try to squeeze the buttons into a single line.
		//
		// ------------------------------
		// | [img] Receive | [img] Send |
		// ------------------------------
		//                 ^ might not be centered, but at least the buttons fit on 1 line
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer(minLength: 0)
			
			Button {
				didTapReceiveButton()
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} wasTruncated: {
						log.debug("footerTruncationDetected_condensed(receive): \(dts)")
						self.footerTruncationDetection_condensed[dts] = true
					}
				} icon: {
					Image("ic_receive_resized")
						.resizable()
						.frame(width: receiveImageSize, height: receiveImageSize)
						.foregroundColor(.appAccent)
				} // </Label>
			} // </Button>
			.read(footerButtonHeightReader)
			.accessibilityLabel("Receive payment")
			.accessibilitySortPriority(38)

			Spacer(minLength: 0)
			if let footerButtonHeight = footerButtonHeight {
				Divider().frame(width: 1, height: footerButtonHeight).background(Color.borderColor)
				Spacer(minLength: 0)
			}

			Button {
				didTapSendButton()
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} wasTruncated: {
						log.debug("footerTruncationDetected_condensed(send): \(dts)")
						self.footerTruncationDetection_condensed[dts] = true
					}
				} icon: {
					Image("ic_scan_resized")
						.resizable()
						.frame(width: sendImageSize, height: sendImageSize)
						.foregroundColor(.appAccent)
				} // </Label>
			} // </Button>
			.read(footerButtonHeightReader)
			.accessibilityLabel("Send payment")
			.accessibilitySortPriority(39)
		
			Spacer(minLength: 0)
			
		} // </HStack>
	}
	
	@ViewBuilder
	func footer_accessibility(buttonTextColor: Color) -> some View {
		
		// There's a large font being used, and possibly a small screen too.
		// Horizontal space is so tight that we can't get the 2 buttons on a single line.
		//
		// So we're going to put them on multiple lines.
		//
		// -----------------
		//   [img] Receive
		//   [img] Send
		// -----------------
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					didTapReceiveButton()
				} label: {
					Label {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} icon: {
						Image("ic_receive_resized")
							.resizable()
							.frame(width: receiveImageSize, height: receiveImageSize)
							.foregroundColor(.appAccent)
					} // </Label>
				} // </Button>
				.frame(minWidth: footerButtonWidth, alignment: Alignment.leading)
				.read(footerButtonWidthReader)
				.accessibilityLabel("Receive payment")
				.accessibilitySortPriority(38)
				
				Divider().frame(height: 1).background(Color.borderColor)
				
				Button {
					didTapSendButton()
				} label: {
					Label {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(buttonTextColor)
					} icon: {
						Image("ic_scan_resized")
							.resizable()
							.frame(width: sendImageSize, height: sendImageSize)
							.foregroundColor(.appAccent)
					} // </Label>
				} // </Button>
				.frame(minWidth: footerButtonWidth, alignment: Alignment.leading)
				.read(footerButtonWidthReader)
				.accessibilityLabel("Send payment")
				.accessibilitySortPriority(39)
				
			} // </VStack>
			Spacer()
		} // </HStack>
		.padding(.horizontal)
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
		case .ConfigurationView:
			ConfigurationView()
			
		case .TransactionsView:
			TransactionsView()
		
		case .ReceiveView:
			ReceiveView()
			
		case .SendView:
			SendView(location: .MainView)
			
		case .CurrencyConverter:
			CurrencyConverterView()
			
		case .SwapInWalletDetails:
			SwapInWalletDetails(location: .embedded, popTo: popTo)
			
		case .FinalWalletDetails:
			FinalWalletDetails()
			
		case .LiquidityAdsView:
			LiquidityAdsView(location: .embedded)
			
		case .LoginView(let flow):
			LoginView(flow: flow, popTo: self.popTo)
			
		case .ValidateView(let flow):
			ValidateView(flow: flow, popTo: self.popTo)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
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
	// MARK: Notifications
	// --------------------------------------------------
	
	private func canMergeChannelsForSplicingChanged(_ value: Bool) {
		log.trace("canMergeChannelsForSplicingChanged()")
		
		canMergeChannelsForSplicing = value
	}
	
	private func didReceiveExternalLightningUrl(_ urlStr: String) {
		log.trace("didReceiveExternalLightningUrl()")
		
		parseExternalUrl(urlStr)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapSendButton() {
		log.trace("didTapSendButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			navigateTo(.SendView)
		}
	}
	
	func didTapReceiveButton() {
		log.trace("didTapReceiveButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			navigateTo(.ReceiveView)
		}
	}
	
	func showLiquidityAds() {
		log.trace("showLiquidityAds()")
		
		navigateTo(.LiquidityAdsView)
	}
	
	func showSwapInWallet() {
		log.trace("showSwapInWallet()")
		
		navigateTo(.SwapInWalletDetails)
	}
	
	func showFinalWallet() {
		log.trace("showFinalWallet()")
		
		navigateTo(.FinalWalletDetails)
	}
	
	// --------------------------------------------------
	// MARK: Navigation
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.description))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		if #available(iOS 17, *) {
			
			if let value {
				navCoordinator.path.removeAll()
				
				switch value {
				case .paymentHistory:
					navCoordinator.path.append(NavLinkTag.TransactionsView)
					
				case .backup:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.RecoveryPhrase)
						
				case .drainWallet:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.DrainWallet)
					
				case .electrum:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.ElectrumServer)
					
				case .backgroundPayments:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.PaymentOptions)
					navCoordinator.path.append(PaymentOptionsList.NavLinkTag.BackgroundPaymentsSelector)
					
				case .liquiditySettings:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.ChannelManagement)
					
				case .forceCloseChannels:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.ForceCloseChannels)
					
				case .swapInWallet:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.WalletInfo)
					navCoordinator.path.append(WalletInfoView.NavLinkTag.SwapInWalletDetails)
					
				case .finalWallet:
					navCoordinator.path.append(NavLinkTag.ConfigurationView)
					navCoordinator.path.append(ConfigurationList.NavLinkTag.WalletInfo)
					navCoordinator.path.append(WalletInfoView.NavLinkTag.FinalWalletDetails)
				}
			}
			
		} else { // iOS 16
			
			if let value {
				// Navigate towards deep link (if needed)
				var newNavLinkTag: NavLinkTag? = nil
				var delay: TimeInterval = 1.5 // seconds; multiply by number of screens we need to navigate
				switch value {
					case .paymentHistory     : newNavLinkTag = .TransactionsView  ; delay *= 1
					case .backup             : newNavLinkTag = .ConfigurationView ; delay *= 2
					case .drainWallet        : newNavLinkTag = .ConfigurationView ; delay *= 2
					case .electrum           : newNavLinkTag = .ConfigurationView ; delay *= 2
					case .backgroundPayments : newNavLinkTag = .ConfigurationView ; delay *= 3
					case .liquiditySettings  : newNavLinkTag = .ConfigurationView ; delay *= 3
					case .forceCloseChannels : newNavLinkTag = .ConfigurationView ; delay *= 2
					case .swapInWallet       : newNavLinkTag = .ConfigurationView ; delay *= 2
					case .finalWallet        : newNavLinkTag = .ConfigurationView ; delay *= 2
				}
				
				if let newNavLinkTag = newNavLinkTag {
					
					self.swiftUiBugWorkaround = newNavLinkTag
					self.swiftUiBugWorkaroundIdx += 1
					clearSwiftUiBugWorkaround(delay: delay)
					
					self.navLinkTag = newNavLinkTag // Trigger/push the view
				}
				
			} else {
				// We reached the final destination of the deep link
				clearSwiftUiBugWorkaround(delay: 0.0)
			}
		}
	}
	
	private func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.description ?? "nil")")
		
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
				
			} else if tag == nil {
				
				// If there's a pending popToDestination, it's now safe to continue the flow.
				//
				// Note that performing this operation in `onAppear` doesn't work properly:
				// - it appears to work fine on the simulator, but isn't reliable on the actual device
				// - it seems that, IF using a `navLinkTag`, then we need to wait for the tag to be
				//   unset before it can be set properly again.
				//
				if let destination = popToDestination {
					log.debug("popToDestination: \(destination)")
					
					popToDestination = nil
					if let deepLink = destination.followedBy {
						deepLinkManager.broadcast(deepLink)
					}
				}
			}
		}
	}
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		if #available(iOS 17, *) {
			log.warning("clearSwiftUiBugWorkaround(): This function is for iOS 16 only !")
		} else {
			let idx = self.swiftUiBugWorkaroundIdx
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				if self.swiftUiBugWorkaroundIdx == idx {
					log.debug("swiftUiBugWorkaround = nil")
					self.swiftUiBugWorkaround = nil
				}
			}
		}
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
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
