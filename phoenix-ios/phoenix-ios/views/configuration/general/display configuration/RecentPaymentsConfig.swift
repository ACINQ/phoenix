import Foundation

/**
 * In the HomeView we only display "recent" payments.
 *
 * This is stored in UserDefaults as:
 * enum RecentPaymentsConfig: Equatable, Codable {
 *   case withinTime(seconds: Int)
 *   case mostRecent(count: Int)
 *   case inFlightOnly
 * }
 *
 * The enum's here control the user options defined in the UI,
 * and help to map between the raw value and the enum option
 */

enum RecentPaymentsConfig_WithinTime: Int, CaseIterable {
	case oneMinute  = 60       // 60 * 1
	case tenMinutes = 600      // 60 * 10
	case twoHours   = 7_200    // 60 * 60 * 2
	case oneDay     = 86_400   // 60 * 60 * 24
	case threeDays  = 259_200  // 60 * 60 * 24 * 3
	case sevenDays  = 604_800; // 60 * 60 * 24 * 7
	
	var seconds: Int {
		return rawValue
	}
	
	static var defaultValue = RecentPaymentsConfig_WithinTime.threeDays
	
	static func defaultTuple() -> (Int, RecentPaymentsConfig_WithinTime) {
		
		let idx = self.allCases.firstIndex(of: defaultValue)!
		return (idx, defaultValue)
	}
	
	static func closest(seconds: Int) -> (Int, RecentPaymentsConfig_WithinTime) {
		
		let allOptions = self.allCases
		
		let minIdx = allOptions.enumerated().map { (idx, option) in
			return (idx, abs(option.seconds - seconds))
		}
		.min(by: { tuple1, tuple2 in
			let (_, diff1) = tuple1
			let (_, diff2) = tuple2
			
			// areInIncreasingOrder
			// The predicate returns true if its first argument should be
			// ordered before its second argument; otherwise, false.
			
			return (diff1 < diff2) ? true : false
		})!.0
		
		return (minIdx, allOptions[minIdx])
	}
	
	func configDisplay() -> String {
		switch self {
		case .oneMinute:
			return NSLocalizedString("Show payments within the last 1 minute", comment: "Recent payments option")
		case .tenMinutes:
			return NSLocalizedString("Show payments within the last 10 minutes", comment: "Recent payments option")
		case .twoHours:
			return NSLocalizedString("Show payments within the last 2 hours", comment: "Recent payments option")
		case .oneDay:
			return NSLocalizedString("Show payments within the last 24 hours", comment: "Recent payments option")
		case .threeDays:
			return NSLocalizedString("Show payments within the last 3 days", comment: "Recent payments option")
		case .sevenDays:
			return NSLocalizedString("Show payments within the last 7 days", comment: "Recent payments option")
		}
	}
	
	func homeDisplay(paymentCount count: Int) -> String {
		switch self {
		case .oneMinute:
			return count == 1
			  ? NSLocalizedString("1 payment within the last minute", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last minute", comment: "Recent payments footer")
		case .tenMinutes:
			return count == 1
			  ? NSLocalizedString("1 payment within the last 10 minutes", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last 10 minutes", comment: "Recent payments footer")
		case .twoHours:
			return count == 1
			  ? NSLocalizedString("1 payment within the last 2 hours", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last 2 hours", comment: "Recent payments footer")
		case .oneDay:
			return count == 1
			  ? NSLocalizedString("1 payments within the last 24 hours", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last 24 hours", comment: "Recent payments footer")
		case .threeDays:
			return count == 1
			  ? NSLocalizedString("1 payment within the last 3 days", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last 3 days", comment: "Recent payments footer")
		case .sevenDays:
			return count == 1
			  ? NSLocalizedString("1 payment within the last 7 days", comment: "Recent payments footer")
			  : NSLocalizedString("\(count) payments within the last 7 days", comment: "Recent payments footer")
		}
	}
}

enum RecentPaymentsConfig_MostRecent: Int, CaseIterable {
	case one     = 1
	case three   = 3
	case five    = 5
	case ten     = 10
	case fifteen = 15
	case twenty  = 20;
	
	var count: Int {
		return rawValue
	}
	
	static var defaultValue = RecentPaymentsConfig_MostRecent.five
	
	static func defaultTuple() -> (Int, RecentPaymentsConfig_MostRecent) {
		
		let idx = self.allCases.firstIndex(of: defaultValue)!
		return (idx, defaultValue)
	}
	
	static func closest(count: Int) -> (Int, RecentPaymentsConfig_MostRecent) {
		
		let allOptions = self.allCases
		
		let minIdx = allOptions.enumerated().map { (idx, option) in
			return (idx, abs(option.count - count))
		}
		.min(by: { tuple1, tuple2 in
			let (_, diff1) = tuple1
			let (_, diff2) = tuple2
			
			// areInIncreasingOrder
			// The predicate returns true if its first argument should be
			// ordered before its second argument; otherwise, false.
			
			return (diff1 < diff2) ? true : false
		})!.0
		
		return (minIdx, allOptions[minIdx])
	}
	
	func configDisplay() -> String {
		switch self {
		case .one:
			return NSLocalizedString("Show most recent 1 payment", comment: "Recent payments option")
		case .three:
			return NSLocalizedString("Show most recent 3 payments", comment: "Recent payments option")
		case .five:
			return NSLocalizedString("Show most recent 5 payments", comment: "Recent payments option")
		case .ten:
			return NSLocalizedString("Show most recent 10 payments", comment: "Recent payments option")
		case .fifteen:
			return NSLocalizedString("Show most recent 15 payments", comment: "Recent payments option")
		case .twenty:
			return NSLocalizedString("Show most recent 20 payments", comment: "Recent payments option")
		}
	}
}
