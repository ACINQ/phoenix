import SwiftUI
import PhoenixShared
import CircularCheckmarkProgress
import CloudKit

fileprivate let filename = "AppStatusPopover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct AppStatusPopover: View {

	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@State var isTorEnabled: Bool = Biz.business.appConfigurationManager.isTorEnabledValue
	@State var electrumConfig: ElectrumConfig = Biz.business.appConfigurationManager.electrumConfigValue
	
	@State var srvExtConnectedToPeer = Biz.srvExtConnectedToPeer.value
	
	@State var syncState: SyncBackupManager_State = .initializing
	@State var pendingSettings: SyncBackupManager_PendingSettings? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let syncManager = Biz.syncManager!.syncBackupManager
	
	enum TitleIconWidth: Preference {}
	let titleIconWidthReader = GeometryPreferenceReader(
		key: AppendValue<TitleIconWidth>.self,
		value: { [$0.size.width] }
	)
	@State var titleIconWidth: CGFloat? = nil

	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.topTrailing) {
			// I think it looks cleaner without the explicit closeButton
		//	closeButton()
			
			content()
		}
	}
	
	@ViewBuilder
	func closeButton() -> some View {
		
		Button {
			closePopover()
		} label: {
			Image(systemName: "xmark")
				.imageScale(.medium)
				.font(.title2)
				.foregroundStyle(Color.secondary)
		}
		.accessibilityLabel("Close")
		.accessibilityHidden(popoverState.dismissable)
		.padding([.top, .trailing])
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			connectionStatus_section()
				.padding([.top, .leading, .trailing])
			
			Divider()
				.padding([.leading, .trailing])
				.padding([.top, .bottom], 25)
			
			syncStatusSection()
				.padding([.leading, .trailing])
				.padding(.bottom, 25)
		}
		.assignMaxPreference(for: titleIconWidthReader.key, to: $titleIconWidth)
		.onReceive(Biz.srvExtConnectedToPeer) {
			srvExtConnectedToPeerChanged($0)
		}
		.onReceive(syncManager.statePublisher) {
			syncStateChanged($0)
		}
		.onReceive(syncManager.pendingSettingsPublisher) {
			pendingSettingsChanged($0)
		}
	}
	
	@ViewBuilder
	func connectionStatus_section() -> some View {
		
		VStack(alignment: .leading) {
			
			connectionStatus_header()
				.padding(.bottom, 15)
			
			connectionStatus_cell_internet()
				.padding(.bottom, 8)
			
			connectionStatus_cell_peer()
				.padding(.bottom, 8)
			
			connectionStatus_cell_electrum()
			
			if isTorEnabled {
				connectionStatus_torWarning()
			}
		
		} // </VStack>
	}
	
	@ViewBuilder
	func connectionStatus_header() -> some View {
		
		let globalStatus = connectionsMonitor.connections.global
		Label {
			Text(globalStatus.localizedText())
			
		} icon: {
			Group {
				if globalStatus.isClosed() {
					Image(systemName: "bolt.slash.fill")
				} else if globalStatus.isEstablishing() {
					Image(systemName: "bolt.slash")
				} else {
					Image(systemName: "bolt.fill")
				}
			}
			.imageScale(.medium)
			.read(titleIconWidthReader)
			.frame(width: titleIconWidth, alignment: .center)
		}
		.font(.title3)
		.accessibilityLabel("Connection status: \(globalStatus.localizedText())")
	}
	
	@ViewBuilder
	func connectionStatus_cell_internet() -> some View {
		
		ConnectionCell(
			connection: connectionsMonitor.connections.internet,
			label: "Internet"
		)
	}
	
	@ViewBuilder
	func connectionStatus_cell_peer() -> some View {
		
		if srvExtConnectedToPeer {
			ConnectionCell(
				connection: connectionsMonitor.connections.peer,
				label: "Lightning peer",
				status: {
					Text("Receiving in background…")
						.lineLimit(3)
						.multilineTextAlignment(.trailing)
						.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				}
			)
		} else {
			ConnectionCell(
				connection: connectionsMonitor.connections.peer,
				label: "Lightning peer"
			)
		}
	}
	
	@ViewBuilder
	func connectionStatus_cell_electrum() -> some View {
		
		if isInvalidElectrumAddress {
			ConnectionCell(
				connection: connectionsMonitor.connections.electrum,
				label: "Electrum server",
				status: {
					Text("Invalid address")
						.foregroundStyle(Color.appAccent)
						.lineLimit(3)
						.multilineTextAlignment(.trailing)
						.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				},
				onTap: navigateToElectrumSettings
			)
		} else {
			ConnectionCell(
				connection: connectionsMonitor.connections.electrum,
				label: "Electrum server"
			)
		}
	}
	
	@ViewBuilder
	func connectionStatus_torWarning() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
				Label {
					Text("Tor is enabled").bold()
				} icon: {
					Image(systemName: "shield.lefthalf.filled")
				}
				
				Text("Make sure your Tor VPN is active and running.")
					.fixedSize(horizontal: false, vertical: true) // text truncation bugs
					.foregroundStyle(.secondary)
			}
			
			Spacer()
		}// </HStack>
		.padding(.all, 10)
		.background(Color(.systemGroupedBackground))
		.cornerRadius(10)
	}
	
	@ViewBuilder
	func syncStatusSection() -> some View {
		
		if let pendingSettings = pendingSettings {
			syncStatusSection_pending(pendingSettings)
		} else {
			syncStatusSection_syncState()
		}
	}
	
	@ViewBuilder
	func syncStatusSection_pending(_ value: SyncBackupManager_PendingSettings) -> some View {
		
		VStack(alignment: .leading) {
			
			Label {
				switch value.paymentSyncing {
				case .willEnable:
					Text("Will enable cloud syncing")
				case .willDisable:
					Text("Will disable cloud syncing")
				}
			} icon: {
				Image(systemName: "hourglass")
					.imageScale(.medium)
					.read(titleIconWidthReader)
					.frame(width: titleIconWidth, alignment: .center)
			}
			.font(.title3)
			.padding(.bottom, 15)
			
			PendingSettingsDetails(pendingSettings: value).font(.callout)
		}
	}
	
	@ViewBuilder
	func syncStatusSection_syncState() -> some View {
		
		VStack(alignment: .leading) {
			
			switch syncState {
				case .initializing:
					
					Label {
						Text("Cloud sync initializing")
					} icon: {
						Image(systemName: "arrow.clockwise.icloud")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
					
				case .updatingCloud(let details):
					
					Label {
						Text("Updating cloud")
					} icon: {
						Image(systemName: "icloud")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
					.padding(.bottom, 10)
					
					switch details.kind {
						case .creatingRecordZone:
							Text("Creating record zone...").font(.callout)
						case .deletingRecordZone:
							Text("Deleting record zone...").font(.callout)
					}
					
				case .downloading(let details):
					
					Label {
						Text("Downloading payments")
					} icon: {
						Image(systemName: "icloud.and.arrow.down")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
					.padding(.bottom, 15)
					
					DownloadProgressDetails(details: details)
						.font(.callout)
				
				case .uploading(let details):
					
					Label {
						Text("Uploading payments")
					} icon: {
						Image(systemName: "icloud.and.arrow.up")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
					.padding(.bottom, 15)
					
					UploadProgressDetails(details: details)
						.font(.callout)
				
				case .waiting(let details):
					
					switch details.kind {
						case .forInternet:
							
							Label {
								Text("Cloud sync paused")
							} icon: {
								Image(systemName: "icloud.slash")
									.imageScale(.medium)
									.read(titleIconWidthReader)
									.frame(width: titleIconWidth, alignment: .center)
							}
							.font(.title3)
							.padding(.bottom, 15)
							
							Text("Waiting for internet")
								.font(.callout)
							
						case .forCloudCredentials:
							
							Label {
								Text("Cloud sync error")
							} icon: {
								Image(systemName: "exclamationmark.icloud")
									.imageScale(.medium)
									.read(titleIconWidthReader)
									.frame(width: titleIconWidth, alignment: .center)
							}
							.font(.title3)
							.padding(.bottom, 15)
							
							Text("Sign into iCloud to backup your payments")
								.font(.callout)
							
						case .exponentialBackoff:
							
							Label {
								Text("Cloud sync error")
							} icon: {
								Image(systemName: "exclamationmark.icloud")
									.imageScale(.medium)
									.read(titleIconWidthReader)
									.frame(width: titleIconWidth, alignment: .center)
							}
							.font(.title3)
							.padding(.bottom, 15)
							
							SyncWaitingDetails(waiting: details).font(.callout)
							
						case .randomizedUploadDelay:
							
							Label {
								Text("Cloud sync paused")
							} icon: {
								Image(systemName: "hourglass")
									.imageScale(.medium)
									.read(titleIconWidthReader)
									.frame(width: titleIconWidth, alignment: .center)
							}
							.font(.title3)
							.padding(.bottom, 15)
							
							SyncWaitingDetails(waiting: details).font(.callout)
					}
				
				case .synced:
				
					Label {
						Text("Cloud sync complete")
					} icon: {
						Image(systemName: "checkmark.icloud")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
				
				case .disabled:
					
					Label {
						Text("Cloud sync disabled")
					} icon: {
						Image(systemName: "icloud.slash")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
				
				case .shutdown:
					
					Label {
						Text("Cloud sync disabled")
					} icon: {
						Image(systemName: "icloud.slash")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
					.font(.title3)
			
			} // </switch>
		} // </VStack>
	}
	
	var isInvalidElectrumAddress: Bool {
		
		if isTorEnabled {
			if let customConfig = electrumConfig as? ElectrumConfig.Custom {
				return !customConfig.server.isOnion && customConfig.requireOnionIfTorEnabled
			}
		}
	
		return false
	}
	
	func srvExtConnectedToPeerChanged(_ newValue: Bool) {
		log.trace("srvExtConnectedToPeerChanged()")
		
		srvExtConnectedToPeer = newValue
	}
	
	func syncStateChanged(_ newSyncState: SyncBackupManager_State) {
		log.trace("syncStateChanged()")
		
		syncState = newSyncState
	}
	
	func pendingSettingsChanged(_ newPendingSettings: SyncBackupManager_PendingSettings?) {
		log.trace("pendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	func navigateToElectrumSettings() {
		log.trace("navigateToElectrumSettings()")
		
		deepLinkManager.broadcast(.electrum)
		popoverState.close()
	}
	
	func closePopover() {
		log.trace("closePopover()")
		
		popoverState.close()
	}
}

fileprivate struct ConnectionCell<Content: View>: View {
	
	let connection: Lightning_kmpConnection
	let label: LocalizedStringKey
	let status: Content
	let onTap: (() -> Void)?
	
	init(
		connection: Lightning_kmpConnection,
		label: LocalizedStringKey,
		@ViewBuilder status: () -> Content = { EmptyView() },
		onTap: (() -> Void)? = nil
	) {
		self.connection = connection
		self.onTap = onTap
		self.label = label
		self.status = status()
	}
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			let bullet = Image(systemName: "circle.fill")
				.imageScale(.small)
				.layoutPriority(3)
				.accessibilityHidden(true)

			if connection.isEstablished() {
				bullet.foregroundColor(.appPositive)
			}
			else if connection.isEstablishing() {
				bullet.foregroundColor(.appWarn)
			}
			else /* connection.isClosed() */ {
				bullet.foregroundColor(.appNegative)
			}

			Text(label).padding(.leading)
				.multilineTextAlignment(.leading)
				.layoutPriority(1)
			
			Text(verbatim: ":")
				.layoutPriority(2)
			
			Spacer()
			if !(status is EmptyView) {
				status
			} else {
				Text(connection.localizedText())
					.lineLimit(3)
					.multilineTextAlignment(.trailing)
					.fixedSize(horizontal: false, vertical: true) // text truncation bugs
					
			}
			
		} // </HStack>
		.font(.callout)
		.accessibilityElement(children: .combine)
		.contentShape(Rectangle()) // make Spacer area tappable
		.onTapGesture {
			if let onTap { onTap() }
		}
	}
}

fileprivate struct DownloadProgressDetails: View {
	
	@StateObject var details: SyncBackupManager_State_Downloading
	
	@ViewBuilder
	var body: some View {
			
		HStack(alignment: .center, spacing: 6) {
			
			ProgressView()
				.progressViewStyle(CircularProgressViewStyle())
			
			if details.completedCount == 1 {
				Text("Fetched \(details.completedCount) item")
			} else {
				Text("Fetched \(details.completedCount) items")
			}
		}
	}
}

fileprivate struct UploadProgressDetails: View {
	
	@StateObject var details: SyncBackupManager_State_Uploading
	
	@ViewBuilder
	var body: some View {
			
		HStack(alignment: .center, spacing: 6) {
			
			ProgressView(value: uploadProgressValue())
				.progressViewStyle(CircularProgressViewStyle())
			
			Text(uploadProgressText())
		}
	}
	
	func uploadProgressValue() -> Double {
		
		let inFlightFraction = details.inFlightProgress?.fractionCompleted ?? 0.0
		let inFlightValue = Double(details.inFlightCount) * inFlightFraction
		
		let numerator: Double = Double(details.completedCount) + inFlightValue
		let denominator: Double = Double(details.totalCount)
		
		guard denominator != 0 else {
			return 0.0
		}
		
		let percent = numerator / denominator
		return max(0.0, min(1.0, percent))
	}
	
	func uploadProgressText() -> String {
	
		let completed = details.completedCount
		let total = details.totalCount
		let inFlight = details.inFlightCount
		
		if inFlight > 0 {
			return NSLocalizedString(
				"\(completed) of \(total), pushing batch of \(inFlight)",
				comment: "Upload progress information"
			)
		} else {
			return NSLocalizedString(
				"\(completed) of \(total)",
				comment: "Upload progress information"
			)
		}
	}
}

fileprivate struct SyncWaitingDetails: View, ViewName {
	
	let waiting: SyncBackupManager_State_Waiting
	
	let timer = Timer.publish(every: 0.5, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			switch waiting.kind {
				case .exponentialBackoff:
					Text("Exponential backoff")
					
				case .randomizedUploadDelay:
					Text("Randomized upload delay")
					
				default:
					Text("Waiting...")
			} // </switch>
			
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				
				let (progress, remaining, total) = progressInfo()
				
				ProgressView(value: progress, total: 1.0)
					.progressViewStyle(CircularCheckmarkProgressViewStyle(
						strokeStyle: StrokeStyle(lineWidth: 3.0),
						showGuidingLine: true,
						guidingLineWidth: 1.0,
						showPercentage: false,
						checkmarkAnimation: .trim
					))
					.foregroundColor(Color.appAccent)
					.frame(maxWidth: 20, maxHeight: 20)
				
				Text(verbatim: "\(remaining) / \(total)")
					.font(.system(.callout, design: .monospaced))
				
				Spacer()
				
				Button {
					skipButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Text("Skip")
						Image(systemName: "arrowshape.turn.up.forward")
							.imageScale(.medium)
					}
				}
			}
			.padding(.top, 4)
			.padding(.bottom, 4)
			
			if let errorInfo = errorInfo() {
				Text(errorInfo)
					.font(.callout)
					.multilineTextAlignment(.leading)
					.lineLimit(2)
			}
			
		} // </VStack>
		.onReceive(timer) { _ in
			self.currentDate = Date()
		}
	}
	
	func progressInfo() -> (Double, String, String) {
		
		guard let until = waiting.until else {
			return (1.0, "0:00", "0:00")
		}
		
		let start = until.startDate.timeIntervalSince1970
		let end = until.fireDate.timeIntervalSince1970
		let now = currentDate.timeIntervalSince1970
		
		guard start < end, now >= start, now < end else {
			return (1.0, "0:00", "0:00")
		}
		
		let progressFraction = (now - start) / (end - start)
		
		let remaining = formatTimeInterval(end - now)
		let total = formatTimeInterval(until.delay)
		
		return (progressFraction, remaining, total)
	}
	
	func formatTimeInterval(_ value: TimeInterval) -> String {
		
		let minutes = Int(value) / 60
		let seconds = Int(value) % 60
		
		return String(format: "%d:%02d", minutes, seconds)
	}
	
	func errorInfo() -> String? {
		
		guard case .exponentialBackoff(let error) = waiting.kind else {
			return nil
		}
		
		var result: String? = nil
		if let ckerror = error as? CKError {
			
			switch ckerror.errorCode {
				case CKError.quotaExceeded.rawValue:
					result = "iCloud storage is full"
				
				default: break
			}
		}
		
		return result ?? error.localizedDescription
	}
	
	func skipButtonTapped() -> Void {
		log.trace("[\(viewName)] skipButtonTapped()")
		
		waiting.skip()
	}
}

fileprivate struct PendingSettingsDetails: View, ViewName {
	
	let pendingSettings: SyncBackupManager_PendingSettings
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 8) {
			
			Button {
				cancelButtonTapped()
			} label: {
				Text("Cancel")
			}
			
			Spacer()
			
		} // </HStack>
	}
	
	func cancelButtonTapped() -> Void {
		log.trace("[\(viewName)] cancelButtonTapped()")
		
		pendingSettings.cancel()
	}
}
