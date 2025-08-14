import SwiftUI

fileprivate let filename = "LockContainer"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LockContainer: View {
	
	let target: LockViewTarget
	@State var visible: Bool
	
	@ObservedObject var appState = AppState.shared
	
	init(target: LockViewTarget) {
		self.target = target

		switch target {
			case .automatic      : self.visible = true
			case .walletSelector : self.visible = false
		}
	}
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.environmentObject(GlobalEnvironment.deviceInfo)
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
				LockView(target: target)
					.zIndex(1)
					.transition(.asymmetric(
						insertion : target == .walletSelector ? .move(edge: .bottom) : .identity,
						removal   : .move(edge: .bottom)
					))
			}
			
		} // </ZStack>
	}
	
	func onAppear() {
		log.trace(#function)
		
		if target == .walletSelector {
			withAnimation {
				visible = true
			}
		}
	}
	
	func isUnlockedChanged() {
		log.trace(#function)
		
		if appState.isUnlocked {
			visible = false
		}
	}
}
