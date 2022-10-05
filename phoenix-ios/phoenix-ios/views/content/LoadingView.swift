import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LoadingView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
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
			
			let topPadding = geometry.size.height / 4.0
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				logoContent()
					.padding(.bottom, 40)
				loadingContent()
			}
			.padding(.top, topPadding)
			.frame(maxWidth: .infinity)
		}
		.background(Color(UIColor.systemBackground))
		.edgesIgnoringSafeArea(.all)
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
				
			} else if !lockState.firstUnlockAttempted {
				Text("Loading…")
				
			} else if lockState.firstUnlockFoundMnemonics {
				Text("Decrypting wallet…")
				
			} else {
				Text("…")
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

