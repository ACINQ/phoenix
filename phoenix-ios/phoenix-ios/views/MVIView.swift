import SwiftUI
import PhoenixShared

struct MVIView<M : MVI.Model, I : MVI.Intent, V : View> : View {

    class ControllerHolder<M : MVI.Model, I : MVI.Intent> : ObservableObject {
        @Published var controller: MVIController<M, I>? = nil

        deinit {
            controller?.stop()
        }
    }


    private let content: (M, MVIController<M, I>) -> V

    private let getController: (AppDI) -> MVIController<M, I>

    @EnvironmentObject private var diHolder: DIHolder

    @ObservedObject private var controllerHolder = ControllerHolder<M, I>()

    @State private var model: M? = nil

    init(
            _ getController: @escaping (AppDI) -> MVIController<M, I>,
            @ViewBuilder content: @escaping (M, MVIController<M, I>) -> V
    ) {
        self.getController = getController
        self.content = content
    }

    var body: some View {
        if controllerHolder.controller == nil { controllerHolder.controller = getController(diHolder.di) }

        let m: M = model ?? controllerHolder.controller!.firstModel

        var unsub: (() -> Void)? = nil
        return content(m, controllerHolder.controller!)
                .onAppear { unsub = self.controllerHolder.controller!.subscribe { self.model = $0 } }
                .onDisappear { unsub?() }
    }

}

func mockView<V : View>(_ content: V, block: @escaping (MockDIBuilder) -> Void) -> some View {
    NavigationView {
        content
    }
            .environmentObject(DIHolder(AppDI(di: MockDIBuilder().apply(block: block).di())))
}
