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
	
	let headerButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<HeaderButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var headerButtonHeight: CGFloat? = nil
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
			
			// === Top-row buttons ===
			HStack {
				AppStatusButton(
					headerButtonHeightReader: headerButtonHeightReader,
					headerButtonHeight: $headerButtonHeight
				)
				Spacer()
				ToolsMenu(
					buttonHeightReader: headerButtonHeightReader,
					buttonHeight: $headerButtonHeight,
					openCurrencyConverter: { navLinkTag = .CurrencyConverter }
				)
			}
			.padding(.all)
			.assignMaxPreference(for: headerButtonHeightReader.key, to: $headerButtonHeight)
			
			HomeView()
			bottomBar()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func bottomBar() -> some View {
		
		HStack {
			
			Button {
				navLinkTag = .ConfigurationView
			} label: {
				Image("ic_settings")
					.resizable()
					.frame(width: 22, height: 22)
					.foregroundColor(Color.appAccent)
			}
			.padding()
			.padding(.leading, 8)

			Divider().frame(width: 1, height: 40).background(Color.borderColor)
			Spacer()
			
			Button {
				navLinkTag = .ReceiveView
			} label: {
				HStack {
					Image("ic_receive")
						.resizable()
						.frame(width: 22, height: 22)
						.foregroundColor(Color.appAccent)
						.padding(4)
					Text("Receive")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
			Divider().frame(width: 1, height: 40).background(Color.borderColor)
			Spacer()

			Button {
				navLinkTag = .SendView
			} label: {
				HStack {
					Image("ic_scan")
						.resizable()
						.frame(width: 22, height: 22)
						.foregroundColor(Color.appAccent)
						.padding(4)
					Text("Send")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
		
		} // </HStack>
		.padding(.top, 10)
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		switch navLinkTag {
		case .ConfigurationView:
			ConfigurationView()
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
			case .backup:
				self.navLinkTag = .ConfigurationView
			case .drainWallet:
				self.navLinkTag = .ConfigurationView
			case .electrum:
				self.navLinkTag = .ConfigurationView
			}
		}
	}
}
