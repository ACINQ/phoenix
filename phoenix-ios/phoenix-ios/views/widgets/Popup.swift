import SwiftUI
import Combine


struct Popup<Content: View>: View {

    let content: (() -> Content)?

    @State var keyboardHeight: CGFloat = 0

    init(show: Bool, @ViewBuilder content: @escaping () -> Content) {
        if (show) {
            self.content = content
        } else {
            self.content = nil
        }
    }

    init<T>(of: T?, @ViewBuilder content: @escaping (T) -> Content) {
        if let value = of {
            self.content = { content(value) }
        } else {
            self.content = nil
        }
    }

	var body: some View {
		Group {
			if let content = self.content {
				VStack {
					VStack(alignment: .leading) {
						content()
					}
					.frame(maxWidth: .infinity)
					.background(Color(UIColor.secondarySystemBackground))
					.cornerRadius(15)
					.padding(32)
					.padding([.bottom], keyboardHeight * 0.8)
				}
				.frame(maxWidth: .infinity, maxHeight: .infinity)
				.background(Color.black.opacity(0.25))
				.edgesIgnoringSafeArea(.all)
				.zIndex(1000)
				.transition(.opacity)
			}
		}
		.onReceive(Publishers.keyboardInfo) { keyboardInfo in
			
			var animation: Animation
			switch keyboardInfo.animationCurve {
				case .linear  : animation = Animation.linear(duration: keyboardInfo.animationDuration)
				case .easeIn  : animation = Animation.easeIn(duration: keyboardInfo.animationDuration)
				case .easeOut : animation = Animation.easeOut(duration: keyboardInfo.animationDuration)
				default       : animation = Animation.easeInOut(duration: keyboardInfo.animationDuration)
			}
			
			withAnimation(animation) {
				keyboardHeight = keyboardInfo.height
			}
		}
	}
}
