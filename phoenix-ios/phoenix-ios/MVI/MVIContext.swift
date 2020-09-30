import SwiftUI
import PhoenixShared

struct MVIContext<Model: MVI.Model, Intent: MVI.Intent, Content: View> : View {

    class ModelChange {
        let newModel: Model
        let previousModel: Model?
        var animation: Animation? = nil

        init(newModel: Model, previousModel: Model?) {
            self.newModel = newModel
            self.previousModel = previousModel
        }

        func animateIfModelTypeChanged(animation: Animation = .default) {
            if (type(of: newModel) != type(of: previousModel)) {
                self.animation = animation
            }
        }
    }

    private class MVIState: ObservableObject {
        private var unsub: (() -> Void)? = nil
        private var controller: MVIController<Model, Intent>? = nil
        private var subCount: Int = 0
        @Published var model: Model? = nil

        private let background: Bool
        private var onModel: ((ModelChange) -> Void)? = nil

        init(background: Bool) {
            self.background = background
        }

        deinit {
            let _unsub = unsub
            let _controller = controller
            DispatchQueue.main.async {
                _unsub?()
                _controller?.stop()
            }
        }

        func initController(_ di: DI, onModel: ((ModelChange) -> Void)? = nil) {
            if controller == nil {
                controller = di.instance(of: MVIController<Model, Intent>.self, params: [Model.self, Intent.self])
                model = controller!.firstModel
            }
        }

        func subscribe(onModel: ((ModelChange) -> Void)?) {
            self.onModel = onModel
            if unsub == nil {
                unsub = controller!.subscribe { newModel in
                    let modelChange = ModelChange(newModel: newModel, previousModel: self.model)
                    self.onModel?(modelChange)
                    if let animation = modelChange.animation {
                        withAnimation(animation) {
                            self.model = newModel
                        }
                    } else {
                        self.model = newModel
                    }
                }
            }
            subCount += 1
        }

        func unsubscribe() {
            subCount -= 1
            if subCount < 0 { fatalError("subCount < 0") }
            if subCount == 0 && !background {
                unsub!()
                unsub = nil
            }
        }

        func intent(_ intent: Intent) {
            controller!.intent(intent: intent)
        }
    }

    private let content: (Model, @escaping (Intent) -> Void) -> Content

    @EnvironmentObject private var envDi: ObservableDI

    @StateObject private var state: MVIState

    private var onModel: ((ModelChange) -> Void)? = nil

    init(
            background: Bool = false,
            onModel: ((ModelChange) -> Void)? = nil,
            @ViewBuilder content: @escaping (Model, @escaping (Intent) -> Void) -> Content
    ) {
        _state = StateObject(wrappedValue: MVIState(background: background))
        self.onModel = onModel
        self.content = content
    }

    var body: some View {
        state.initController(envDi.di)

        return content(state.model!, state.intent)
                .onAppear {
                    state.subscribe(onModel: onModel)
                }
                .onDisappear {
                    state.unsubscribe()
                }
    }

}
