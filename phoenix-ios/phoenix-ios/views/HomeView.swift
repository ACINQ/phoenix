import SwiftUI
import PhoenixShared
import Network

struct HomeView : View {

    @State var lastTransaction: PhoenixShared.Transaction? = nil
    @State var showConnections = false

    @State var selectedTransaction: PhoenixShared.Transaction? = nil

    @Environment(\.openURL) var openURL
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

    var body: some View {
        MVIView({ $0.home() },
			background: true,
			onModel: { change in
				if lastTransaction != change.newModel.lastTransaction {
					lastTransaction = change.newModel.lastTransaction
					selectedTransaction = lastTransaction
				}
			}
        ) { model, postIntent in
            
			mainView(model, postIntent)
				.navigationBarTitle("", displayMode: .inline)
				.navigationBarHidden(true)
    	}
	}
	
	@ViewBuilder func mainView(
		_ model: Home.Model,
		_ postIntent: @escaping (Home.Intent) -> Void
	) -> some View {
		
		ZStack {
			
			VStack {
				HStack {
					
					ConnectionStatus(status: model.connections.global, showPopup: $showConnections)
					
					Spacer()
					
					Button {
						openURL(URL(string: "https://phoenix.acinq.co/faq")!)
					} label: {
						HStack {
							Image(systemName: "questionmark.circle")
								.resizable()
								.frame(width: 16, height: 16)
							Text("FAQ")
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
					
				} // </HStack>
				.padding()

				// === Total Balance ====
				HStack(alignment: .bottom) {
					
					let amount = Utils.format(currencyPrefs, sat: model.balanceSat)
					Text(amount.digits)
						.font(.largeTitle)
						.onTapGesture { toggleCurrencyType() }
					
					Text(amount.type)
						.font(.title2)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
					
				} // </HStack>

				// === Transaction List ====
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

				BottomBar(canScan: model.connections.global == Eclair_kmpConnection.established)
			
			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top)
			.padding(.top)

			Popup(show: showConnections) {
				ConnectionPopup(show: $showConnections, connections: model.connections)
			}
		
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.appBackground)
		.edgesIgnoringSafeArea(.top)
		.edgesIgnoringSafeArea(.bottom)
	}

    struct ConnectionStatus : View {
        let status: Eclair_kmpConnection

        @Binding var showPopup: Bool

        @State var dimStatus = false

        var body: some View {
            Group {
                Button {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showPopup = true
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
                        .isHidden(status == Eclair_kmpConnection.established)
            }
                    .onAppear {
                        DispatchQueue.main.async {
                            withAnimation(Animation.linear(duration: 1.0).repeatForever()) {
                                self.dimStatus.toggle()
                            }
                        }
                    }
        }
    }

    struct ConnectionPopup : View {

        @Binding var show: Bool

        let connections: Connections

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
                        withAnimation { show = false }
                    }
                            .font(.title2)
                }
                        .padding()
            }
                    .padding([.top, .leading, .trailing])
        }
    }

    struct ConnectionCell : View {
        let label: String
        let connection: Eclair_kmpConnection

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
		
		@EnvironmentObject var currencyPrefs: CurrencyPrefs

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
				
                if transaction.status != .failure {
					HStack(spacing: 0) {
						
						let amount = Utils.format(currencyPrefs, msat: transaction.amountMsat)
						let isNegative = transaction.amountMsat < 0
						
						Text(isNegative ? "" : "+")
							.foregroundColor(isNegative ? .appRed : .appGreen)
							.padding(.trailing, 1)
						
						Text(amount.digits)
							.foregroundColor(isNegative ? .appRed : .appGreen)
							
						Text(" " + amount.type)
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

        let canScan: Bool

        @State var isShowingScan: Bool = false

        var body: some View {
            HStack {

                NavigationLink(
                        destination: ConfigurationView()
                ) {
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
                    Text("Send")
                            .foregroundColor(canScan ? .appDark : .gray)
                }
                        .disabled(!canScan)

                Spacer()
            }
                    .padding(.top, 10)
                    .padding(.bottom, keyWindow?.safeAreaInsets.bottom)
                    .background(Color.white)
                    .cornerRadius(15, corners: [.topLeft, .topRight])
        }
    }
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
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
        mockView(HomeView())
			.environmentObject(CurrencyPrefs.mockEUR())
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
