import SwiftUI
import PhoenixShared

struct MVIView<Model: MVI.Model, Intent: MVI.Intent, Content: View> : View {

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
        private let onModel: ((ModelChange) -> Void)?

        init(
                background: Bool,
                onModel: ((ModelChange) -> Void)?
        ) {
            self.background = background
            self.onModel = onModel
        }

        deinit {
            let _unsub = unsub
            let _controller = controller
            DispatchQueue.main.async {
                _unsub?()
                _controller?.stop()
            }
        }

        func initController(getController: () -> MVIController<Model, Intent>) {
            if controller == nil {
                controller = getController()
                model = controller!.firstModel
            }
        }

        func subscribe() {
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

    @EnvironmentObject private var envControllerFactory: ObservableControllerFactory

    @StateObject private var state: MVIState

    private let getController: (ControllerFactory) -> MVIController<Model, Intent>

    init(
            _ getController: @escaping (ControllerFactory) -> MVIController<Model, Intent>,
            background: Bool = false,
            onModel: ((ModelChange) -> Void)? = nil,
            @ViewBuilder content: @escaping (Model, @escaping (Intent) -> Void) -> Content
    ) {
        _state = StateObject(wrappedValue: MVIState(background: background, onModel: onModel))
        self.getController = getController
        self.content = content
    }

    var body: some View {
        state.initController { getController(envControllerFactory.controllerFactory) }

        return content(state.model!, state.intent)
                .onAppear {
                    state.subscribe()
                }
                .onDisappear {
                    state.unsubscribe()
                }
    }

}
