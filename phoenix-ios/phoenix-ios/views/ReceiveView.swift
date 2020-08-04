import SwiftUI
import PhoenixShared

import UIKit

struct ReceiveView: View {

    var body: some View {
        MVIView({ $0.receiveControllerInstance() }) { model, controller in
            self.view(model: model, controller: controller)
        }
    }

    func view(model: Receive.Model, controller: MVIController<Receive.Model, Receive.Intent>) -> some View {
        var view: AnyView
        switch model {
        case _ as Receive.ModelAwaiting:
            view = AnyView(Text("..."))
        case _ as Receive.ModelGenerating:
            view = AnyView(Text("Generating payment request..."))
        case let m as Receive.ModelGenerated:
            let image = qrCode(request: m.request)
            if let image = image {
                view = AnyView(image.resizable().scaledToFit())
            } else {
                view = AnyView(Text("Could not generate QR Code"))
            }
        case let m as Receive.ModelReceived:
            view = AnyView(Text("Received \(m.amountMsat / 1000) Satoshis!"))
        case _ as Receive.ModelDisconnected:
            view = AnyView(Text("Disconnected!"))
        default:
            fatalError("Unknown model \(model)")
        }

        return view
                .navigationBarTitle("Receive ", displayMode: .inline)
                .onAppear {
                    controller.intent(intent: Receive.IntentAsk(amountMsat: 125000000))
                }
    }

    func qrCode(request: String) -> Image? {
        let data = request.data(using: .ascii)
        guard let qrFilter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        qrFilter.setValue(data, forKey: "inputMessage")
        let cgTransform = CGAffineTransform(scaleX: 8, y: 8)
        guard let ciImage = qrFilter.outputImage?.transformed(by: cgTransform) else { return nil }
        guard let cgImg = CIContext().createCGImage(ciImage, from: ciImage.extent) else { return nil }
        return Image(decorative: cgImg, scale: 1.0)
    }

}


class ReceiveView_Previews: PreviewProvider {

    static let mockModel = Receive.ModelGenerated(request: "lngehrsiufehywajgiorghwjkbeslfmhfjqhlefiowahfaewhgopesuhiotopfgeaiowhwaejiofaulgjahgbvlpsehgjfaglwfaelwhekwhewahfjkoaewhyerjfowahgiajrowagraewhgfaewkljgprstghaefwkgfalwhfdklghersjfopewhhvweijlkaln3frhjqbdghqjvhwaejiofaulgjahgbvlpsehgjfaglwfaelwhekwhewahfjkoaewhyerjfowahgiajrowagraewh")

    static var previews: some View {
        mockView(ReceiveView()) { $0.receiveModel = ReceiveView_Previews.mockModel }
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
