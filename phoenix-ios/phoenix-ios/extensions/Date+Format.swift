import Foundation

extension Date {
	
	/// Note that `Date.formatted()` & `Date.FormatStyle` is iOS 15+.
	/// So until we drop support for iOS 14, we won't be using it.
	///
	func format(
		date dateStyle: DateFormatter.Style = .long,
		time timeStyle: DateFormatter.Style = .short
	) -> String {
		let formatter = DateFormatter()
		formatter.dateStyle = dateStyle
		formatter.timeStyle = timeStyle
		return formatter.string(from: self)
	}
}
