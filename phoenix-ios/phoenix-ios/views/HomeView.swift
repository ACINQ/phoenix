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


fileprivate enum NavLinkTag: String {
	case ConfigurationView
	case ReceiveView
	case SendView
	case CurrencyConverter
}

struct HomeView : MVIView {

	static let appDelegate = AppDelegate.get()
	static let phoenixBusiness = appDelegate.business
	static let encryptedNodeId = appDelegate.encryptedNodeId!
	
	let phoenixBusiness = HomeView.phoenixBusiness
	let encryptedNodeId = HomeView.encryptedNodeId
	
	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var selectedItem: WalletPaymentInfo? = nil
	@State var isMempoolFull = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let paymentsPagePublisher = phoenixBusiness.paymentsManager.paymentsPagePublisher()
	@State var paymentsPage = PaymentsManager.PaymentsPage(offset: 0, count: 0, rows: [])
	
	let lastCompletedPaymentPublisher = phoenixBusiness.paymentsManager.lastCompletedPaymentPublisher()
	let chainContextPublisher = phoenixBusiness.appConfigurationManager.chainContextPublisher()
	
	let incomingSwapsPublisher = phoenixBusiness.paymentsManager.incomingSwapsPublisher()
	let incomingSwapScaleFactor_BIG: CGFloat = 1.2
	@State var lastIncomingSwaps = [String: Lightning_kmpMilliSatoshi]()
	@State var incomingSwapScaleFactor: CGFloat = 1.0
	@State var incomingSwapAnimationsRemaining = 0
	
	// Toggles confirmation dialog (used to select preferred explorer)
	@State var showBlockchainExplorerOptions = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.openURL) var openURL
	@Environment(\.colorScheme) var colorScheme
	
	@State var didAppear = false
	@State var didPreFetch = false
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	let externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	@State var externalLightningRequest: AppScanController? = nil
	@State var temp: [AppScanController] = []
	
	let backupSeed_enabled_publisher = Prefs.shared.backupSeed_isEnabled_publisher
	let manualBackup_taskDone_publisher = Prefs.shared.manualBackup_taskDone_publisher
	@State var backupSeed_enabled = Prefs.shared.backupSeed_isEnabled
	@State var manualBackup_taskDone = Prefs.shared.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
	
	@ViewBuilder
	var view: some View {
		
		ZStack {

			// iOS 14 & 15 have bugs when using NavigationLink.
			// The suggested workarounds include using only a single NavigationLink.
			//
			NavigationLink(
				destination: navLinkView(),
				isActive: Binding(
					get: { navLinkTag != nil },
					set: { if !$0 { navLinkTag = nil }}
				)
			) {
				EmptyView()
			}
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}

			main

		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onChange(of: navLinkTag) { tag in
			navLinkTagChanged(tag)
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onReceive(paymentsPagePublisher) {
			paymentsPageChanged($0)
		}
		.onReceive(lastCompletedPaymentPublisher) {
			lastCompletedPaymentChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
		.onReceive(incomingSwapsPublisher) {
			onIncomingSwapsChanged($0)
		}
		.onReceive(externalLightningUrlPublisher) {
			didReceiveExternalLightningUrl($0)
		}
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) {
			self.manualBackup_taskDone = Prefs.shared.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		}
	}

	@ViewBuilder
	var main: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			// === Top-row buttons ===
			HStack {
				AppStatusButton()
				Spacer()
				ToolsButton(navLinkTag: $navLinkTag)
			}
			.padding(.all)

			// === Total Balance ====
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
				HStack(alignment: VerticalAlignment.firstTextBaseline) {
					
					if currencyPrefs.hideAmountsOnHomeScreen {
						let amount = Utils.hiddenAmount(currencyPrefs)
						
						Text(amount.digits)
							.font(.largeTitle)
							.onTapGesture { toggleCurrencyType() }
						
					} else {
						let amount = Utils.format(currencyPrefs, msat: mvi.model.balance.msat, policy: .showMsatsIfZeroSats)
						
						if currencyPrefs.currencyType == .bitcoin &&
							currencyPrefs.bitcoinUnit == .sat &&
							amount.hasFractionDigits
						{
							// We're showing the value in satoshis, but the value contains a fractional
							// component representing the millisatoshis.
							// This can be a little confusing for those new to Lightning.
							// So we're going to downplay the millisatoshis visually.
							HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
								Text(amount.integerDigits)
									.font(.largeTitle)
									.onTapGesture { toggleCurrencyType() }
								Text(verbatim: "\(amount.decimalSeparator)\(amount.fractionDigits)")
									.font(.title)
									.foregroundColor(.secondary)
									.onTapGesture { toggleCurrencyType() }
							}
							.environment(\.layoutDirection, .leftToRight) // issue #237
							
						} else {
							Text(amount.digits)
								.font(.largeTitle)
								.onTapGesture { toggleCurrencyType() }
						}
						
						Text(amount.type)
							.font(.title2)
							.foregroundColor(Color.appAccent)
							.padding(.bottom, 4)
							.onTapGesture { toggleCurrencyType() }
						
					}
				} // </HStack>
				.lineLimit(1)            // SwiftUI bugs
				.minimumScaleFactor(0.5) // Truncating text
				
				if let incoming = incomingAmount() {
					let incomingAmountStr = currencyPrefs.hideAmountsOnHomeScreen ? incoming.digits : incoming.string
					
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
						if #available(iOS 15.0, *) {
						
							Image(systemName: "link") // 
								.padding(.trailing, 2)
								.onTapGesture { showBlockchainExplorerOptions = true }
							
							Text("+\(incomingAmountStr) incoming".lowercased())
								.onTapGesture { showBlockchainExplorerOptions = true }
								.confirmationDialog("Blockchain Explorer",
									isPresented: $showBlockchainExplorerOptions,
									titleVisibility: .automatic
								) {
									Button("Mempool.space") {
										exploreIncomingSwap(website: BlockchainExplorer.WebsiteMempoolSpace())
									}
									Button("Blockstream.info") {
										exploreIncomingSwap(website: BlockchainExplorer.WebsiteBlockstreamInfo())
									}
									
									let addrCount = lastIncomingSwaps.count
									if addrCount >= 2 {
										Button("Copy bitcoin addresses (\(addrCount)") {
											copyIncomingSwap()
										}
									} else {
										Button("Copy bitcoin address") {
											copyIncomingSwap()
										}
									}
									
								}
							
						} else { // same functionality as before
							
							Image(systemName: "link")
								.padding(.trailing, 2)
							
							Text("+\(incomingAmountStr) incoming".lowercased())
								.onTapGesture { toggleCurrencyType() }
						}
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
			generalNotice
			
			// === Mempool Full Warning ====
			if isMempoolFull {
				NoticeBox {
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
					//
					// Here's how this works:
					// - ForEach uses the given type (which conforms to Swift's Identifiable protocol)
					//   to determine whether or not the row is new/modified or the same as before.
					// - If the row is new/modified, then it it initialized with fresh state,
					//   and the row's `onAppear` will fire.
					// - If the row is unmodified, then it is initialized with existing state,
					//   and the row's `onAppear` with NOT fire.
					//
					// Since we ultimately use WalletPaymentOrderRow.identifier, our unique identifier
					// contains the row's completedAt date, which is modified when the row changes.
					// Thus our row is automatically refreshed after it fails/succeeds.
					//
					ForEach(paymentsPage.rows) { row in
						Button {
							didSelectPayment(row: row)
						} label: {
							PaymentCell(row: row, didAppearCallback: paymentCellDidAppear)
						}
					}
				}
			}

			BottomBar(navLinkTag: $navLinkTag)
		
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
	
	@ViewBuilder
	var generalNotice: some View {
		
		if mvi.model.balance.msat == 0 && Prefs.shared.isNewWallet {

			// Reserved for potential "welcome" message.
			EmptyView()

		} else if !backupSeed_enabled && !manualBackup_taskDone {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.padding(.trailing, 10)
					Button {
						navigateToBackup()
					} label: {
						Group {
							Text("Backup your recovery phrase to prevent losing your funds. ")
								.foregroundColor(.primary)
							+
							Text("Let's go ")
								.foregroundColor(.appAccent)
							+
							Text(Image(systemName: "arrowtriangle.forward"))
								.foregroundColor(.appAccent)
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					}
				} // </HStack>
				.font(.caption)
			} // </NoticeBox>
			
		}
	}

	@ViewBuilder
	func navLinkView() -> some View {
		
		switch navLinkTag {
		case .ConfigurationView:
			ConfigurationView()
		case .ReceiveView:
			ReceiveView()
		case .SendView:
			SendView(controller: externalLightningRequest)
		case .CurrencyConverter:
			CurrencyConverterView()
		default:
			EmptyView()
		}
	}
	
	func incomingAmount() -> FormattedAmount? {
		
		let msatTotal = lastIncomingSwaps.values.reduce(Int64(0)) {(sum, item) -> Int64 in
			return sum + item.msat
		}
		if msatTotal > 0 {
			return currencyPrefs.hideAmountsOnHomeScreen
				? Utils.hiddenAmount(currencyPrefs)
				: Utils.format(currencyPrefs, msat: msatTotal)
		} else {
			return nil
		}
	}
	
	func didSelectPayment(row: WalletPaymentOrderRow) -> Void {
		log.trace("didSelectPayment()")
		
		// pretty much guaranteed to be in the cache
		let fetcher = HomeView.phoenixBusiness.paymentsManager.fetcher
		let options = WalletPaymentFetchOptions.companion.Descriptions
		fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
			
			if let result = result {
				selectedItem = result
			}
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
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
		log.trace("onModelChange()")
		
		if model.balance.msat > 0 || model.incomingBalance?.msat ?? 0 > 0 || model.paymentsCount > 0 {
			if Prefs.shared.isNewWallet {
				Prefs.shared.isNewWallet = false
			}
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged()")
		
		if tag == nil {
			// If we pushed the SendView, triggered by an external lightning url,
			// then we can nil out the associated controller now (since we handed off to SendView).
			self.externalLightningRequest = nil
		}
	}
	
	func paymentsPageChanged(_ page: PaymentsManager.PaymentsPage) -> Void {
		log.trace("paymentsPageChanged()")
		
		paymentsPage = page
		maybePreFetchPaymentsFromDatabase()
	}
	
	func lastCompletedPaymentChanged(_ payment: Lightning_kmpWalletPayment) -> Void {
		log.trace("lastCompletedPaymentChanged()")
		
		let paymentId = payment.walletPaymentId()
		
		// PaymentView will need `WalletPaymentFetchOptions.companion.All`,
		// so as long as we're fetching from the database, we might as well fetch everything we need.
		let options = WalletPaymentFetchOptions.companion.All
		
		phoenixBusiness.paymentsManager.getPayment(id: paymentId, options: options) { result, _ in
			
			if selectedItem == nil {
				selectedItem = result // triggers display of PaymentView sheet
			}
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		isMempoolFull = context.mempool.v1.highUsage
	}
	
	func onIncomingSwapsChanged(_ incomingSwaps: [String: Lightning_kmpMilliSatoshi]) -> Void {
		log.trace("onIncomingSwapsChanged(): \(incomingSwaps)")
		
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
		log.debug("Pre-fetching: \(row.id)")

		let fetcher = phoenixBusiness.paymentsManager.fetcher
		let options = WalletPaymentFetchOptions.companion.Descriptions
		fetcher.getPayment(row: row, options: options) { (_, _) in
			prefetchPaymentsFromDatabase(idx: idx + 1)
		}
	}
	
	func startAnimatingIncomingSwapText() -> Void {
		log.trace("startAnimatingIncomingSwapText()")
		
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
		log.trace("animateIncomingSwapText()")
		
		let duration = 0.5 // seconds
		let nextScaleFactor = incomingSwapScaleFactor == 1.0 ? incomingSwapScaleFactor_BIG : 1.0
		
		withAnimation(Animation.linear(duration: duration)) {
			self.incomingSwapScaleFactor = nextScaleFactor
		}
	}
	
	func incomingSwapAnimationCompleted() -> Void {
		log.trace("incomingSwapAnimationCompleted()")
		
		incomingSwapAnimationsRemaining -= 1
		log.debug("incomingSwapAnimationsRemaining = \(incomingSwapAnimationsRemaining)")
		
		if incomingSwapAnimationsRemaining > 0 {
			animateIncomingSwapText()
		}
	}
	
	func toggleCurrencyType() -> Void {
		log.trace("toggleCurrencyType()")
		
		// bitcoin -> fiat -> hidden
		
		if currencyPrefs.hideAmountsOnHomeScreen {
			currencyPrefs.toggleHideAmountsOnHomeScreen()
			if currencyPrefs.currencyType == .fiat {
				currencyPrefs.toggleCurrencyType()
			}
			
		} else if currencyPrefs.currencyType == .bitcoin {
			currencyPrefs.toggleCurrencyType()
			
		} else if currencyPrefs.currencyType == .fiat {
			currencyPrefs.toggleHideAmountsOnHomeScreen()
		}
	}
	
	func mempoolFullInfo() -> Void {
		log.trace("mempoolFullInfo()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq#high-mempool-size-impacts") {
			openURL(url)
		}
	}
	
	func paymentCellDidAppear(_ visibleRow: WalletPaymentOrderRow) -> Void {
		log.trace("paymentCellDidAppear(): \(visibleRow.id)")
		
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
				
				log.debug("increasing page.count: Page(offset=\(prvOffset), count=\(newCount)")
				
				AppDelegate.get().business.paymentsManager.subscribeToPaymentsPage(
					offset: prvOffset,
					count: newCount
				)
			}
		}
	}
	
	func exploreIncomingSwap(website: BlockchainExplorer.Website) {
		log.trace("exploreIncomingSwap()")
		
		guard let addr = lastIncomingSwaps.keys.first else {
			return
		}
		
		let business = AppDelegate.get().business
		let txUrlStr = business.blockchainExplorer.addressUrl(addr: addr, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copyIncomingSwap() {
		log.trace("copyIncomingSwap()")
		
		let addresses = lastIncomingSwaps.keys
		
		if addresses.count == 1 {
			UIPasteboard.general.string = addresses.first
			
		} else if addresses.count >= 2 {
			UIPasteboard.general.string = addresses.joined(separator: ", ")
		}
	}
	
	func didReceiveExternalLightningUrl(_ urlStr: String) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
	
		if navLinkTag == .SendView {
			log.debug("Ignoring: handled by SendView")
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
		unsubscribe = scanController.subscribe { (model: Scan.Model) in
			
			// Ignore first subscription fire (Scan.ModelReady)
			if let _ = model as? Scan.ModelReady {
				return
			} else {
				self.externalLightningRequest = scanController
				self.navLinkTag = .SendView
			}
			
			// Cleanup
			if let idx = self.temp.firstIndex(where: { $0 === scanController }) {
				self.temp.remove(at: idx)
			}
			unsubscribe?()
		}
		
		scanController.intent(intent: Scan.IntentParse(request: urlStr))
	}
	
	func navigateToBackup() {
		log.trace("navigateToBackup()")
		
		deepLinkManager.broadcast(DeepLink.backup)
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged()")
		
		switch value {
		case .backup:
			self.navLinkTag = .ConfigurationView
		default:
			break
		}
	}
}

fileprivate struct PaymentCell : View, ViewName {

	let row: WalletPaymentOrderRow
	let didAppearCallback: (WalletPaymentOrderRow) -> Void
	
	let phoenixBusiness: PhoenixBusiness = AppDelegate.get().business
	
	@State var fetched: WalletPaymentInfo?
	@State var fetchedIsStale: Bool
	
	@ScaledMetric var textScaling: CGFloat = 100
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	init(
		row: WalletPaymentOrderRow,
		didAppearCallback: @escaping (WalletPaymentOrderRow)->Void
	) {
		self.row = row
		self.didAppearCallback = didAppearCallback
		
		let fetcher = phoenixBusiness.paymentsManager.fetcher
		let options = WalletPaymentFetchOptions.companion.Descriptions
		var result = fetcher.getCachedPayment(row: row, options: options)
		if let _ = result {
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: false)
		} else {
			
			result = fetcher.getCachedStalePayment(row: row, options: options)
			
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

				if currencyPrefs.hideAmountsOnHomeScreen {
					
					// Do not display any indication as to whether payment in incoming or outgoing
					Text(verbatim: amount.digits)
						.foregroundColor(.primary)
					
				} else {
					
					let color: Color = isFailure ? .secondary : (isOutgoing ? .appNegative : .appPositive)
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
						Text(verbatim: isOutgoing ? "-" : "+")
							.foregroundColor(color)
							.padding(.trailing, 1)
						
						Text(verbatim: amount.digits)
							.foregroundColor(color)
					}
					.environment(\.layoutDirection, .leftToRight) // issue #237
					
					Text(verbatim: " ") // separate for RTL languages
						.font(.caption)
						.foregroundColor(.gray)
					Text(verbatim: amount.type)
						.font(.caption)
						.foregroundColor(.gray)
				}
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

		guard let payment = fetched?.payment else {
			return ""
		}
		let timestamp = payment.completedAt()
		guard timestamp > 0 else {
			return NSLocalizedString("pending", comment: "timestamp string for pending transaction")
		}
			
		let date = timestamp.toDate(from: .milliseconds)
		
		let formatter = DateFormatter()
		if textScaling > 100 {
			formatter.dateStyle = .short
		} else {
			formatter.dateStyle = .long
		}
		formatter.timeStyle = .short
		
		return formatter.string(from: date)
	}
	
	func paymentAmountInfo() -> (FormattedAmount, Bool, Bool) {

		if let payment = fetched?.payment {

			let amount = currencyPrefs.hideAmountsOnHomeScreen
				? Utils.hiddenAmount(currencyPrefs)
				: Utils.format(currencyPrefs, msat: payment.amount)

			let isFailure = payment.state() == WalletPaymentState.failure
			let isOutgoing = payment is Lightning_kmpOutgoingPayment

			return (amount, isFailure, isOutgoing)

		} else {
			
			let currency = currencyPrefs.currency
			let amount = FormattedAmount(amount: 0.0, currency: currency, digits: "", decimalSeparator: " ")

			let isFailure = false
			let isOutgoing = true

			return (amount, isFailure, isOutgoing)
		}
	}
	
	func onAppear() -> Void {
		
		if fetched == nil || fetchedIsStale {
			
			let fetcher = phoenixBusiness.paymentsManager.fetcher
			let options = WalletPaymentFetchOptions.companion.Descriptions
			fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
				self.fetched = result
			}
		}
		
		didAppearCallback(row)
	}
}

fileprivate struct AppStatusButton: View, ViewName {
	
	@State var dimStatus = false
	
	@State var syncState: SyncTxManager_State = .initializing
	@State var pendingSettings: SyncTxManager_PendingSettings? = nil
	
	@StateObject var connectionsManager = ObservableConnectionsManager()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	let syncTxManager = AppDelegate.get().syncManager!.syncTxManager
	
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
		.onReceive(syncTxManager.statePublisher) {
			syncTxManagerStateChanged($0)
		}
		.onReceive(syncTxManager.pendingSettingsPublisher) {
			syncTxManagerPendingSettingsChanged($0)
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
	
	func syncTxManagerStateChanged(_ newState: SyncTxManager_State) -> Void {
		log.trace("[\(viewName)] syncTxManagerStateChanged()")
		
		syncState = newState
	}
	
	func syncTxManagerPendingSettingsChanged(_ newPendingSettings: SyncTxManager_PendingSettings?) -> Void {
		log.trace("[\(viewName)] syncTxManagerPendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	func showAppStatusPopover() -> Void {
		log.trace("[\(viewName)] showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}

fileprivate struct ToolsButton: View, ViewName {
	
	@Binding var navLinkTag: NavLinkTag?
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	var body: some View {
		
		Menu {
			Button {
				currencyConverterTapped()
			} label: {
				Label(
					NSLocalizedString("Currency converter", comment: "HomeView: Tools menu: Label"),
					systemImage: "globe"
				)
			}
			Button {
				sendFeedbackButtonTapped()
			} label: {
				Label(
					NSLocalizedString("Send feedback", comment: "HomeView: Tools menu: Label"),
					image: "email"
				)
			}
			Button {
				faqButtonTapped()
			} label: {
				Label(
					NSLocalizedString("FAQ", comment: "HomeView: Tools menu: Label"),
					systemImage: "safari"
				)
			}
			Button {
				twitterButtonTapped()
			} label: {
				Label {
					Text(verbatim: "Twitter")
				} icon: {
					Image("twitter")
				}
			}
			Button {
				telegramButtonTapped()
			} label: {
				Label {
					Text(verbatim: "Telegram")
				} icon: {
					Image("telegram")
				}
			}
			Button {
				githubButtonTapped()
			} label: {
				Label {
					Text("View source")
				} icon: {
					Image("github")
				}
			}
			
		} label: {
			Image(systemName: "wrench.fill")
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
	
	func currencyConverterTapped() {
		log.trace("[\(viewName)] currencyConverterTapped()")
		navLinkTag = .CurrencyConverter
	}
	
	func sendFeedbackButtonTapped() {
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
	
	func telegramButtonTapped() {
		log.trace("[\(viewName)] telegramButtonTapped()")
		
		if let url = URL(string: "https://t.me/phoenix_wallet") {
			openURL(url)
		}
	}
	
	func twitterButtonTapped() {
		log.trace("[\(viewName)] twitterButtonTapped()")
		
		if let url = URL(string: "https://twitter.com/PhoenixWallet") {
			openURL(url)
		}
	}
	
	func faqButtonTapped() {
		log.trace("[\(viewName)] faqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func githubButtonTapped() {
		log.trace("[\(viewName)] githubButtonTapped()")
		
		if let url = URL(string: "https://github.com/ACINQ/phoenix") {
			openURL(url)
		}
	}
}

fileprivate struct NoticeBox<Content: View>: View {
	
	let content: Content
	
	init(@ViewBuilder builder: () -> Content) {
		content = builder()
	}
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			content
			Spacer(minLength: 0) // ensure content takes up full width of screen
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
	
	@Binding var navLinkTag: NavLinkTag?
	
	var body: some View {
		
		HStack {
			
			Button {
				navLinkTag = .ConfigurationView
			} label: {
				Image("ic_settings")
					.resizable()
					.frame(width: 22, height: 22)
					.foregroundColor(Color.appAccent)
			}
			.padding()
			.padding(.leading, 8)

			Divider().frame(width: 1, height: 40).background(Color.borderColor)
			Spacer()
			
			Button {
				navLinkTag = .ReceiveView
			} label: {
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

			Button {
				navLinkTag = .SendView
			} label: {
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
