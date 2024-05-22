import SwiftUI

fileprivate let filename = "LoadingView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LoadingView: View {
	
	@ObservedObject var lockState = LockState.shared
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.top) {
			
			Color.clear   // an additional layer
				.zIndex(0) // for animation purposes
			
			if !lockState.isUnlocked {
				content()
					.zIndex(1)
					.transition(.asymmetric(
						insertion : .identity,
						removal   : .move(edge: .bottom)
					))
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
			}
			.frame(maxWidth: .infinity)
		}
		.background(Color(UIColor.systemBackground))
	}
	
	@ViewBuilder
	func logoContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Image(logoImageName)
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
			
			if !lockState.migrationStepsCompleted {
				Text("Updating internals…")
			} else if !lockState.protectedDataAvailable {
				Text("Waiting for keychain…")
			} else {
				Text("Loading…")
			}
		}
	}
	
	var logoImageName: String {
		if BusinessManager.isTestnet {
			return "logo_blue"
		} else {
			return "logo_green"
		}
	}
}

