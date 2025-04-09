import Foundation

extension String {
	
	func isValidEmailAddress() -> Bool {
		let types: NSTextCheckingResult.CheckingType = [.link]
		guard let linkDetector = try? NSDataDetector(types: types.rawValue) else {
			return false
		}
		let range = NSRange(location: 0, length: self.count)
		let result = linkDetector.firstMatch(in: self, options: .reportCompletion, range: range)
		let scheme = result?.url?.scheme ?? ""
		return (scheme == "mailto") && (result?.range.length == self.count)
	}
}
