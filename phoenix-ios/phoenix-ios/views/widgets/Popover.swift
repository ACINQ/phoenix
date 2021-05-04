import SwiftUI
import Combine

/// The PopoverState is exposed via an Environment variable:
/// ```
/// @Environment(\.popoverState) var popoverState: PopoverState
/// ```
///
/// When you want to display a popover:
/// ```
/// popoverState.display.send(PopoverItem(
///
///    YourPopoverView().anyView,
///    dismissable: false
/// ))
/// ```
///
/// When you want to dismiss the popover:
/// ```
/// popoverState.close.send()
/// ```
///
public class PopoverState: ObservableObject {

	/// When you want to present a popover, just send it through this signal
	public var display = PassthroughSubject<PopoverItem, Never>()
	
	/// When you want to close the popover, just send an update on this signal
	public var close = PassthroughSubject<Void, Never>()
}

/// Encompasses the view & options for the popover.
///
public struct PopoverItem {
	
	/// The view to be displayed in the popover window.
	/// (Use the View.anyView extension function.)
	let view: AnyView
	
	/// Whether or not the popover is dimissable by clicking outside the popover.
	let dismissable: Bool
	
	init(_ view: AnyView, dismissable: Bool/* purposefully non-optional parameter */) {
		self.view = view
		self.dismissable = dismissable
	}
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
						popoverState.close.send()
					}
				}
		)
	}
}

