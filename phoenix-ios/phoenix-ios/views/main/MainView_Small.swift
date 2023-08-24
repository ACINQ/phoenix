import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MainView_Small"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum NavLinkTag: String {
	case ConfigurationView
	case TransactionsView
	case ReceiveView
	case SendView
	case CurrencyConverter
	case SwapInWalletDetails
}

struct MainView_Small: View {
	
	private let phoenixBusiness = Biz.business
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var canMergeChannelsForSplicing = Biz.canMergeChannelsForSplicingPublisher.value
	@State var showingMergeChannelsView = false
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	@State var externalLightningRequest: AppScanController? = nil
	
	@State var popToDestination: PopToDestination? = nil
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
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
	
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
				NavigationLink(
					destination: navLinkView(),
					isActive: navLinkTagBinding()
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}

			content()

		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // For iOS 16+
			navLinkView()
		}
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			HomeView(showSwapInWallet: showSwapInWallet)
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
				openCurrencyConverter: { navLinkTag = .CurrencyConverter }
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
			navLinkTag = .ConfigurationView
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
			navLinkTag = .TransactionsView
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
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .ConfigurationView   : ConfigurationView()
			case .TransactionsView    : TransactionsView()
			case .ReceiveView         : ReceiveView()
			case .SendView            : SendView(controller: externalLightningRequest)
			case .CurrencyConverter   : CurrencyConverterView()
			case .SwapInWalletDetails : SwapInWalletDetails(location: .embedded, popTo: popTo)
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
	// MARK: Notifications
	// --------------------------------------------------
	
	private func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		if let value = value {
			
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
	
	private func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
			
			log.debug("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
			
		} else if tag == nil {
			
			// If we pushed the SendView, triggered by an external lightning url,
			// then we can nil out the associated controller now (since we handed off to SendView).
			self.externalLightningRequest = nil
			
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
	
	private func canMergeChannelsForSplicingChanged(_ value: Bool) {
		log.trace("canMergeChannelsForSplicingChanged()")
		
		canMergeChannelsForSplicing = value
	}
	
	private func didReceiveExternalLightningUrl(_ urlStr: String) {
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
			navLinkTag = .SendView
		}
	}
	
	func didTapReceiveButton() {
		log.trace("didTapReceiveButton()")
		
		if canMergeChannelsForSplicing {
			showingMergeChannelsView = true
		} else {
			navLinkTag = .ReceiveView
		}
	}
	
	func showSwapInWallet() {
		log.trace("showSwapInWallet()")
		
		navLinkTag = .SwapInWalletDetails
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		popToDestination = destination
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				log.debug("swiftUiBugWorkaround = nil")
				self.swiftUiBugWorkaround = nil
			}
		}
	}
}
