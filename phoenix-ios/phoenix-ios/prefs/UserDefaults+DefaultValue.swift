import Foundation

extension UserDefaults {
	
	func bool(forKey key: String, defaultValue: Bool) -> Bool {
		if object(forKey: key) == nil {
			return defaultValue
		} else {
			return bool(forKey: key)
		}
	}
	
	func integer(forKey key: String, defaultValue: Int) -> Int {
		if object(forKey: key) == nil {
			return defaultValue
		} else {
			return integer(forKey: key)
		}
	}
	
	func number(forKey key: String) -> NSNumber? {
		return object(forKey: key) as? NSNumber
	}
}
