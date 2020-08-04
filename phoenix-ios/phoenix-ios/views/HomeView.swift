import SwiftUI
import PhoenixShared

struct HomeView: View {

    @State var showReceive = false

    var body: some View {
        MVIView({ $0.homeControllerInstance() }) { model, controller in
            VStack() {
                List(model.channels, id: \.cid) { channel in
                    Text("\(String(channel.cid.prefix(5))): \(channel.local) / \(channel.local + channel.remote)")
                }
                HStack {
                    Spacer()
                    Button(action: { controller.intent(intent: Home.IntentConnect()) }) {
                        Text("Connect")
                                .fontWeight(.bold)
                                .font(.title)
                    }
                    Spacer()
                    Button(action: { self.showReceive = true }) {
                        Text("Receive")
                                .fontWeight(.bold)
                                .font(.title)
                    }
                            .disabled(!model.connected)
                    Spacer()
                    NavigationLink(destination: Text("Test")) { Text("Test") }
                }

                NavigationLink(destination: ReceiveView(), isActive: self.$showReceive) { EmptyView() }
            }
                    .navigationBarTitle("Channels")
                    .navigationBarItems(trailing: Text(model.connected ? "Connected_" : "Disconnected"))
        }
    }
}


class HomeView_Previews: PreviewProvider {
    static let mockModel = Home.Model(connected: true, channels: [
        Home.ModelChannel(cid: "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", local: 0, remote: 100000),
        Home.ModelChannel(cid: "FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210", local: 94290, remote: 5710)
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
