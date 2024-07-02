import SwiftUI
import PhoenixShared

fileprivate let filename = "ReceiveView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	enum Tab: Int, CaseIterable, Identifiable {
		case lnOffer = 0
		case lnInvoice = 1
		case blockchain = 2

		var id: Self { self }
	}
	
	@State var selectedTab: Tab = .lnOffer
	
	@State var lightningOfferView_didAppear = false
	@State var lightningInvoiceView_didAppear = false
	@State var showSendView = false
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
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
		}
	}
	
	@ViewBuilder
	func customTabView() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			TabView(selection: $selectedTab) {
				
				LightningOfferView(
					mvi: mvi,
					toast: toast,
					didAppear: $lightningOfferView_didAppear,
					showSendView: $showSendView
				)
				
				LightningInvoiceView(
					mvi: mvi,
					toast: toast,
					didAppear: $lightningInvoiceView_didAppear,
					showSendView: $showSendView
				)
				.tag(Tab.lnInvoice)
				
				SwapInView(
					toast: toast
				)
				.tag(Tab.blockchain)
			}
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
			.disabled(selectedTab == Tab.lnInvoice)
			
			Spacer()
			customTabViewStyle_Icons()
			Spacer()
			
			Button {
				moveToNextTab()
			} label: {
				Image(systemName: "chevron.forward")
			}
			.disabled(selectedTab == Tab.blockchain)
			
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
				case .lnOffer:
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
					
				case .lnInvoice:
					Button {
						selectTabWithAnimation(tab)
					} label: {
						Image(systemName: "tag")
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func moveToPreviousTab() {
		log.trace("moveToPreviousTab()")
		
		switch selectedTab {
			case Tab.lnOffer    : break
			case Tab.lnInvoice  : selectTabWithAnimation(.lnOffer)
			case Tab.blockchain : selectTabWithAnimation(.lnInvoice)
		}
	}
	
	func moveToNextTab() {
		log.trace("moveToNextTab()")
		
		switch selectedTab {
			case Tab.lnOffer    : selectTabWithAnimation(.lnInvoice)
			case Tab.lnInvoice  : selectTabWithAnimation(.blockchain)
			case Tab.blockchain : break
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
	/// - LightningOfferView
	/// - LightningInvoiceView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
}
