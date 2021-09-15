import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "IntroContainer"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct IntroContainer: View {
	
	@State var introFinished = false
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			if introFinished {
			
				InitializationView()
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
	
	func introScreensFinished() -> Void {
		log.trace("introScreenFinished()")
		
		withAnimation {
			introFinished = true
		}
	}
}
