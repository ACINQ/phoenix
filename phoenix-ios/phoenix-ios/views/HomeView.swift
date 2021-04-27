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

struct HomeView : MVIView, ViewName {

	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var selectedPayment: Lightning_kmpWalletPayment? = nil
	
	@State var isMempoolFull = false
	
	@StateObject var toast = Toast()
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let lastCompletedPaymentPublisher = AppDelegate.get().business.paymentsManager.lastCompletedPaymentPublisher()
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	let incomingSwapsPublisher = AppDelegate.get().business.paymentsManager.incomingSwapsPublisher()
	@State var lastIncomingSwaps = [String: Lightning_kmpMilliSatoshi]()
	@State var incomingSwapScaleFactor: CGFloat = 1.0
	@State var incomingSwapAnimationsRemaining = 0
	
	let incomingSwapScaleFactor_BIG: CGFloat = 1.2
	
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.openURL) var openURL
	
	@ViewBuilder
	var view: some View {

		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			main
			
			toast.view()
			
		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
		.onReceive(lastCompletedPaymentPublisher) {
			lastCompletedPaymentChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
		.onReceive(incomingSwapsPublisher) { incomingSwaps in
			onIncomingSwapsChanged(incomingSwaps)
		}
	}
	
	@ViewBuilder
	var main: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			// === Top-row buttons ===
			HStack {
				ConnectionStatusButton()
				Spacer()
				FaqButton()
			}
			.padding(.all)

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
					.scaleEffect(incomingSwapScaleFactor, anchor: .top)
					.onAnimationCompleted(for: incomingSwapScaleFactor) {
						incomingSwapAnimationCompleted()
					}
				}
			}
			.padding([.top, .leading, .trailing])
			.padding(.bottom, 30)
			.background(
				VStack {
					Spacer()
					RoundedRectangle(cornerRadius: 10)
						.frame(width: 70, height: 6, alignment: /*@START_MENU_TOKEN@*/.center/*@END_MENU_TOKEN@*/)
						.foregroundColor(Color.appAccent)
				}
			)
			.padding(.bottom, 25)

			// === Beta Version Disclaimer ===
			DisclaimerBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "umbrella")
						.imageScale(.large)
						.padding(.trailing, 10)
					Text("This app is experimental. Please backup your seed. You can report issues to phoenix@acinq.co.")
				}
				.font(.caption)
			}
			
			// === Mempool Full Warning ====
			if isMempoolFull {
				DisclaimerBox {
					HStack(alignment: VerticalAlignment.top, spacing: 0) {
						Image(systemName: "exclamationmark.triangle")
							.imageScale(.large)
							.padding(.trailing, 10)
						VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
							Text("Bitcoin mempool is full and fees are high.")
							Button {
								mempoolFullInfo()
							} label: {
								Text("See how Phoenix is affected".uppercased())
							}
						}
					}
					.font(.caption)
				}
			}
			
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
	}
	
	func incomingAmount() -> FormattedAmount? {
		
		let msatTotal = lastIncomingSwaps.values.reduce(Int64(0)) {(sum, item) -> Int64 in
			return sum + item.msat
		}
		if msatTotal > 0 {
			return Utils.format(currencyPrefs, msat: msatTotal)
		} else {
			return nil
		}
	}
	
	func lastCompletedPaymentChanged(_ payment: Lightning_kmpWalletPayment) -> Void {
		log.trace("[\(viewName)] lastCompletedPaymentChanged()")
		
		if selectedPayment == nil {
			selectedPayment = payment // selection triggers display of PaymentView sheet
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("[\(viewName)] chainContextChanged()")
		
		isMempoolFull = context.mempool.v1.highUsage
	}
	
	func onIncomingSwapsChanged(_ incomingSwaps: [String: Lightning_kmpMilliSatoshi]) -> Void {
		log.trace("[\(viewName)] onIncomingSwapsChanged()")
		
		let oldSum = lastIncomingSwaps.values.reduce(Int64(0)) {(sum, item) -> Int64 in
			return sum + item.msat
		}
		let newSum = incomingSwaps.values.reduce(Int64(0)) { (sum, item) -> Int64 in
			return sum + item.msat
		}
		
		lastIncomingSwaps = incomingSwaps
		if newSum > oldSum {
			// Since the sum increased, there is a new incomingSwap for the user.
			// This isn't added to the transaction list, but is instead displayed under the balance.
			// So let's add a little animation to draw the user's attention to it.
			startAnimatingIncomingSwapText()
		}
	}
	
	func startAnimatingIncomingSwapText() -> Void {
		log.trace("[\(viewName)] startAnimatingIncomingSwapText()")
		
		// Here's what we want to happen:
		// - text starts at normal scale (factor = 1.0)
		// - text animates to bigger scale, and then back to normal
		// - it repeats this animation a few times
		// - and ends the animation at the normal scale
		//
		// This is annoyingly difficult to do with SwiftUI.
		// That is, it's easy to do something like this:
		//
		// withAnimation(Animation.linear(duration: duration).repeatCount(4, autoreverses: true)) {
		//     self.incomingSwapScaleFactor = 1.2
		// }
		//
		// But this doesn't give us what we want.
		// Because when the animation ends, the text is scaled at factor 1.2, and not at the normal 1.0.
		//
		// There are hacks that rely on doing something like this:
		//
		// DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
		//     withAnimation(Animation.linear(duration: duration)) {
		//         self.incomingSwapScaleFactor = 1.0
		//     }
		// }
		//
		// But it has a tendancy to be jumpy.
		// Because it depends on the animation timing to be exact, and the dispatch_after to also be exact.
		//
		// A smoother animation can be had by using a completion callback for the animation.
		// The only caveat is that this callback will be invoked for each animation.
		// That is, if we use the `animation.repeatCount(4)`, the callback will be invoked 4 times.
		//
		// So our solution is to use 2 state variables:
		//
		// incomingSwapAnimationsRemaining: Int
		// incomingSwapScaleFactor: CGFloat
		//
		// We increment `incomingSwapAnimationsRemaining` by some even number.
		// And then we animate the Text in a single direction (either bigger or smaller).
		// After each animation completes, we decrement `incomingSwapAnimationsRemaining`.
		// If the result is still greater than zero, then we reverse the direction of the animation.
		
		if incomingSwapAnimationsRemaining == 0 {
			incomingSwapAnimationsRemaining += (4 * 2) // must be even
			animateIncomingSwapText()
		}
	}
	
	func animateIncomingSwapText() -> Void {
		log.trace("[\(viewName)] animateIncomingSwapText()")
		
		let duration = 0.5 // seconds
		let nextScaleFactor = incomingSwapScaleFactor == 1.0 ? incomingSwapScaleFactor_BIG : 1.0
		
		withAnimation(Animation.linear(duration: duration)) {
			self.incomingSwapScaleFactor = nextScaleFactor
		}
	}
	
	func incomingSwapAnimationCompleted() -> Void {
		log.trace("[\(viewName)] incomingSwapAnimationCompleted()")
		
		incomingSwapAnimationsRemaining -= 1
		log.debug("[\(viewName)]: incomingSwapAnimationsRemaining = \(incomingSwapAnimationsRemaining)")
		
		if incomingSwapAnimationsRemaining > 0 {
			animateIncomingSwapText()
		}
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func mempoolFullInfo() -> Void {
		log.trace("[\(viewName)] mempoolFullInfo()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq#high-mempool-size-impacts") {
			openURL(url)
		}
	}
}

fileprivate struct PaymentCell : View {

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
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				let amount = Utils.format(currencyPrefs, msat: payment.amountMsat())
				
				let isFailure = payment.state() == WalletPaymentState.failure
				let isNegative = payment.amountMsat() < 0
				
				let color: Color = isFailure ? .secondary : (isNegative ? .appNegative : .appPositive)
				
				Text(isNegative ? "" : "+")
					.foregroundColor(color)
					.padding(.trailing, 1)
				
				Text(amount.digits)
					.foregroundColor(color)
					
				Text(" " + amount.type)
					.font(.caption)
					.foregroundColor(.gray)
			}
		}
		.padding([.top, .bottom], 14)
		.padding([.leading, .trailing], 12)
	}
}

fileprivate struct ConnectionStatusButton : View {
	
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

fileprivate struct FaqButton: View, ViewName {
	
	@Environment(\.openURL) var openURL
	
	var body: some View {
		
		Button {
			didTapButton()
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
	
	func didTapButton() -> Void {
		log.trace("[\(viewName)] didTapButton()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
}

fileprivate struct DisclaimerBox<Content: View>: View {
	
	let content: Content
	
	init(@ViewBuilder builder: () -> Content) {
		content = builder()
	}
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			content
			Spacer() // ensure content takes up full width of screen
		}
		.padding(12)
		.background(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.appAccent, lineWidth: 1)
		)
		.padding([.leading, .trailing, .bottom], 10)
	}
}

fileprivate struct BottomBar: View, ViewName {

	@ObservedObject var toast: Toast
	
	@State private var selectedTag: String? = nil
	enum Tag: String {
		case ConfigurationView
		case ReceiveView
		case SendView
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

			Divider().frame(width: 1, height: 40).background(Color.borderColor)
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
			Divider().frame(width: 1, height: 40).background(Color.borderColor)
			Spacer()

			NavigationLink(
				destination: SendView(firstModel: externalLightningRequest),
				tag: Tag.SendView.rawValue,
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
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
		.onReceive(AppDelegate.get().externalLightningUrlPublisher, perform: { (url: URL) in
			didReceiveExternalLightningUrl(url)
		})
		.onChange(of: selectedTag, perform: { tag in
			if tag == nil {
				// If we pushed the SendView with a external lightning url,
				// we should nil out the url (since the user is finished with it now).
				self.externalLightningRequest = nil
			}
		})
	}
	
	func didReceiveExternalLightningUrl(_ url: URL) -> Void {
		log.trace("[\(viewName)] didReceiveExternalLightningUrl()")
		
		if selectedTag == Tag.SendView.rawValue {
			log.debug("[\(viewName)] Ignoring: handled by SendView")
			return
		}
		
		// We want to:
		// - Parse the incoming lightning url
		// - If it's invalid, we want to display a warning (using the Toast view)
		// - Otherwise we want to jump DIRECTLY to the "Confirm Payment" screen.
		//
		// In particular, we do **NOT** want the user experience to be:
		// - switch to SendView
		// - then again switch to ConfirmView
		// This feels jittery :(
		//
		// So we need to:
		// - get a Scan.ModelValidate instance
		// - pass this to SendView as the `firstModel` parameter
		
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
				self.selectedTag = Tag.SendView.rawValue
				
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
