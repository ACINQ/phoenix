import SwiftUI
import PhoenixShared
import os

protocol MVIView : View {
    associatedtype Model: MVI.Model
    associatedtype Intent: MVI.Intent

    typealias IntentReceiver = (Intent) -> Void
}

extension MVIView {
    func mvi<Content: View>(background: Bool = false, onModel: ((Model) -> Void)? = nil, @ViewBuilder content: @escaping (Model, @escaping IntentReceiver) -> Content) -> MVIContext<Model, Intent, Content> {
        MVIContext(Model.self, Intent.self, background: background, onModel: onModel, content: content)
    }

    func logger() -> Logger { Logger(from: type(of: self)) }
}
