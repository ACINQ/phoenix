import SwiftUI
import Combine

/// The ShortSheetState is exposed via an EnvironmentObject variable:
/// ```
/// @EnvironmentObject var shortSheetState: ShortSheetState
/// ```
///
/// When you want to display a short sheet:
/// ```
/// shortSheetState.display(dismissable: false) {
///    YourCustomView()
/// }
/// ```
///
/// When you want to dismiss the sheet:
/// ```
/// shortSheetState.close()
/// ```
///
public class ShortSheetState: ObservableObject {
	
	/// Fires when:
	/// - sheet view will animate on screen (onWillAppear)
	/// - sheet view has animated off screen (onDidDisappear)
	///
	var itemPublisher = CurrentValueSubject<ShortSheetItem?, Never>(nil)
	
	/// Fires when:
	/// - sheet view will animate off screen (onWillDisapper)
	///
	var closePublisher = PassthroughSubject<Void, Never>()
	
	/// Whether or not the sheet is dimissable by tapping outside the sheet.
	///
	var dismissablePublisher = CurrentValueSubject<Bool, Never>(true)
	
	var currentItem: ShortSheetItem? {
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
		itemPublisher.send(ShortSheetItem(
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
		itemPublisher.sink { (item: ShortSheetItem?) in
			
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
		itemPublisher.sink { (item: ShortSheetItem?) in
			
			if item == nil {
				// Attempting to display another ShortSheet won't work until the next RunLoop cycle.
				DispatchQueue.main.async {
					action()
				}
				cancellables.removeAll()
			}
			
		}.store(in: &cancellables)
	}
}

/// Encompasses the view & options for the popover.
///
public struct ShortSheetItem: SmartModalItem {
	
	/// The view to be displayed in the sheet.
	/// (Use the View.anyView extension function.)
	let view: AnyView
}

struct ShortSheetWrapper<Content: View>: View {

	let content: () -> Content
	
	@State var dismissable: Bool
	@State var animation: CGFloat = 0.0
	
	@EnvironmentObject var shortSheetState: ShortSheetState
	
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
							shortSheetState.close()
						}
					}
					.accessibilityHidden(!dismissable)
					.accessibilityLabel("Dismiss sheet")
					.accessibilitySortPriority(-1000)
					.accessibilityAction {
						shortSheetState.close()
					}
				
				VStack {
					Spacer()
					content()
						.background(
							Color(UIColor.systemBackground)
								.cornerRadius(15, corners: [.topLeft, .topRight])
								.edgesIgnoringSafeArea([.horizontal, .bottom])
						)
				}
				.zIndex(1)
				.transition(.move(edge: .bottom))
			}
		}
		.transition(.identity)
		.onAppear {
			withAnimation {
				animation = 1
			}
		}
		.onReceive(shortSheetState.closePublisher) { _ in
			withAnimation {
				animation = 2
			}
		}
		.onReceive(shortSheetState.dismissablePublisher) { newValue in
			dismissable = newValue
		}
		.onAnimationCompleted(for: animation) {
			animationCompleted()
		}
	}
	
	func animationCompleted() {
		if animation == 1 {
			// ShortSheet is now visible
			UIAccessibility.post(notification: .screenChanged, argument: nil)
		}
		else if animation == 2 {
			// ShortSheet is now hidden
			UIAccessibility.post(notification: .screenChanged, argument: nil)
			shortSheetState.itemPublisher.send(nil)
		}
	}
}
