import SwiftUI
import PhoenixShared

fileprivate let filename = "ReceiveView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ReceiveView: MVIView {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		
		case CurrencyConverter(
			initialAmount: CurrencyAmount?,
			didChange: ((CurrencyAmount?) -> Void)?,
			didClose: (() -> Void)?
		)
		
		private var internalValue: Int {
			switch self {
				case .CurrencyConverter(_, _, _): return 1
			}
		}
		
		static func == (lhs: NavLinkTag, rhs: NavLinkTag) -> Bool {
			return lhs.internalValue == rhs.internalValue
		}
		
		func hash(into hasher: inout Hasher) {
			hasher.combine(self.internalValue)
		}
		
		var description: String {
			switch self {
				case .CurrencyConverter: return "CurrencyConverter"
			}
		}
	}

	// The order of items within this enum controls the order in the UI.
	// If you change the order, you might also consider changing the initial value for `selectedTab`.
	enum Tab: CaseIterable, Identifiable, CustomStringConvertible {
		case lightning
		case blockchain
 
		var id: Self { self }

		var description: String {
			switch self {
				case .lightning  : return "lightning"
				case .blockchain : return "blockchain"
			}
		}

		func previous() -> Tab? {
			var previous: Tab? = nil
			for t in Tab.allCases {
				if t == self {
					return previous
				} else {
					previous = t
				}
			}
			return nil
		}

		func next() -> Tab? {
			var found = false
			for t in Tab.allCases {
				if t == self {
					found = true
				} else if found {
					return t
				}
			}
			return nil
		}
	}
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var selectedTab: Tab = .lightning
	
	@State var lightningInvoiceView_didAppear = false
	@State var showSendView = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@StateObject var inboundFeeState = InboundFeeState()
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		layers()
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
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
			toast.view()
			
		} // </ZStack>
	}
	
	@ViewBuilder
	func content() -> some View {
		
		if showSendView {
			SendView(location: .ReceiveView)
				.zIndex(5) // needed for proper animation
				.transition(
					.asymmetric(
						insertion: .move(edge: .bottom),
						removal: .move(edge: .bottom)
					)
				)
			
		} else {
			customTabView()
				.zIndex(4) // needed for proper animation
				.transition(
					.asymmetric(
						insertion: .identity,
						removal: .opacity
					)
				)
				.navigationBarItems(trailing: scanButton())
		}
	}
	
	@ViewBuilder
	func customTabView() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			TabView(selection: $selectedTab) {
				ForEach(Tab.allCases) { tab in
					
					switch tab {
					case .lightning:
						LightningDualView(
							mvi: mvi,
							inboundFeeState: inboundFeeState,
							toast: toast,
							didAppear: $lightningInvoiceView_didAppear,
							navigateTo: navigateTo
						)
						.tag(Tab.lightning)
						
					case .blockchain:
						SwapInView(
							toast: toast
						)
						.tag(Tab.blockchain)
						
					} // </switch>
				} // </ForEach>
			} // </TabView>
			.tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
			
			customTabViewStyle()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func customTabViewStyle() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			customTabViewStyle_IconsRow()
		}
		.padding(.horizontal)
		.padding(.top, 20)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
	}
	
	@ViewBuilder
	func customTabViewStyle_IconsRow() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Button {
				moveToPreviousTab()
			} label: {
				Image(systemName: "chevron.backward")
			}
			.disabled(selectedTab == Tab.allCases.first!)
			
			Spacer()
			customTabViewStyle_Icons()
			Spacer()
			
			Button {
				moveToNextTab()
			} label: {
				Image(systemName: "chevron.forward")
			}
			.disabled(selectedTab == Tab.allCases.last!)
			
		} // </HStack>
	}
	
	@ViewBuilder
	func customTabViewStyle_Icons() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 15) {
			ForEach(Tab.allCases) { tab in
				
				let isSelected = (tab == selectedTab)
				let imageSize: CGFloat = isSelected ? 30 : 15
				let imageColor: Color = isSelected ? Color.primary : Color.secondary
				
				switch tab {
				case .lightning:
					Button {
						selectTabWithAnimation(tab)
					} label: {
						Image(systemName: "bolt")
							.resizable()
							.scaledToFill()
							.frame(width: imageSize, height: imageSize)
							.foregroundColor(imageColor)
					}
					.disabled(isSelected)
					
				case .blockchain:
					Button {
						selectTabWithAnimation(tab)
					} label: {
						Image(systemName: "link")
							.resizable()
							.scaledToFill()
							.frame(width: imageSize, height: imageSize)
							.foregroundColor(imageColor)
					}
					.disabled(isSelected)
					
				} // </switch tab>
			} // </ForEach>
		} // </HStack>
	}
	
	@ViewBuilder
	func scanButton() -> some View {
		
		Button {
			withAnimation {
				showSendView = true
			}
		} label: {
			Image(systemName: "qrcode.viewfinder")
		}
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
		case .CurrencyConverter(let initialAmount, let didChange, let didClose):
			CurrencyConverterView(
				initialAmount: initialAmount,
				didChange: didChange,
				didClose: didClose
			)
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
	
	func moveToPreviousTab() {
		log.trace("moveToPreviousTab()")
		
		if let previousTag = selectedTab.previous() {
			selectTabWithAnimation(previousTag)
		}
	}
	
	func moveToNextTab() {
		log.trace("moveToNextTab()")
		
		if let nextTab = selectedTab.next() {
			selectTabWithAnimation(nextTab)
		}
	}
	
	func selectTabWithAnimation(_ tab: Tab) {
		
		withAnimation {
			selectedTab = tab
			UIAccessibility.post(notification: .screenChanged, argument: nil)
		}
	}
	
	// --------------------------------------------------
	// MARK: Static Shared
	// --------------------------------------------------
	
	/// Shared logic. Used by:
	/// - LightningDualView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
}
