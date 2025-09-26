import SwiftUI
import PhoenixShared

fileprivate let filename = "NotificationsView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct NotificationsView : View {
	
	enum Location {
		case sheet
		case embedded
	}
	let location: Location
	
	@StateObject var noticeMonitor = NoticeMonitor()
	
	let bizNotificationsPublisher = Biz.business.notificationsManager.notificationsPublisher()
	
	@State var bizNotifications_payment: [PhoenixShared.NotificationsManager.NotificationItem] = []
	@State var bizNotifications_watchtower: [PhoenixShared.NotificationsManager.NotificationItem] = []
	
	@Environment(\.openURL) var openURL
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		Group {
			switch location {
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
					NotificationCell.backupSeed()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { navigateToBackup() }
			}
			
			if noticeMonitor.hasNotice_electrumServer {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.electrumServer()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { navigateToElectrumServer() }
			}
			
			if noticeMonitor.hasNotice_swapInExpiration {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.swapInExpiration()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { navigateToSwapInWallet() }
			}
			
			if noticeMonitor.hasNotice_backgroundPayments {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.backgroundPayments()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { navigateToBackgroundPayments() }
			}
			
			if noticeMonitor.hasNotice_watchTower {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.watchTower()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { fixBackgroundAppRefreshDisabled() }
			}
			
			if noticeMonitor.hasNotice_mempoolFull {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.mempoolFull()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { openMempoolFullURL() }
			}
			
			if noticeMonitor.hasNotice_torNetworkIssue {
				NoticeBox(backgroundColor: .mutedBackground) {
					NotificationCell.torNetworkIssue()
				}
				.font(.callout)
				.contentShape(Rectangle()) // make Spacer area tappable
				.onTapGesture { showTorNetworkIssueSheet() }
			}
			
		} header: {
			Text("Important messages").padding(.leading, 12)
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
			Text("Recent activity").padding(.leading, 12)
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
			Text("Watchtower").padding(.leading, 12)
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
		
		return noticeMonitor.hasNotice
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func bizNotificationsChanged(_ list: [PhoenixShared.NotificationsManager.NotificationItem]) {
		log.trace("bizNotificationsChanges()")
		
		bizNotifications_payment = list.filter({ item in
			if let _ = item.notification as? PhoenixShared.Notification.PaymentRejected {
				return true
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
	
	func navigateToElectrumServer() {
		log.trace("navigateToElectrumServer()")
		
		presentationMode.wrappedValue.dismiss()
		deepLinkManager.broadcast(DeepLink.electrum)
	}
	
	func navigateToSwapInWallet() {
		log.trace("navigateToSwapInWallet()")
		
		presentationMode.wrappedValue.dismiss()
		deepLinkManager.broadcast(DeepLink.swapInWallet)
	}
	
	func navigateToBackgroundPayments() {
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
	
	func showTorNetworkIssueSheet() {
		log.trace(#function)
		
		smartModalState.display(dismissable: true) {
			TorNetworkIssueSheet()
		}
	}
	
	func deleteNotification(_ item: PhoenixShared.NotificationsManager.NotificationItem) {
		log.trace(#function)
		
		Biz.business.notificationsManager.dismissNotifications(ids: item.ids)
	}
	
	func closeSheet() {
		log.trace(#function)
		
		presentationMode.wrappedValue.dismiss()
	}
}
