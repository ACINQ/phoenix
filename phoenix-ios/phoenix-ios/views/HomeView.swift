import SwiftUI
import PhoenixShared

struct HomeView: MVIView {
    typealias Model = Home.Model
    typealias Intent = Home.Intent

    @State var barHidden: Bool = true

    var body: some View {
        mvi { model, controller in
            VStack() {
                List(model.channels, id: \.cid) { channel in
                    Text("\(String(channel.cid.prefix(5))): \(channel.local) / \(channel.local + channel.remote) (\(channel.state))")
                }
                HStack {
                    Spacer()
                    Button(action: { controller.intent(intent: Home.IntentConnect()) }) {
                        Text("Connect")
                                .fontWeight(.bold)
                                .font(.title)
                    }
                    Spacer()
                    NavigationLink(
                            destination: ReceiveView()
                                    .onAppear { self.barHidden = false }
                    ) {
                        Text("Receive")
                                .fontWeight(.bold)
                                .font(.title)
                    }
                            .disabled(!model.connected)
                    Spacer()
                }
            }
        }
                .navigationBarTitle("", displayMode: .inline)
                .navigationBarHidden(barHidden)
                .onAppear { self.barHidden = true }
    }
}


class HomeView_Previews: PreviewProvider {
    static let mockModel = Home.Model(connected: true, channels: [
        Home.ModelChannel(cid: "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", local: 0, remote: 100000, state: "Normal"),
        Home.ModelChannel(cid: "FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210", local: 94290, remote: 5710, state: "Normal")
    ])

    static var previews: some View {
        mockView(HomeView()) { $0.homeModel = HomeView_Previews.mockModel }
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
