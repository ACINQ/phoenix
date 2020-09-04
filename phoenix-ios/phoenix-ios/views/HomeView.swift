import SwiftUI
import PhoenixShared
import Network

struct HomeView : MVIView {
    typealias Model = Home.Model
    typealias Intent = Home.Intent

    @State var lastTransaction: PhoenixShared.Transaction? = nil
    @State var showConnections = false

    @State var selectedTransaction: PhoenixShared.Transaction? = nil

    var body: some View {
        mvi(
                background: true,
                onModel: { model in
                    if lastTransaction != model.lastTransaction {
                        lastTransaction = model.lastTransaction
                        selectedTransaction = lastTransaction
                    }
                }
        ) { model, intent in
            ZStack {
                VStack() {
                    HStack {
                        ConnectionStatus(status: model.connections.global, show: $showConnections)
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
                            ForEach(model.history.indices, id: \.self) { index in
                                Button {
                                    selectedTransaction = model.history[index]
                                } label: {
                                    TransactionCell(transaction: model.history[index])
                                }
                            }
                        }
                                .sheet(isPresented: .constant(selectedTransaction != nil)) {
                                    selectedTransaction = nil
                                } content: {
                                    TransactionView(
                                            transaction: selectedTransaction!,
                                            close: { selectedTransaction = nil }
                                    )
                                }
                    }

                    BottomBar()
                }

                if showConnections {
                    VStack {
                        ConnectionPopup(connections: model.connections, show: $showConnections)
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
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.top)
        }
                .navigationBarTitle("", displayMode: .inline)
                .navigationBarHidden(true)
    }

    struct ConnectionStatus : View {
        let status: EklairConnection

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
                    .background(Color.appBackgroundLight)
                    .cornerRadius(10)
                    .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.gray, lineWidth: 1)
                    )
                    .opacity(dimStatus ? 0.2 : 1.0)
                    .isHidden(status == EklairConnection.established)
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
        let connections: Connections

        @Binding var show: Bool

        var body: some View {
            VStack(alignment: .leading) {
                Text("Connection status:")
                        .font(.title2)
                        .padding([.bottom])

                Divider()
                ConnectionCell(label: "Internet", connection: connections.internet)
                Divider()
                ConnectionCell(label: "Lightning peer", connection: connections.peer)
                Divider()
                ConnectionCell(label: "Electrum server", connection: connections.electrum)
                Divider()

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

    struct ConnectionCell : View {
        let label: String
        let connection: EklairConnection

        var body : some View {
            HStack {
                let bullet = Image("ic_bullet").resizable().frame(width: 10, height: 10)

                if connection == .established { bullet.foregroundColor(.appGreen) }
                else if connection == .establishing { bullet.foregroundColor(.appYellow) }
                else if connection == .closed { bullet.foregroundColor(.appRed) }

                Text("\(label):")
                Spacer()
                Text(connection.text())
            }
                    .padding([.top, .bottom], 8)
        }
    }

    struct TransactionCell : View {

        let transaction: PhoenixShared.Transaction

        var body: some View {
            HStack {
                switch (transaction.status) {
                case .success:
                    Image("payment_holder_def_success")
                            .padding(4)
                            .background(
                                    RoundedRectangle(cornerRadius: .infinity)
                                            .fill(Color.appHorizon)
                            )
                case .pending:
                    Image("payment_holder_def_pending")
                            .padding(4)
                case .failure:
                    Image("payment_holder_def_failed")
                            .padding(4)
                default: EmptyView()
                }
                VStack(alignment: .leading) {
                    Text(transaction.desc)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .foregroundColor(.appDark)
                    Text(transaction.timestamp.formatDateMS())
                            .font(.caption)
                            .foregroundColor(.gray)
                }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding([.leading, .trailing], 6)
                if (transaction.status != .failure) {
                    HStack {
                        Text((transaction.amountSat >= 0 ? "+" : "") + Int64((Double(transaction.amountSat) / 1000.0).rounded()).formatNumber())
                                .foregroundColor(transaction.amountSat >= 0 ? .appGreen : .appRed)
                                + Text(" sat")
                                .font(.caption)
                                .foregroundColor(.gray)
                    }
                }
            }
                    .padding([.top, .bottom], 14)
                    .padding([.leading, .trailing], 12)
        }

    }

    struct BottomBar : View {

        @State var isShowingScan: Bool = false

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
                        destination: ReceiveView()
                ) {
                    Image("ic_receive").resizable().frame(width: 22, height: 22)
                    Text("Receive")
                            .foregroundColor(.appDark)
                }

                Spacer()

                Divider()
                        .frame(height: 40)

                Spacer()

                NavigationLink(
                        destination: ScanView(isShowing: $isShowingScan),
                        isActive: $isShowingScan
                ) {
                    Image("ic_scan").resizable().frame(width: 22, height: 22)
                    Text("Scan")
                            .foregroundColor(.appDark)
                }

                Spacer()
            }
                    .padding(.top, 10)
                    .background(Color.white)
                    .cornerRadius(15, corners: [.topLeft, .topRight])
        }
    }
}

class HomeView_Previews : PreviewProvider {
    static let mockModel = Home.Model(
            connections: Connections(internet: .established, peer: .established, electrum: .closed),
            balanceSat: 123500,
            history: [
                mockSpendFailedTransaction,
                mockReceiveTransaction,
                mockSpendTransaction,
                mockPendingTransaction
            ],
            lastTransaction: nil
    )

    static var previews: some View {
//        mockView(HomeView()) { $0.homeModel = mockModel }
        appView(HomeView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
