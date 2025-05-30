import Foundation

struct InvalidPin: Equatable, Codable {
	let count: Int
	let timestamp: Date
	
	static func none() -> InvalidPin {
		return InvalidPin(count: 0, timestamp: Date.distantPast)
	}
	
	static func one() -> InvalidPin {
		return InvalidPin(count: 1, timestamp: Date.now)
	}
	
	func increment() -> InvalidPin {
		let newCount = (count < Int.max) ? count + 1 : Int.max
		let newTimestamp = Date.now
		
		return InvalidPin(count: newCount, timestamp: newTimestamp)
	}
	
	var waitTime: TimeInterval { // TimeInterval => seconds (Double)
		switch count {
			case 0  : return  0.seconds()
			case 1  : return  0.seconds()
			case 2  : return  0.seconds()
			case 3  : return 10.seconds()
			case 4  : return  1.minutes()
			case 5  : return  2.minutes()
			case 6  : return  5.minutes()
			case 7  : return 10.minutes()
			case 8  : return 30.minutes()
			default : return  1.hours()
		}
	}
	
	func waitTimeFrom(_ now: Date) -> TimeInterval? {
		
		let trigger = timestamp.addingTimeInterval(waitTime)
		
		let diff = trigger.timeIntervalSince(now)
		return (diff > 0.0) ? diff : nil
	}
	
	func hasWaitTime(_ now: Date) -> Bool {
		return waitTimeFrom(now) != nil
	}
	
	var elapsed: TimeInterval {
		return timestamp.timeIntervalSinceNow * -1.0
	}
}
