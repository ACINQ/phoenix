import SwiftUI
import Combine

/// The PopoverState is exposed via an Environment variable:
/// Just add this to your view:
/// ```
/// @Environment(\.popoverState) var popoverState: PopoverState
/// ```
///
public class PopoverState: ObservableObject {

	/// When you want to present a popover, just send it through this signal
	public var displayContent = PassthroughSubject<AnyView, Never>()

	/// Whether or not the popover is dimissable by clicking outside the popover.
	public var dismissable = CurrentValueSubject<Bool, Never>(true)
	
	/// When you want to close the popover, just send an update on this signal
	public var close = PassthroughSubject<Void, Never>()
}

struct PopoverEnvironmentKey: EnvironmentKey {

	static var defaultValue = PopoverState()
}

public extension EnvironmentValues {

	var popoverState: PopoverState {
		get { self[PopoverEnvironmentKey.self] }
		set { self[PopoverEnvironmentKey.self] = newValue }
	}
}

public extension View {

	/// Helper to create `AnyView` from view
	var anyView: AnyView {
		AnyView(self)
	}
}

struct PopoverWrapper<Content: View>: View {

	let content: () -> Content
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			VStack {
				VStack {
					content()
				}
				.padding(.all, 20)
				.background(Color(UIColor.systemBackground))
				.cornerRadius(16)
			}
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color(UIColor.secondarySystemBackground), lineWidth: 1.0)
			)
			.padding(.all, 20)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.edgesIgnoringSafeArea(.all)
		.transition(.opacity)
		.background(
			Color.primary.opacity(0.4)
				.edgesIgnoringSafeArea(.all)
				.onTapGesture {
					if popoverState.dismissable.value {
						popoverState.close.send()
					}
				}
		)
	}
}

