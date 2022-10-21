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


fileprivate let PAGE_COUNT_START = 25
fileprivate let PAGE_COUNT_INCREMENT = 25


struct TransactionsView: View {
	
	private let paymentsPageFetcher = Biz.getPaymentsPageFetcher(name: "TransactionsView")
	
	let paymentsCountPublisher = Biz.business.paymentsManager.paymentsCountPublisher()
	@State var paymentsCount: Int64 = 0
	
	let paymentsPagePublisher: AnyPublisher<PaymentsPage, Never>
	@State var paymentsPage = PaymentsPage(offset: 0, count: 0, rows: [])
	@State var sections: [PaymentsSection] = []
	
	@State var selectedItem: WalletPaymentInfo? = nil
	
	let syncStatePublisher = Biz.syncManager!.syncTxManager.statePublisher
	@State var isDownloadingTxs: Bool = false
	
	@State var didAppear = false
	@State var didPreFetch = false
	
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
			NavigationLink(
				destination: navLinkView(),
				isActive: Binding(
					get: { selectedItem != nil },
					set: { if !$0 { selectedItem = nil }}
				)
			) {
				EmptyView()
			}
			.isDetailLink(false)
			
			content()
		}
		.navigationTitle(NSLocalizedString("Payments", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Color.primaryBackground.frame(height: 25)
			
			ScrollView {
				LazyVStack(pinnedViews: [.sectionHeaders]) {
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
					ForEach(sections) { section in
						Section {
							ForEach(section.payments) { row in
								Button {
									didSelectPayment(row: row)
								} label: {
									PaymentCell(row: row, didAppearCallback: paymentCellDidAppear)
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
					} else if paymentsPage.rows.isEmpty {
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
	func navLinkView() -> some View {
		
		if let selectedItem = selectedItem {
			PaymentView(
				type: .embedded,
				paymentInfo: selectedItem
			)
			
		} else {
			EmptyView()
		}
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
		
		let calendar = Calendar.current
		
		let dateFormatter = DateFormatter()
		dateFormatter.setLocalizedDateFormatFromTemplate("yyyyMMMM")
		
		var newSections = [PaymentsSection]()
		for row in page.rows {
			
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
		maybePreFetchPaymentsFromDatabase()
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
		for (idx, row) in paymentsPage.rows.enumerated() {
			
			if row == visibleRow {
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
			let hasMoreRowsInDatabase = rowIdxWithinDatabase + 1 < paymentsCount
			
			if hasMoreRowsInDatabase {
				
				// increase paymentsPage.count
				
				let prvOffset = paymentsPage.offset
				let newCount = paymentsPage.count + Int32(PAGE_COUNT_INCREMENT)
				
				log.debug("increasing page.count: Page(offset=\(prvOffset), count=\(newCount))")
				
				paymentsPageFetcher.subscribeToAll(
					offset: prvOffset,
					count: newCount
				)
			}
		}
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
	// MARK: Actions
	// --------------------------------------------------
	
	func didSelectPayment(row: WalletPaymentOrderRow) -> Void {
		log.trace("didSelectPayment()")
		
		// pretty much guaranteed to be in the cache
		let fetcher = Biz.business.paymentsManager.fetcher
		let options = WalletPaymentFetchOptions.companion.Descriptions
		fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _) in
			
			if let result = result {
				selectedItem = result
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Prefetch
	// --------------------------------------------------
	
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
		Biz.business.paymentsManager.fetcher.getPayment(row: row, options: options) { (_, _) in
			prefetchPaymentsFromDatabase(idx: idx + 1)
		}
	}
}
