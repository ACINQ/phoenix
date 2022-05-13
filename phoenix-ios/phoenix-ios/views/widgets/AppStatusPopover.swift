import SwiftUI
import PhoenixShared
import os.log
import CircularCheckmarkProgress
import CloudKit

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppStatusPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct AppStatusPopover: View {

	@StateObject var monitor = ObservableConnectionsManager()
	
	@State var syncState: SyncTxManager_State = .initializing
	@State var pendingSettings: SyncTxManager_PendingSettings? = nil
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	let syncManager = AppDelegate.get().syncManager!.syncTxManager
	
	enum TitleIconWidth: Preference {}
	let titleIconWidthReader = GeometryPreferenceReader(
		key: AppendValue<TitleIconWidth>.self,
		value: { [$0.size.width] }
	)
	@State var titleIconWidth: CGFloat? = nil

	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			connectionStatusSection()
				.padding([.top, .leading, .trailing])
			
			Divider()
				.padding([.leading, .trailing])
				.padding([.top, .bottom], 25)
			
			syncStatusSection()
				.padding([.leading, .trailing])
				.padding(.bottom, 25)
			
			HStack {
				Spacer()
				Button(NSLocalizedString("Close", comment: "Button")) {
					withAnimation {
						popoverState.close()
					}
				}
				.font(.title2)
			}
			.padding(.top, 10)
			.padding([.leading, .trailing])
			.padding(.bottom, 10)
			.background(
				Color(UIColor.secondarySystemBackground)
			)
		}
		.assignMaxPreference(for: titleIconWidthReader.key, to: $titleIconWidth)
		.onReceive(syncManager.statePublisher) {
			syncStateChanged($0)
		}
		.onReceive(syncManager.pendingSettingsPublisher) {
			pendingSettingsChanged($0)
		}
	}
	
	@ViewBuilder
	func connectionStatusSection() -> some View {
		
		VStack(alignment: .leading) {
			
			Group {
				
				let globalStatus = monitor.connections.global
				if globalStatus is Lightning_kmpConnection.CLOSED {
					
					Label {
						Text("Offline")
					} icon: {
						Image(systemName: "bolt.slash.fill")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
				
				} else if globalStatus is Lightning_kmpConnection.ESTABLISHING {
					
					Label {
						Text("Connecting...")
					} icon: {
						Image(systemName: "bolt.slash")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
				
				} else {
				
					Label {
						Text("Connected")
					} icon: {
						Image(systemName: "bolt.fill")
							.imageScale(.medium)
							.read(titleIconWidthReader)
							.frame(width: titleIconWidth, alignment: .center)
					}
				}
				
			} // </HStack>
			.font(.title3)
			.padding(.bottom, 15)
			
			ConnectionCell(
				label: NSLocalizedString("Internet", comment: "AppStatusPopover: label"),
				connection: monitor.connections.internet
			)
			.padding(.bottom, 8)
			ConnectionCell(
				label: NSLocalizedString("Lightning peer", comment: "AppStatusPopover: label"),
				connection: monitor.connections.peer
			)
			.padding(.bottom, 8)
			ConnectionCell(
				label: NSLocalizedString("Electrum server", comment: "AppStatusPopover: label"),
				connection: monitor.connections.electrum
			)
		
		} // </VStack>
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
	func syncStatusSection_pending(_ value: SyncTxManager_PendingSettings) -> some View {
		
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
					
				case .downloading(let progress):
					
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
					
					SyncProgressDetails(syncState: $syncState, syncProgress: progress)
						.font(.callout)
				
				case .uploading(let progress):
					
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
					
					SyncProgressDetails(syncState: $syncState, syncProgress: progress)
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
			
			} // </switch>
		} // </VStack>
	}
	
	func syncStateChanged(_ newSyncState: SyncTxManager_State) {
		log.trace("syncStateChanged()")
		
		syncState = newSyncState
	}
	
	func pendingSettingsChanged(_ newPendingSettings: SyncTxManager_PendingSettings?) {
		log.trace("pendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
}

fileprivate struct ConnectionCell: View {
	
	let label: String
	let connection: Lightning_kmpConnection

	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.center) {
			let bullet = Image(systemName: "circle.fill").imageScale(.small)

			if connection is Lightning_kmpConnection.ESTABLISHED{
				bullet.foregroundColor(.appPositive)
			}
			else if connection is Lightning_kmpConnection.ESTABLISHING {
				bullet.foregroundColor(.appWarn)
			}
			else if connection is Lightning_kmpConnection.CLOSED {
				bullet.foregroundColor(.appNegative)
			}

			Text(verbatim: "\(label):")
			Spacer()
			Text(connection.localizedText())
			
		} // </HStack>
		.font(.callout)
	}
}

fileprivate struct SyncProgressDetails: View {
	
	@Binding var syncState: SyncTxManager_State
	@StateObject var syncProgress: SyncTxManager_State_Progress
	
	@ViewBuilder
	var body: some View {
			
		HStack(alignment: .center, spacing: 6) {
			
			switch syncState {
				case .downloading:
			
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle())
						
					if syncProgress.completedCount == 1 {
						Text("Fetched \(syncProgress.completedCount) item")
					} else {
						Text("Fetched \(syncProgress.completedCount) items")
					}
			
				case .uploading:
				
					ProgressView(value: uploadProgressValue())
						.progressViewStyle(CircularProgressViewStyle())
					
					Text(uploadProgressText())
				
				default:
				
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle())
					
					Text(verbatim: "...")
					
			} // </switch>
		} // </HStack>
	}
	
	func uploadProgressValue() -> Double {
		
		let inFlightFraction = syncProgress.inFlightProgress?.fractionCompleted ?? 0.0
		let inFlightValue = Double(syncProgress.inFlightCount) * inFlightFraction
		
		let numerator: Double = Double(syncProgress.completedCount) + inFlightValue
		let denominator: Double = Double(syncProgress.totalCount)
		
		return numerator / denominator
	}
	
	func uploadProgressText() -> String {
	
		let completed = syncProgress.completedCount
		let total = syncProgress.totalCount
		let inFlight = syncProgress.inFlightCount
		
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
	
	let waiting: SyncTxManager_State_Waiting
	
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
	
	let pendingSettings: SyncTxManager_PendingSettings
	
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
