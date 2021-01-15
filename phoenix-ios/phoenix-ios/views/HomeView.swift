import SwiftUI
import PhoenixShared
import Network

struct HomeView : View {

    @State var lastTransaction: PhoenixShared.Transaction? = nil
    @State var showConnections = false

    @State var selectedTransaction: PhoenixShared.Transaction? = nil
	
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
				
				// === Top-row buttons ===
				HStack {
					ConnectionStatusButton()
					Spacer()
					FaqButton()
				}
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

				BottomBar(model: model)
			
			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top)
			.padding(.top)
		
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea(.top)
		.edgesIgnoringSafeArea(.bottom)
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
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
					.foregroundColor(.primaryForeground)
				Text(transaction.timestamp.formatDateMS())
					.font(.caption)
					.foregroundColor(.secondary)
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

struct ConnectionStatusButton : View {
	
	@State var dimStatus = false
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		let status = connectionsMonitor.connections.global
		
		Group {
			Button {
				popoverState.dismissable.send(true)
				popoverState.displayContent.send(
					ConnectionPopup().anyView
				)
			} label: {
				HStack {
					Image("ic_connection_lost")
						.resizable()
						.frame(width: 16, height: 16)
					Text(status.localizedText())
						.font(.caption2)
				}
			}
			.buttonStyle(PlainButtonStyle())
			.padding([.leading, .top, .bottom], 4)
			.padding([.trailing], 6)
			.background(Color.buttonFill)
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

struct FaqButton: View {
	
	@Environment(\.openURL) var openURL
	
	var body: some View {
		
		Button {
			openURL(URL(string: "https://phoenix.acinq.co/faq")!)
		} label: {
			HStack {
				Image(systemName: "questionmark.circle")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 16, height: 16)
				Text("FAQ")
					.font(.caption2)
			}
		}
		.buttonStyle(PlainButtonStyle())
		.padding([.top, .bottom], 4)
		.padding([.leading, .trailing], 6)
		.background(Color.buttonFill)
		.cornerRadius(10)
		.overlay(
			RoundedRectangle(cornerRadius: 10)
				.stroke(Color(UIColor.systemGray), lineWidth: 1)
		)
	}
}

struct BottomBar: View {

	let model: Home.Model

	@Environment(\.colorScheme) var colorScheme
	
	@State var isShowingScan: Bool = false

	var body: some View {
		
		HStack {

			NavigationLink(
				destination: ConfigurationView()
			) {
				Image("ic_settings")
					.resizable()
					.frame(width: 22, height: 22)
			}
			.padding()
			.padding(.leading, 8)

			Divider().frame(height: 40)
			Spacer()
			
			NavigationLink(
				destination: ReceiveView()
			) {
				HStack {
					Image("ic_receive")
						.resizable()
						.frame(width: 22, height: 22)
					Text("Receive")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
			Divider().frame(height: 40)
			Spacer()

			NavigationLink(
				destination: ScanView(isShowing: $isShowingScan),
				isActive: $isShowingScan
			) {
				HStack {
					Image("ic_scan")
						.resizable()
						.frame(width: 22, height: 22)
					Text("Send")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
		}
		.padding(.top, 10)
		.padding(.bottom, keyWindow?.safeAreaInsets.bottom)
		.background(colorScheme == .dark ? Color(UIColor.secondarySystemBackground) : Color.white)
		.cornerRadius(15, corners: [.topLeft, .topRight])
	}
}

struct ConnectionPopup : View {

	@StateObject var monitor = ObservableConnectionsMonitor()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Connection status")
				.font(.title3)
				.padding(.bottom)
			Divider()
			ConnectionCell(label: "Internet", connection: monitor.connections.internet)
			Divider()
			ConnectionCell(label: "Lightning peer", connection: monitor.connections.peer)
			Divider()
			ConnectionCell(label: "Electrum server", connection: monitor.connections.electrum)
			Divider()
			HStack {
				Spacer()
				Button("OK") {
					withAnimation {
						popoverState.close.send()
					}
				}
				.font(.title2)
			}
			.padding(.top)
		}
		.padding()
	}
}

struct ConnectionCell : View {
	let label: String
	let connection: Eclair_kmpConnection

	var body : some View {
		HStack {
			let bullet = Image("ic_bullet").resizable().frame(width: 10, height: 10)

			if connection == .established {
				bullet.foregroundColor(.appGreen)
			}
			else if connection == .establishing {
				bullet.foregroundColor(.appYellow)
			}
			else if connection == .closed {
				bullet.foregroundColor(.appRed)
			}

			Text("\(label):")
			Spacer()
			Text(connection.localizedText())
		}
		.padding([.top, .bottom], 8)
	}
}

// MARK: -

class HomeView_Previews: PreviewProvider {
	
	static let connections = Connections(
		internet : .established,
		peer     : .established,
		electrum : .closed
	)
	
	static let mockModel = Home.Model(
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
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
			.environmentObject(CurrencyPrefs.mockEUR())
		
		mockView(HomeView())
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
			.environmentObject(CurrencyPrefs.mockEUR())
		
	//	mockView(HomeView())
	//		.preferredColorScheme(.light)
	//		.previewDevice("iPhone 11")
	//		.environmentObject(CurrencyPrefs.mockEUR())
	}

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
