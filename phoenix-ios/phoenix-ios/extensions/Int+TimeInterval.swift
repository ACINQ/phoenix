import Foundation


extension Int {
	
	func milliseconds() -> TimeInterval {
		return Double(self) / Double(1_000)
	}
	
	func seconds() -> TimeInterval {
		return Double(self)
	}
}
