import SwiftUI

/// SmartModel display the appropriate dialog for the current platform:
/// - iPhone => ShortSheet
/// - iPad   => Popover
///
/// The SmartModalState is exposed via an Environment variable:
/// ```
/// @Environment(\.smartModalState) var smartModalState: SmartModalState
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

	/// Singleton instance
	///
	public static let shared = SmartModalState()
	
	private init() {/* must use shared instance */}
	
	private var isIPad: Bool {
		return UIDevice.current.userInterfaceIdiom == .pad
	}
	
	func display<Content: View>(
		dismissable: Bool,
		@ViewBuilder builder: () -> Content,
		onWillDisappear: (() -> Void)? = nil
	) {
		if isIPad {
			PopoverState.shared.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear
			)
		} else {
			ShortSheetState.shared.display(
				dismissable: dismissable,
				builder: builder,
				onWillDisappear: onWillDisappear
			)
		}
	}
	
	func close() {
		if isIPad {
			PopoverState.shared.close()
		} else {
			ShortSheetState.shared.close()
		}
	}
	
	func close(animationCompletion: @escaping () -> Void) {
		if isIPad {
			PopoverState.shared.close(animationCompletion: animationCompletion)
		} else {
			ShortSheetState.shared.close(animationCompletion: animationCompletion)
		}
	}
	
	func onNextWillDisappear(_ action: @escaping () -> Void) {
		if isIPad {
			PopoverState.shared.onNextWillDisappear(action)
		} else {
			ShortSheetState.shared.onNextWillDisappear(action)
		}
	}
	
	func onNextDidDisappear(_ action: @escaping () -> Void) {
		if isIPad {
			PopoverState.shared.onNextDidDisappear(action)
		} else {
			ShortSheetState.shared.onNextDidDisappear(action)
		}
	}
}

struct SmartModalEnvironmentKey: EnvironmentKey {

	static var defaultValue = SmartModalState.shared
}

public extension EnvironmentValues {

	var smartModalState: SmartModalState {
		get { self[SmartModalEnvironmentKey.self] }
		set { self[SmartModalEnvironmentKey.self] = newValue }
	}
}
