import SwiftUI
import PhoenixShared

fileprivate let filename = "MainView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

enum HeaderButtonHeight: Preference {}

struct MainView: View {
	
	static let idiom = UIDevice.current.userInterfaceIdiom
	
	@EnvironmentObject var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		Group {
			if MainView.idiom == .pad {
				MainView_Big()
			} else {
				MainView_Small()
			}
		}.onAppear {
			onAppear()
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		if AppMigration.shared.didUpdate && AppMigration.shared.currentBuildNumber == "85" {
			if GroupPrefs.shared.isTorEnabled {
				popoverState.display(dismissable: false) {
					V85Popover()
				}
			}
		}
	}
}
