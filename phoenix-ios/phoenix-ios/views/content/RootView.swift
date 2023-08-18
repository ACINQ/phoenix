import SwiftUI

struct RootView: View {
	
	@ViewBuilder
	var body: some View {
		
		GeometryReader { geometry in
			ContentView()
				.frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
				.modifier(GlobalEnvironment.mainInstance())
				.onAppear {
					GlobalEnvironment.deviceInfo._windowSize = geometry.size
					GlobalEnvironment.deviceInfo.windowSafeArea = geometry.safeAreaInsets
				}
				.onChange(of: geometry.size) { newSize in
					GlobalEnvironment.deviceInfo._windowSize = newSize
				}
				.onChange(of: geometry.safeAreaInsets) { newValue in
					GlobalEnvironment.deviceInfo.windowSafeArea = newValue
				}
		} // </GeometryReader>
	}
}
