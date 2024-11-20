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
	
	@ViewBuilder
	var body: some View {
		if MainView.idiom == .pad {
			MainView_Big()
		} else {
			MainView_Small()
		}
	}
}
