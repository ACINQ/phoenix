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


struct NotificationsView : View {
	
	@ObservedObject var noticeMonitor: NoticeMonitor
	
	let bizNotificationsPublisher = Biz.business.notificationsManager.notificationsPublisher()
	
	@State var bizNotifications_payment: [PhoenixShared.NotificationsManager.NotificationItem] = []
	@State var bizNotifications_watchtower: [PhoenixShared.NotificationsManager.NotificationItem] = []
	
	@Environment(\.openURL) var openURL
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
		.onReceive(bizNotificationsPublisher) {
			bizNotificationsChanged($0)
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
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
				BizNotificationCell(action: closeSheet, item: item)
					.padding(12)
					.background(
						RoundedRectangle(cornerRadius: 8)
							.fill(Color.mutedBackground)
					)
					.listRowBackground(Color.clear)
			}
			
		} header: {
			Text("Recent payment failures")
		}
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden)   // remove lines between items
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
			return item.notification is PhoenixShared.Notification.PaymentRejected
		})
		
		bizNotifications_watchtower = list.filter({ item in
			return item.notification is PhoenixShared.WatchTowerOutcome
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
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
