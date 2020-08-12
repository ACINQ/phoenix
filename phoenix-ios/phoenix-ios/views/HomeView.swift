import SwiftUI
import PhoenixShared
import Network

struct HomeView: MVIView {
    typealias Model = Home.Model
    typealias Intent = Home.Intent

    @State var barHidden: Bool = true

    var body: some View {
        mvi { model, intent in
            VStack() {
                HStack {
                    switch model.connected {
                    case .closed: Text("Disconnected")
                    case .establishing: Text("Connecting...")
                    case .established: Text("Connected")
                    default: EmptyView()
                    }
                    Spacer()
                }
                .padding()

                List {
                    ForEach(model.channels, id: \.cid) { channel in
                        Text("\(String(channel.cid.prefix(5))): \(channel.local) / \(channel.local + channel.remote) (\(channel.state))")
                        .background(Color.clear)
                    }
                            .listRowBackground(Color.clear)
                }
                        .listStyle(PlainListStyle())
                HStack {
                    Button {

                    } label: {
                        Image("ic_settings").resizable().frame(width: 22, height: 22)
                    }
                            .padding()
                            .padding(.leading, 8)

                    Divider()
                            .frame(height: 40)

                    Spacer()

                    NavigationLink(
                            destination: ReceiveView().onAppear { self.barHidden = false }
                    ) {
                        Image("ic_receive").resizable().frame(width: 22, height: 22)
                        Text("Receive")
                                .foregroundColor(Color(red: 0x2B / 0xFF, green: 0x31 / 0xFF, blue: 0x3E / 0xFF))
                    }

                    Spacer()

                    Divider()
                            .frame(height: 40)

                    Spacer()

                    NavigationLink(
                            destination: ScanView().onAppear { self.barHidden = false }
                    ) {
                        Image("ic_scan").resizable().frame(width: 22, height: 22)
                        Text("Scan")
                                .foregroundColor(Color(red: 0x2B / 0xFF, green: 0x31 / 0xFF, blue: 0x3E / 0xFF))
                    }

                    Spacer()
                }
                        .padding(.top, 10)
                        .background(Color.white)
                        .cornerRadius(15, corners: [.topLeft, .topRight])

            }
                    .frame(maxHeight: .infinity)
                    .background(Color(red: 0.90, green: 0.90, blue: 0.90))
        }
                .navigationBarTitle("", displayMode: .inline)
                .navigationBarHidden(barHidden)
                .onAppear { self.barHidden = true }
    }
}


class HomeView_Previews: PreviewProvider {
    static let mockModel = Home.Model(connected: .established, channels: [
        Home.ModelChannel(cid: "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", local: 0, remote: 100000, state: "Normal"),
        Home.ModelChannel(cid: "FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210", local: 94290, remote: 5710, state: "Normal")
    ])

    static var previews: some View {
        mockView(HomeView()) { $0.homeModel = mockModel }
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
