import SwiftUI
import Combine

/// The PopoverState is exposed via an Environment variable:
/// ```
/// @Environment(\.popoverState) var popoverState: PopoverState
/// ```
///
/// When you want to display a popover:
/// ```
/// popoverState.display.send(dismissable: false) {
///    YourPopoverView()
/// }
/// ```
///
/// When you want to dismiss the popover:
/// ```
/// popoverState.close()
/// ```
///
public class PopoverState: ObservableObject {

	public var publisher = PassthroughSubject<PopoverItem?, Never>()
	
	func display<Content: View>(dismissable: Bool, @ViewBuilder builder: () -> Content) {
		publisher.send(PopoverItem(
			dismissable: dismissable,
			view: builder().anyView
		))

	}
	
	func close() {
		publisher.send(nil)
	}
}

/// Encompasses the view & options for the popover.
///
public struct PopoverItem {
	
	/// Whether or not the popover is dimissable by clicking outside the popover.
	let dismissable: Bool
	
	/// The view to be displayed in the popover window.
	/// (Use the View.anyView extension function.)
	let view: AnyView
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

struct PopoverWrapper<Content: View>: View {

	let dismissable: Bool
	let content: () -> Content
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack {
			VStack {
				VStack {
					content()
				}
			//	.padding(.all, 20) // do NOT enforce padding here; not flexible enough
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
					if dismissable {
						popoverState.close()
					}
				}
		)
	}
}

