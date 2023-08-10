import Foundation

extension String {
	func leftPadding(toLength: Int, withPad character: Character) -> String {
		if count < toLength {
			return String(repeating: character, count: toLength - count) + self
		} else {
			return self
		}
	}
}
