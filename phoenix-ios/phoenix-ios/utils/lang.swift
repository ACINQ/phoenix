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

    func formatDate() -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        let time = Date(timeIntervalSince1970: TimeInterval(self))
        return formatter.string(from: time)
    }
}
