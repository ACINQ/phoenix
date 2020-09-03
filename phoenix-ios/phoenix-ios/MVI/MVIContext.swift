import SwiftUI
import PhoenixShared

struct MVIContext<Model: MVI.Model, Intent: MVI.Intent, Content: View> : View {

    class ObservableController<M : MVI.Model, I : MVI.Intent> : ObservableObject {
        var unsub: (() -> Void)? = nil
        @Published var controller: MVIController<M, I>? = nil

        deinit {
            unsub?()
            controller?.stop()
        }
    }

    private let modelType: Model.Type
    private let intentType: Intent.Type

    private let background: Bool

    private let onModel: ((Model) -> Void)?

    private let content: (Model, @escaping (Intent) -> Void) -> Content

    @EnvironmentObject private var envDi: ObservableDI

    @StateObject private var controllerState = ObservableController<Model, Intent>()

    @State private var model: Model? = nil

    init(
            _ modelType: Model.Type,
            _ intentType: Intent.Type,
            background: Bool = false,
            onModel: ((Model) -> Void)? = nil,
            @ViewBuilder content: @escaping (Model, @escaping (Intent) -> Void) -> Content
    ) {
        self.modelType = modelType
        self.intentType = intentType
        self.background = background
        self.onModel = onModel
        self.content = content
    }

    var body: some View {
        if controllerState.controller == nil {
            controllerState.controller = envDi.di.instance(of: MVIController<Model, Intent>.self, params: [modelType, intentType])
        }

        let m: Model = model ?? controllerState.controller!.firstModel

        return content(m, { controllerState.controller!.intent(intent: $0) })
                .onAppear {
                    if controllerState.unsub == nil {
                        controllerState.unsub = self.controllerState.controller!.subscribe {
                            onModel?($0)
                            self.model = $0
                        }
                    }
                }
                .onDisappear {
                    if !background {
                        controllerState.unsub?()
                        controllerState.unsub = nil
                    }
                }
    }

}
