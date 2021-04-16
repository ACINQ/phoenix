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
	static var currencyPrefs = CurrencyPrefs()

	func body(content: Self.Content) -> some View {
		content
			.environmentObject(Self.currencyPrefs)
	}
}

struct ContentView: MVIView {

	@StateObject var mvi = MVIState({ $0.content() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@ObservedObject var lockState: LockState
	@State var unlockedOnce = false
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverItem: PopoverItem? = nil
	
	@ViewBuilder
	var view: some View {

		ZStack {
			
			if lockState.isUnlocked || unlockedOnce {
			
				primaryView()
					.zIndex(0) // needed for proper animation
					.onAppear {
						unlockedOnce = true
					}

				if let popoverItem = popoverItem {
					PopoverWrapper(dismissable: popoverItem.dismissable) {
						popoverItem.view
					}
					.zIndex(1) // needed for proper animation
				}
			
			} else { // prior to first unlock

				NavigationView {
					loadingView()
				}
				.zIndex(2) // needed for proper animation
				.transition(.asymmetric(
					insertion : .identity,
					removal   : .opacity
				))
			}
			
		} // </ZStack>
		.modifier(GlobalEnvironment())
		.onReceive(popoverState.display) { (newPopoverItem: PopoverItem) in
			withAnimation {
				popoverItem = newPopoverItem
			}
		}
		.onReceive(popoverState.close) { _ in
			withAnimation {
				popoverItem = nil
			}
		}
	}

	@ViewBuilder
	func primaryView() -> some View {

		NavigationView {

			if mvi.model is Content.ModelIsInitialized {
				HomeView()
			} else if mvi.model is Content.ModelNeedInitialization {
				IntroContainer()
			} else {
				loadingView()
			}
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
