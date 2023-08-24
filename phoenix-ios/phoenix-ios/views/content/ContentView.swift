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
	
	@State var hasMergedChannelsForSplicing = Prefs.shared.hasMergedChannelsForSplicing
	
	@EnvironmentObject var shortSheetState: ShortSheetState
	@State var shortSheetItem: ShortSheetItem? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	@State var popoverItem: PopoverItem? = nil
	
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
		.onChange(of: lockState.walletExistence) { (newValue: WalletExistence) in
			walletExistenceChanged(newValue)
		}
		.onReceive(Prefs.shared.hasMergedChannelsForSplicingPublisher) { (newValue: Bool) in
			hasMergedChannelsForSplicing = newValue
		}
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
		case .exists:
			if !hasMergedChannelsForSplicing {
				MergeChannelsView(type: .standalone)
			} else {
				MainView()
			}
			
		case .doesNotExist:
			IntroContainer()
			
		case .unknown:
			LoadingView()
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func walletExistenceChanged(_ value: WalletExistence) {
		log.trace("walletExistenceChanged(value = \(value))")
		
		if value == .doesNotExist {
			Prefs.shared.hasMergedChannelsForSplicing = true
		}
	}
	
	func hasMergedChannelsForSplicingChanged(_ value: Bool) {
		log.trace("hasMergedChannelsForSplicingChanged(value = \(value))")
		
		hasMergedChannelsForSplicing = value
	}
}
