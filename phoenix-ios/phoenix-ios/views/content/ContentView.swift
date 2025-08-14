import SwiftUI
import PhoenixShared

fileprivate let filename = "ContentView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContentView: View {
	
	@ObservedObject var appState = AppState.shared
	@State var unlockedOnce = false
	
	@ViewBuilder
	var body: some View {
		
		GlobalEnvironmentView {
			if appState.isUnlocked || unlockedOnce {
				MainView()
					.onAppear {
						log.debug("unlockedOnce = true")
						unlockedOnce = true
					}

			} else {
				EmptyView()
			}
		}
	}
}
