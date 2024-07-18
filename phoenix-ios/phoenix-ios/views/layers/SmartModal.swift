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
			return popoverState.currentItem
		} else {
			return shortSheetState.currentItem
		}
	}
	
	var dismissable: Bool {
		get {
			if isIPad {
				return popoverState.dismissable
			} else {
				return shortSheetState.dismissable
			}
		}
		set {
			if isIPad {
				popoverState.dismissable = newValue
			} else {
				shortSheetState.dismissable = newValue
			}
		}
	}
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil,
		onDidDisappear: (() -> Void)? = nil
	) {
		if isIPad {
			popoverState.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear,
				onDidDisappear: onDidDisappear
			)
		} else {
			shortSheetState.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear,
				onDidDisappear: onDidDisappear
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

protocol SmartModalItem {}
