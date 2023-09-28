import Foundation


enum TimestampType {
	case milliseconds
	case seconds
}

extension Int64 {
	
	func toDate(from timestampType: TimestampType) -> Date {
		switch timestampType {
			case .milliseconds : return Date(timeIntervalSince1970: TimeInterval(self) / TimeInterval(1_000))
			case .seconds      : return Date(timeIntervalSince1970: TimeInterval(self))
		}
	}
	
	func minus(hours: Int) -> Int64 {
		return self - Int64(1_000 * 60 * 60 * hours)
	}
}

extension Date {
	
	func toMilliseconds() -> Int64 {
		return Int64(self.timeIntervalSince1970 * 1_000)
	}
}
