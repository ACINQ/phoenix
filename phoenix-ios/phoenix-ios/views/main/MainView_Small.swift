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
}

struct MainView_Small: View {
	
	private let appDelegate = AppDelegate.get()
	private let phoenixBusiness = AppDelegate.get().business
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	@State var externalLightningRequest: AppScanController? = nil
	@State var temp: [AppScanController] = []
	
	@ScaledMetric var sendImageSize: CGFloat = 17
	@ScaledMetric var receiveImageSize: CGFloat = 18
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
	
	@State var footerTruncationDetection_standard: [ContentSizeCategory: Bool] = [:]
	@State var footerTruncationDetection_condensed: [ContentSizeCategory: Bool] = [:]
	
	@Environment(\.sizeCategory) var contentSizeCategory: ContentSizeCategory
	
	// When we drop iOS 14 support, switch to this:
//	@State var footerTruncationDetection_standard: [DynamicTypeSize: Bool] = [:]
//	@State var footerTruncationDetection_condensed: [DynamicTypeSize: Bool] = [:]
//	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
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
		
		NavigationView {
			layers()
		}
		.navigationViewStyle(StackNavigationViewStyle())
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {

			// iOS 14 & 15 have bugs when using NavigationLink.
			// The suggested workarounds include using only a single NavigationLink.
			//
			NavigationLink(
				destination: navLinkView(),
				isActive: Binding(
					get: { navLinkTag != nil },
					set: { if !$0 { navLinkTag = nil }}
				)
			) {
				EmptyView()
			}
			.accessibilityHidden(true)
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}

			content()

		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
		.onChange(of: navLinkTag) { tag in
			navLinkTagChanged(tag)
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onReceive(externalLightningUrlPublisher) {
			didReceiveExternalLightningUrl($0)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			HomeView()
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
		
		let csc = contentSizeCategory
		let footerTruncationDetected_condensed = footerTruncationDetection_condensed[csc] ?? false
		let footerTruncationDetected_standard = footerTruncationDetection_standard[csc] ?? false
		
		Group {
			if footerTruncationDetected_condensed {
				footer_accessibility()
			} else if footerTruncationDetected_standard {
				footer_condensed(csc)
			} else {
				footer_standard(csc)
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
	func footer_standard(_ csc: ContentSizeCategory) -> some View {
		
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
				navLinkTag = .ReceiveView
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
					} wasTruncated: {
						log.debug("footerTruncationDetected_standard(receive): \(csc)")
						self.footerTruncationDetection_standard[csc] = true
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
				navLinkTag = .SendView
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
					} wasTruncated: {
						log.debug("footerTruncationDetected_standard(send): \(csc)")
						self.footerTruncationDetection_standard[csc] = true
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
	func footer_condensed(_ csc: ContentSizeCategory) -> some View {
		
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
				navLinkTag = .ReceiveView
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
					} wasTruncated: {
						log.debug("footerTruncationDetected_condensed(receive): \(csc)")
						self.footerTruncationDetection_condensed[csc] = true
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
				navLinkTag = .SendView
			} label: {
				Label {
					TruncatableView(fixedHorizontal: true, fixedVertical: true) {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
					} wasTruncated: {
						log.debug("footerTruncationDetected_condensed(send): \(csc)")
						self.footerTruncationDetection_condensed[csc] = true
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
	func footer_accessibility() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					navLinkTag = .ReceiveView
				} label: {
					Label {
						Text("Receive")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
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
					navLinkTag = .SendView
				} label: {
					Label {
						Text("Send")
							.lineLimit(1)
							.foregroundColor(.primaryForeground)
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
		
		switch navLinkTag {
		case .ConfigurationView:
			ConfigurationView()
		case .TransactionsView:
			TransactionsView()
		case .ReceiveView:
			ReceiveView()
		case .SendView:
			SendView(controller: externalLightningRequest)
		case .CurrencyConverter:
			CurrencyConverterView()
		default:
			EmptyView()
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
	
	private func didReceiveExternalLightningUrl(_ urlStr: String) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
	
		if navLinkTag == .SendView {
			log.debug("Ignoring: handled by SendView")
			return
		}
		
		// We want to:
		// - Parse the incoming lightning url
		// - If it's invalid, we want to display a warning (using the Toast view)
		// - Otherwise we want to jump DIRECTLY to the "Confirm Payment" screen.
		//
		// In particular, we do **NOT** want the user experience to be:
		// - switch to SendView
		// - then again switch to ConfirmView
		// This feels jittery :(
		//
		// So we need to:
		// - get a Scan.ModelValidate instance
		// - pass this to SendView as the `firstModel` parameter
		
		let controllers = AppDelegate.get().business.controllers
		guard let scanController = controllers.scan(firstModel: Scan.ModelReady()) as? AppScanController else {
			return
		}
		temp.append(scanController)
		
		var unsubscribe: (() -> Void)? = nil
		unsubscribe = scanController.subscribe { (model: Scan.Model) in
			
			// Ignore first subscription fire (Scan.ModelReady)
			if let _ = model as? Scan.ModelReady {
				return
			} else {
				self.externalLightningRequest = scanController
				self.navLinkTag = .SendView
			}
			
			// Cleanup
			if let idx = self.temp.firstIndex(where: { $0 === scanController }) {
				self.temp.remove(at: idx)
			}
			unsubscribe?()
		}
		
		scanController.intent(intent: Scan.IntentParse(request: urlStr))
	}
	
	private func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		if let value = value {
			switch value {
				case .paymentHistory : self.navLinkTag = .TransactionsView
				case .backup         : self.navLinkTag = .ConfigurationView
				case .drainWallet    : self.navLinkTag = .ConfigurationView
				case .electrum       : self.navLinkTag = .ConfigurationView
			}
		}
	}
}

extension ContentSizeCategory: CustomStringConvertible {
	public var description: String {
		switch self {
			case .extraSmall                        : return "XS"
			case .small                             : return "S"
			case .medium                            : return "M"
			case .large                             : return "L"
			case .extraLarge                        : return "XL"
			case .extraExtraLarge                   : return "XXL"
			case .extraExtraExtraLarge              : return "XXXL"
			case .accessibilityMedium               : return "aM"
			case .accessibilityLarge                : return "aL"
			case .accessibilityExtraLarge           : return "aXL"
			case .accessibilityExtraExtraLarge      : return "aXXL"
			case .accessibilityExtraExtraExtraLarge : return "aXXXL"
			@unknown default                        : return "U"
		}
	}
}
