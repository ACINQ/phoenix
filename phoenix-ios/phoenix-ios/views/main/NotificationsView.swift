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
				closeButtonTapped()
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
				NoticeBox {
					NotificationCell.backupSeed(action: navigateToBackup)
				}
			}
			
			if noticeMonitor.hasNotice_electrumServer {
				NoticeBox {
					NotificationCell.electrumServer(action: navigationToElecrumServer)
				}
			}
			
			if noticeMonitor.hasNotice_backgroundPayments {
				NoticeBox {
					NotificationCell.backgroundPayments(action: navigationToBackgroundPayments)
				}
			}
			
			if noticeMonitor.hasNotice_watchTower {
				NoticeBox {
					NotificationCell.watchTower(action: fixBackgroundAppRefreshDisabled)
				}
			}
			
			if noticeMonitor.hasNotice_mempoolFull {
				NoticeBox {
					NotificationCell.mempoolFull(action: openMempoolFullURL)
				}
			}
			
		} header: {
			Text("Important messages")
		}
		.listRowInsets(EdgeInsets(top: 5, leading: 1, bottom: 5, trailing: 1))
		.listRowSeparator(.hidden)   // remove lines between items
	}
	
	@ViewBuilder
	func section_payments() -> some View {
		
		Section {
			
			// Todo...
			
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
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
