import SwiftUI
import PhoenixShared

import UIKit

struct ScanView: MVIView {
    typealias Model = Scan.Model
    typealias Intent = Scan.Intent

    var body: some View {
        mvi { model, intent in
            view(model: model, intent: intent)
        }
                .navigationBarTitle("", displayMode: .inline)
    }

    func view(model: Scan.Model, intent: @escaping IntentReceiver) -> some View {
        switch model {
        case _ as Scan.ModelReady: return AnyView(ReadyView(intent: intent))
        case let m as Scan.ModelValidate: return AnyView(ValidateView(model: m, intent: intent))
        case let m as Scan.ModelSending: return AnyView(SendingView(model: m))
        case let m as Scan.ModelFulfilled: return AnyView(FulfilledView(model: m))
        default:
            fatalError("Unknown model \(model)")
        }
    }

    struct ReadyView: View {
        let intent: IntentReceiver

        @State var request: String = ""

        var body: some View {
            VStack {
                Text("Payment Request:")
                        .font(.title)
                        .padding()
                TextEditor(text: $request)
                        .padding()
                        .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color.gray, lineWidth: 4)
                        )
                        .padding()
                Button {
                    intent(Scan.IntentParse(request: request))
                } label: {
                    Text("OK")
                            .font(.title)
                }
                        .disabled(request.isEmpty)
                        .padding()
            }
        }
    }

    struct ValidateView: View {
        let model: Scan.ModelValidate

        let intent: IntentReceiver

        var body: some View {
            Text("\(model.amountMsat ?? 0) msat")
                    .font(.title)
                    .padding()

            Text(model.requestDescription ?? "")
                    .padding()
            
            Button {
                intent(Scan.IntentSend(request: model.request, amountMsat: model.amountMsat?.int64Value ?? 0))
            } label: {
                Text("Pay")
                        .font(.title)
                        .padding()
            }
            
        }
    }

    struct SendingView: View {
        let model: Scan.ModelSending

        var body: some View {
            Text("Sending \(model.amountMsat) msat...")
                    .font(.title)
                    .padding()

            Text(model.requestDescription ?? "")
                    .padding()
        }
    }

    struct FulfilledView: View {
        let model: Scan.ModelFulfilled

        var body: some View {
            Text("Sent \(model.amountMsat) msat!")
                    .font(.title)
                    .padding()

            Text(model.requestDescription ?? "")
                    .padding()
        }
    }

}


class ScanView_Previews: PreviewProvider {

    static let mockModel = Scan.ModelReady()

    static var previews: some View {
        mockView(ScanView()) { $0.scanModel = ScanView_Previews.mockModel }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
