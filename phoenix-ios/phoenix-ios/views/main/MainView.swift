import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MainView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum HeaderButtonHeight: Preference {}

struct MainView: View {
	
	let idiom = UIDevice.current.userInterfaceIdiom
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	
	@ViewBuilder
	var body: some View {
		if idiom == .pad {
			MainView_Big()
		} else {
			MainView_Small()
		}
	}
}
