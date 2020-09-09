import SwiftUI
import PhoenixShared

struct ElectrumConfigurationView: MVIView {
    typealias Model = ElectrumConfiguration.Model
    typealias Intent = ElectrumConfiguration.Intent

    @State var showElectrumAddressPopup: Bool = false

    var body: some View {
        mvi { model, intent in
            ZStack {
                VStack(spacing: 0) {
                    Text("""
                         By default Phoenix connects to random Electrum servers in order to access the Bitcoin blockchain. 
                         You can also choose to connect to your own Electrum server.
                         """)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)

                    Divider()

                    Section(header: "Server") {
                        VStack(alignment: .leading) {
                            if model.connection == .established {
                                Text("Connected to:")
                                Text(model.electrumServer.address()).bold()
                            } else if model.connection == .establishing {
                                Text("Connecting to:")
                                Text(model.electrumServer.address()).bold()
                            } else if model.electrumServer.customized {
                                Text("You will connect to:")
                                Text(model.electrumServer.address()).bold()
                            } else {
                                Text("Not connected")
                            }

                            if model.error != nil {
                                Text(model.error?.message ?? "")
                                    .foregroundColor(Color.red)
                            }

                            Button {
                                showElectrumAddressPopup = true
                            } label: {
                                Image(systemName: "square.and.pencil")
                                    .imageScale(.medium)
                                Text("Set server")
                            }
                                    .buttonStyle(PlainButtonStyle())
                                    .padding(8)
                                    .background(Color.white)
                                    .cornerRadius(16)
                                    .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                    .stroke(Color.appHorizon, lineWidth: 2)
                                    )
                        }
                    }

                    if model.walletIsInitialized {
                        Section(header: "Block height") {
                            let height = model.electrumServer.blockHeight
                            Text("\(height > 0 ? height.formatNumber() : "-")")
                        }

                        Section(header: "Tip timestamp") {
                            let time = model.electrumServer.tipTimestamp
                            Text("\(time > 0 ? time.formatDateS() : "-")")
                        }

                        Section(header: "Fee rate") {
                            if model.feeRate > 0 {
                                Text("\(model.feeRate.formatNumber()) sat/byte")
                            } else {
                                Text("-")
                            }
                        }

                        Section(header: "Master public key") {
                            if model.xpub == nil || model.path == nil {
                                Text("-")
                            } else {
                                VStack(alignment: .leading) {
                                    Text(model.xpub!)
                                    Text("Path: \(model.path!)").padding(.top)
                                }
                            }
                        }
                    }

                    Spacer()
                }

                if showElectrumAddressPopup {
                    if model.electrumServer.customized {
                        ElectrumAddressPopup(intent: intent, show: $showElectrumAddressPopup,
                                customize: model.electrumServer.customized, addressInput: model.electrumServer.address())
                    } else {
                        ElectrumAddressPopup(intent: intent, show: $showElectrumAddressPopup)
                    }
                }
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Electrum server", displayMode: .inline)
        }
    }

    struct Section<Content>: View where Content: View {
        let header: String
        let content: () -> Content

        init(header: String, fullMode: Bool = true, @ViewBuilder content: @escaping () -> Content) {
            self.header = header
            self.content = content
        }

        var body: some View {
            HStack(alignment: .top) {
                Text(header).bold()
                        .frame(width: 90, alignment: .leading)
                        .font(.subheadline)
                        .foregroundColor(.appDark)
                        .padding([.leading, .top, .bottom])

                content()
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
            }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)

            Divider()
        }
    }

    struct ElectrumAddressPopup: View {
        let intent: IntentReceiver

        @Binding var show: Bool
        @State var customize: Bool = false
        @State var addressInput: String = ""

        var body: some View {
            VStack {
                VStack(alignment: .leading) {
                    Toggle(isOn: $customize) {
                        Text("Use a custom server")
                    }
                            .padding()
                            .frame(maxWidth: .infinity)

                    VStack(alignment: .leading) {
                        Text("Server address")
                                .padding([.leading, .trailing])
                                .foregroundColor(Color.appHorizon)

                        TextField("host:port", text: $addressInput)
                                .padding([.leading, .trailing])
                                .padding([.top, .bottom], 8)
                                .disableAutocorrection(true)

                        Text("Server must have a valid certificate")
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.appBackground)

                        HStack {
                            Spacer()
                            Button("OK") {
                                show = false
                                intent(ElectrumConfiguration.IntentUpdateElectrumServer(customized: customize, address: addressInput))
                            }
                                    .font(.title2)
                                    .padding()
                        }
                    }
                            .frame(maxWidth: .infinity)
                            .opacity(customize ? 1 : 0.5)
                }
                        .frame(maxWidth: .infinity)
                        .background(Color.white)
                        .cornerRadius(15)
                        .padding(32)
                        .onTapGesture(perform: {})
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.25))
                    .edgesIgnoringSafeArea(.all)
                    .transition(.opacity)
                    .onTapGesture(perform: {
                        show = false
                    })
        }
    }
}

class ElectrumConfigurationView_Previews: PreviewProvider {
    static let mockModel = ElectrumConfiguration.Model(
            walletIsInitialized: true,
            connection: .closed,
            electrumServer: ElectrumServer(id: 0, host: "tn.not.fyi", port: 55002, customized: true, blockHeight: 123456789, tipTimestamp: 1599564210),
            feeRate: 9999,
            xpub: "vpub5ZqweUWHkV3Q5HrftL6c7L7rwvQdSPrQzPdFEd8tHU32EmduQqy1CgmAb4g1jQGoBFaGW4EcNhc1Bm9grBkV2hSUz787L7ALTfNbz3xNigS",
            path: "m/84'/1'/0'",
            error: nil
    )

    static var previews: some View {
        mockView(ElectrumConfigurationView()) {
            $0.electrumConfigurationModel = mockModel
        }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
