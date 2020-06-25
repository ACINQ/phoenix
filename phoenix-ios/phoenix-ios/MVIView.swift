//
// Created by Salomon BRYS on 25/06/2020.
// Copyright (c) 2020 orgName. All rights reserved.
//

import Foundation
import SwiftUI
import Phoenix

extension View {
    func withController<M, I>(_ controller: MVIController<M, I>, onModel: @escaping (M) -> Void) -> some View {
        var unsub: (() -> Void)? = nil

        return self
                .onAppear {
                    unsub = controller.subscribe { onModel($0!) }
                }
                .onDisappear { unsub?() }

    }
}
