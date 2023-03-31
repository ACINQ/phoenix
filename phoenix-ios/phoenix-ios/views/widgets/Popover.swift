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

	/// Singleton instance
	///
	public static let shared = PopoverState()
	
	private init() {/* must use shared instance */}
	
	/// Fires when:
	/// - view will animate on screen (onWillAppear)
	/// - view has animated off screen (onDidDisappear)
	///
	var publisher = CurrentValueSubject<PopoverItem?, Never>(nil)
	
	/// Fires when:
	/// - view will animate off screen (onWillDisapper)
	///
	var closePublisher = PassthroughSubject<Void, Never>()
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil
	) {
		publisher.send(PopoverItem(
			dismissable: dismissable,
			view: builder().anyView
		))
		if let willDisappearLambda = onWillDisappear {
			onNextWillDisappear(willDisappearLambda)
		}
	}
	
	func close() {
		closePublisher.send()
	}
	
	func close(animationCompletion: @escaping () -> Void) {
		
		var cancellables = Set<AnyCancellable>()
		publisher.sink { (item: PopoverItem?) in
			
			// NB: This fires right away because publisher is CurrentValueSubject.
			// It only means `onDidDisappear` if item is nil.
			if item == nil {
				animationCompletion()
				cancellables.removeAll()
			}
			
		}.store(in: &cancellables)
		
		closePublisher.send()
	}
	
	func onNextWillDisappear(_ action: @escaping () -> Void) {
		
		var cancellables = Set<AnyCancellable>()
		closePublisher.sink { _ in
			
			action()
			cancellables.removeAll()
			
		}.store(in: &cancellables)
	}
	
	func onNextDidDisappear(_ action: @escaping () -> Void) {
		
		var cancellables = Set<AnyCancellable>()
		publisher.sink { (item: PopoverItem?) in
			
			if item == nil {
				action()
				cancellables.removeAll()
			}
			
		}.store(in: &cancellables)
	}
}

/// Encompasses the view & options for the popover.
///
public struct PopoverItem: SmartModalItem {
	
	/// Whether or not the popover is dimissable by tapping outside the popover.
	let dismissable: Bool
	
	/// The view to be displayed in the popover window.
	/// (Use the View.anyView extension function.)
	let view: AnyView
}

struct PopoverEnvironmentKey: EnvironmentKey {

	static var defaultValue = PopoverState.shared
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
	
	@State var animation: CGFloat = 0.0
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		ZStack {
			if animation == 1 {
				Color.primary.opacity(0.4)
					.edgesIgnoringSafeArea(.all)
					.zIndex(0)
					.transition(.opacity)
					.onTapGesture {
						if dismissable {
							popoverState.close()
						}
					}
					.accessibilityHidden(!dismissable)
					.accessibilityLabel("Dismiss popover")
					.accessibilitySortPriority(-1000)
					.accessibilityAction {
						popoverState.close()
					}
				
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
					.frame(maxWidth: 600, alignment: .center)
					.padding(.all, 20)
				}
				.zIndex(1)
				.transition(.opacity)
			
			} // </if animation>
		}
		.transition(.identity)
		.onAppear {
			withAnimation {
				animation = 1
			}
		}
		.onReceive(popoverState.closePublisher) { _ in
			withAnimation {
				animation = 2
			}
		}
		.onAnimationCompleted(for: animation) {
			animationCompleted()
		}
	}
	
	func animationCompleted() {
		if animation == 1 {
			// Popover is now visible
			UIAccessibility.post(notification: .screenChanged, argument: nil)
		} else if animation == 2 {
			// Popover is now hidden
			UIAccessibility.post(notification: .screenChanged, argument: nil)
			popoverState.publisher.send(nil)
		}
	}
}

