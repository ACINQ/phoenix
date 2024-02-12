import SwiftUI
import Combine

/// The PopoverState is exposed via an EnvironmentObject variable:
/// ```
/// @EnvironmentObject var popoverState: PopoverState
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
	
	/// Fires when:
	/// - view will animate on screen (onWillAppear)
	/// - view has animated off screen (onDidDisappear)
	///
	var itemPublisher = CurrentValueSubject<PopoverItem?, Never>(nil)
	
	/// Fires when:
	/// - view will animate off screen (onWillDisapper)
	///
	var closePublisher = PassthroughSubject<Void, Never>()
	
	/// Whether or not the popover is dismissable by tapping outside the popover.
	///
	var dismissablePublisher = CurrentValueSubject<Bool, Never>(true)
	
	var currentItem: PopoverItem? {
		return itemPublisher.value
	}
	
	var dismissable: Bool {
		get { dismissablePublisher.value }
		set { dismissablePublisher.send(newValue) }
	}
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil
	) {
		dismissablePublisher.send(dismissable)
		itemPublisher.send(PopoverItem(
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
		itemPublisher.sink { (item: PopoverItem?) in
			
			// NB: This fires right away because itemPublisher is CurrentValueSubject.
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
		itemPublisher.sink { (item: PopoverItem?) in
			
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
	
	/// The view to be displayed in the popover window.
	/// (Use the View.anyView extension function.)
	let view: AnyView
}

struct PopoverWrapper<Content: View>: View {

	let content: () -> Content
	
	@State var dismissable: Bool
	@State var animation: CGFloat = 0.0
	
	@EnvironmentObject var popoverState: PopoverState
	
	init(dismissable: Bool, content: @escaping () -> Content) {
		self.dismissable = dismissable
		self.content = content
	}
	
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
		.onReceive(popoverState.dismissablePublisher) { newValue in
			dismissable = newValue
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
			popoverState.itemPublisher.send(nil)
		}
	}
}

