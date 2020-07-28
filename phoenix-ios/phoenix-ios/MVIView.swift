//
// Created by Salomon BRYS on 25/06/2020.
// Copyright (c) 2020 orgName. All rights reserved.
//

import Foundation
import SwiftUI
import PhoenixShared

extension View {
    func withController<M : MVI.Model, I : MVI.Intent>(_ controller: MVIController<M, I>, onModel: @escaping (M) -> Void) -> some View {
        var unsub: (() -> Void)? = nil

        return self
                .onAppear {
                    print("Appear")
                    unsub = controller.subscribe { onModel($0) }
                }
                .onDisappear {
                    print("Disappear")
                    unsub?()
                }

    }
}
