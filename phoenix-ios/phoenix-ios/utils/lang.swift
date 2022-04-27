//
// Created by Salomon BRYS on 20/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import Foundation

enum TimestampType {
	case milliseconds
	case seconds
}

extension Int64 {
	
	/// Do NOT use this for currency values.
	/// For currency, use Utils.format(...)
	///
	func formatInDecimalStyle() -> String {
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true
		return formatter.string(from: NSNumber(value: self))!
	}

	func formatDateMS() -> String {
		let date = self.toDate(from: .milliseconds)
		let formatter = DateFormatter()
		formatter.dateStyle = .long
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	
	func formatDateS() -> String {
		let date = self.toDate(from: .seconds)
		let formatter = DateFormatter()
		formatter.dateStyle = .long
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	func toDate(from timestampType: TimestampType) -> Date {
		switch timestampType {
			case .milliseconds : return Date(timeIntervalSince1970: TimeInterval(self / 1000))
			case .seconds      : return Date(timeIntervalSince1970: TimeInterval(self))
		}
	}
}

extension Int32 {
	
    /// Do NOT use this for currency values.
    /// For currency, use Utils.format(...)
    ///
    func formatInDecimalStyle() -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = true
        return formatter.string(from: NSNumber(value: self))!
    }
}
