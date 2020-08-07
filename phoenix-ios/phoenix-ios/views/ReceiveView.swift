import SwiftUI
import PhoenixShared

import UIKit

struct ReceiveView: MVIView {
    typealias Model = Receive.Model
    typealias Intent = Receive.Intent

    var body: some View {
        mvi { model, controller in
            self.view(model: model, controller: controller)
                    .navigationBarTitle("Receive ", displayMode: .inline)
                    .onAppear {
                        controller.intent(intent: Receive.IntentAsk(amountMsat: 125000000))
                    }
        }
                .navigationBarTitle("", displayMode: .inline)
    }

    func view(model: Receive.Model, controller: MVIController<Receive.Model, Receive.Intent>) -> some View {
        switch model {
        case _ as Receive.ModelAwaiting:
            return AnyView(Text("..."))
        case _ as Receive.ModelGenerating:
            return AnyView(Text("Generating payment request..."))
        case let m as Receive.ModelGenerated:
            let image = qrCode(request: m.request)
            if let image = image {
                return AnyView(image.resizable().scaledToFit())
            } else {
                return AnyView(Text("Could not generate QR Code"))
            }
        case let m as Receive.ModelReceived:
            return AnyView(Text("Received \(m.amountMsat / 1000) Satoshis!"))
        case _ as Receive.ModelDisconnected:
            return AnyView(Text("Disconnected!"))
        default:
            fatalError("Unknown model \(model)")
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
