import Foundation

extension Collection where Element: Equatable {
	
	/// The built-in function `firstIndex(of:)` returns a value of type `Self.Index`.
	/// That can be annoying if you were hoping for result as an Int value.
	/// This extension function performs that conversion for you.
	///
	func firstIndexAsInt(of element: Self.Element) -> Int? {
		if let idx = firstIndex(of: element) {
			return distance(from: startIndex, to: idx)
		} else {
			return nil
		}
	}
}

extension BidirectionalCollection where Element: Equatable {
	
	/// The built-in function `lastIndex(of:)` returns a value of type `Self.Index`.
	/// That can be annoying if you were hoping for result as an Int value.
	/// This extension function performs that conversion for you.
	///
	func lastIndexAsInt(of element: Self.Element) -> Int? {
		if let idx = lastIndex(of: element) {
			return distance(from: startIndex, to: idx)
		} else {
			return nil
		}
	}
}
