import SwiftUI

fileprivate let filename = "LoadingView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LoadingView: View {
	
	@ObservedObject var appState = AppState.shared
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.top) {
			
			Color.clear   // an additional layer
				.zIndex(0) // for animation purposes
			
			if !appState.isUnlocked {
				content()
					.zIndex(1)
				//	.transition(.asymmetric(
				//		insertion : .identity,
				//		removal   : .move(edge: .bottom)
				//	))
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		// The layout & math here is designed to match the LockView.
		// 
		// That is, both the LoadingView & LockView are designed to place
		// the logoContent at the exact same coordinates.
		// So that a transition from the LoadingView to the LockView is smooth.
		
		GeometryReader { geometry in
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: geometry.size.height / 4.0)
					.layoutPriority(-2)
				
				logoContent()
				
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: 50)
					.layoutPriority(-1)
				
				loadingContent()
				
				Spacer(minLength: 0)
					.layoutPriority(-3)
			}
			.frame(maxWidth: .infinity, maxHeight: .infinity)
		}
		.background(Color(UIColor.systemBackground))
	}
	
	@ViewBuilder
	func logoContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Image(Biz.isTestnet ? "logo_blue" : "logo_green")
				.resizable()
				.frame(width: 96, height: 96)

			Text("Phoenix")
				.font(Font.title2)
				.padding(.top, -5)
		}
	}
	
	@ViewBuilder
	func loadingContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			if !appState.migrationStepsCompleted {
				Text("Updating internals…")
			} else if !appState.protectedDataAvailable {
				Text("Waiting for keychain…")
			} else {
				Text("Loading…")
			}
		}
	}
}
