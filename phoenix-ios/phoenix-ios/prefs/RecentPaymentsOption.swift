import Foundation


/// In the HomeView we only display "recent" payments.
/// This struct controls the user options for defining "recent".
///
enum RecentPaymentsOption: Int, CaseIterable {
	case zero       = 0
	case oneMinute  = 60       // 60 * 1
	case tenMinutes = 600      // 60 * 10
	case twoHours   = 7_200    // 60 * 60 * 2
	case oneDay     = 86_400   // 60 * 60 * 24
	case threeDays  = 259_200  // 60 * 60 * 24 * 3
	case sevenDays  = 604_800; // 60 * 60 * 24 * 7
	
	var seconds: Int {
		return rawValue
	}
	
	static func closest(seconds: Int) -> (Int, RecentPaymentsOption) {
		
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
}

extension RecentPaymentsOption {
	func configDisplay() -> String {
		switch self {
		case .zero:
			return NSLocalizedString("Only show in-flight outgoing payments.", comment: "Recent payments option")
		case .oneMinute:
			return NSLocalizedString("Show payments within the last 1 minute.", comment: "Recent payments option")
		case .tenMinutes:
			return NSLocalizedString("Show payments within the last 10 minutes.", comment: "Recent payments option")
		case .twoHours:
			return NSLocalizedString("Show payments within the last 2 hours.", comment: "Recent payments option")
		case .oneDay:
			return NSLocalizedString("Show payments within the last 24 hours.", comment: "Recent payments option")
		case .threeDays:
			return NSLocalizedString("Show payments within the last 3 days.", comment: "Recent payments option")
		case .sevenDays:
			return NSLocalizedString("Show payments within the last 7 days.", comment: "Recent payments option")
		}
	}
	
	func homeDisplay(paymentCount count: Int) -> String {
		switch self {
		case .zero:
			if count == 1 {
				return NSLocalizedString("1 in-flight outgoing payment", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) in-flight outgoing payments", comment: "Recent payments footer")
			}
		case .oneMinute:
			if count == 1 {
				return NSLocalizedString("1 payment within the last minute", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) payments within the last minute", comment: "Recent payments footer")
			}
		case .tenMinutes:
			if count == 1 {
				return NSLocalizedString("1 payment within the last 10 minutes", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) payments within the last 10 minutes", comment: "Recent payments footer")
			}
		case .twoHours:
			if count == 1 {
				return NSLocalizedString("1 payment within the last 2 hours", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) payments within the last 2 hours", comment: "Recent payments footer")
			}
		case .oneDay:
			if count == 1 {
				return NSLocalizedString("1 payments within the last 24 hours", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) payments within the last 24 hours", comment: "Recent payments footer")
			}
		case .threeDays:
			if count == 1 {
				return NSLocalizedString("1 payment within the last 3 days", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(count) payments within the last 3 days", comment: "Recent payments footer")
			}
		case .sevenDays:
			if count == 1 {
				return NSLocalizedString("1 payment within the last 7 days", comment: "Recent payments option")
			} else {
				return NSLocalizedString("\(count) payments within the last 7 days", comment: "Recent payments option")
			}
		}
	}
}
