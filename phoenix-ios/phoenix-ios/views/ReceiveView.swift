import SwiftUI
import PhoenixShared


struct ReceiveView: View {

    var body: some View {
        MVIView({ $0.receiveControllerInstance() }) { model, controller in
            self.switchView(model: model)
                    .navigationBarTitle("Receive", displayMode: .inline)
                    .onAppear {
                        controller.intent(intent: Receive.IntentAsk(amountMsat: 125000000))
                    }
        }
    }

    func switchView(model: Receive.Model) -> some View {
        switch model {
        case _ as Receive.ModelAwaiting:
            return AnyView(Text("..."))
        case _ as Receive.ModelGenerating:
            return AnyView(Text("Generating payment request..."))
        case let m as Receive.ModelGenerated:
            return AnyView(Text(m.request))
        case let m as Receive.ModelReceived:
            return AnyView(Text("Received \(m.amountMsat / 1000) Satoshis!"))
        case _ as Receive.ModelDisconnected:
            return AnyView(Text("Disconnected!"))
        default:
            return AnyView(Text("THIS SHOULD NEVER HAPPEN!"))
        }
    }

}


class ReceiveView_Previews: PreviewProvider {
    static var previews: some View {
        ReceiveView()
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: ReceiveView())
    }
    #endif
}
