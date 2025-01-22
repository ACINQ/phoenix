import SwiftUI
import Combine
import PhoenixShared

fileprivate let filename = "TransactionsView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let PAGE_COUNT_START = 30
fileprivate let PAGE_COUNT_INCREMENT = 20
fileprivate let PAGE_COUNT_MAX = PAGE_COUNT_START + (PAGE_COUNT_INCREMENT * 2) // 70


struct TransactionsView: View {
	
	enum NavLinkTag: String, Codable {
		case PaymentView
		case HistoryExporter
	}

	private let paymentsPageFetcher = Biz.getPaymentsPageFetcher(name: "TransactionsView")
	
	let paymentsCountPublisher = Biz.business.paymentsManager.paymentsCountPublisher()
	@State var paymentsCount: Int64 = 0
	
	let paymentsPagePublisher: AnyPublisher<PaymentsPage, Never>
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	@State var cachedRows: [WalletPaymentInfo] = []
	@State var sections: [PaymentsSection] = []
	
	@State var selectedItem: WalletPaymentInfo? = nil
	
	let contactsPublisher = Biz.business.contactsManager.contactsListPublisher()
	
	let syncStatePublisher = Biz.syncManager!.syncBackupManager.statePublisher
	@State var isDownloadingTxs: Bool = false
	
	@State var didAppear = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init() {
		paymentsPagePublisher = paymentsPageFetcher.paymentsPagePublisher()
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Payments", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarItems(trailing: exportButton())
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
		}
		.onAppear {
			onAppear()
		}
		.onReceive(paymentsCountPublisher) {
			paymentsCountChanged($0)
		}
		.onReceive(paymentsPagePublisher) {
			paymentsPageChanged($0)
		}
		.onReceive(contactsPublisher) {
			contactsChanged($0)
		}
		.onReceive(syncStatePublisher) {
			syncStateChanged($0)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Color.primaryBackground.frame(height: 25)
			list()
		}
	}
	
	@ViewBuilder
	func list() -> some View {
		
		ScrollView {
			LazyVStack(pinnedViews: [.sectionHeaders]) {
				
				ForEach(sections) { section in
					Section {
						ForEach(section.payments) { row in
							Button {
								didSelectPayment(row: row)
							} label: {
								PaymentCell(
									info: row,
									didAppearCallback: paymentCellDidAppear,
									didDisappearCallback: nil
								)
							}
						}
						
					} header: {
						Text(verbatim: section.name)
							.padding([.top, .bottom], 10)
							.frame(maxWidth: .infinity)
							.background(
								colorScheme == ColorScheme.light
								? Color(UIColor.secondarySystemBackground)
								: Color.mutedBackground // Color(UIColor.secondarySystemGroupedBackground)
							)
					}
				}
			
				if isDownloadingTxs {
					cell_syncing()
				} else if sections.isEmpty {
					cell_zeroPayments()
				}
				
			} // </LazyVStack>
		} // </ScrollView>
		.background(
			Color.primaryBackground.ignoresSafeArea()
		)
	}
	
	@ViewBuilder
	func cell_syncing() -> some View {
		
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
	func cell_zeroPayments() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Label {
				Text("No payments yet")
			} icon: {
				Image(systemName: "moon.zzz")
					.imageScale(.large)
			}
			.font(.callout)
			.foregroundColor(.secondary)
			.padding(.vertical, 10)
		}
	}
	
	@ViewBuilder
	func exportButton() -> some View {
		
		Button {
			navigateTo(.HistoryExporter)
		} label: {
			Image(systemName: "square.and.arrow.up")
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
			
		switch tag {
		case .PaymentView:
			if let selectedItem {
				PaymentView(
					location: .embedded(popTo: popTo),
					paymentInfo: selectedItem
				)
			} else {
				EmptyView()
			}
			
		case .HistoryExporter:
			TxHistoryExporter()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// Careful: this function is also called when returning from subviews
		if !didAppear {
			didAppear = true
			
			paymentsPageFetcher.subscribeToAll(
				offset: 0,
				count: Int32(PAGE_COUNT_START)
			)
			
		} else {
			
			selectedItem = nil
			if let destination = popToDestination {
				log.debug("popToDestination: \(destination)")
				
				popToDestination = nil
				switch destination {
				case .RootView(_):
					log.debug("Unhandled popToDestination")
					
				case .ConfigurationView(_):
					log.debug("Invalid popToDestination")
					
				case .TransactionsView:
					log.debug("At destination")
				}
			}
		}
		
		if !deviceInfo.isIPad {
			// On iPad, we handle this within MainView_Big
			if let deepLink = deepLinkManager.deepLink, deepLink == .paymentHistory {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
			}
		}
	}
	
	func paymentsCountChanged(_ count: Int64) {
		log.trace("paymentsCountChanged() => \(count)")
		
		paymentsCount = count
	}
	
	func paymentsPageChanged(_ page: PaymentsPage) {
		log.trace("paymentsPageChanged() => \(page.rows.count)")
		
		let preOffsetPayments = preOffsetPayments(page: page)
		let allPayments = preOffsetPayments + page.rows
		log.debug("allPayments.count = \(allPayments.count)")
		
		let calendar = Calendar.current
		
		let dateFormatter = DateFormatter()
		dateFormatter.setLocalizedDateFormatFromTemplate("yyyyMMMM")
		
		var newSectionMap = [String: PaymentsSection]()
		for row in allPayments {
			
			// Implementation note:
			// In theory, `allPayments` is perfectly sorted according to `row.sortDate`.
			// However, if theory != practice, then you could end up with duplicate sections.
			// This was the case recently, when the DB query was changed, and the end result
			// was a very messed up UI when attempting to scroll.
			//
			// So it's better to code more defensively, and don't assume perfect sort order.
			
			let date = row.payment.sortDate
			let comps = calendar.dateComponents([.year, .month], from: date)
			
			let year = comps.year!
			let month = comps.month!
			
			let sectionId = "\(year)-\(month)"
			if var section = newSectionMap[sectionId] {
				
				section.payments.append(row)
				newSectionMap[sectionId] = section
				
			} else {
				let name = dateFormatter.string(from: date)
				var section = PaymentsSection(year: year, month: month, name: name)
				
				section.payments.append(row)
				newSectionMap[sectionId] = section
			}
		}
		
		let newSections = newSectionMap.values.sorted { (a: PaymentsSection, b: PaymentsSection) in
			// return true if `a` should be ordered before `b`; otherwise return false
			return (a.year > b.year) || (a.year == b.year && a.month > b.month)
		}
		
		paymentsPage = page
		sections = newSections
	}
	
	func paymentCellDidAppear(_ visibleRow: WalletPaymentInfo) -> Void {
	//	log.trace("paymentCellDidAppear(): \(visibleRow.payment.id)")
		
		// Infinity Scrolling
		//
		// Here's the general idea:
		//
		// - We start by fetching a small "page" from the database.
		//   => Page(offset=0, count=30)
		// - When the user scrolls to the bottom, we can increase the count.
		//   => Page(offset=0, count=50)
		// - As the user continues scrolling, we continue increasing the count until we reach the max.
		//   => Page(offset=0, count=70)
		// - After the max, we instead increase the offset.
		//   => Page(offset=20, count=70)
		// - If the user scrolls back up to the offset (idx=20), then we decrease the offset.
		//   => Page(offset=0, count=70)
		//
		// In the past (pre-SwiftUI; using UIKit) when we changed the offset, we could simultaneously
		// change the scrollView's contentOffset so it looked like nothing changed.
		// That is, so the current rows on the screen remained in the exact same position.
		// However, using the same approach doesn't work well with SwiftUI.
		//
		// First, there's no API to set the scrollView's contentOffset.
		// If we hack it, by assuming the SwiftUI ScrollView is backed by UIKit's UIScrollView,
		// then we risk it breaking in a future update (or on some platforms and not others).
		//
		// Second, in SwiftUI, the change in rows seems to trigger an automatic scroll
		// to the top of the List. So we have contend with that problem too.
		//
		// To get around these problems, we simply keep in memory the (stale) versions of earlier rows.
		// Here's what this looks like:
		//
		//     1      2      3      4      5      6      7
		// --- -> --- -> --- -> --- -> --- -> --- -> --- -> ---
		// |V|    |P|    |P|    |S|    |S|    |S|    |S|    |P|
		// |P|    |V|    |P|    |P|    |S|    |S|    |P|    |V|
		// ---    |P|    |V|    |P|    |P|    |P|    |V|    |P|
		//        ---    |P|    |V|    |P|    |V|    |P|    |P|
		//               ---    |P|    |V|    |P|    |P|    ---
		//                      ---    |P|    |P|    ---
		//                             ---    ---
		//
		// P => The paymentsPage; We're subscribed to these rows. If they change in the DB, we're notified.
		// V => Visible rows on screen; This is a subset of P.
		// S => Stale cached rows stored in memory; Falls outside of paymentsPage range.
		//
		// (1): user scrolls to the bottom, and we increment page.count
		// (2): user scrolls to the bottom, and we increment page.count again (now we're at max page.count)
		// (3): user scrolls to the bottom, and we increment page.offset (earlier rows cached in memory)
		// (4): user scrolls to the bottom, and we increment page.offset again
		// (5): user scrolls up, nothing to do since they're moving within page
		// (6): user scrolls to top of P, and we decrement page.offset (P replaces S, and cache items dropped)
		// (7): user scrolls to top of P, and we decrement page.offset again (cache items dropped)
		//
		// Notes:
		//
		// 1) We only increase the count, we never decrease it.
		//    Thus once we reach the max count, it remains that way.
		//
		// 2) When we increase the offset, we keep in memory the previous rowIds.
		//    For example, if we have Page(offset=20, count=70),
		//    then we'll also have cached in memory rows[0-20]
		//
		// 3) The rows cached in memory are never on screen.
		//    If the user scrolls back up, we decrease the page offset,
		//    and trim the cache.
		//
		// 4) Since we're using a LazyVStack, only the visisble rows are kept in memory.
		//    All non-visible rows are only represnted as an instance of `WalletPaymentInfo` in memory.
		//
		//
		// There's one other detail to discuss:
		// What happens when a new payment arrives ?
		//
		// Let's consider what happens under 2 different scenarios.
		//
		// Scenario #1: Page(offset=0, count=70)
		//
		// The new payment arrives, and is inserted into the table at index 0.
		// All other rows get shifted down by 1.
		//
		// So if the user is at the top, they will see the new payment inserted.
		// If they're scrolled down a little bit, they will see all rows shift down.
		//
		// Technically, the last row is dropped from the table.
		// However, this row isn't visible to the user.
		// If it was visible, then we would have already incremented the page.count or page.offset.
		//
		// Scenario #2: Page(offset=20, count=70)
		//
		// The new payment arrives, which affects the rows in Page.
		// So the row that used to be at index 19 is now at 20.
		// And thus there is an overlap between the cache and the page.
		// That is, the row formerly known as 19 is now represented in both the cache and the page.
		//
		// This isn't a problem at all.
		// We simply remove any duplicates from the cache.
		//
		// The end effect is that the user's scroll position remains in the same place.
		// Because ultimately the only thing that changed is that we removed the last row from the table.
		// That is, we used to have cache.size=20 + page.size=70
		// But now we have cache.size=19 + page.size=70
		//
		// And remember, the last row isn't visible to the user.
		// If it was visible, then we would have already increment the page.offset.
		//
		//
		// But what if we try really hard to break things ?
		// What if set Page(offset=20, count=70), and then we receive 20 payments ?
		//
		// The only extra logic we need to fix this is:
		// - in `paymentsPageChanged`, just invoke `paymentCellDidAppear` for first & last rows
		// - this will perform the standard check (like during scrolling) to see if offset needs to change
		
		let allRows: [WalletPaymentInfo] = sections.flatMap { $0.payments }
		
		guard let rowIdx = allRows.firstIndexAsInt(of: visibleRow) else {
			// Row not found within current page.
			// Perhaps the page just changed, and it no longer includes this row.
			return
		}
		
		if rowIdx == (allRows.count - 1) {
			
			// We've reached the last row, so we'd like to load more items.
			// That is, if we have reason to believe there are more items.
			
			let maybeHasMoreRows = paymentsPage.rows.count == paymentsPage.count
			if maybeHasMoreRows {
				
				var newOffset = paymentsPage.offset
				var newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				if newCount <= PAGE_COUNT_MAX { // increase count
					
					log.debug("increasing page.count: Page(offset=\(newOffset), count=\(newCount))")
					
					paymentsPageFetcher.subscribeToAll(
						offset: newOffset,
						count: newCount
					)
					
				} else { // increase offset instead
					
					newOffset = paymentsPage.offset + Int32(PAGE_COUNT_INCREMENT)
					newCount = paymentsPage.count
					log.debug("increasing page.offset: Page(offset=\(newOffset), count=\(newCount))")
					
					cachedRows = allRows
					paymentsPageFetcher.subscribeToAll(
						offset: newOffset,
						count: newCount
					)
				}
			}
			
		} else if rowIdx == (allRows.count - PAGE_COUNT_MAX) {
			
			// The user is scrolling up (towards more recent items).
			// We want to move the subscription window 1 increment backwards.
			
			let preOffsetPayments = preOffsetPayments(page: paymentsPage)
			
			let newOffset = max(Int32(0), paymentsPage.offset - Int32(PAGE_COUNT_INCREMENT))
			let newCount = paymentsPage.count
			
			log.debug("decreasing page.offset: Page(offset=\(newOffset), count=\(newCount))")
			
			cachedRows = preOffsetPayments
			paymentsPageFetcher.subscribeToAll(
				offset: newOffset,
				count: newCount
			)
		}
	}
	
	func contactsChanged(_ contacts: [ContactInfo]) {
		log.trace("contactsChanged()")
		
		let contactsManager = Biz.business.contactsManager
		
		let updatedCachedRows = cachedRows.map { row in
			let updatedContact = contactsManager.contactForPayment(payment: row.payment)
			return WalletPaymentInfo(
				payment  : row.payment,
				metadata : row.metadata,
				contact  : updatedContact
			)
		}
		
		let updatedPaymentsPageRows = paymentsPage.rows.map { row in
			let updatedContact = contactsManager.contactForPayment(payment: row.payment)
			return WalletPaymentInfo(
				payment  : row.payment,
				metadata : row.metadata,
				contact  : updatedContact
			)
		}
		let updatedPage = PaymentsPage(
			offset : paymentsPage.offset,
			count  : paymentsPage.count,
			rows   : updatedPaymentsPageRows
		)
		
		cachedRows = updatedCachedRows
		paymentsPageChanged(updatedPage)
	}
	
	func syncStateChanged(_ state: SyncBackupManager_State) {
		log.trace("syncStateChanged()")
		
		if case .downloading(_) = state {
			self.isDownloadingTxs = true
		} else {
			self.isDownloadingTxs = false
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func preOffsetPayments(page: PaymentsPage) -> [WalletPaymentInfo] {
		
		if cachedRows.isEmpty {
			return []
		}
		
		// We want a list of all the payments in the list PRIOR to the given page.
		//
		// Where PRIOR means:
		// - more recent
		// - towards the top of the list (e.g. lower index values)
		//
		// Step 1:
		// First we simply exclude any duplicate payments.
		
		let pagePaymentIds: Set<Lightning_kmpUUID> = Set(page.rows.map { $0.payment.id })
		var preOffsetPayments: [WalletPaymentInfo] = cachedRows.filter {
			!pagePaymentIds.contains($0.payment.id)
		}
		
		// Step 2:
		// In the unlikely chance that the cached list not only overlaps the page,
		// but exceeds it in time, we'll also remove anything out-of-order.
		//
		// This is theoretically possible.
		// But only if the user receives a large batch of payments while scrolling.
		
		if let first = page.rows.first {
			let firstDate = first.payment.sortDate
			
			preOffsetPayments = preOffsetPayments.filter { $0.payment.sortDate >= firstDate }
		}
		
		return preOffsetPayments
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
		}
	}
	
	func didSelectPayment(row: WalletPaymentInfo) {
		log.trace("didSelectPayment()")
		
		if selectedItem == nil {
			selectedItem = row
			navigateTo(.PaymentView)
		} else {
			log.warning("selectItem != nil")
		}
	}
}
