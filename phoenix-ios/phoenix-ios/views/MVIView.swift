import SwiftUI
import PhoenixShared

struct MVIView<M : MVI.Model, I : MVI.Intent, V : View> : View {

    private let content: (M, MVIController<M, I>) -> V

    private let controller: MVIController<M, I>

    @State private var model: M

    init(
            _ getController: (AppDI) -> MVIController<M, I>,
            @ViewBuilder content: @escaping (M, MVIController<M, I>) -> V) {
        let di = (UIApplication.shared.delegate as! AppDelegate).di

        self.controller = getController(di)
        self.content = content

        _model = State(initialValue: self.controller.firstModel)
    }

    var body: some View {
        var unsub: (() -> Void)? = nil
        return content(model, controller)
                .onAppear { unsub = self.controller.subscribe { self.model = $0 } }
                .onDisappear { unsub?() }
    }

}
