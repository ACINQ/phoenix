import SwiftUI
import Combine

/// The ShortSheetState is exposed via an Environment variable:
/// ```
/// @Environment(\.shortSheetState) var shortSheetState: ShortSheetState
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

	/// Singleton instance
	///
	public static let shared = ShortSheetState()
	
	private init() {/* must use shared instance */}
	
	/// Fires when:
	/// - sheet view will animate on screen (onWillAppear)
	/// - sheet view has animated off screen (onDidDisappear)
	///
	var publisher = CurrentValueSubject<ShortSheetItem?, Never>(nil)
	
	/// Fires when:
	/// - sheet view will animate off screen (onWillDisapper)
	///
	var closePublisher = PassthroughSubject<Void, Never>()
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil
	) {
		publisher.send(ShortSheetItem(
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
		publisher.sink { (item: ShortSheetItem?) in
			
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
		publisher.sink { (item: ShortSheetItem?) in
			
			if item == nil {
				action()
				cancellables.removeAll()
			}
			
		}.store(in: &cancellables)
	}
}

/// Encompasses the view & options for the popover.
///
public struct ShortSheetItem: SmartModalItem {
	
	/// Whether or not the popover is dimissable by tapping outside the popover.
	let dismissable: Bool
	
	/// The view to be displayed in the sheet.
	/// (Use the View.anyView extension function.)
	let view: AnyView
}

struct ShortSheetEnvironmentKey: EnvironmentKey {

	static var defaultValue = ShortSheetState.shared
}

public extension EnvironmentValues {

	var shortSheetState: ShortSheetState {
		get { self[ShortSheetEnvironmentKey.self] }
		set { self[ShortSheetEnvironmentKey.self] = newValue }
	}
}

struct ShortSheetWrapper<Content: View>: View {

	let dismissable: Bool
	let content: () -> Content
	
	@State var animation: CGFloat = 0.0
	
	@Environment(\.shortSheetState) private var shortSheetState: ShortSheetState
	
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
			shortSheetState.publisher.send(nil)
		}
	}
}
