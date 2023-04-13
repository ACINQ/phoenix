import SwiftUI
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TransactionsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let PAGE_COUNT_START = 30
fileprivate let PAGE_COUNT_INCREMENT = 20
fileprivate let PAGE_COUNT_MAX = PAGE_COUNT_START + (PAGE_COUNT_INCREMENT * 2) // 70


struct TransactionsView: View {
	
	private let paymentsPageFetcher = Biz.getPaymentsPageFetcher(name: "TransactionsView")
	
	let paymentsCountPublisher = Biz.business.paymentsManager.paymentsCountPublisher()
	@State var paymentsCount: Int64 = 0
	
	let paymentsPagePublisher: AnyPublisher<PaymentsPage, Never>
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	@State var cachedRows: [WalletPaymentOrderRow] = []
	@State var sections: [PaymentsSection] = []
	@State var visibleRows: Set<WalletPaymentOrderRow> = Set()
	
	@State var selectedItem: WalletPaymentInfo? = nil
	@State var historyExporterOpen: Bool = false
	
	let syncStatePublisher = Biz.syncManager!.syncTxManager.statePublisher
	@State var isDownloadingTxs: Bool = false
	
	@State var didAppear = false
	
	@Environment(\.colorScheme) var colorScheme
	
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
		
		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: navLinkView(),
					isActive: navLinkBinding()
				) {
					EmptyView()
				}
				.isDetailLink(false)
				
			} // else: uses.navigationStackDestination()
			
			content()
		}
		.navigationTitle(NSLocalizedString("Payments", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
		.navigationBarItems(trailing: exportButton())
		.navigationStackDestination( // For iOS 16+
			isPresented: navLinkBinding(),
			destination: navLinkView
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Color.primaryBackground.frame(height: 25)
			
			ScrollView {
				LazyVStack(pinnedViews: [.sectionHeaders]) {
					// Reminder:
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
					ForEach(sections) { section in
						Section {
							ForEach(section.payments) { row in
								Button {
									didSelectPayment(row: row)
								} label: {
									PaymentCell(
										row: row,
										didAppearCallback: paymentCellDidAppear,
										didDisappearCallback: paymentCellDidDisappear
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
			
		} // </VStack>
		.onAppear {
			onAppear()
		}
		.onReceive(paymentsCountPublisher) {
			paymentsCountChanged($0)
		}
		.onReceive(paymentsPagePublisher) {
			paymentsPageChanged($0)
		}
		.onReceive(syncStatePublisher) {
			syncStateChanged($0)
		}
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
			historyExporterOpen = true
		} label: {
			Image(systemName: "square.and.arrow.up")
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let selectedItem {
			PaymentView(
				type: .embedded,
				paymentInfo: selectedItem
			)
			
		} else if historyExporterOpen {
			TxHistoryExporter()
			
		} else {
			EmptyView()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { selectedItem != nil || historyExporterOpen },
			set: { if !$0 { selectedItem = nil; historyExporterOpen = false }}
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
		
		var newSections = [PaymentsSection]()
		for row in allPayments {
			
			let date = row.sortDate
			let comps = calendar.dateComponents([.year, .month], from: date)
			
			let year = comps.year!
			let month = comps.month!
			
			if var lastSection = newSections.last, lastSection.year == year, lastSection.month == month {
				
				lastSection.payments.append(row)
				let _ = newSections.popLast()
				newSections.append(lastSection)
				
			} else {
				let name = dateFormatter.string(from: date)
				var section = PaymentsSection(year: year, month: month, name: name)
				
				section.payments.append(row)
				newSections.append(section)
			}
		}
		
		paymentsPage = page
		sections = newSections
		
		let sortedVisibleRows = visibleRows.sorted { a, b in
			// return true if `a` should be ordered before `b`; otherwise return false
			return a.sortDate > b.sortDate
		}
		
		if let topVisibleRow = sortedVisibleRows.first {
			paymentCellDidAppear(topVisibleRow)
			if let bottomVisibleRow = sortedVisibleRows.last {
				paymentCellDidAppear(bottomVisibleRow)
			}
		}
	}
	
	func paymentCellDidAppear(_ visibleRow: WalletPaymentOrderRow) -> Void {
		log.trace("paymentCellDidAppear(): \(visibleRow.id)")
		
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
		//    All non-visible rows are only represnted as an instance of `WalletPaymentOrderRow` in memory.
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
		
		visibleRows.insert(visibleRow)
		let allRows = sections.flatMap { $0.payments }
		
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
	
	func paymentCellDidDisappear(_ visibleRow: WalletPaymentOrderRow) -> Void {
		log.trace("paymentCellDidDisappear(): \(visibleRow.id)")
		
		visibleRows.remove(visibleRow)
	}
	
	func syncStateChanged(_ state: SyncTxManager_State) {
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
	
	func preOffsetPayments(page: PaymentsPage) -> [WalletPaymentOrderRow] {
		
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
		
		let pagePaymentIds = Set(page.rows.map { $0.walletPaymentId })
		var preOffsetPayments = cachedRows.filter { !pagePaymentIds.contains($0.walletPaymentId) }
		
		// Step 2:
		// In the unlikely chance that the cached list not only overlaps the page,
		// but exceeds it in time, we'll also remove anything out-of-order.
		//
		// This is theoretically possible.
		// But only if the user receives a large batch of payments while scrolling.
		
		if let first = page.rows.first {
			let firstDate = first.sortDate
			
			preOffsetPayments = preOffsetPayments.filter { $0.sortDate >= firstDate }
		}
		
		return preOffsetPayments
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didSelectPayment(row: WalletPaymentOrderRow) -> Void {
		log.trace("didSelectPayment()")
		
		// pretty much guaranteed to be in the cache
		let fetcher = Biz.business.paymentsManager.fetcher
		let options = PaymentCell.fetchOptions
		fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
			
			if let result = result {
				selectedItem = result
			}
		}
	}
}
