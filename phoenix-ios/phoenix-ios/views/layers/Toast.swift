import SwiftUI

fileprivate let filename = "Toast"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class Toast: ObservableObject {
	
	enum ToastAlignment {
		case top(padding: CGFloat = 45)
		case topLeading(padding: CGFloat = 25)
		case topTrailing(padding: CGFloat = 25)
		case middle
		case bottom(padding: CGFloat = 45)
		case none
	}
	
	enum ToastStyle {
		case ultraThin
		case thin
		case regular
		case chrome
		case thick
	}
	
	private var contentId: Int = 0
	
	@Published private var message: String? = nil
	@Published private var colorScheme: ColorScheme = ColorScheme.light
	@Published private var style: ToastStyle = .regular
	@Published private var alignment: ToastAlignment = .bottom()
	@Published private var transition: AnyTransition = .opacity
	@Published private var showCloseButton: Bool = false
	
	func pop(
		_ message: String,
		colorScheme: ColorScheme,
		style: ToastStyle = .regular,
		duration: TimeInterval = 1.5,
		alignment: ToastAlignment = .bottom(),
		transition: AnyTransition = .opacity,
		showCloseButton: Bool = false
	) {
		
		self.contentId += 1
		let prvContentId = contentId
		
		withAnimation(.linear(duration: 0.15)) {
			self.message = message
			self.colorScheme = colorScheme
			self.style = style
			self.alignment = alignment
			self.transition = transition
			self.showCloseButton = showCloseButton
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
			withAnimation(.linear(duration: 0.15)) {
				if self.contentId == prvContentId {
					self.message = nil
				}
			}
		}
	}
	
	@ViewBuilder
	func view() -> some View {
		
		if let message = message {
			aligned(message)
				.transition(transition)
				.zIndex(1001)
				.onAppear {
					self.onAppear()
				}
		}
	}
	
	@ViewBuilder
	private func aligned(_ message: String) -> some View {
		
		switch alignment {
		case .top(let padding):
			VStack {
				wrapped(message).padding(.top, padding)
				Spacer()
			}
		case .topLeading(let padding):
			VStack {
				HStack {
					wrapped(message).padding(.leading, padding)
					Spacer()
				}
				Spacer()
			}
		case .topTrailing(let padding):
			VStack {
				HStack {
					Spacer()
					wrapped(message).padding(.trailing, padding)
				}
				Spacer()
			}
		case .middle:
			VStack {
				Spacer()
				wrapped(message)
				Spacer()
			}
		case .bottom(let padding):
			VStack {
				Spacer()
				wrapped(message).padding(.bottom, padding)
			}
		case .none:
			wrapped(message)
		}
	}
	
	@ViewBuilder
	private func wrapped(_ message: String) -> some View {
		
		if showCloseButton {
			withBlurBackground(message)
				.padding(.all, 18)
				.background(closeButton())
				.padding(.all, 4)
			
		} else {
			withBlurBackground(message)
				.padding()
		}
	}
	
	@ViewBuilder
	private func withBlurBackground(_ message: String) -> some View {
		
		text(message)
			.environment(\.colorScheme, self.colorScheme)
			.padding()
			.background(
				VisualEffectView(style: blurEffectStyle())
					.clipShape(Capsule())
			)
	}
	
	@ViewBuilder
	private func text(_ message: String) -> some View {
		
		Text(message).multilineTextAlignment(.center)
	}
	
	@ViewBuilder
	private func closeButton() -> some View {
		
		VStack {
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer()
				CloseButtonView(
					colorScheme: colorScheme,
					blurEffectStyle: blurEffectStyle(),
					onTap: closeToast
				)
			}
			Spacer()
		}
	}
	
	private func blurEffectStyle() -> UIBlurEffect.Style {
		
		let isDark = self.colorScheme == .dark
		switch self.style {
			case .ultraThin : return isDark ? .systemUltraThinMaterialDark : .systemUltraThinMaterialLight
			case .thin      : return isDark ? .systemThinMaterialDark      : .systemThinMaterialLight
			case .regular   : return isDark ? .systemMaterialDark          : .systemMaterialLight
			case .chrome    : return isDark ? .systemChromeMaterialDark    : .systemChromeMaterialLight
			case .thick     : return isDark ? .systemThickMaterialDark     : .systemThickMaterialLight
		}
	}
	
	private func closeToast() {
		
		withAnimation(.linear(duration: 0.15)) {
			self.message = nil
		}
	}
	
	private func onAppear() {
		log.trace("onAppear()")
		
		if let message = message {
			UIAccessibility.post(notification: .announcement, argument: message)
		}
	}
}

struct CloseButtonView: View {
	
	let colorScheme: ColorScheme
	let blurEffectStyle: UIBlurEffect.Style
	let onTap: () -> Void
	
	@ViewBuilder
	var body: some View {
		
		Button {
			onTap()
		} label: {
			Image(systemName: "multiply")
				.resizable()
				.foregroundColor(Color(UIColor.label))
				.frame(width: 16, height: 16)
				.environment(\.colorScheme, colorScheme)
				.padding(.all, 8)
				.background(
					VisualEffectView(style: blurEffectStyle)
						.clipShape(Capsule())
				)
		}
	}
}

struct VisualEffectView: UIViewRepresentable {
	let style: UIBlurEffect.Style
	
	func makeUIView(context: UIViewRepresentableContext<Self>) -> UIVisualEffectView {
		return UIVisualEffectView(effect: UIBlurEffect(style: style))
	}
	func updateUIView(_ uiView: UIVisualEffectView, context: UIViewRepresentableContext<Self>) {}
}
