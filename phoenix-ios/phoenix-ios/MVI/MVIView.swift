import SwiftUI
import PhoenixShared
import os

protocol MVIView : View {
    associatedtype Model: MVI.Model
    associatedtype Intent: MVI.Intent

    typealias IntentReceiver = (Intent) -> Void
}

extension MVIView {
    func mvi<Content: View>(background: Bool = false, onModel: ((MVIContext<Model, Intent, Content>.ModelChange) -> Void)? = nil, @ViewBuilder content: @escaping (Model, @escaping IntentReceiver) -> Content) -> MVIContext<Model, Intent, Content> {
        MVIContext(background: background, onModel: onModel, content: content)
    }

    func logger() -> Logger { Logger(from: type(of: self)) }
}
