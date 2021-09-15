//
// Created by Salomon BRYS on 20/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import Foundation

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
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        let date = Date(timeIntervalSince1970: TimeInterval(self / 1000))
        return formatter.string(from: date)
    }

    func formatDateS() -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        let date = Date(timeIntervalSince1970: TimeInterval(self))
        return formatter.string(from: date)
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
