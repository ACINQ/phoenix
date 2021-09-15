import SwiftUI


extension TextField where Label == Text {
	
	/// Text has a nice initializer we can use when we don't need to localize the string:
	/// ```
	/// Text(verbatim: String)
	/// ```
	///
	/// TextField is missing this, so we're adding it here.
	init(
		verbatim: String,
		text: Binding<String>,
		onEditingChanged: @escaping (Bool) -> Void = { _ in },
		onCommit: @escaping () -> Void = {}
	) {
		self.init(verbatim, text: text, onEditingChanged: onEditingChanged, onCommit: onCommit)
	}
}
