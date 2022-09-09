import Foundation


extension Int {
	
	func milliseconds() -> TimeInterval {
		return Double(self) / Double(1_000)
	}
	
	func seconds() -> TimeInterval {
		return Double(self)
	}
	
	func minutes() -> TimeInterval {
		return Double(self) * Double(60)
	}
	
	func hours() -> TimeInterval {
		return Double(self) * Double(60 * 60)
	}
	
	func days() -> TimeInterval {
		return Double(self) * Double(60 * 60 * 24)
	}
}
