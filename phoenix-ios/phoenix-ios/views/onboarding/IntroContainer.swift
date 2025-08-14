import SwiftUI

fileprivate let filename = "IntroContainer"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct IntroContainer: View {
	
	@State var introFinished = false
	
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@ViewBuilder
	var body: some View {
		
		NavigationStack(path: $navCoordinator.path) {
			content()
		}
		.environmentObject(navCoordinator)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack {
			
			if introFinished {
			
				InitializationView(location: .introContainer)
					.zIndex(0)
			
			} else {
				
				IntroView(finish: introScreensFinished)
					.zIndex(1) // needed for proper animation
					.transition(.asymmetric(
						insertion : .identity,
						removal   : .move(edge: .bottom)
					))
			}
		}
	}
	
	func introScreensFinished() {
		log.trace("introScreenFinished()")
		
		withAnimation {
			introFinished = true
		}
	}
}
