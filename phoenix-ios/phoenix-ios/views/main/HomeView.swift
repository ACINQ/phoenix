import SwiftUI
import Combine
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
	
	private let phoenixBusiness = Biz.business
	private let encryptedNodeId = Biz.encryptedNodeId!
	private let paymentsManager = Biz.business.paymentsManager
	private let paymentsPageFetcher = Biz.getPaymentsPageFetcher(name: "HomeView")
	
	@StateObject var mvi = MVIState({ $0.home() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var selectedItem: WalletPaymentInfo? = nil
	
	@State var isMempoolFull = false
	@State var swapIn_minFundingSat: Int64 = 0
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	@State var bgAppRefreshDisabled = NotificationsManager.shared.backgroundRefreshStatus.value != .available
	
	@StateObject var customElectrumServerObserver = CustomElectrumServerObserver()
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let recentPaymentsConfigPublisher = Prefs.shared.recentPaymentsConfigPublisher
	@State var recentPaymentsConfig = Prefs.shared.recentPaymentsConfig
	
	let paymentsPagePublisher: AnyPublisher<PaymentsPage, Never>
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	
	@StateObject var syncState = DownloadMonitor()
	
	let lastCompletedPaymentPublisher = Biz.business.paymentsManager.lastCompletedPaymentPublisher()
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	
	let swapInWalletBalancePublisher = Biz.business.balanceManager.swapInWalletBalancePublisher()
	@State var swapInWalletBalance: WalletBalance = WalletBalance.companion.empty()
	
	let incomingSwapScaleFactor_BIG: CGFloat = 1.2
	@State var incomingSwapScaleFactor: CGFloat = 1.0
	@State var incomingSwapAnimationsRemaining = 0
	
	// Toggles confirmation dialog (used to select preferred explorer)
	@State var showBlockchainExplorerOptions = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.openURL) var openURL
	@Environment(\.colorScheme) var colorScheme
	
	@State var didAppear = false
	
	let backupSeed_enabled_publisher = Prefs.shared.backupSeed.isEnabled_publisher
	let manualBackup_taskDone_publisher = Prefs.shared.backupSeed.manualBackup_taskDone_publisher
	@State var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	@State var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
		encryptedNodeId: Biz.encryptedNodeId!
	)
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init() {
		paymentsPagePublisher = paymentsPageFetcher.paymentsPagePublisher()
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	/* Accessibility sort priority:
	 *
	 * - Total balance    = 49
	 * - Incoming balance = 48
	 * - Notice boxes     = 47
	 * - Footer cell      = 10
	 */
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			content()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onChange(of: currencyPrefs.hideAmounts) { _ in
			hideAmountsChanged()
		}
		.onReceive(recentPaymentsConfigPublisher) {
			recentPaymentsConfigChanged($0)
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
		.onReceive(swapInWalletBalancePublisher) {
			swapInWalletBalanceChanged($0)
		}
		.onReceive(NotificationsManager.shared.permissions) {
			notificationPermissionsChanged($0)
		}
		.onReceive(NotificationsManager.shared.backgroundRefreshStatus) {
			backgroundRefreshStatusChanged($0)
		}
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) {
			self.manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		}
	}

	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {

			balance()
				.padding(.bottom, 25)
			notices()
			paymentsList()
		}
		.onAppear {
			onAppear()
		}
		.sheet(isPresented: Binding(
			get: { selectedItem != nil },
			set: { if !$0 { selectedItem = nil }} // needed if user slides the sheet to dismiss
		)) {
			PaymentView(
				type: .sheet(closeAction: { self.selectedItem = nil }),
				paymentInfo: selectedItem!
			)
			.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
		}
	}
	
	@ViewBuilder
	func balance() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			totalBalance()
			incomingBalance()
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
	}
	
	@ViewBuilder
	func totalBalance() -> some View {
		
		ZStack(alignment: Alignment.center) {
			
			let balanceMsats = mvi.model.balance?.msat
			let unknownBalance = balanceMsats == nil
			let hiddenBalance = currencyPrefs.hideAmounts
			
			let amount = Utils.format( currencyPrefs,
			                     msat: balanceMsats ?? 0,
			                   policy: .showMsatsIfZeroSats)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				
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
				
				Text_CurrencyName(currency: amount.currency, fontTextStyle: .title2)
					.foregroundColor(.appAccent)
					.padding(.bottom, 4)
				
			} // </HStack>
			.lineLimit(1)            // SwiftUI truncation bugs
			.minimumScaleFactor(0.5) // SwiftUI truncation bugs
			.onTapGesture { toggleCurrencyType() }
			.accessibilityElement(children: .combine)
			.accessibilityLabel("Total balance is \(amount.string)")
			.accessibilityAddTraits(.isButton)
			.accessibilitySortPriority(49)
			.if(unknownBalance || hiddenBalance) { view in
				view
					.opacity(0.0)
					.accessibility(hidden: true)
			}
			
			if unknownBalance || hiddenBalance {
				
				let imgName = unknownBalance ? "circle.dotted" : "circle.fill"
				Group {
					Text(Image(systemName: imgName)) + Text("\u{202f}") +
					Text(Image(systemName: imgName)) + Text("\u{202f}") +
					Text(Image(systemName: imgName))
				}
				.font(.body)
				.if(colorScheme == .dark) { view in
					view.foregroundColor(Color(UIColor.systemGray2))
				}
				.lineLimit(1)            // SwiftUI truncation bugs
				.minimumScaleFactor(0.5) // SwiftUI truncation bugs
				.onTapGesture { toggleCurrencyType() }
				.accessibilityLabel("Hidden balance")
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(49)
			}
		}
	}
	
	@ViewBuilder
	func incomingBalance() -> some View {
		
		let incomingSat = swapInWalletBalance.total.sat
		if incomingSat > 0 {
			let formattedAmount = currencyPrefs.hideAmounts
				? Utils.hiddenAmount(currencyPrefs)
				: Utils.format(currencyPrefs, sat: incomingSat)
			
			if incomingSat >= swapIn_minFundingSat {
				incomingBalance_sufficient(formattedAmount)
					.onTapGesture { showIncomingDepositPopover() }
			} else {
				incomingBalance_insufficient(formattedAmount)
					.onTapGesture { showIncomingDepositPopover() }
			}
		}
	}
	
	@ViewBuilder
	func incomingBalance_sufficient(_ incoming: FormattedAmount) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
		
			Image(systemName: "link")
				.padding(.trailing, 2)
			
			if currencyPrefs.hideAmounts {
				Text("+\(incoming.digits) incoming".lowercased()) // digits => "***"
					.accessibilityLabel("plus hidden amount incoming")
				
			} else {
				Text("+\(incoming.string) incoming".lowercased())
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
		.accessibilityElement(children: .combine)
		.accessibilityHint("pending on-chain confirmation")
		.accessibilitySortPriority(48)
	}
	
	@ViewBuilder
	func incomingBalance_insufficient(_ incoming: FormattedAmount) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
		
			Image(systemName: "exclamationmark.triangle")
				.padding(.trailing, 2)
			
			if currencyPrefs.hideAmounts {
				Text("+\(incoming.digits) incoming".lowercased()) // digits => "***"
					.accessibilityLabel("plus hidden amount incoming")
				
			} else {
				Text("+\(incoming.string) incoming".lowercased())
			}
		}
		.font(.callout)
		.foregroundColor(.appNegative)
		.padding(.top, 7)
		.padding(.bottom, 2)
		.scaleEffect(incomingSwapScaleFactor, anchor: .top)
		.onAnimationCompleted(for: incomingSwapScaleFactor) {
			incomingSwapAnimationCompleted()
		}
		.accessibilityElement(children: .combine)
		.accessibilityHint("amount insufficient")
		.accessibilitySortPriority(48)
	}
	
	@ViewBuilder
	func notices() -> some View {
		
		// === Welcome / Backup Seed ====
		if mvi.model.balance?.msat == 0 && Prefs.shared.isNewWallet {

			// Reserved for potential "welcome" message.
			EmptyView()

		} else if !backupSeed_enabled && !manualBackup_taskDone {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.padding(.trailing, 10)
						.accessibilityLabel("Warning")
					
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
					} // </Button>
				} // </HStack>
				.font(.caption)
				.accessibilityElement(children: .combine)
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(47)
				
			} // </NoticeBox>
		}
		
		// === Custom Electrum Server Problems ====
		if customElectrumServerObserver.problem == .badCertificate {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.shield")
						.imageScale(.large)
						.padding(.trailing, 10)
						.accessibilityHidden(true)
						.accessibilityLabel("Warning")
					
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
					} // </Button>
					
				} // </HStack>
				.font(.caption)
				.accessibilityElement(children: .combine)
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(47)
				
			} // </NoticeBox>
		}
		
		// === Mempool Full Warning ====
		if isMempoolFull {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "tray.full")
						.imageScale(.large)
						.padding(.trailing, 10)
						.accessibilityHidden(true)
						.accessibilityLabel("Warning")
					
					VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
						Text("Bitcoin mempool is full and fees are high.")
						Button {
							openMempoolFullURL()
						} label: {
							Text("See how Phoenix is affected".uppercased())
						}
					} // </VStack>
				} // </HStack>
				.font(.caption)
				.accessibilityElement(children: .combine)
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(47)
				
			} // </NoticeBox>
		}
		
		// === Background payments disabled ====
		if notificationPermissions == .disabled {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.padding(.trailing, 10)
						.accessibilityLabel("Warning")
					
					Button {
						navigationToBackgroundPayments()
					} label: {
						Group {
							Text("Background payments disabled. ")
								.foregroundColor(.primary)
							+
							Text("Fix ")
								.foregroundColor(.appAccent)
							+
							Text(Image(systemName: "arrowtriangle.forward"))
								.foregroundColor(.appAccent)
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					} // </Button>
					
				} // </HStack>
				.font(.caption)
				.accessibilityElement(children: .combine)
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(47)
				
			} // </NoticeBox>
		}
		
		// === Background App Refresh Disabled ====
		if bgAppRefreshDisabled {
			
			NoticeBox {
				HStack(alignment: VerticalAlignment.top, spacing: 0) {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.padding(.trailing, 10)
						.accessibilityLabel("Warning")
					
					Button {
						fixBackgroundAppRefreshDisabled()
					} label: {
						Group {
							Text("Watchtower disabled. ")
								.foregroundColor(.primary)
							+
							Text("Fix ")
								.foregroundColor(.appAccent)
							+
							Text(Image(systemName: "arrowtriangle.forward"))
								.foregroundColor(.appAccent)
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					} // </Button>
					
				} // </HStack>
				.font(.caption)
				.accessibilityElement(children: .combine)
				.accessibilityAddTraits(.isButton)
				.accessibilitySortPriority(47)
				
			} // </NoticeBox>
		}
	}
	
	@ViewBuilder
	func paymentsList() -> some View {
		
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
						PaymentCell(row: row, didAppearCallback: nil)
					}
				}
				
				let totalPaymentCount = paymentsPage.rows.count + Int(paymentsPage.offset)
				FooterCell(
					totalPaymentCount: totalPaymentCount,
					recentPaymentsConfig: recentPaymentsConfig,
					isDownloadingMoreTxs: isDownloadingMoreTxs(),
					isFetchingMoreTxs: isFetchingMoreTxs(),
					didAppearCallback: footerCellDidAppear
				)
				.accessibilitySortPriority(10)
			}
		}
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func isDownloadingMoreTxs() -> Bool {
		
		guard syncState.isDownloading else {
			return false
		}
		
		switch recentPaymentsConfig {
		case .withinTime(let seconds):
			
			// We're displaying all "recent" payments within a given time period.
			//
			// So we may still be downloading these payments until `syncState.oldestCompletedDownload`
			// shows a date older than our "recent" range.
			
			if let oldest = syncState.oldestCompletedDownload {
				log.debug("oldest = \(oldest.description)")
				
				// We've downloaded one or more batches from the cloud.
				// Let's check to see if we expect to download any more "recent" payments.
				let cutoff = Date().addingTimeInterval(Double(seconds * -1))
				log.debug("cutoff = \(cutoff.description)")
				
				if oldest < cutoff {
					// Already downloaded all the tx's that could be considered "recent"
					return false
				} else {
					// May have more "recent" tx's to download
					return true
				}
				
			} else {
				// We're downloading the first batch from the cloud
				log.debug("oldest = nil")
				return true
			}
			
		case .mostRecent(let count):
			
			// We're displaying the most recent X payments.
			//
			// So we may still be downloading these payments until either:
			// - we have X payments in the database (we always download from newest to oldest)
			// - we're no longer downloading from the cloud
			
			if paymentsPage.count >= count {
				return false
			} else {
				return true
			}
			
		case .inFlightOnly:
			
			// We're only displaying (outgoing) in-flight payments.
			// So there's no need to show the "still downloading" notice.
			return false
		}
	}
	
	func isFetchingMoreTxs() -> Bool {
		
		switch recentPaymentsConfig {
		case .withinTime(_):
			// For this config, we increase the fetchCount when we scroll to the bottom:
			//
			// paymentsPageFetcher.subscribeToRecent(
			//   offset: 0,
			//   count: Int32(PAGE_COUNT_START), <-- value gets increased
			//   seconds: Int32(seconds)
			// )
			//
			// So we fetch more when we get to the end, if there might be more.
			
			if paymentsPage.rows.count < paymentsPage.count {
				return false // done
			} else {
				return true // might be more
			}
			
		case .mostRecent(_):
			// For this config, we the fetchCount matches the recentCount:
			//
			// paymentsPageFetcher.subscribeToAll(
			//   offset: 0,
			//   count: Int32(count) <-- max value; doesn't change
			// )
			//
			// So we don't ever have more to fetch from the database.
			
			return false
			
		case .inFlightOnly:
			// For this config, we increase the fetchCount when we scroll to the bottom:
			//
			// paymentsPageFetcher.subscribeToInFlight(
			//   offset: 0,
			//   count: Int32(PAGE_COUNT_START) <-- value gets increased
			// )
			//
			// So we fetch more when we get to the end, if there might be more.
			
			if paymentsPage.rows.count < paymentsPage.count {
				return false // done
			} else {
				return true // might be more
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// Careful: this function may be called when returning from the Receive/Send view
		if !didAppear {
			didAppear = true
			paymentsPageFetcher_subscribe()
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onModelChange(model: Home.Model) -> Void {
		log.trace("onModelChange()")
		
		let balance = model.balance?.msat ?? 0
		let incomingBalance = swapInWalletBalance.total.sat
		
		if balance > 0 || incomingBalance > 0 || model.paymentsCount > 0 {
			if Prefs.shared.isNewWallet {
				Prefs.shared.isNewWallet = false
			}
		}
	}
	
	func hideAmountsChanged() {
		log.trace("hideAmountsChanged()")
		
		// Without this, VoiceOver re-reads the previous text/button,
		// before reading the new text/button that replaces it.
		UIAccessibility.post(notification: .screenChanged, argument: nil)
	}

	func recentPaymentsConfigChanged(_ value: RecentPaymentsConfig) {
		log.trace("recentPaymentsConfigChanged()")
		
		recentPaymentsConfig = value
		paymentsPageFetcher_subscribe()
	}
	
	func paymentsPageChanged(_ page: PaymentsPage) {
		log.trace("paymentsPageChanged()")
		
		paymentsPage = page
	}
	
	func lastCompletedPaymentChanged(_ payment: Lightning_kmpWalletPayment) {
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
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) {
		log.trace("chainContextChanged()")
		
		isMempoolFull = context.mempool.v1.highUsage
		swapIn_minFundingSat = context.payToOpen.v1.minFundingSat // not yet segregated for swapIn - future work
	}
	
	func notificationPermissionsChanged(_ newValue: NotificationPermissions) {
		log.trace("notificationPermissionsChanged()")
		
		notificationPermissions = newValue
	}

	func backgroundRefreshStatusChanged(_ newValue: UIBackgroundRefreshStatus) {
		log.trace("backgroundRefreshStatusChanged()")
		
		bgAppRefreshDisabled = newValue != .available
	}
	
	func swapInWalletBalanceChanged(_ walletBalance: WalletBalance) {
		log.trace("swapInWalletBalanceChanged()")
		
		let oldBalance = swapInWalletBalance.total.sat
		let newBalance = walletBalance.total.sat
		
		swapInWalletBalance = walletBalance
		if newBalance > oldBalance {
			// Since the balance increased, there is a new utxo for the user.
			// This isn't added to the transaction list, but is instead displayed under the balance.
			// So let's add a little animation to draw the user's attention to it.
			startAnimatingIncomingSwapText()
		}
	}
	
	fileprivate func footerCellDidAppear() {
		log.trace("footerCellDidAppear()")
		
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
		
		let maybeHasMoreRowsInDatabase = paymentsPage.rows.count == paymentsPage.count
		if maybeHasMoreRowsInDatabase {
			log.debug("maybeHasMoreRowsInDatabase")
			
			if case let .withinTime(recentPaymentSeconds) = recentPaymentsConfig {
				
				// increase paymentsPage.count
				
				let prvOffset = paymentsPage.offset
				let newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				log.debug("increasing page.count: Page(offset=\(prvOffset), count=\(newCount)")
				
				paymentsPageFetcher.subscribeToRecent(
					offset: prvOffset,
					count: newCount,
					seconds: Int32(recentPaymentSeconds)
				)
			} else {
				log.debug("!recentPayments.withinTime(X)")
			}
			
		} else {
			
			log.debug("!maybeHasMoreRowsInDatabase")
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func toggleCurrencyType() -> Void {
		log.trace("toggleCurrencyType()")
		
		// bitcoin -> fiat -> hidden
		
		if currencyPrefs.hideAmounts {
			currencyPrefs.toggleHideAmounts()
			if currencyPrefs.currencyType == .fiat {
				currencyPrefs.toggleCurrencyType()
			}
			
		} else if currencyPrefs.currencyType == .bitcoin {
			currencyPrefs.toggleCurrencyType()
			
		} else if currencyPrefs.currencyType == .fiat {
			currencyPrefs.toggleHideAmounts()
		}
	}
	
	func showIncomingDepositPopover() {
		log.trace("showIncomingDepositPopover()")
		
		popoverState.display(dismissable: true) {
			IncomingDepositPopover()
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
	
	func navigationToBackgroundPayments() {
		log.trace("navigateToBackgroundPayments()")
		
		deepLinkManager.broadcast(DeepLink.backgroundPayments)
	}
	
	func openMempoolFullURL() {
		log.trace("openMempoolFullURL()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq#high-mempool-size-impacts") {
			openURL(url)
		}
	}
	
	func fixBackgroundAppRefreshDisabled() {
		log.trace("fixBackgroundAppRefreshDisabled()")
		
		popoverState.display(dismissable: true) {
			BgRefreshDisabledPopover()
		}
	}
	
	func didSelectPayment(row: WalletPaymentOrderRow) -> Void {
		log.trace("didSelectPayment()")
		
		// pretty much guaranteed to be in the cache
		let fetcher = paymentsManager.fetcher
		let options = PaymentCell.fetchOptions
		fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
			
			if let result = result {
				selectedItem = result
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: PaymentsFetcher
	// --------------------------------------------------
	
	func paymentsPageFetcher_subscribe() {
		
		switch recentPaymentsConfig {
		case .withinTime(let seconds):
			paymentsPageFetcher.subscribeToRecent(
				offset: 0,
				count: Int32(PAGE_COUNT_START),
				seconds: Int32(seconds)
			)
		case .mostRecent(let count):
			paymentsPageFetcher.subscribeToAll(
				offset: 0,
				count: Int32(count)
			)
		case .inFlightOnly:
			paymentsPageFetcher.subscribeToInFlight(
				offset: 0,
				count: Int32(PAGE_COUNT_START)
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Incoming Swap Animation
	// --------------------------------------------------
	
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
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

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

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

fileprivate struct FooterCell: View {
	
	let totalPaymentCount: Int
	let recentPaymentsConfig: RecentPaymentsConfig
	let isDownloadingMoreTxs: Bool
	let isFetchingMoreTxs: Bool
	let didAppearCallback: () -> Void
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		Group {
			if isDownloadingMoreTxs {
				body_downloading()
			} else if isFetchingMoreTxs {
				body_fetching()
			} else {
				body_ready()
			}
		}
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func body_downloading() -> some View {
		
		Label {
			Text("Downloading payments from cloud")
		} icon: {
			Image(systemName: "icloud.and.arrow.down")
				.imageScale(.large)
		}
		.font(.callout)
		.foregroundColor(.secondary)
		.padding(.vertical, 10)
	}
	
	@ViewBuilder
	func body_fetching() -> some View {
		
		Text("fetching more rows...")
			.font(.footnote)
			.foregroundColor(.secondary)
			.padding(.vertical, 10)
	}
	
	@ViewBuilder
	func body_ready() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text(verbatim: limitedPaymentsText())
				.font(.subheadline)
				.multilineTextAlignment(.center)
				.foregroundColor(.secondary)
			
			Button {
				deepLinkManager.broadcast(DeepLink.paymentHistory)
			} label: {
				Text("full payment history")
					.font(.footnote)
					.padding(.top, 4)
			}
			.accessibilityHidden(true) // duplicate button
		}
		.padding(.vertical, 10)
	}
	
	func limitedPaymentsText() -> String {
		
		switch recentPaymentsConfig {
		case .withinTime(let seconds):
			let option = RecentPaymentsConfig_WithinTime.closest(seconds: seconds).1
			return option.homeDisplay(paymentCount: totalPaymentCount)
			
		case .mostRecent(_):
			if totalPaymentCount == 1 {
				return NSLocalizedString("1 recent payment", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(totalPaymentCount) recent payments", comment: "Recent payments footer")
			}
			
		case .inFlightOnly:
			if totalPaymentCount == 1 {
				return NSLocalizedString("1 in-flight payment", comment: "Recent payments footer")
			} else {
				return NSLocalizedString("\(totalPaymentCount) in-flight payments", comment: "Recent payments footer")
			}
		}
	}
	
	func onAppear() {
		log.trace("[FooterCell] onAppear()")
		
		didAppearCallback()
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

class DownloadMonitor: ObservableObject {
	
	@Published var isDownloading: Bool = false
	@Published var oldestCompletedDownload: Date? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		let syncManager = Biz.syncManager!
		let syncStatePublisher = syncManager.syncTxManager.statePublisher
		
		syncStatePublisher.sink {[weak self](state: SyncTxManager_State) in
			self?.update(state)
		}
		.store(in: &cancellables)
	}
	
	private func update(_ state: SyncTxManager_State) {
		log.trace("[DownloadMonitor] update()")
		
		if case .downloading(let details) = state {
			log.trace("[DownloadMonitor] isDownloading = true")
			isDownloading = true
			
			subscribe(details)
		} else {
			log.trace("[DownloadMonitor] isDownloading = false")
			isDownloading = false
		}
	}
	
	private func subscribe(_ details: SyncTxManager_State_Downloading) {
		log.trace("[DownloadMonitor] subscribe()")
		
		details.$oldestCompletedDownload.sink {[weak self](date: Date?) in
			log.trace("[DownloadMonitor] oldestCompletedDownload = \(date?.description ?? "nil")")
			self?.oldestCompletedDownload = date
		}
		.store(in: &cancellables)
	}
}
