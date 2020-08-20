//
// Created by Salomon BRYS on 20/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import Foundation
import PhoenixShared

let mockSpendTransaction = Transaction(
        amountSat: -1500,
        desc: "1 Blockaccino",
        success: false,
        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        paymentRequest: "lnwhatever...",
        paymentPreimage: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)

let mockReceiveTransaction = Transaction(
        amountSat: 125000,
        desc: "On-Chain payment to 8b44f33a8c86f1fe0c18935df9db961ff5a6edb4ee49d3cee666458745d676fd",
        success: true,
        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        paymentRequest: "lnwhatever...",
        paymentPreimage: "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)

let mockSpendFailedTransaction = Transaction(
        amountSat: -1700,
        desc: "1 Espresso Coin Panna",
        success: false,
        paymentHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        paymentRequest: "lnwhatever...",
        paymentPreimage: "",
        creationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 10),
        expirationTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 5),
        completionTimestamp: Int64(NSDate().timeIntervalSince1970 - 60 * 8)
)
