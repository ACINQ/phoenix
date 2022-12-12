import SwiftUI

extension View {
	/// Applies the given transform if the given condition evaluates to `true`.
	/// - Parameters:
	///   - condition: The condition to evaluate.
	///   - transform: The transform to apply to the source `View`.
	/// - Returns: Either the original `View` or the modified `View` if the condition is `true`.
	@ViewBuilder func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content) -> some View {
		if condition {
			transform(self)
		} else {
			self
		}
	}
	
	/// Can be used when extra logic is needed. For example:
	/// ```
	/// someView.modify { view in
	///   if #available(iOS 16.0, *) {
	///     view.foo()
	///   } else {
	///     view.bar()
	///   }
	/// }
	/// ```
	func modify<T: View>(@ViewBuilder _ modifier: (Self) -> T) -> some View {
		return modifier(self)
	}
}
