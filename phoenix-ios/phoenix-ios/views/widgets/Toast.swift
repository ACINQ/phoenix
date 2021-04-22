import SwiftUI

class Toast: ObservableObject {
	
	enum ToastLocation {
		case bottom
		case middle
	}
	
	@Published private var text: String? = nil
	@Published private var location: ToastLocation = .bottom
	@Published private var showCloseButton: Bool = false
	@Published private var backgroundColor: Color = Color.primaryForeground.opacity(0.6)
	@Published private var foregroundColor: Color = Color.white

	func toast(
		text: String,
		duration: TimeInterval = 1.5,
		location: ToastLocation = .bottom,
		showCloseButton: Bool = false,
		backgroundColor: Color = Color.primaryForeground.opacity(0.6),
		foregroundColor: Color = Color.white
	) {
		
		withAnimation(.linear(duration: 0.15)) {
			self.text = text
			self.location = location
			self.showCloseButton = showCloseButton
			self.backgroundColor = backgroundColor
			self.foregroundColor = foregroundColor
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
					textView(text)
						.padding(.bottom, showCloseButton ? (42 - 16) : 42)
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
	
	@ViewBuilder
	private func textView(_ text: String) -> some View {
		
		Text(text)
			.multilineTextAlignment(.center)
			.foregroundColor(self.foregroundColor)
			.padding()
			.background(
				RoundedRectangle(cornerRadius: .infinity)
					.fill(self.backgroundColor)
			)
			.modifier(
				CloseButtonModifier(
					color: self.backgroundColor,
					visible: showCloseButton,
					onTap: {[weak self] in
						self?.closeToast()
					}
				)
			)
	}
	
	private func closeToast() -> Void {
		
		withAnimation(.linear(duration: 0.15)) {
			self.text = nil
		}
	}
}

struct CloseButtonModifier: ViewModifier {
	
	let color: Color
	let visible: Bool
	let onTap: () -> Void
	
	@ViewBuilder
	func body(content: Content) -> some View {
		
		if visible {
			content
				.padding(.all, 16)
				.background(
					VStack {
						HStack(alignment: VerticalAlignment.top) {
							Spacer()
							Button {
								onTap()
							} label: {
								Image(systemName: "multiply.circle")
									.resizable()
									.foregroundColor(self.color)
									.frame(width: 26, height: 26)
							}
						}
						Spacer()
					}
				)
		} else {
			content
		}
	}
}
