import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ContentView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct GlobalEnvironment: ViewModifier {
	static var deviceInfo = DeviceInfo()
	static var currencyPrefs = CurrencyPrefs()
	static var deepLinkManager = DeepLinkManager()

	func body(content: Self.Content) -> some View {
		content
			.environmentObject(Self.deviceInfo)
			.environmentObject(Self.currencyPrefs)
			.environmentObject(Self.deepLinkManager)
	}
}

struct ContentView: MVIView {

	@StateObject var mvi = MVIState({ $0.content() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@ObservedObject var lockState: LockState
	@State var unlockedOnce = false
	
	@Environment(\.shortSheetState) private var shortSheetState: ShortSheetState
	@State private var shortSheetItem: ShortSheetItem? = nil
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverItem: PopoverItem? = nil
	
	@ViewBuilder
	var view: some View {
		
		GeometryReader { geometry in
			content
				.frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
				.modifier(GlobalEnvironment())
				.onAppear {
					GlobalEnvironment.deviceInfo._windowSize = geometry.size
					GlobalEnvironment.deviceInfo.windowSafeArea = geometry.safeAreaInsets
				}
				.onChange(of: geometry.size) { newSize in
					GlobalEnvironment.deviceInfo._windowSize = newSize
				}
				.onChange(of: geometry.safeAreaInsets) { newValue in
					GlobalEnvironment.deviceInfo.windowSafeArea = newValue
				}
		} // </GeometryReader>
	}
	
	@ViewBuilder
	var content: some View {

		ZStack {

			if lockState.isUnlocked || unlockedOnce {
				
				primaryView()
					.zIndex(0) // needed for proper animation
					.onAppear {
						unlockedOnce = true
					}
					.accessibilityHidden(shortSheetItem != nil)

				if let shortSheetItem = shortSheetItem {
					ShortSheetWrapper(dismissable: shortSheetItem.dismissable) {
						shortSheetItem.view
					}
					.zIndex(1) // needed for proper animation
				}

				if let popoverItem = popoverItem {
					PopoverWrapper(dismissable: popoverItem.dismissable) {
						popoverItem.view
					}
					.zIndex(2) // needed for proper animation
				}

			} else { // prior to first unlock
				
				loadingView()
					.zIndex(3) // needed for proper animation
					.transition(.asymmetric(
						insertion : .identity,
						removal   : .opacity
					))
			}
			
		} // </ZStack>
		.onReceive(shortSheetState.publisher) { (item: ShortSheetItem?) in
			withAnimation {
				shortSheetItem = item
			}
		}
		.onReceive(popoverState.publisher) { (item: PopoverItem?) in
			withAnimation {
				popoverItem = item
			}
		}
	}

	@ViewBuilder
	func primaryView() -> some View {

		if mvi.model is Content.ModelIsInitialized {
			MainView()
		} else if mvi.model is Content.ModelNeedInitialization {
			IntroContainer()
		} else {
			loadingView()
		}
	}

	@ViewBuilder
	func loadingView() -> some View {

		VStack {
			Image(systemName: "arrow.triangle.2.circlepath")
				.imageScale(.large)
		}
		.edgesIgnoringSafeArea(.all)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
	}
}


class ContentView_Previews: PreviewProvider {

	static var lockState_true = LockState(isUnlocked: true)
	static var lockState_false = LockState(isUnlocked: false)
	
	static var previews: some View {
		
		ContentView(lockState: lockState_true)
			.mock(Content.ModelWaiting())
			.previewDevice("iPhone 11")
		
//		ContentView(lockState: lockState_true)
//			.mock(Content.ModelNeedInitialization())
//			.previewDevice("iPhone 11")
		
//		ContentView(lockState: lockState_false)
//			.mock(Content.ModelIsInitialized())
//			.previewDevice("iPhone 11")
	}
}
