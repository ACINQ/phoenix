import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NotificationsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum NotificationsViewType {
	
	case sheet
	case embedded
}

struct NotificationsView : View {
	
	let type: NotificationsViewType
	
	@StateObject var noticeMonitor = NoticeMonitor()
	
	let bizNotificationsPublisher = Biz.business.notificationsManager.notificationsPublisher()
	
	@State var bizNotifications_payment: [PhoenixShared.NotificationsManager.NotificationItem] = []
	@State var bizNotifications_watchtower: [PhoenixShared.NotificationsManager.NotificationItem] = []
	
	@Environment(\.openURL) var openURL
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		Group {
			switch type {
			case .sheet:
				body_sheet()
				
			case .embedded:
				body_embedded()
			}
		}
		.onReceive(bizNotificationsPublisher) {
			bizNotificationsChanged($0)
		}
	}
	
	@ViewBuilder
	func body_sheet() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			sheetHeader()
			Color.primaryBackground.frame(height: 15)
			content()
		}
	}
	
	@ViewBuilder
	func body_embedded() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Color.primaryBackground.frame(height: 15)
			content()
		}
		.navigationTitle("Notifications")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func sheetHeader() -> some View {
		
		HStack {
			Spacer().frame(width: 30)
			Spacer(minLength: 0)
			
			Text("Notifications")
				.font(.title3)
				.padding(.horizontal)
			
			Spacer(minLength: 0)
			Button {
				closeSheet()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close sheet")
			.accessibilitySortPriority(-1)
			
		} // </HStack>
		.padding()
		.background(Color.primaryBackground)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if hasImportantNotifications() {
				section_important()
			}
			if !bizNotifications_payment.isEmpty {
				section_payments()
			}
			if !bizNotifications_watchtower.isEmpty {
				section_watchtower()
			}
			
			if !hasImportantNotifications() && bizNotifications_payment.isEmpty && bizNotifications_watchtower.isEmpty {
				section_empty()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_important() -> some View {
		
		Section {
			
			if noticeMonitor.hasNotice_backupSeed {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.backupSeed(action: navigateToBackup)
				}
				.font(.callout)
			}
			
			if noticeMonitor.hasNotice_electrumServer {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.electrumServer(action: navigationToElecrumServer)
				}
				.font(.callout)
			}
			
			if noticeMonitor.hasNotice_backgroundPayments {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.backgroundPayments(action: navigationToBackgroundPayments)
				}
				.font(.callout)
			}
			
			if noticeMonitor.hasNotice_watchTower {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.watchTower(action: fixBackgroundAppRefreshDisabled)
				}
				.font(.callout)
			}
			
			if noticeMonitor.hasNotice_mempoolFull {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.mempoolFull(action: openMempoolFullURL)
				}
				.font(.callout)
			}
			
		} header: {
			Text("Important messages")
		}
		.listRowBackground(Color.clear)
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden)   // remove lines between items
	}
	
	@ViewBuilder
	func section_payments() -> some View {
		
		Section {
			
			ForEach(self.bizNotifications_payment) { item in
				BizNotificationCell(item: item, location: .NotificationsView(preAction: closeSheet))
					.padding(12)
					.background(
						RoundedRectangle(cornerRadius: 8)
							.fill(Color.mutedBackground)
					)
					.listRowBackground(Color.clear)
					.swipeActions(allowsFullSwipe: true) {
						Button(role: .destructive) {
							deleteNotification(item)
						} label: {
							Label("Delete", systemImage: "trash.fill")
						}
					}
			}
			
		} header: {
			Text("Recent activity")
		}
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden) // remove lines between items
	}
	
	@ViewBuilder
	func section_watchtower() -> some View {
		
		Section {
			
			ForEach(self.bizNotifications_watchtower) { item in
				BizNotificationCell(item: item, location: .NotificationsView(preAction: closeSheet))
					.padding(12)
					.background(
						RoundedRectangle(cornerRadius: 8)
							.fill(Color.mutedBackground)
					)
					.listRowBackground(Color.clear)
			}
			
		} header: {
			Text("Watchtower")
		}
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden) // remove lines between items
	}
	
	@ViewBuilder
	func section_empty() -> some View {
		
		Section {
			
			Label {
				Text("No notifications")
			} icon: {
				Image(systemName: "tray")
			}
			.padding(12)
		}
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden) // remove lines between items
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func hasImportantNotifications() -> Bool {
		
		return noticeMonitor.hasNotice_backupSeed
		    || noticeMonitor.hasNotice_electrumServer
		    || noticeMonitor.hasNotice_backgroundPayments
		    || noticeMonitor.hasNotice_watchTower
		    || noticeMonitor.hasNotice_mempoolFull
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func bizNotificationsChanged(_ list: [PhoenixShared.NotificationsManager.NotificationItem]) {
		log.trace("bizNotificationsChanges()")
		
		bizNotifications_payment = list.filter({ item in
			if let paymentRejected = item.notification as? PhoenixShared.Notification.PaymentRejected {
				// Remove items where source == onChain
				return !(paymentRejected.source == Lightning_kmpLiquidityEventsSource.onchainwallet)
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateToBackup() {
		log.trace("navigateToBackup()")
		
		presentationMode.wrappedValue.dismiss()
		deepLinkManager.broadcast(DeepLink.backup)
	}
	
	func navigationToElecrumServer() {
		log.trace("navigateToElectrumServer()")
		
		presentationMode.wrappedValue.dismiss()
		deepLinkManager.broadcast(DeepLink.electrum)
	}
	
	func navigationToBackgroundPayments() {
		log.trace("navigateToBackgroundPayments()")
		
		presentationMode.wrappedValue.dismiss()
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
		
		presentationMode.wrappedValue.dismiss()
		popoverState.display(dismissable: true) {
			BgRefreshDisabledPopover()
		}
	}
	
	func deleteNotification(_ item: PhoenixShared.NotificationsManager.NotificationItem) {
		log.trace("deleteNotification()")
		
		Biz.business.notificationsManager.dismissNotifications(ids: item.ids)
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
