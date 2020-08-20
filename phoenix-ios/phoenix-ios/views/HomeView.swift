import SwiftUI
import PhoenixShared
import Network

struct HomeView : MVIView {
    typealias Model = Home.Model
    typealias Intent = Home.Intent

    @State var showConnections = false

    var body: some View {
        mvi { model, intent in
            ZStack {
                VStack() {
                    HStack {
                        ConnectionStatus(status: model.connected, show: $showConnections)
                        Spacer()
                    }
                    .padding()

                    HStack(alignment: .bottom) {
                        Text(model.balanceSat.formatNumber())
                                .font(.largeTitle)
                        Text("sat")
                                .font(.title2)
                                .padding(.bottom, 4)
                    }

                    ScrollView {
                        LazyVStack {
                            ForEach(model.history.indices.reversed(), id: \.self) { index in
                                TransactionCell(transaction: model.history[index])
                            }
                        }
                    }

                    BottomBar()
                }

                if showConnections {
                    VStack {
                        ConnectionPopup(show: $showConnections)
                    }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.black.opacity(0.25))
                            .edgesIgnoringSafeArea(.all)
                            .transition(.opacity)
                }
            }
                    .padding(.top, keyWindow?.safeAreaInsets.top)
                    .padding(.top, keyWindow?.safeAreaInsets.bottom)
                    .padding(.top, 10)
                    .frame(maxHeight: .infinity)
                    .background(Color(red: 0.96, green: 0.96, blue: 0.98))
                    .edgesIgnoringSafeArea(.top)
        }
                .navigationBarTitle("", displayMode: .inline)
                .navigationBarHidden(true)
    }
}

extension EklairPeer.Connection {
    func text() -> String {
        switch self {
        case .closed: return "Offline"
        case .establishing: return "Connecting..."
        case .established: return "Connected"
        default: return "Unknown"
        }
    }
}

struct ConnectionStatus : View {
    let status: EklairPeer.Connection

    @Binding var show: Bool

    @State var started = false
    @State var dimStatus = false

    var body: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.3)) {
                show = true
            }
        } label: {
            HStack {
                Image("ic_connection_lost")
                        .resizable()
                        .frame(width: 16, height: 16)
                Text(status.text())
                        .font(.caption2)
            }
        }
                .buttonStyle(PlainButtonStyle())
                .padding(.all, 4)
                .background(Color(red: 0.99, green: 0.99, blue: 1.0))
                .cornerRadius(10)
                .overlay(
                        RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.gray, lineWidth: 1)
                )
                .opacity(dimStatus ? 0.2 : 1.0)
                .isHidden(status == EklairPeer.Connection.established)
                .onAppear {
                    if (!started) {
                        started = true
                        DispatchQueue.main.async {
                            withAnimation(Animation.linear(duration: 1.0).repeatForever()) {
                                self.dimStatus.toggle()
                            }
                        }
                    }
                }
    }
}

struct ConnectionPopup : View {

    @Binding var show: Bool

    var body: some View {
        VStack(alignment: .leading) {
            Text("Connection status:")
                    .font(.title2)
                    .padding([.bottom])

            HStack {
                Text("Internet:")
                Spacer()
                Text(EklairPeer.Connection.established.text())
            }
                    .padding()

            Divider()

            HStack {
                Text("Lightning peer:")
                Spacer()
                Text(EklairPeer.Connection.establishing.text())
            }
                    .padding()

            Divider()

            HStack {
                Text("Electrum server:")
                Spacer()
                Text(EklairPeer.Connection.closed.text())
            }
                    .padding()

            HStack {
                Spacer()
                Button("OK") {
                    show = false
                }
                        .font(.title2)
                        .padding([.top])
            }

        }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .cornerRadius(15)
                .padding(32)
    }
}

struct TransactionCell : View {

    let transaction: PhoenixShared.Transaction

    var body: some View {
        HStack {
            if transaction.success {
                Image("payment_holder_def_success")
                        .padding(4)
                        .background(
                                RoundedRectangle(cornerRadius: .infinity)
                                        .fill(Color(hex: "91B4D1"))
                        )
            } else {
                Image("payment_holder_def_failed")
                        .padding(4)
            }
            VStack(alignment: .leading) {
                Text(transaction.desc)
                        .lineLimit(1)
                        .truncationMode(.tail)
                Text(transaction.completionTimestamp.formatDate())
                        .font(.caption)
                        .foregroundColor(.gray)
            }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding([.leading, .trailing], 8)
            Text(transaction.amountSat.formatNumber())
        }
                .padding([.top, .bottom], 14)
                .padding([.leading, .trailing], 16)
    }

}

struct BottomBar : View {

    var body: some View {
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
                    destination: ReceiveView()//.onAppear { barHidden = false }
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
                    destination: ScanView()//.onAppear { barHidden = false }
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
}

class HomeView_Previews : PreviewProvider {
    static let mockModel = Home.Model(
            connected: .establishing,
            balanceSat: 123500,
            history: [
                mockSpendTransaction,
                mockReceiveTransaction,
                mockSpendFailedTransaction
            ]
    )

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
