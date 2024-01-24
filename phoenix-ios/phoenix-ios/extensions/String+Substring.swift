import Foundation

extension String {
	
	/// Getting the substring of a String in Swift is complete insanity.
	/// This function works like substring in most other languages.
	///
	func substring(location: Int, length: Int? = nil) -> String {
		let start = min(max(0, location), self.count)
		let limitedLength = min(self.count - start, length ?? Int.max)
		let from = index(startIndex, offsetBy: start)
		let to = index(startIndex, offsetBy: start + limitedLength)
		return String(self[from..<to])
	}
}
