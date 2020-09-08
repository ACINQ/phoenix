//
// Created by Salomon BRYS on 20/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import Foundation

extension Int64 {
    func formatNumber() -> String {
        let formatter = NumberFormatter()
        formatter.groupingSeparator = " "
        formatter.numberStyle = .decimal
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
    func formatNumber() -> String {
        let formatter = NumberFormatter()
        formatter.groupingSeparator = " "
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: self))!
    }
}
