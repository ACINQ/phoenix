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

	// Fires when:
	// - sheet view will animate on screen (onWillAppear)
	// - sheet view has animated off screen (onDidDisappear)
	//
	var publisher = PassthroughSubject<ShortSheetItem?, Never>()
	
	// Fires when:
	// - sheet view will animate off screen (onWillDisapper)
	// 
	var closePublisher = PassthroughSubject<Void, Never>()
	
	func display<Content: View>(dismissable: Bool, @ViewBuilder builder: () -> Content) {
		publisher.send(ShortSheetItem(
			dismissable: dismissable,
			view: builder().anyView
		))

	}
	
	func close() {
		closePublisher.send()
	}
	
	func close(animationCompletion: @escaping () -> Void) {
		
		var cancellables = Set<AnyCancellable>()
		publisher.sink { (item: ShortSheetItem?) in
			
			animationCompletion()
			cancellables.removeAll()
			
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
public struct ShortSheetItem {
	
	/// Whether or not the popover is dimissable by clicking outside the popover.
	let dismissable: Bool
	
	/// The view to be displayed in the sheet.
	/// (Use the View.anyView extension function.)
	let view: AnyView
}

struct ShortSheetEnvironmentKey: EnvironmentKey {

	static var defaultValue = ShortSheetState()
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
		if animation == 2 {
			shortSheetState.publisher.send(nil)
		}
	}
}
