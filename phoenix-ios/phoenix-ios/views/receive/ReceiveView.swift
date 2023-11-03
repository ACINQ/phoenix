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

struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	enum Tab: Int, CaseIterable, Identifiable {
		case lightning = 0
		case blockchain = 1

		var id: Self { self }
	}
	
	@State var selectedTab: Tab = .lightning
	
	@State var receiveLightningView_didAppear = false
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
				
				ReceiveLightningView(
					mvi: mvi,
					toast: toast,
					didAppear: $receiveLightningView_didAppear,
					showSendView: $showSendView
				)
				.tag(Tab.lightning)
				
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
			.disabled(selectedTab == Tab.lightning)
			
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func moveToPreviousTab() {
		log.trace("moveToPreviousTab()")
		
		switch selectedTab {
			case Tab.lightning  : break
			case Tab.blockchain : selectTabWithAnimation(.lightning)
		}
	}
	
	func moveToNextTab() {
		log.trace("moveToNextTab()")
		
		switch selectedTab {
			case Tab.lightning  : selectTabWithAnimation(.blockchain)
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
	/// - ReceiveLightningView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
}
