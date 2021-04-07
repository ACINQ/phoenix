import SwiftUI
import PhoenixShared
import Network
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "HomeView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct HomeView : MVIView {

	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var lastCompletedPayment: PhoenixShared.Lightning_kmpWalletPayment? = nil
	@State var showConnections = false

	@State var selectedPayment: PhoenixShared.Lightning_kmpWalletPayment? = nil
	
	@StateObject var toast = Toast()
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let lastCompletedPaymentPublisher = KotlinPassthroughSubject<Lightning_kmpWalletPayment>(
		AppDelegate.get().business.paymentsManager.lastCompletedPayment
	)

	@ViewBuilder
	var view: some View {

		main
			.navigationBarTitle("", displayMode: .inline)
			.navigationBarHidden(true)
			.onReceive(lastCompletedPaymentPublisher) { (payment: Lightning_kmpWalletPayment) in
				
				if lastCompletedPayment != payment {
					lastCompletedPayment = payment
					selectedPayment = payment
				}
			}
	}
	
	@ViewBuilder
	var main: some View {
		
		ZStack {
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
			}
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				// === Top-row buttons ===
				HStack {
					ConnectionStatusButton()
					Spacer()
					FaqButton()
				}
				.padding()

				// === Total Balance ====
				VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
					HStack(alignment: VerticalAlignment.bottom) {
						
						let amount = Utils.format(currencyPrefs, msat: mvi.model.balance.msat)
						Text(amount.digits)
							.font(.largeTitle)
							.onTapGesture { toggleCurrencyType() }
						
						Text(amount.type)
							.font(.title2)
							.foregroundColor(Color.appAccent)
							.padding(.bottom, 4)
							.onTapGesture { toggleCurrencyType() }
						
					} // </HStack>
					
					if let incoming = incomingAmount() {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							
							Image(systemName: "link")
								.padding(.trailing, 2)
							
							Text("+\(incoming.string) incoming".lowercased())
								.onTapGesture { toggleCurrencyType() }
						}
						.font(.callout)
						.foregroundColor(.secondary)
						.padding(.top, 7)
						.padding(.bottom, 2)
					}
				}
				.padding([.top, .leading, .trailing])
				.padding(.bottom, 33)
				.background(
					VStack {
						Spacer()
						RoundedRectangle(cornerRadius: 10)
							.frame(width: 70, height: 6, alignment: /*@START_MENU_TOKEN@*/.center/*@END_MENU_TOKEN@*/)
							.foregroundColor(Color.appAccent)
					}
				)
				.padding(.bottom, 25)

				// === Disclaimer ===
				VStack {
					Text("This app is experimental. Please back up your seed. \nYou can report issues to phoenix@acinq.co.")
						.font(.caption)
						.padding(12)
						.background(
							RoundedRectangle(cornerRadius: 5)
								.stroke(Color.appAccent, lineWidth: 1)
						)
				}.padding(12)

				// === Payments List ====
				ScrollView {
					LazyVStack {
						ForEach(mvi.model.payments.indices, id: \.self) { index in
							Button {
								selectedPayment = mvi.model.payments[index]
							} label: {
								PaymentCell(payment: mvi.model.payments[index])
							}
						}
					}
					.sheet(isPresented: .constant(selectedPayment != nil)) {
						selectedPayment = nil
					} content: {
						PaymentView(
							payment: selectedPayment!,
							close: { selectedPayment = nil }
						)
						.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
					}
				}

				BottomBar(toast: toast)
			
			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top ?? 0) // bottom handled in BottomBar
			.padding(.top)
		
			toast.view()
			
		} // </ZStack>
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea(.all)
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func incomingAmount() -> FormattedAmount? {
		
		if let incomingMsat = mvi.model.incomingBalance, incomingMsat.toLong() > 0 {
			return Utils.format(currencyPrefs, msat: incomingMsat)
		}
		return nil
	}
}

struct PaymentCell : View {

	let payment: PhoenixShared.Lightning_kmpWalletPayment
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	var body: some View {
		HStack {
			switch payment.state() {
			case .success:
				Image("payment_holder_def_success")
					.foregroundColor(Color.accentColor)
					.padding(4)
					.background(
						RoundedRectangle(cornerRadius: .infinity)
							.fill(Color.appAccent)
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
				Text(payment.desc() ?? NSLocalizedString("No description", comment: "placeholder text"))
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.primaryForeground)
				
				let timestamp = payment.timestamp()
				let timestampStr = timestamp > 0
					? timestamp.formatDateMS()
					: NSLocalizedString("pending", comment: "timestamp string for pending transaction")
				
				Text(timestampStr)
					.font(.caption)
					.foregroundColor(.secondary)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding([.leading, .trailing], 6)
			
			if payment.state() != .failure {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					let amount = Utils.format(currencyPrefs, msat: payment.amountMsat())
					let isNegative = payment.amountMsat() < 0
					
					Text(isNegative ? "" : "+")
						.foregroundColor(isNegative ? .appNegative : .appPositive)
						.padding(.trailing, 1)
					
					Text(amount.digits)
						.foregroundColor(isNegative ? .appNegative : .appPositive)
						
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
				showConnectionsPopover()
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
			.padding([.leading, .top, .bottom], 5)
			.padding([.trailing], 10)
			.background(Color.buttonFill)
			.cornerRadius(30)
			.overlay(
				RoundedRectangle(cornerRadius: 30)
					.stroke(Color.borderColor, lineWidth: 1)
			)
			.opacity(dimStatus ? 0.2 : 1.0)
			.isHidden(status == Lightning_kmpConnection.established)
		}
		.onAppear {
			DispatchQueue.main.async {
				withAnimation(Animation.linear(duration: 1.0).repeatForever()) {
					self.dimStatus.toggle()
				}
			}
		}
	}
	
	func showConnectionsPopover() -> Void {
		log.trace("(ConnectionStatusButton) showConnectionsPopover()")
		
		popoverState.display.send(PopoverItem(
		
			ConnectionsPopover().anyView,
			dismissable: true
		))
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
		.padding([.leading, .top, .bottom], 5)
		.padding([.trailing], 10)
		.background(Color.buttonFill)
		.cornerRadius(30)
		.overlay(
			RoundedRectangle(cornerRadius: 30)
				.stroke(Color.borderColor, lineWidth: 1)
		)
	}
}

struct BottomBar: View, ViewName {

	@ObservedObject var toast: Toast
	
	@State private var selectedTag: String? = nil
	enum Tag: String {
		case ConfigurationView
		case ReceiveView
		case ScanView
	}
	
	@State var temp: [AppScanController] = []
	@State var externalLightningRequest: Scan.ModelValidate? = nil
	
	@Environment(\.colorScheme) var colorScheme
	
	var body: some View {
		
		HStack {

			NavigationLink(
				destination: ConfigurationView(),
				tag: Tag.ConfigurationView.rawValue,
				selection: $selectedTag
			) {
				Image("ic_settings")
					.resizable()
					.frame(width: 22, height: 22)
					.foregroundColor(Color.appAccent)
			}
			.padding()
			.padding(.leading, 8)

			Divider().frame(height: 40).background(Color.borderColor)
			Spacer()
			
			NavigationLink(
				destination: ReceiveView(),
				tag: Tag.ReceiveView.rawValue,
				selection: $selectedTag
			) {
				HStack {
					Image("ic_receive")
						.resizable()
						.frame(width: 22, height: 22)
						.foregroundColor(Color.appAccent)
						.padding(4)
					Text("Receive")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
			Divider().frame(height: 40).background(Color.borderColor)
			Spacer()

			NavigationLink(
				destination: ScanView(firstModel: externalLightningRequest),
				tag: Tag.ScanView.rawValue,
				selection: $selectedTag
			) {
				HStack {
					Image("ic_scan")
						.resizable()
						.frame(width: 22, height: 22)
						.foregroundColor(Color.appAccent)
						.padding(4)
					Text("Send")
						.foregroundColor(.primaryForeground)
				}
			}

			Spacer()
		}
		.padding(.top, 10)
		.padding(.bottom, keyWindow?.safeAreaInsets.bottom)
		.background(Color.mutedBackground)
		.cornerRadius(15, corners: [.topLeft, .topRight])
		.onReceive(AppDelegate.get().externalLightningUrlPublisher, perform: { (url: URL) in
			didReceiveExternalLightningUrl(url)
		})
		.onChange(of: selectedTag, perform: { tag in
			if tag == nil {
				// If we pushed the ScanView with a external lightning url,
				// we should nil out the url (since the user is finished with it now).
				self.externalLightningRequest = nil
			}
		})
	}
	
	func didReceiveExternalLightningUrl(_ url: URL) -> Void {
		log.trace("[\(viewName)] didReceiveExternalLightningUrl()")
		
		if selectedTag == Tag.ScanView.rawValue {
			log.debug("[\(viewName)] Ignoring: handled by ScanView")
			return
		}
		
		// We want to:
		// - Parse the incoming lightning url
		// - If it's invalid, we want to display a warning (using the Toast view)
		// - Otherwise we want to jump DIRECTLY to the "Confirm Payment" screen.
		//
		// In particular, we do **NOT** want the user experience to be:
		// - switch to ScanView
		// - then again switch to ConfirmView
		// This feels jittery :(
		//
		// So we need to:
		// - get a Scan.ModelValidate instance
		// - pass this to ScanView as the `firstModel` parameter
		
		let controllers = AppDelegate.get().business.controllers
		guard let scanController = controllers.scan(firstModel: Scan.ModelReady()) as? AppScanController else {
			return
		}
		temp.append(scanController)
		
		var unsubscribe: (() -> Void)? = nil
		var isFirstFire = true
		unsubscribe = scanController.subscribe { (model: Scan.Model) in
			
			if isFirstFire { // ignore first subscription fire (Scan.ModelReady)
				isFirstFire = false
				return
			}
			
			if let model = model as? Scan.ModelValidate {
				self.externalLightningRequest = model
				self.selectedTag = Tag.ScanView.rawValue
				
			} else if let _ = model as? Scan.ModelBadRequest {
				let msg = NSLocalizedString("Invalid Lightning Request", comment: "toast warning")
				toast.toast(text: msg, duration: 4.0, location: .middle)
			}
			
			// Cleanup
			if let idx = self.temp.firstIndex(where: { $0 === scanController }) {
				self.temp.remove(at: idx)
			}
			unsubscribe?()
		}
		
		scanController.intent(intent: Scan.IntentParse(request: url.absoluteString))
	}
}

// MARK: -

class HomeView_Previews: PreviewProvider {
	
	static let connections = Connections(
		internet : .established,
		peer     : .established,
		electrum : .closed
	)

	static var previews: some View {

		HomeView().mock(Home.Model(
			balance: Lightning_kmpMilliSatoshi(msat: 123500),
			incomingBalance: Lightning_kmpMilliSatoshi(msat: 0),
			payments: []
		))
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
		
		HomeView().mock(Home.Model(
			balance: Lightning_kmpMilliSatoshi(msat: 1000000),
			incomingBalance: Lightning_kmpMilliSatoshi(msat: 12000000),
			payments: []
		))
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
	}
}
