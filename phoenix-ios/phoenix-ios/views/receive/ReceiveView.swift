import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum SelectedTab {
	case lightning
	case blockchain
}

struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var selectedTab: SelectedTab = .lightning
	
	@State var lastDescription: String? = nil
	@State var lastAmount: Lightning_kmpMilliSatoshi? = nil
	
	@State var receiveLightningView_didAppear = false
	
	@State var swapIn_enabled = true
	
	enum TabBarButtonHeight: Preference {}
	let tabBarButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<TabBarButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var tabBarButtonHeight: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var popoverState: PopoverState
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}
			
			customTabView()
			
			toast.view()
		}
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
	}
	
	@ViewBuilder
	func customTabView() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			switch selectedTab {
			case .lightning:
				ReceiveLightningView(
					mvi: mvi,
					toast: toast,
					didAppear: $receiveLightningView_didAppear
				)
				
			case .blockchain:
				SwapInView(
					mvi: mvi,
					toast: toast,
					lastDescription: $lastDescription,
					lastAmount: $lastAmount
				)
			}
			
			customTabBar()
				.padding(.top, 20)
				.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
				.background(
					Color.mutedBackground
						.cornerRadius(15, corners: [.topLeft, .topRight])
						.edgesIgnoringSafeArea([.horizontal, .bottom])
				)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func customTabBar() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer(minLength: 2)
			
			Button {
				didSelectTab(.lightning)
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "bolt").font(.title2).imageScale(.large)
					VStack(alignment: HorizontalAlignment.center, spacing: 4) {
						Text("Lightning")
						Text("(layer 2)").font(.footnote.weight(.thin)).opacity(0.7)
					}
				}
			}
			.foregroundColor(selectedTab == .lightning ? Color.appAccent : Color.primary)
			.read(tabBarButtonHeightReader)
			
			Spacer(minLength: 2)
			if let tabBarButtonHeight {
				Divider().frame(width: 1, height: tabBarButtonHeight).background(Color.borderColor)
				Spacer(minLength: 2)
			}
			
			Button {
				didSelectTab(.blockchain)
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "link").font(.title2).imageScale(.large)
					VStack(alignment: HorizontalAlignment.center, spacing: 4) {
						Text("Blockchain")
						Text("(layer 1)").font(.footnote.weight(.thin)).opacity(0.7)
					}
				}
			}
			.foregroundColor(selectedTab == .blockchain ? Color.appAccent : Color.primary)
			.read(tabBarButtonHeightReader)
			
			Spacer(minLength: 2)
			
		} // </HStack>
		.clipped() // SwiftUI force-extends height of button to bottom of screen for some odd reason
		.assignMaxPreference(for: tabBarButtonHeightReader.key, to: $tabBarButtonHeight)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			lastDescription = m.desc
			lastAmount = m.amount
		}
	}
	
	func didSelectTab(_ tab: SelectedTab) {
		
		switch tab {
		case .lightning:
			mvi.intent(Receive.IntentAsk(
				amount: lastAmount,
				desc: lastDescription,
				expirySeconds: Prefs.shared.invoiceExpirationSeconds
			))
			
		case .blockchain:
			if swapIn_enabled {
				mvi.intent(Receive.IntentRequestSwapIn())
			} else {
				popoverState.display(dismissable: true) {
					SwapInDisabledPopover()
				}
			}
		}
		
		selectedTab = tab
	}
	
	// --------------------------------------------------
	// MARK: Static Shared
	// --------------------------------------------------
	
	/// Shared logic. Used by:
	/// - ReceiveLightningView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
}
