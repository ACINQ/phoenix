import SwiftUI

class Toast: ObservableObject {
	
	enum ToastLocation {
		case bottom
		case middle
	}
	
	@Published private var text: String? = nil
	@Published private var location: ToastLocation = .bottom

	func toast(text: String, duration: Double = 1.5, location: ToastLocation = .bottom) {
		
		withAnimation(.linear(duration: 0.15)) {
			self.text = text
			self.location = location
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
			withAnimation(.linear(duration: 0.15)) {
				self.text = nil
			}
		}
	}

	@ViewBuilder
	func view() -> some View {
		
		if let text = text {
			
			VStack {
				switch location {
				case .bottom:
					Spacer()
					Text(text).padding([.bottom], 42)
				case .middle:
					Spacer()
					textView(text)
					Spacer()
				}
			}
			.transition(.opacity)
			.zIndex(1001)
		}
	}
	
	func textView(_ text: String) -> some View {
		
		Text(text)
			.padding()
			.foregroundColor(.white)
			.background(Color.primaryForeground.opacity(0.4))
			.cornerRadius(42)
	}
}
