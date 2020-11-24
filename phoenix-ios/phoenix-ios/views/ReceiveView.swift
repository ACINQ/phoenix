import SwiftUI
import PhoenixShared


struct ReceiveView: View {

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

    @State var sharing: String? = nil
    @State var editing: Bool = false
    @State var unit: String = "sat"

    @StateObject var toast = Toast()

    var body: some View {
        ZStack {
            MVIView({ $0.receive() }, onModel: { change in
                if let m = change.newModel as? Receive.ModelGenerated {
                    qrCode.generate(value: m.request)
                }
            }) { model, postIntent in
                    view(model: model, postIntent: postIntent)
                            .navigationBarTitle("Receive ", displayMode: .inline)
                            .onAppear {
                                postIntent(Receive.IntentAsk(amount: nil, unit: BitcoinUnit.satoshi, desc: nil))
                            }
            }

            toast.view()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder func view(
		model: Receive.Model,
		postIntent: @escaping (Receive.Intent) -> Void
	) -> some View {
		
		ZStack {
			switch model {
				case _ as Receive.ModelAwaiting:
					VStack {
						Text("...")
						Spacer()
					}
					.zIndex(0)
					
                case _ as Receive.ModelGenerating:
					VStack {
						Text("Generating payment request...")
						Spacer()
					}
					.zIndex(1)
					
                case let m as Receive.ModelGenerated:
					
					generatedView(model: m, postIntent: postIntent)
						.zIndex(2)
                    
                default:
					fatalError("Unknown model \(model)")
			}
		}
    }

	@ViewBuilder func generatedView(
		model      : Receive.ModelGenerated,
		postIntent : @escaping (Receive.Intent) -> Void
	) -> some View {
		
		VStack {
			if qrCode.value == model.request {
				qrCodeView()
					.frame(width: 200, height: 200)
					.padding()
					.background(Color.white)
					.cornerRadius(20)
					.overlay(
						RoundedRectangle(cornerRadius: 20)
					//      .stroke(Color(UIColor.separator), lineWidth: 1)
							.stroke(Color.accent, lineWidth: 1)
					)
					.padding()
				
				VStack(alignment: .center) {
					if let amount = model.amount {
						Text("\(amount.description) \(model.unit.abbrev)")
							.font(.caption2)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					} else {
						Text("any amount")
							.font(.caption2)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
					if let desc = model.desc, desc.count > 0 {
						Text(desc)
							.lineLimit(1)
							.font(.caption2)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					} else {
						Text("no description")
							.lineLimit(1)
							.font(.caption2)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding([.leading, .trailing], 20)

				HStack {
					actionButton(image: Image(systemName: "square.on.square")) {
						UIPasteboard.general.string = model.request
						toast.toast(text: "Copied in pasteboard!")
					}
					actionButton(image: Image(systemName: "square.and.arrow.up")) {
						sharing = "lightning:\(model.request)"
					}
					.sharing($sharing)
					
					actionButton(image: Image(systemName: "square.and.pencil")) {
						withAnimation { editing = true }
					}
				}
				
				Spacer()
			}
		} // </VStack>
		.sheet(isPresented: $editing) {
			
			PopupContent(
				show: $editing,
				amount: model.amount?.description ?? "",
				unit: model.unit,
				desc: model.desc ?? "",
				postIntent: postIntent
			)
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
                .frame(width: 20, height: 20)
                .padding(10)
				.background(Color.buttonFill)
                .cornerRadius(50)
                .overlay(
					RoundedRectangle(cornerRadius: 50)
						.stroke(Color(UIColor.separator), lineWidth: 1)
				)
        }
        .padding()
    }

    struct PopupContent : View {

        @Binding var show: Bool

        @State var amount: String
        @State var unit: BitcoinUnit
        @State var desc: String

        @State var invalidAmount: Bool = false

        let postIntent: (Receive.Intent) -> Void

		var body: some View {
			VStack(alignment: .leading) {
				Text("Edit payment request")
					.font(.title2)
					.padding()

				HStack {
					TextField("Amount (optional)", text: $amount)
						.keyboardType(.decimalPad)
						.disableAutocorrection(true)
						.onChange(of: amount) {
							invalidAmount = !$0.isEmpty && (Double($0) == nil || Double($0)! < 0)
						}
						.foregroundColor(invalidAmount ? Color.red : Color.primary)
						.padding()

					Picker(selection: $unit, label: Text(unit.abbrev).frame(width: 50)) {
						ForEach(0..<BitcoinUnit.default().values.count) {
							let u = BitcoinUnit.default().values[$0]
							Text(u.abbrev).tag(u)
						}
					}.pickerStyle(MenuPickerStyle())
				}
				.background(Capsule().stroke(Color(UIColor.separator)))
				.padding()

				HStack {
					TextField("Description (optional)", text: $desc)
						.padding()
				}
				.background(Capsule().stroke(Color(UIColor.separator)))
				.padding()

				Spacer()
				
				HStack {
					Spacer()
					Button("OK") {
						saveChanges()
						withAnimation { show = false }
					}
					.font(.title2)
					.disabled(invalidAmount)
				}
				.padding()

			} // </VStack>
			
        } // </body>
		
		// Question: Is it possible to call this if the user manually dismisses the view ?
		func saveChanges() -> Void {
			if !invalidAmount {
				postIntent(
					Receive.IntentAsk(
						amount : amount.isEmpty ? nil : KotlinDouble(value: Double(amount)!),
						unit   : unit,
						desc   : desc.isEmpty ? nil : desc
					)
				)
			}
		}
		
    } // </PopupContent>
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
        mockView(ReceiveView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
