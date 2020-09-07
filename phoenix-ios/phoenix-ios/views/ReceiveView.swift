import SwiftUI
import PhoenixShared

import UIKit

struct ReceiveView: MVIView {
    typealias Model = Receive.Model
    typealias Intent = Receive.Intent

    class QRCode : ObservableObject {
        @Published var value: String? = nil
        @Published var image: Image? = nil

        func generate(value: String) {
            if value == self.value { return }
            self.value = value
            self.image = nil

            DispatchQueue.global(qos: .userInitiated).async {
                let data = value.data(using: .ascii)
                guard let qrFilter = CIFilter(name: "CIQRCodeGenerator") else { fatalError("No CIQRCodeGenerator") }
                qrFilter.setValue(data, forKey: "inputMessage")
                let cgTransform = CGAffineTransform(scaleX: 8, y: 8)
                guard let ciImage = qrFilter.outputImage?.transformed(by: cgTransform) else { fatalError("Could not scale QRCode") }
                guard let cgImg = CIContext().createCGImage(ciImage, from: ciImage.extent) else { fatalError("Could not generate QRCode image") }
                let image =  Image(decorative: cgImg, scale: 1.0)
                DispatchQueue.main.async {
                    if value != self.value { return }
                    self.image = image
                }
            }
        }
    }

    @StateObject var qrCode = QRCode()

    var body: some View {
        mvi { model in
            if let m = model as? Receive.ModelGenerated {
                qrCode.generate(value: m.request)
            }
        } content: { model, intent in
                view(model: model, intent: intent)
                        .navigationBarTitle("Receive ", displayMode: .inline)
                        .onAppear {
                            intent(Receive.IntentAsk(amountMsat: 50000))
                        }
                    .navigationBarTitle("", displayMode: .inline)
        }
    }

    @ViewBuilder
    func view(model: Receive.Model, intent: (Receive.Intent) -> Void) -> some View {
        switch model {
        case _ as Receive.ModelAwaiting:
            Text("...")
        case _ as Receive.ModelGenerating:
            Text("Generating payment request...")
        case let m as Receive.ModelGenerated:
            if qrCode.value == m.request {
                VStack {
                    if let image = qrCode.image {
                        image.resizable().scaledToFit()
                                .padding()
                                .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                                .stroke(Color.gray, lineWidth: 4)
                                )
                                .padding()
                    } else {
                        Text("Generating QRCode...")
                                .padding()
                    }

                    Text(m.request).padding()
                    Button {
                        UIPasteboard.general.string = m.request
                    } label: {
                        Text("Copy")
                                .font(.title3)
                    }
                            .padding()
                }
            }
        default:
            fatalError("Unknown model \(model)")
        }
    }

}


class ReceiveView_Previews: PreviewProvider {

    static let mockModel = Receive.ModelGenerated(request: "lngehrsiufehywajgiorghwjkbeslfmhfjqhlefiowahfaewhgopesuhiotopfgeaiowhwaejiofaulgjahgbvlpsehgjfaglwfaelwhekwhewahfjkoaewhyerjfowahgiajrowagraewhgfaewkljgprstghaefwkgfalwhfdklghersjfopewhhvweijlkaln3frhjqbdghqjvhwaejiofaulgjahgbvlpsehgjfaglwfaelwhekwhewahfjkoaewhyerjfowahgiajrowagraewh")
//    static let mockModel = Receive.ModelAwaiting()

    static var previews: some View {
        mockView(ReceiveView()) { $0.receiveModel = ReceiveView_Previews.mockModel }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
