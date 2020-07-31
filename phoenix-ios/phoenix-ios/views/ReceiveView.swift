import SwiftUI
import PhoenixShared


struct ReceiveView: View {

    var body: some View {
        MVIView({ $0.receiveControllerInstance() }) { model, controller in
            ReceiveView.view(model: model, controller: controller)
        }
    }

    static func view(model: Receive.Model, controller: MVIController<Receive.Model, Receive.Intent>) -> some View {
        var view: AnyView
        switch model {
        case _ as Receive.ModelAwaiting:
            view = AnyView(Text("..."))
        case _ as Receive.ModelGenerating:
            view = AnyView(Text("Generating payment request..."))
        case let m as Receive.ModelGenerated:
            view = AnyView(Text(m.request))
        case let m as Receive.ModelReceived:
            view = AnyView(Text("Received \(m.amountMsat / 1000) Satoshis!"))
        case _ as Receive.ModelDisconnected:
            view = AnyView(Text("Disconnected!"))
        default:
            fatalError("Unknown model \(model)")
        }

        return view
                .navigationBarTitle("Receive", displayMode: .inline)
                .onAppear {
                    controller.intent(intent: Receive.IntentAsk(amountMsat: 125000000))
                }
    }

}


class ReceiveView_Previews: PreviewProvider {

    static let mockModel = Receive.ModelGenerating()

    static var previews: some View {
        ReceiveView()
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: ReceiveView())
    }
    #endif
}
