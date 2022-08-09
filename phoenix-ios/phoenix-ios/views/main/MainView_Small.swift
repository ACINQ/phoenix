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
	
	@ScaledMetric var footerButtonImageSize: CGFloat = 22
	
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
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
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
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
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
			
			// Leading Button 2
			header_transactionsButton()
			
			Spacer()
			
			// Trailing Button 2
			AppStatusButton(
				headerButtonHeightReader: headerButtonHeightReader,
				headerButtonHeight: $headerButtonHeight
			)
			.padding(.trailing)
			
			// Trailing Button 1
			ToolsMenu(
				buttonHeightReader: headerButtonHeightReader,
				buttonHeight: $headerButtonHeight,
				openCurrencyConverter: { navLinkTag = .CurrencyConverter }
			)
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
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer()
			Button {
				navLinkTag = .ReceiveView
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image("ic_receive")
						.resizable()
						.frame(width: footerButtonImageSize, height: footerButtonImageSize)
						.foregroundColor(.appAccent)
					Text("Receive")
						.minimumScaleFactor(0.5)
						.foregroundColor(.primaryForeground)
				}
				.frame(minWidth: footerButtonWidth, alignment: Alignment.trailing)
				.read(footerButtonWidthReader)
				.read(footerButtonHeightReader)
			}

			Spacer()
			if let footerButtonHeight = footerButtonHeight {
				Divider().frame(width: 1, height: footerButtonHeight).background(Color.borderColor)
				Spacer()
			}

			Button {
				navLinkTag = .SendView
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image("ic_scan")
						.resizable()
						.frame(width: footerButtonImageSize, height: footerButtonImageSize)
						.foregroundColor(.appAccent)
					Text("Send")
						.minimumScaleFactor(0.5)
						.foregroundColor(.primaryForeground)
				}
				.frame(minWidth: footerButtonWidth, alignment: Alignment.leading)
				.read(footerButtonWidthReader)
				.read(footerButtonHeightReader)
			}
			Spacer()
		
		} // </HStack>
		.padding(.top, 20)
		.padding(.bottom, 10)
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
		.assignMaxPreference(for: footerButtonWidthReader.key, to: $footerButtonWidth)
		.assignMaxPreference(for: footerButtonHeightReader.key, to: $footerButtonHeight)
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
