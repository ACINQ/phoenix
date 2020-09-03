//
// Created by Salomon BRYS on 02/09/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import PhoenixShared


extension EklairConnection {
    func text() -> String {
        switch self {
        case .closed: return "Offline"
        case .establishing: return "Connecting..."
        case .established: return "Connected"
        default: return "Unknown"
        }
    }
}
