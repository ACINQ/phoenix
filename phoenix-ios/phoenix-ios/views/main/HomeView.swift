import SwiftUI
import Combine
import PhoenixShared
import Network

fileprivate let filename = "HomeView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let PAGE_COUNT_START = 25
fileprivate let PAGE_COUNT_INCREMENT = 25


struct HomeView : MVIView {
	
	private let paymentsPageFetcher = Biz.getPaymentsPageFetcher(name: "HomeView")
	
	let showLiquidityAds: () -> Void
	let showSwapInWallet: () -> Void
	let showFinalWallet: () -> Void
	
	@StateObject var mvi = MVIState({ Biz.business.controllers.home() })
	
	@StateObject var noticeMonitor = NoticeMonitor()
	@StateObject var serverMessageMonitor = ServerMessageMonitor()
	@StateObject var syncState = DownloadMonitor()
	
	let recentPaymentsConfigPublisher = Prefs.current.recentPaymentsConfigPublisher
	@State var recentPaymentsConfig = Prefs.current.recentPaymentsConfig
	@State var lastCompletedPaymentId: Lightning_kmpUUID? = nil
	
	let paymentsPagePublisher: AnyPublisher<PaymentsPage, Never>
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	
	let lastCompletedPaymentPublisher = Biz.business.paymentsManager.lastCompletedPaymentPublisher()
	
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	@State var channels: [LocalChannelInfo] = []
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
	
	let incomingSwapScaleFactor_BIG: CGFloat = 1.2
	@State var incomingSwapScaleFactor: CGFloat = 1.0
	@State var incomingSwapAnimationsRemaining = 0
	
	let bizNotificationsPublisher = Biz.business.notificationsManager.notificationsPublisher()
	@State var bizNotifications_payment: [PhoenixShared.NotificationsManager.NotificationItem] = []
	@State var bizNotifications_watchtower: [PhoenixShared.NotificationsManager.NotificationItem] = []
	
	@State var didAppear = false
		
	enum NoticeBoxContentHeight: Preference {}
	let noticeBoxContentHeightReader = GeometryPreferenceReader(
		key: AppendValue<NoticeBoxContentHeight>.self,
		value: { [$0.size.height] }
	)
	@State var noticeBoxContentHeight: CGFloat? = nil
	
	enum HomeViewSheet: Identifiable, Equatable {
		case paymentView(payment: WalletPaymentInfo)
		case notificationsView
		
		var id: String {
			switch self {
				case .paymentView(let payment) : return "paymentView(\(payment.id))"
				case .notificationsView        : return "notificationsView"
			}
		}
	}
	
	@State var activeSheet: HomeViewSheet? = nil
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.openURL) var openURL
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(
		showLiquidityAds: @escaping () -> Void,
		showSwapInWallet: @escaping () -> Void,
		showFinalWallet: @escaping () -> Void
	) {
		self.showLiquidityAds = showLiquidityAds
		self.showSwapInWallet = showSwapInWallet
		self.showFinalWallet = showFinalWallet
		
		self.paymentsPagePublisher = paymentsPageFetcher.paymentsPagePublisher()
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
		
		content()
			.onAppear {
				onAppear()
			}
			.onChange(of: mvi.model) { newModel in
				onModelChange(model: newModel)
			}
			.onChange(of: deepLinkManager.deepLink) {
				deepLinkChanged($0)
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
			.onReceive(swapInWalletPublisher) {
				swapInWalletChanged($0)
			}
			.onReceive(finalWalletPublisher) {
				finalWalletChanged($0)
			}
			.onReceive(channelsPublisher) {
				channelsChanged($0)
			}
			.onReceive(bizNotificationsPublisher) {
				bizNotificationsChanged($0)
			}
	}

	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			balance()
			if showAddLiquidityButton() {
				addLiquidityButton()
					.padding(.top, 15)
					.padding(.bottom, 15)
			} else {
				Spacer().frame(height: 25)
			}
			notices()
			paymentsList()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.sheet(item: $activeSheet) { (sheet: HomeViewSheet) in
			switch sheet {
			case .paymentView(let selectedPayment):
				GlobalEnvironmentView {
					PaymentView(
						location: .sheet(closeSheet: { self.activeSheet = nil }),
						paymentInfo: selectedPayment
					)
				}
				
			case .notificationsView:
				GlobalEnvironmentView {
					NotificationsView(
						location: .sheet
					)
				}
			}
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
					Text(Image(systemName: imgName)) + Text(verbatim: "\u{202f}") +
					Text(Image(systemName: imgName)) + Text(verbatim: "\u{202f}") +
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
		
		let swapInWalletBalance: Int64 = swapInWallet.totalBalance.sat
		let finalWalletBalance: Int64 = finalWallet.totalBalance.sat
		
		let incomingBalance = swapInWalletBalance + finalWalletBalance
		if incomingBalance > 0 {
			let formattedAmount = currencyPrefs.hideAmounts
				? Utils.hiddenAmount(currencyPrefs)
				: Utils.format(currencyPrefs, sat: incomingBalance)
			
			let hasNonSwapInBalance = (finalWalletBalance > 0)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
				Group {
					if hasNonSwapInBalance {
						Image(systemName: "link")
					} else {
						let unconfirmedBalance = swapInWallet.unconfirmedBalance.sat
						let weaklyConfirmedBalance = swapInWallet.weaklyConfirmedBalance.sat
						
						if let days = swapInWallet.expirationWarningInDays(), days <= 7 {
							Image(systemName: "exclamationmark.triangle")
								.foregroundColor(.appNegative)
						} else if unconfirmedBalance == 0 && weaklyConfirmedBalance == 0 {
							Image(systemName: "zzz")
								.foregroundColor(.appWarn)
								
						} else {
							Image(systemName: "clock")
						}
					}
				} // </Group>
				.padding(.trailing, 2)
				
				if currencyPrefs.hideAmounts {
					Text("+\(formattedAmount.digits)".lowercased()) // digits => "***"
						.accessibilityLabel("plus hidden amount incoming")
					
				} else {
					Text("+\(formattedAmount.string)".lowercased())
				}
			}
			.font(.callout)
			.foregroundColor(.secondary)
			.onTapGesture {
				if hasNonSwapInBalance {
					showIncomingBalancePopover()
				} else {
					showSwapInWallet()
				}
			}
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
	}
	
	@ViewBuilder
	func addLiquidityButton() -> some View {
		
		Button {
			showLiquidityAds()
		} label: {
			Text("add liquidity")
				.font(.subheadline)
				.foregroundColor(.appAccent)
				.padding(.vertical, -4)
				.padding(.horizontal, -1)
		}
		.buttonStyle(.bordered)
		.buttonBorderShape(ButtonBorderShape.capsule)
	}
	
	@ViewBuilder
	func notices() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			let count_missedPayments = notificationCount_missedLightningPayments()
			if count_missedPayments > 0 {
				NoticeBox {
					notice_missedPayment_content(count: count_missedPayments)
				}
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture {
					openNotificationsSheet()
				}
				.frame(maxWidth: deviceInfo.textColumnMaxWidth)
				.padding([.leading, .trailing, .bottom], 10)
			}
			
			if let serverMessage = serverMessageMonitor.serverMessage {
				NoticeBox {
					notice_serverMessage_content(serverMessage)
				}
				.frame(maxWidth: deviceInfo.textColumnMaxWidth)
				.padding([.leading, .trailing, .bottom], 10)
			}
			
			let count_other = notificationCount_other()
			if count_other > 0 {
				Group {
					if count_other > 1 {
						notice_other_multiple(totalCount: count_missedPayments + count_other)
					} else {
						notice_other_single()
					}
				}
				.frame(maxWidth: deviceInfo.textColumnMaxWidth)
				.padding([.leading, .trailing, .bottom], 10)
			}
		}
	}
	
	@ViewBuilder
	func notice_other_multiple(totalCount: Int) -> some View {
		
		NoticeBox {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				notice_other_content(isSingle: false)
					.read(noticeBoxContentHeightReader)
				
				if let dividerHeight = noticeBoxContentHeight {
					Spacer(minLength: 6)
					Divider()
						.frame(width: 1, height: dividerHeight).background(Color.borderColor)
						.padding(.trailing, 6)
				} else {
					Spacer(minLength: 13)
				}
				
				Group {
					let remainingCount = totalCount - 1
					if remainingCount < 100 {
						Text(verbatim: "+\(remainingCount)")
							.padding(.top, 2)
							.padding(.bottom, 2.5)
							.padding(.horizontal, 7.5)
							.foregroundColor(.white)
							.background(Capsule().fill(Color.appAccent))
					} else {
						Text(verbatim: "99+")
							.padding(.vertical, 2.5)
							.padding(.horizontal, 7.5)
							.foregroundColor(.white)
							.background(Capsule().fill(Color.appAccent))
					}
				}
				.read(noticeBoxContentHeightReader)
				
			} // </HStack>
			.assignMaxPreference(for: noticeBoxContentHeightReader.key, to: $noticeBoxContentHeight)
			
		} // </NoticeBox>
		.contentShape(Rectangle()) // make Spacer area tappable
		.onTapGesture {
			openNotificationsSheet()
		}
	}
	
	@ViewBuilder
	func notice_other_single() -> some View {
		
		if noticeMonitor.hasNotice {
			NoticeBox {
				notice_other_content(isSingle: true)
			}
			.contentShape(Rectangle()) // make Spacer area tappable
			.onTapGesture {
				if noticeMonitor.hasNotice_backupSeed {
					navigateToBackup()
				} else if noticeMonitor.hasNotice_electrumServer {
					navigationToElecrumServer()
				} else if noticeMonitor.hasNotice_swapInExpiration {
					showSwapInWallet()
				} else if noticeMonitor.hasNotice_backgroundPayments {
					navigationToBackgroundPayments()
				} else if noticeMonitor.hasNotice_watchTower {
					fixBackgroundAppRefreshDisabled()
				} else if noticeMonitor.hasNotice_mempoolFull {
					openMempoolFullURL()
				}
			}
			
		} else {
			NoticeBox {
				notice_other_content(isSingle: true)
			}
		}
	}
	
	@ViewBuilder
	func notice_other_content(isSingle: Bool) -> some View {
		
		if noticeMonitor.hasNotice_backupSeed {
			NotificationCell.backupSeed()
				.font(.footnote)
			
		} else if noticeMonitor.hasNotice_electrumServer {
			NotificationCell.electrumServer()
				.font(.footnote)
			
		} else if noticeMonitor.hasNotice_swapInExpiration {
			NotificationCell.swapInExpiration()
				.font(.footnote)
			
		} else if noticeMonitor.hasNotice_backgroundPayments {
			NotificationCell.backgroundPayments()
				.font(.footnote)
			
		} else if noticeMonitor.hasNotice_watchTower {
			NotificationCell.watchTower()
				.font(.footnote)
			
		} else if noticeMonitor.hasNotice_mempoolFull {
			NotificationCell.mempoolFull()
				.font(.footnote)
			
		} else if let item = bizNotifications_watchtower.first {
			let location = isSingle ?
			   BizNotificationCell.Location.HomeView_Single(preAction: {})
			 : BizNotificationCell.Location.HomeView_Multiple
			
			BizNotificationCell(item: item, location: location)
				.font(.caption)
		}
	}
	
	@ViewBuilder
	func notice_missedPayment_content(count: Int) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			Image(systemName: "info.circle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			if count == 1 {
				Text("1 incoming payment recently rejected")
			} else {
				Text("\(count) incoming payments recently rejected")
			}
		} // </HStack>
		.font(.caption)
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func notice_serverMessage_content(_ serverMessage: ServerMessage) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "info.circle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Message")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				Text(serverMessage.message)
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer()
					Button {
						dismissServerMessage(index: serverMessage.index)
					} label: {
						Text("OK").bold()
					}
				}
			}
			
		} // </HStack>
		.font(.caption)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func paymentsList() -> some View {
		
		ScrollView(.vertical) {
			VStack {
				ForEach(paymentsPage.rows) { row in
					Button {
						didSelectPayment(row: row)
					} label: {
						PaymentCell(info: row)
					}
				}
				
				let totalPaymentCount = paymentsPage.rows.count + Int(paymentsPage.offset)
				FooterCell(
					totalPaymentCount: totalPaymentCount,
					recentPaymentsConfig: recentPaymentsConfig,
					isDownloadingMoreTxs: isDownloadingMoreTxs(),
					isFetchingMoreTxsFromDb: isFetchingMoreTxsFromDb(),
					didAppearCallback: footerCellDidAppear
				)
				.accessibilitySortPriority(10)
				
			} // </VStack>
			
		} // </ScrollView>
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
	
	func isFetchingMoreTxsFromDb() -> Bool {
		
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
	
	func notificationCount_missedLightningPayments() -> Int {
		
		return bizNotifications_payment.count
	}
	
	func notificationCount_other() -> Int {
		
		var count = 0
		if noticeMonitor.hasNotice_backupSeed         { count += 1 }
		if noticeMonitor.hasNotice_electrumServer     { count += 1 }
		if noticeMonitor.hasNotice_swapInExpiration   { count += 1 }
		if noticeMonitor.hasNotice_mempoolFull        { count += 1 }
		if noticeMonitor.hasNotice_backgroundPayments { count += 1 }
		if noticeMonitor.hasNotice_watchTower         { count += 1 }
		
		count += bizNotifications_watchtower.count
		
		return count
	}
	
	func showAddLiquidityButton() -> Bool {
		
		let hasNoChannels = channels.filter { !$0.isTerminated }.isEmpty
		if hasNoChannels {
			// Cannot purchase liquidity without any channels
			return false
		}
		
		return true
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
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async {
					deepLinkChanged(deepLink)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onModelChange(model: Home.Model) -> Void {
		log.trace("onModelChange()")
		
		let balance = model.balance?.msat ?? 0
		let incomingBalance = swapInWallet.totalBalance.sat
		
		if balance > 0 || incomingBalance > 0 {
			if Prefs.current.isNewWallet {
				Prefs.current.isNewWallet = false
			}
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.description ?? "nil")")
		
		if let value {
			switch value {
			case .payment(let paymentId):
				Biz.business.paymentsManager.getPayment(id: paymentId) { result, _ in
					if let result {
						activeSheet = .paymentView(payment: result)
					} else {
						log.warning("deepLinkChanged(): getPayment failed")
					}
				}
				
			case .paymentHistory     : break
			case .backup             : break
			case .drainWallet        : break
			case .electrum           : break
			case .backgroundPayments : break
			case .liquiditySettings  : break
			case .forceCloseChannels : break
			case .swapInWallet       : break
			case .finalWallet        : break
			case .appAccess          : break
			case .walletMetadata     : break
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
		log.trace("paymentsPageChanged(count: \(page.count), offset: \(page.offset))")
		
		paymentsPage = page
	}
	
	func lastCompletedPaymentChanged(_ payment: Lightning_kmpWalletPayment) {
		log.trace("lastCompletedPaymentChanged()")
		
		let paymentId = payment.id
		
		if paymentId.isEqual(lastCompletedPaymentId) {
			// Ignoring duplicate (rebroadcast when returning to this View)
			return
		} else {
			lastCompletedPaymentId = paymentId
		}
		
		Biz.business.paymentsManager.getPayment(id: paymentId) { result, _ in
			
			if activeSheet == nil, let result = result {
				activeSheet = .paymentView(payment: result) // triggers display of PaymentView sheet
			}
		}
	}
	
	func swapInWalletChanged(_ newWallet: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
		let oldBalance = swapInWallet.totalBalance.sat
		let newBalance = newWallet.totalBalance.sat
		
		swapInWallet = newWallet
		if newBalance > oldBalance {
			// Since the balance increased, there is a new utxo for the user.
			// This isn't added to the transaction list, but is instead displayed under the balance.
			// So let's add a little animation to draw the user's attention to it.
			startAnimatingIncomingSwapText()
		}
	}
	
	func finalWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("finalWalletChanged()")
		
		finalWallet = newValue
	}
	
	func channelsChanged(_ channels: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		
		self.channels = channels
	}
	
	func bizNotificationsChanged(_ list: [PhoenixShared.NotificationsManager.NotificationItem]) {
		log.trace("bizNotificationsChanges()")
		
		let cutOffDate = Date.now.toMilliseconds().minus(hours: 15)
		
		bizNotifications_payment = list.filter({ item in
			if let paymentRejected = item.notification as? PhoenixShared.Notification.PaymentRejected {
				// Remove items where source == onChain
				if paymentRejected.source == Lightning_kmpLiquidityEventsSource.offChainPayment {
					return paymentRejected.createdAt > cutOffDate
				} else {
					return false
				}
			} else {
				return false
			}
		})
		
		bizNotifications_watchtower = list.filter({ item in
			if let watchTower = item.notification as? PhoenixShared.WatchTowerOutcome {
				// Remove "Nominal" notifications (which just mean everything is working as expected)
				return !(watchTower is PhoenixShared.WatchTowerOutcome.Nominal)
			} else {
				return false
			}
		})
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
			
			switch recentPaymentsConfig {
			case .withinTime(let recentPaymentSeconds):
				log.debug("recentPaymentsConfig.withinTime(seconds: \(recentPaymentSeconds))")
				
				// increase paymentsPage.count
				
				let prvOffset = paymentsPage.offset
				let newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				log.debug("increasing page.count: Page(offset=\(prvOffset), count=\(newCount)")
				
				paymentsPageFetcher.subscribeToRecent(
					offset: prvOffset,
					count: newCount,
					seconds: Int32(recentPaymentSeconds)
				)
				
			case .mostRecent(let count):
				log.debug("recentPaymentsConfig.mostRecent(count: \(count))")
				
				// Nothing to do here.
				// The original subscription was configured with the correct count.
				
			case .inFlightOnly:
				log.debug("recentPaymentsConfig.inFlightOnly")
				
				// increase paymentsPage.count
				
				let prvOffset = paymentsPage.offset
				let newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				log.debug("increasing page.count: Page(offset=\(prvOffset), count=\(newCount)")
				
				paymentsPageFetcher.subscribeToInFlight(
					offset: prvOffset,
					count: newCount
				)
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
	
	func showIncomingBalancePopover() {
		log.trace("showIncomingBalancePopover()")
		
		popoverState.display(dismissable: true) {
			IncomingBalancePopover(
				showSwapInWallet: showSwapInWallet,
				showFinalWallet: showFinalWallet
			)
		}
	}
	
	func dismissServerMessage(index: Int) {
		log.trace("dismissServerMessage(index: \(index))")
		
		Prefs.current.serverMessageReadIndex = index
	}
	
	func openNotificationsSheet() {
		log.trace("openNotificationSheet()")
		
		if activeSheet == nil {
			activeSheet = .notificationsView
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
	
	func didSelectPayment(row: WalletPaymentInfo) -> Void {
		log.trace("didSelectPayment()")
		
		if activeSheet == nil {
			activeSheet = .paymentView(payment: row)
		}
	}
	
	// --------------------------------------------------
	// MARK: PaymentsFetcher
	// --------------------------------------------------
	
	func paymentsPageFetcher_subscribe() {
		log.trace("paymentsPageFetcher_subscribe()")
		
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

fileprivate struct FooterCell: View {
	
	let totalPaymentCount: Int
	let recentPaymentsConfig: RecentPaymentsConfig
	let isDownloadingMoreTxs: Bool
	let isFetchingMoreTxsFromDb: Bool
	let didAppearCallback: () -> Void
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		Group {
			if isDownloadingMoreTxs {
				body_downloading()
			} else if isFetchingMoreTxsFromDb {
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
