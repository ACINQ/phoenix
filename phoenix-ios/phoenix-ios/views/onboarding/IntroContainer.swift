import SwiftUI

fileprivate let filename = "IntroContainer"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct IntroContainer: View {
	
	let animateIn: Bool
	let isCancellable: Bool
	
	@State var visible: Bool
	@State var introFinished = false
	
	@StateObject var navCoordinator = NavigationCoordinator()
	
	@ObservedObject var appState = AppState.shared
	
	init(animateIn: Bool, isCancellable: Bool) {
		self.animateIn = animateIn
		self.isCancellable = isCancellable
		
		self._visible = State(initialValue: !animateIn)
	}
	
	@ViewBuilder
	var body: some View {
		
		GlobalEnvironmentView {
			layers()
		}
		.onAppear {
			onAppear()
		}
		.onChange(of: appState.isUnlocked) { _ in
			isUnlockedChanged()
		}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack(alignment: Alignment.top) {
			
			Color.clear   // an additional layer
				.zIndex(0) // for animation purposes
			
			if visible {
				navStack()
					.zIndex(1)
					.transition(.asymmetric(
						insertion : animateIn ? .move(edge: .bottom) : .identity,
						removal   : .move(edge: .bottom)
					))
			}
		}
	}
	
	@ViewBuilder
	func navStack() -> some View {
		
		NavigationStack(path: $navCoordinator.path) {
			content()
		}
		.environmentObject(navCoordinator)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack {
			if introFinished {
				InitializationView(isCancellable: isCancellable)
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
	
	func onAppear() {
		log.trace(#function)
		
		if !visible {
			withAnimation {
				visible = true
			}
		}
	}
	
	func introScreensFinished() {
		log.trace(#function)
		
		withAnimation {
			introFinished = true
		}
	}
	
	func isUnlockedChanged() {
		log.trace(#function)
		
		if appState.isUnlocked {
			visible = false
		}
	}
}
