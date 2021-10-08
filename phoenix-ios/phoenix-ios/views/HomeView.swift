import SwiftUI
import PhoenixShared
import Network
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "HomeView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let PAGE_COUNT_START = 25
fileprivate let PAGE_COUNT_INCREMENT = 25

struct HomeView : MVIView, ViewName {

	static let phoenixBusiness = AppDelegate.get().business
	let phoenixBusiness: PhoenixBusiness = HomeView.phoenixBusiness
	
	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var selectedItem: WalletPaymentInfo? = nil
	@State var isMempoolFull = false
	
	@StateObject var toast = Toast()
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let paymentsPagerPublisher = phoenixBusiness.paymentsManager.paymentsPagePublisher()
	@State var paymentsPage = PaymentsManager.PaymentsPage(offset: 0, count: 0, rows: [])
	
	let lastCompletedPaymentPublisher = phoenixBusiness.paymentsManager.lastCompletedPaymentPublisher()
	let chainContextPublisher = phoenixBusiness.appConfigurationManager.chainContextPublisher()
	
	let incomingSwapsPublisher = phoenixBusiness.paymentsManager.incomingSwapsPublisher()
	@State var lastIncomingSwaps = [String: Lightning_kmpMilliSatoshi]()
	@State var incomingSwapScaleFactor: CGFloat = 1.0
	@State var incomingSwapAnimationsRemaining = 0
	
	let incomingSwapScaleFactor_BIG: CGFloat = 1.2
	
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.openURL) var openURL
	
	@State var didAppear = false
	@State var didPreFetch = false
	
	@ViewBuilder
	var view: some View {
		
		ZStack {

			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
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
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onReceive(paymentsPagerPublisher) {
			paymentsPageChanged($0)
		}
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
				AppStatusButton()
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
					// paymentsPage.rows: [WalletPaymentOrderRow]
					// WalletPaymentOrderRow.identifiable: String (defined in KotlinExtensions)
					//
					// Here's how this works:
					// - ForEach uses the given `id` (which conforms to Swift's Identifiable protocol)
					//   to determine whether or not the row is new/modified or the same as before.
					// - If the row is new/modified, then it it initialized with fresh state,
					//   and the row's `onAppear` will fire.
					// - If the row is unmodified, then it is initialized with existing state,
					//   and the row's `onAppear` with NOT fire.
					//
					// Since we use WalletPaymentOrderRow.identifiable, our unique identifier
					// contains the row's completedAt date, which is modified when the row changes.
					// Thus our row is automatically refreshed after it fails/succeeds.
					//
					ForEach(paymentsPage.rows, id: \.identifiable) { row in
						Button {
							didSelectPayment(row: row)
						} label: {
							PaymentCell(row: row, didAppearCallback: paymentCellDidAppear)
						}
					}
				}
			}

			BottomBar(toast: toast)
		
		} // </VStack>
		.onAppear {
			onAppear()
		}
		.sheet(isPresented: Binding(
			get: { selectedItem != nil },
			set: { if !$0 { selectedItem = nil }} // needed if user slides the sheet to dismiss
		)) {

			PaymentView(
				paymentInfo: selectedItem!,
				closeSheet: { self.selectedItem = nil }
			)
			.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
		}
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
	
	func didSelectPayment(row: WalletPaymentOrderRow) -> Void {
		log.trace("[\(viewName)] didSelectPayment()")
		
		// pretty much guaranteed to be in the cache
		let options = WalletPaymentFetchOptions.companion.Descriptions
		phoenixBusiness.paymentsManager.getPayment(row: row, options: options) { (result: WalletPaymentInfo?) in
			
			if let result = result {
				selectedItem = result
			}
		}
	}
	
	func onAppear() {
		log.trace("[\(viewName)] onAppear()")
		
		// Careful: this function may be called when returning from the Receive/Send view
		if !didAppear {
			didAppear = true
			AppDelegate.get().business.paymentsManager.subscribeToPaymentsPage(
				offset: 0,
				count: Int32(PAGE_COUNT_START)
			)
		}
	}
	
	func onModelChange(model: Home.Model) -> Void {
		log.trace("[\(viewName)] onModelChange()")
		
		// Todo: maybe update paymentsPage subscription ?
	}
	
	func paymentsPageChanged(_ page: PaymentsManager.PaymentsPage) -> Void {
		log.trace("[\(viewName)] paymentsPageChanged()")
		
		paymentsPage = page
		maybePreFetchPaymentsFromDatabase()
	}
	
	func lastCompletedPaymentChanged(_ payment: Lightning_kmpWalletPayment) -> Void {
		log.trace("[\(viewName)] lastCompletedPaymentChanged()")
		
		let paymentId = payment.walletPaymentId()
		let options = WalletPaymentFetchOptions.companion.Descriptions
		phoenixBusiness.paymentsManager.getPayment(id: paymentId, options: options) { result, _ in
			
			if selectedItem == nil {
				selectedItem = result // triggers display of PaymentView sheet
			}
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
	
	func maybePreFetchPaymentsFromDatabase() -> Void {
		
		if !didPreFetch && paymentsPage.rows.count > 0 {
			didPreFetch = true
			
			// Delay the pre-fetch process a little bit, to give priority to other app-startup tasks.
			DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
				prefetchPaymentsFromDatabase(idx: 0)
			}
		}
	}
	
	func prefetchPaymentsFromDatabase(idx: Int) {

		guard idx < paymentsPage.rows.count else {
			// recursion complete
			return
		}

		let row = paymentsPage.rows[idx]
		log.debug("[\(viewName)] Pre-fetching: \(row.identifiable)")

		let options = WalletPaymentFetchOptions.companion.Descriptions
		phoenixBusiness.paymentsManager.getPayment(row: row, options: options) { _ in
			prefetchPaymentsFromDatabase(idx: idx + 1)
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
	
	func paymentCellDidAppear(_ visibleRow: WalletPaymentOrderRow) -> Void {
		log.trace("[\(viewName)] paymentCellDidAppear(): \(visibleRow.id)")
		
		// Infinity Scrolling
		//
		// Here's the general idea:
		//
		// - We start by fetching a small "page" from the database.
		//   For example: Page(offset=0, count=50)
		// - When the user scrolls to the bottom, we can increase the count.
		//   For example: Page(offset=0, count=100)
		//
		// Note:
		// In the original design, we didn't increase the count forever.
		// At some point we incremented the offset instead.
		// However, this doesn't work well with LazyVStack, because the contentOffset isn't adjusted.
		// So the end result is that the user's position within the scrollView jumps,
		// and results in a very confusing user experience.
		// I cannot find a clean way of accomplishing a solution with pure SwiftUI.
		// So this remains a todo item for future improvement.
		
		var rowIdxWithinPage: Int? = nil
		for (idx, r) in paymentsPage.rows.enumerated() {
			
			if r == visibleRow {
				rowIdxWithinPage = idx
				break
			}
		}
		
		guard let rowIdxWithinPage = rowIdxWithinPage else {
			// Row not found within current page.
			// Perhaps the page just changed, and it no longer includes this row.
			return
		}
		
		let isLastRowWithinPage = rowIdxWithinPage + 1 == paymentsPage.rows.count
		if isLastRowWithinPage {
		
			let rowIdxWithinDatabase = Int(paymentsPage.offset) + rowIdxWithinPage
			let hasMoreRowsInDatabase = rowIdxWithinDatabase + 1 < mvi.model.paymentsCount
			
			if hasMoreRowsInDatabase {
				
				// increase paymentsPage.count
				
				let prvOffset = paymentsPage.offset
				let newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				log.debug("[\(viewName)] increasing page.count: Page(offset=\(prvOffset), count=\(newCount)")
				
				AppDelegate.get().business.paymentsManager.subscribeToPaymentsPage(
					offset: prvOffset,
					count: newCount
				)
			}
		}
	}
}

fileprivate struct PaymentCell : View, ViewName {

	let row: WalletPaymentOrderRow
	let didAppearCallback: (WalletPaymentOrderRow) -> Void
	
	let phoenixBusiness: PhoenixBusiness = AppDelegate.get().business
	
	@State var fetched: WalletPaymentInfo?
	@State var fetchedIsStale: Bool
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	init(
		row: WalletPaymentOrderRow,
		didAppearCallback: @escaping (WalletPaymentOrderRow)->Void
	) {
		self.row = row
		self.didAppearCallback = didAppearCallback
		
		let options = WalletPaymentFetchOptions.companion.Descriptions
		var result = phoenixBusiness.paymentsManager.getCachedPayment(row: row, options: options)
		if let _ = result {
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: false)
		} else {
			
			result = phoenixBusiness.paymentsManager.getCachedStalePayment(row: row, options: options)
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: true)
		}
	}
	
	var body: some View {
		
		HStack {
			if let payment = fetched?.payment {
				
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
							.foregroundColor(Color.appAccent)
							.padding(4)
					case .failure:
						Image("payment_holder_def_failed")
							.foregroundColor(Color.appAccent)
							.padding(4)
					default:
						Image(systemName: "doc.text.magnifyingglass")
							.padding(4)
				}
			} else {
				
				Image(systemName: "doc.text.magnifyingglass")
					.padding(4)
			}

			VStack(alignment: .leading) {
				Text(paymentDescription())
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.primaryForeground)

				Text(paymentTimestamp())
					.font(.caption)
					.foregroundColor(.secondary)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding([.leading, .trailing], 6)

			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {

				let (amount, isFailure, isOutgoing) = paymentAmountInfo()

				let color: Color = isFailure ? .secondary : (isOutgoing ? .appNegative : .appPositive)

				Text(verbatim: isOutgoing ? "-" : "+")
					.foregroundColor(color)
					.padding(.trailing, 1)

				Text(amount.digits)
					.foregroundColor(color)
					
				Text(verbatim: " \(amount.type)")
					.font(.caption)
					.foregroundColor(.gray)
			}
		}
		.padding([.top, .bottom], 14)
		.padding([.leading, .trailing], 12)
		.onAppear {
			onAppear()
		}
	}

	func paymentDescription() -> String {

		if let fetched = fetched {
			return fetched.paymentDescription() ?? NSLocalizedString("No description", comment: "placeholder text")
		} else {
			return ""
		}
	}
	
	func paymentTimestamp() -> String {

		if let payment = fetched?.payment {
			let timestamp = payment.completedAt()
			return timestamp > 0
				? timestamp.formatDateMS()
				: NSLocalizedString("pending", comment: "timestamp string for pending transaction")
		} else {
			return ""
		}
	}
	
	func paymentAmountInfo() -> (FormattedAmount, Bool, Bool) {

		if let payment = fetched?.payment {

			let amount = Utils.format(currencyPrefs, msat: payment.amount)

			let isFailure = payment.state() == WalletPaymentState.failure
			let isOutgoing = payment is Lightning_kmpOutgoingPayment

			return (amount, isFailure, isOutgoing)

		} else {
			
			let currency = currencyPrefs.currency
			let amount = FormattedAmount(currency: currency, digits: "", decimalSeparator: " ")

			let isFailure = false
			let isOutgoing = true

			return (amount, isFailure, isOutgoing)
		}
	}
	
	func onAppear() -> Void {
		
		if fetched == nil || fetchedIsStale {
			
			let options = WalletPaymentFetchOptions.companion.Descriptions
			phoenixBusiness.paymentsManager.getPayment(row: row, options: options) { (result: WalletPaymentInfo?) in
				self.fetched = result
			}
		}
		
		didAppearCallback(row)
	}
}

fileprivate struct AppStatusButton: View, ViewName {
	
	@State var dimStatus = false
	
	@State var syncState: SyncManagerState = .initializing
	@State var pendingSettings: PendingSettings? = nil
	
	@StateObject var connectionsManager = ObservableConnectionsManager()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	let syncManager = AppDelegate.get().syncManager!
	
	@ViewBuilder
	var body: some View {
		
		Button {
			showAppStatusPopover()
		} label: {
			buttonContent
		}
		.buttonStyle(PlainButtonStyle())
		.padding(.all, 7)
		.background(Color.buttonFill)
		.cornerRadius(30)
		.overlay(
			RoundedRectangle(cornerRadius: 30) // Test this with larger dynamicFontSize
				.stroke(Color.borderColor, lineWidth: 1)
		)
		.onReceive(syncManager.statePublisher) {
			syncManagerStateChanged($0)
		}
		.onReceive(syncManager.pendingSettingsPublisher) {
			syncManagerPendingSettingsChanged($0)
		}
	}
	
	@ViewBuilder
	var buttonContent: some View {
		
		let connectionStatus = connectionsManager.connections.global
		if connectionStatus == .closed {
			HStack(alignment: .firstTextBaseline, spacing: 3) {
				Image(systemName: "bolt.slash.fill")
					.imageScale(.large)
				Text(NSLocalizedString("Offline", comment: "Connection state"))
					.padding(.trailing, 6)
			}
			.font(.caption2)
		}
		else if connectionStatus == .establishing {
			HStack(alignment: .firstTextBaseline, spacing: 3) {
				Image(systemName: "bolt.slash")
					.imageScale(.large)
				Text(NSLocalizedString("Connecting...", comment: "Connection state"))
					.padding(.trailing, 6)
			}
			.font(.caption2)
		} else /* .established */ {
			
			if pendingSettings != nil {
				// The user enabled/disabled cloud sync.
				// We are using a 30 second delay before we start operating on the user's decision.
				// Just in-case it was an accidental change, or the user changes his/her mind.
				Image(systemName: "hourglass")
					.imageScale(.large)
					.font(.caption2)
					.squareFrame()
			} else {
				let (isSyncing, isWaiting, isError) = buttonizeSyncStatus()
				if isSyncing {
					Image(systemName: "icloud")
						.imageScale(.large)
						.font(.caption2)
						.squareFrame()
				} else if isWaiting {
					Image(systemName: "hourglass")
						.imageScale(.large)
						.font(.caption2)
						.squareFrame()
				} else if isError {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.font(.caption2)
						.squareFrame()
				} else {
					// Everything is good: connected + {synced|disabled|initializing}
					Image(systemName: "bolt.fill")
						.imageScale(.large)
						.font(.caption2)
						.squareFrame()
				}
			}
		}
	}
	
	func buttonizeSyncStatus() -> (Bool, Bool, Bool) {
		
		var isSyncing = false
		var isWaiting = false
		var isError = false
		
		switch syncState {
			case .initializing: break
			case .updatingCloud: isSyncing = true
			case .downloading: isSyncing = true
			case .uploading: isSyncing = true
			case .waiting(let details):
				switch details.kind {
					case .forInternet: break
					case .forCloudCredentials: break // see discussion below
					case .exponentialBackoff: isError = true
					case .randomizedUploadDelay: isWaiting = true
				}
			case .synced: break
			case .disabled: break
		}
		
		// If the user isn't signed into iCloud, is this an error ?
		// We are choosing to treat it more like the disabled case,
		// since the user has choosed to not sign in,
		// or has ignored Apple's continual "sign into iCloud" popups.
		
		return (isSyncing, isWaiting, isError)
	}
	
	func syncManagerStateChanged(_ newState: SyncManagerState) -> Void {
		log.trace("[\(viewName)] syncManagerStateChanged()")
		
		syncState = newState
	}
	
	func syncManagerPendingSettingsChanged(_ newPendingSettings: PendingSettings?) -> Void {
		log.trace("[\(viewName)] syncManagerPendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	func showAppStatusPopover() -> Void {
		log.trace("[\(viewName)] showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}

fileprivate struct FaqButton: View, ViewName {
	
	@Environment(\.openURL) var openURL
	
	var body: some View {
		
		Menu {
			Button {
				faqButtonTapped()
			} label: {
				Label("FAQ", systemImage: "safari")
			}
			Button {
				sendFeedbackButtonTapped()
			} label: {
				Label("Send feedback", systemImage: "envelope")
			}
		} label: {
			Image(systemName: "questionmark")
				.renderingMode(.template)
				.imageScale(.large)
				.font(.caption2)
				.foregroundColor(.primary)
				.squareFrame()
				.buttonStyle(PlainButtonStyle())
				.padding(.all, 7)
				.background(Color.buttonFill)
				.cornerRadius(30)
				.overlay(
					RoundedRectangle(cornerRadius: 30)
						.stroke(Color.borderColor, lineWidth: 1)
				)
		}
	}
	
	func faqButtonTapped() -> Void {
		log.trace("[\(viewName)] faqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func sendFeedbackButtonTapped() -> Void {
		log.trace("[\(viewName)] sendFeedbackButtonTapped()")
		
		let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
		let device = UIDevice.current
		
		var body = "Phoenix v\(appVersion ?? "x.y.z") "
		body += "(\(device.systemName) \(device.systemVersion))"
		
		var comps = URLComponents()
		comps.scheme = "mailto"
		comps.path = "phoenix@acinq.co"
		comps.queryItems = [
			URLQueryItem(name: "subject", value: "Phoenix iOS Feedback"),
			URLQueryItem(name: "body", value: body)
		]

		if let url = comps.url {
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
	@State var externalLightningRequest: Scan.ModelInvoiceFlowInvoiceRequest? = nil
	
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
		.onReceive(AppDelegate.get().externalLightningUrlPublisher) { (url: URL) in
			didReceiveExternalLightningUrl(url)
		}
		.onChange(of: selectedTag) { tag in
			if tag == nil {
				// If we pushed the SendView with a external lightning url,
				// we should nil out the url (since the user is finished with it now).
				self.externalLightningRequest = nil
			}
		}
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
			
			if let model = model as? Scan.ModelInvoiceFlowInvoiceRequest {
				self.externalLightningRequest = model
				self.selectedTag = Tag.SendView.rawValue
				
			} else if let _ = model as? Scan.ModelBadRequest {
				let msg = NSLocalizedString("Invalid Lightning Request", comment: "toast warning")
				toast.pop(
					Text(msg).anyView,
					colorScheme: colorScheme.opposite,
					duration: 4.0,
					location: .middle
				)
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
			paymentsCount: 0
		))
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())

		HomeView().mock(Home.Model(
			balance: Lightning_kmpMilliSatoshi(msat: 1000000),
			incomingBalance: Lightning_kmpMilliSatoshi(msat: 12000000),
			paymentsCount: 0
		))
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		.environmentObject(CurrencyPrefs.mockEUR())
	}
}
