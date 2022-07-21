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


struct HomeView : MVIView {

	static let appDelegate = AppDelegate.get()
	static let phoenixBusiness = appDelegate.business
	static let encryptedNodeId = appDelegate.encryptedNodeId!
	static let paymentsManager = phoenixBusiness.paymentsManager
	static let paymentsPageFetcher = paymentsManager.makePageFetcher()
	
	let phoenixBusiness = HomeView.phoenixBusiness
	let encryptedNodeId = HomeView.encryptedNodeId
	let paymentsManager = HomeView.paymentsManager
	let paymentsPageFetcher = HomeView.paymentsPageFetcher
	
	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var selectedItem: WalletPaymentInfo? = nil
	@State var isMempoolFull = false
	
	@StateObject var customElectrumServerObserver = CustomElectrumServerObserver()
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let paymentsPagePublisher = paymentsPageFetcher.paymentsPagePublisher()
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	
	let lastCompletedPaymentPublisher = paymentsManager.lastCompletedPaymentPublisher()
	let chainContextPublisher = phoenixBusiness.appConfigurationManager.chainContextPublisher()
	
	let incomingSwapsPublisher = paymentsManager.incomingSwapsPublisher()
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
	
	let backupSeed_enabled_publisher = Prefs.shared.backupSeed.isEnabled_publisher
	let manualBackup_taskDone_publisher = Prefs.shared.backupSeed.manualBackup_taskDone_publisher
	@State var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	@State var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
	
	@ViewBuilder
	var view: some View {
		
		ZStack {

			main

		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
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
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) {
			self.manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		}
	}

	@ViewBuilder
	var main: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {

			// === Total Balance ====
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
				HStack(alignment: VerticalAlignment.firstTextBaseline) {
					
					if currencyPrefs.hideAmountsOnHomeScreen {
						let amount = Utils.hiddenAmount(currencyPrefs)
						
						Text(amount.digits)
							.font(.largeTitle)
						
					} else {
						let amount = Utils.format( currencyPrefs,
						                     msat: mvi.model.balance.msat,
						                   policy: .showMsatsIfZeroSats)
						
						if amount.hasSubFractionDigits {
							
							// We're showing sub-fractional values.
							// For example, we're showing millisatoshis.
							//
							// It's helpful to downplay the sub-fractional part visually.
							
							let hasStdFractionDigits = amount.hasStdFractionDigits
							
							HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
								Text(verbatim: amount.integerDigits)
									.font(.largeTitle)
								Text(verbatim: amount.decimalSeparator)
									.font(hasStdFractionDigits ? .largeTitle : .title)
									.foregroundColor(hasStdFractionDigits ? .primary : .secondary)
								if hasStdFractionDigits {
									Text(verbatim: amount.stdFractionDigits)
										.font(.largeTitle)
								}
								Text(verbatim: amount.subFractionDigits)
									.font(.title)
									.foregroundColor(.secondary)
							}
							.environment(\.layoutDirection, .leftToRight) // issue #237
						
						} else {
							Text(amount.digits)
								.font(.largeTitle)
						}
						
						Text(amount.type)
							.font(.title2)
							.foregroundColor(Color.appAccent)
							.padding(.bottom, 4)
					}
				} // </HStack>
				.lineLimit(1)            // SwiftUI truncation bugs
				.minimumScaleFactor(0.5) // SwiftUI truncation bugs
				.onTapGesture { toggleCurrencyType() }
				
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

			// === Notices & Warnings ===
			notices
			
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
			.frame(maxWidth: deviceInfo.textColumnMaxWidth)
		
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
	var notices: some View {
		
		// === Welcome / Backup Seed ====
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
		
		// === Custom Electrum Server Problems ====
		if customElectrumServerObserver.problem == .badCertificate {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.shield")
						.imageScale(.large)
						.padding(.trailing, 10)
				}
				.font(.caption)
				Button {
					navigationToElecrumServer()
				} label: {
					Group {
						Text("Custom electrum server: bad certificate ! ")
							.foregroundColor(.primary)
						+
						Text("Check it ")
							.foregroundColor(.appAccent)
						+
						Text(Image(systemName: "arrowtriangle.forward"))
							.foregroundColor(.appAccent)
					}
					.multilineTextAlignment(.leading)
					.allowsTightening(true)
				}
			} // </NoticeBox>
		}
		
		// === Mempool Full Warning ====
		if isMempoolFull {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "tray.full")
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
		let fetcher = paymentsManager.fetcher
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
			paymentsPageFetcher.subscribeToRecent(
				offset: 0,
				count: Int32(PAGE_COUNT_START),
				seconds: (2 * 60)
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
	
	func paymentsPageChanged(_ page: PaymentsPage) -> Void {
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
		
		paymentsManager.getPayment(id: paymentId, options: options) { result, _ in
			
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

		let options = WalletPaymentFetchOptions.companion.Descriptions
		paymentsManager.fetcher.getPayment(row: row, options: options) { (_, _) in
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
				
				paymentsPageFetcher.subscribeToRecent(
					offset: prvOffset,
					count: newCount,
					seconds: (2 * 60)
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
	
	func navigateToBackup() {
		log.trace("navigateToBackup()")
		
		deepLinkManager.broadcast(DeepLink.backup)
	}
	
	func navigationToElecrumServer() {
		log.trace("navigateToElectrumServer()")
		
		deepLinkManager.broadcast(DeepLink.electrum)
	}
}

fileprivate struct PaymentCell : View, ViewName {

	static let appDelegate = AppDelegate.get()
	static let phoenixBusiness = appDelegate.business
	static let paymentsManager = phoenixBusiness.paymentsManager
	
	let paymentsManager = PaymentCell.paymentsManager
	
	let row: WalletPaymentOrderRow
	let didAppearCallback: (WalletPaymentOrderRow) -> Void
	
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
		
		let options = WalletPaymentFetchOptions.companion.Descriptions
		var result = paymentsManager.fetcher.getCachedPayment(row: row, options: options)
		if let _ = result {
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: false)
		} else {
			
			result = paymentsManager.fetcher.getCachedStalePayment(row: row, options: options)
			
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
			
			let options = WalletPaymentFetchOptions.companion.Descriptions
			paymentsManager.fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
				self.fetched = result
			}
		}
		
		didAppearCallback(row)
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
