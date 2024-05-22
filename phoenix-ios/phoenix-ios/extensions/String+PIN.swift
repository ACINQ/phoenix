import Foundation

extension String {
	
	var isValidPIN: Bool {
		
		if self.count != 6 {
			return false
		}
		
		let digitsCharacters = CharacterSet(charactersIn: "0123456789")
		return CharacterSet(charactersIn: self).isSubset(of: digitsCharacters)
	}
}
