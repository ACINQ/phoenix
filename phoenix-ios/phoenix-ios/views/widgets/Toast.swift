import SwiftUI

class Toast: ObservableObject {
	
	enum ToastAlignment {
		case top
		case middle
		case bottom
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
	
	@Published private var content: AnyView? = nil
	
	@Published private var colorScheme: ColorScheme = ColorScheme.light
	@Published private var style: ToastStyle = .regular
	@Published private var alignment: ToastAlignment = .bottom
	@Published private var showCloseButton: Bool = false
	
	func pop(
		_ content: AnyView,
		colorScheme: ColorScheme,
		style: ToastStyle = .regular,
		duration: TimeInterval = 1.5,
		alignment: ToastAlignment = .bottom,
		showCloseButton: Bool = false
	) {
		
		self.contentId += 1
		let prvContentId = contentId
		
		withAnimation(.linear(duration: 0.15)) {
			self.content = content
			self.colorScheme = colorScheme
			self.style = style
			self.alignment = alignment
			self.showCloseButton = showCloseButton
		}
		DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
			withAnimation(.linear(duration: 0.15)) {
				if self.contentId == prvContentId {
					self.content = nil
				}
			}
		}
	}
	
	@ViewBuilder
	func view() -> some View {
		
		if let content = content {
			aligned(content)
				.transition(.opacity)
				.zIndex(1001)
		}
	}
	
	@ViewBuilder
	private func aligned(_ content: AnyView) -> some View {
		
		switch alignment {
		case .top:
			VStack {
				wrapped(content).padding(.top, 45)
				Spacer()
			}
		case .middle:
			VStack {
				Spacer()
				wrapped(content)
				Spacer()
			}
		case .bottom:
			VStack {
				Spacer()
				wrapped(content).padding(.bottom, 45)
			}
		case .none:
			wrapped(content)
		}
	}
	
	@ViewBuilder
	private func wrapped(_ content: AnyView) -> some View {
		
		if showCloseButton {
			withBlurBackground(content)
				.padding(.all, 18)
				.background(closeButton())
				.padding(.all, 4)
			
		} else {
			withBlurBackground(content)
				.padding()
		}
	}
	
	@ViewBuilder
	private func withBlurBackground(_ content: AnyView) -> some View {
		
		content
			.environment(\.colorScheme, self.colorScheme)
			.padding()
			.background(
				VisualEffectView(style: blurEffectStyle())
					.clipShape(Capsule())
			)
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
	
	private func closeToast() -> Void {
		
		withAnimation(.linear(duration: 0.15)) {
			self.content = nil
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
