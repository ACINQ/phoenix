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


struct ContentView: View {
	
	@ObservedObject var lockState = LockState.shared
	@State var unlockedOnce = false
	
	@Environment(\.shortSheetState) private var shortSheetState: ShortSheetState
	@State private var shortSheetItem: ShortSheetItem? = nil
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverItem: PopoverItem? = nil
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			if lockState.isUnlocked || unlockedOnce {
				
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

		switch lockState.walletExistence {
			case .exists       : MainView()
			case .doesNotExist : IntroContainer()
			case .unknown      : LoadingView()
		}
	}
}
