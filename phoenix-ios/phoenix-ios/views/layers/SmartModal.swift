import SwiftUI

/// SmartModel display the appropriate dialog for the current platform:
/// - iPhone => ShortSheet
/// - iPad   => Popover
///
/// The SmartModalState is exposed via an EnvironmentObject variable:
/// ```
/// @EnvironmentObject var smartModalState: SmartModalState
/// ```
///
/// When you want to display a modeal:
/// ```
/// smartModalState.display(dismissable: false) {
///    YourCustomView()
/// }
/// ```
///
/// When you want to dismiss the modal:
/// ```
/// smartModalState.close()
/// ```
///
public class SmartModalState: ObservableObject {

	private let popoverState: PopoverState
	private let shortSheetState: ShortSheetState
	
	init(popoverState: PopoverState, shortSheetState: ShortSheetState) {
		self.popoverState = popoverState
		self.shortSheetState = shortSheetState
	}
	
	private var isIPad: Bool {
		return UIDevice.current.userInterfaceIdiom == .pad
	}
	
	var currentItem: SmartModalItem? {
		
		if isIPad {
			return popoverState.publisher.value
		} else {
			return shortSheetState.publisher.value
		}
	}
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil
	) {
		if isIPad {
			popoverState.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear
			)
		} else {
			shortSheetState.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear
			)
		}
	}
	
	func close() {
		if isIPad {
			popoverState.close()
		} else {
			shortSheetState.close()
		}
	}
	
	func close(animationCompletion: @escaping () -> Void) {
		if isIPad {
			popoverState.close(animationCompletion: animationCompletion)
		} else {
			shortSheetState.close(animationCompletion: animationCompletion)
		}
	}
	
	func onNextWillDisappear(_ action: @escaping () -> Void) {
		if isIPad {
			popoverState.onNextWillDisappear(action)
		} else {
			shortSheetState.onNextWillDisappear(action)
		}
	}
	
	func onNextDidDisappear(_ action: @escaping () -> Void) {
		if isIPad {
			popoverState.onNextDidDisappear(action)
		} else {
			shortSheetState.onNextDidDisappear(action)
		}
	}
}

protocol SmartModalItem {
	
	/// Whether or not the item is dimissable by tapping outside the item's view.
	var dismissable: Bool { get }
}
