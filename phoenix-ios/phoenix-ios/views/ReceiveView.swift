import SwiftUI
import PhoenixShared


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

    @State var toastText: String? = nil
    @State var sharing: Bool = false
    @State var editing: Bool = false
    @State var unit: String = "sat"

    private func toast(_ text: String) {
        withAnimation(.linear(duration: 0.15)) { toastText = text }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.linear(duration: 0.15)) { toastText = nil }
        }
    }

    var body: some View {
        ZStack {
            mvi { model in
                if let m = model as? Receive.ModelGenerated {
                    qrCode.generate(value: m.request)
                }
            } content: { model, intent in
                    view(model: model, intent: intent)
                            .navigationBarTitle("Receive ", displayMode: .inline)
                            .onAppear {
                                intent(Receive.IntentAsk(amount: nil, unit: BitcoinUnit.satoshi, desc: nil))
                            }
            }

            if (toastText != nil) {
                VStack {
                    Spacer()
                    Text(toastText!)
                            .padding()
                            .foregroundColor(.white)
                            .background(Color.appDark.opacity(0.4))
                            .cornerRadius(42)
                            .padding([.bottom], 42)
                }
                        .transition(.opacity)
            }
        }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.appBackground)

    }

    @ViewBuilder func view(model: Receive.Model, intent: @escaping IntentReceiver) -> some View {
        ZStack {
            VStack {
                switch model {
                case _ as Receive.ModelAwaiting:
                    Text("...")
                case _ as Receive.ModelGenerating:
                    Text("Generating payment request...")
                case let m as Receive.ModelGenerated:
                    if qrCode.value == m.request {
                        qrCodeView()
                                .frame(width: 200, height: 200)
                                .padding()
                                .background(Color.white)
                                .cornerRadius(20)
                                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.appHorizon, lineWidth: 1))
                                .padding()

                        HStack {
                            actionButton(image: Image(systemName: "square.on.square")) {
                                UIPasteboard.general.string = m.request
                                toast("Copied in pasteboard!")
                            }
                            actionButton(image: Image(systemName: "square.and.arrow.up")) {
                                sharing = true
                            }
                                    .sheet(isPresented: $sharing) {
                                        ActivityView(activityItems: ["lightning:\(m.request)"] as [Any], applicationActivities: nil)
                                    }

                            actionButton(image: Image(systemName: "square.and.pencil")) {
                                withAnimation { editing = true }
                            }
                        }
                    }
                default: fatalError("Unknown model \(model)")
                }
            }
                    .padding([.bottom], 200)

            if let m = model as? Receive.ModelGenerated {
                PopupContent(
                        show: $editing,
                        amount: m.amount?.description ?? "",
                        unit: m.unit,
                        desc: m.desc ?? "",
                        intent: intent
                )
            }
        }
    }

    @ViewBuilder func qrCodeView() -> some View {
        if let image = qrCode.image {
            image.resizable()
        } else {
            Text("Generating QRCode...")
                    .padding()
        }
    }

    @ViewBuilder func actionButton(image: Image, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            image
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundColor(Color.appDark)
                    .frame(width: 20, height: 20)
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(50)
                    .overlay(RoundedRectangle(cornerRadius: 50).stroke(Color.gray.opacity(0.2), lineWidth: 1))
        }
                .padding()
    }

    struct PopupContent : View {

        @Binding var show: Bool

        @State var amount: String
        @State var unit: BitcoinUnit
        @State var desc: String

        @State var illegal: Bool = false

        let intent: IntentReceiver

        var body: some View {
            Popup(
                    show: $show,
                    closeable: !illegal,
                    onOk: {
                        intent(Receive.IntentAsk(amount: amount.isEmpty ? nil : KotlinDouble(value: Double(amount)!), unit: unit, desc: desc.isEmpty ? nil : desc))
                    }
            ) {
                VStack(alignment: .leading) {
                    Text("Edit my payment request")
                            .font(.title2)
                            .padding()

                    Text("You can change the amount and the description of the payment request")
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.appBackground)
                            .padding([.bottom])

                    Text("Amount (optional)")
                            .font(.system(size: 14))
                            .padding([.leading, .trailing])
                            .foregroundColor(Color.appHorizon)

                    HStack {
                        TextField("123", text: $amount)
                                .keyboardType(.decimalPad)
                                .disableAutocorrection(true)
                                .onChange(of: amount) {
                                    illegal = !$0.isEmpty && Double($0) == nil
                                }
                                .foregroundColor(illegal ? Color.red : Color.black)

                        Picker(selection: $unit, label: Text(unit.abbrev).frame(width: 50)) {
                            ForEach(0..<BitcoinUnit.default().values.count) {
                                let u = BitcoinUnit.default().values[$0]
                                Text(u.abbrev).tag(u)
                            }
                        }.pickerStyle(MenuPickerStyle())
                    }
                            .padding([.leading, .trailing, .bottom])

                    Text("Description (optional)")
                            .font(.system(size: 14))
                            .padding([.leading, .trailing])
                            .foregroundColor(Color.appHorizon)

                    TextField("...", text: $desc)
                            .padding([.leading, .trailing, .bottom])

                }
            }
        }
    }

    struct ActivityView: UIViewControllerRepresentable {

        let activityItems: [Any]
        let applicationActivities: [UIActivity]?

        func makeUIViewController(context: UIViewControllerRepresentableContext<ActivityView>) -> UIActivityViewController {
            UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
        }

        func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ActivityView>) {}
    }

}


class ReceiveView_Previews: PreviewProvider {

    static let mockModel = Receive.ModelGenerated(
            request: "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm",
            amount: 0.017,
            unit: BitcoinUnit.millibitcoin,
            desc: "1 Espresso Coin Panna"
    )
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
