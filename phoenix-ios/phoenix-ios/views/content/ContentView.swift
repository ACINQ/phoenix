import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
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

	@ObservedObject var lockState = LockState.shared
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

			if (lockState.isUnlocked || unlockedOnce) && !isWaitingForKeychainOrWallet() {
				
				primaryView()
					.zIndex(0) // needed for proper animation
					.onAppear {
						unlockedOnce = true
					}
					.accessibilityHidden(shortSheetItem != nil || popoverItem != nil)

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
				
				LoadingView()
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
	
	func isWaitingForKeychainOrWallet() -> Bool {
		
		// Here's the UI transition sequence that we want:
		//
		// - No wallet available:
		//   - LoadingView -> IntroContainer
		// - Wallet available & unlocked:
		//   - LoadingView -> MainView
		// - Wallet available & locked:
		//   - LoadingView -> LockView
		//
		// However, there are various delays that we have to work around:
		//
		// - On app launch, we might encounter `protectedDataAvailable == false`.
		//   This means we have to wait until the OS sends us a notification.
		//
		// - Querying the keychain takes a variable amount of time.
		//   So we need to wait to hear back about the status of our wallet & lock state.
		//
		// - If the wallet's seed is available, we can call `loadWallet()`,
		//   but it takes a moment before we receive the updated Model_IsInitialized.
		//
		// So we have these checks to ensure we don't show any other view preemptively.
		
		if !lockState.firstUnlockAttempted {
			// Waiting for first keychain read
			return true
		}
		if lockState.foundMnemonics && mvi.model is Content.ModelNeedInitialization {
			// We're in the process of unlocking the wallet.
			return true
		}
		
		return false
	}
}
