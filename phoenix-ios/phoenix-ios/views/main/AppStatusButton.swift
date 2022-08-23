import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppStatusButton"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct AppStatusButton: View {
	
	let headerButtonHeightReader: GeometryPreferenceReader<AppendValue<HeaderButtonHeight>, [CGFloat]>
	@Binding var headerButtonHeight: CGFloat?
	
	@State var dimStatus = false
	
	@State var syncState: SyncTxManager_State = .initializing
	@State var pendingSettings: SyncTxManager_PendingSettings? = nil
	
	@StateObject var connectionsManager = ObservableConnectionsManager()
	
	@Environment(\.popoverState) var popoverState: PopoverState

	@EnvironmentObject var deviceInfo: DeviceInfo
	
	let syncTxManager = AppDelegate.get().syncManager!.syncTxManager
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			ForEach(AppStatusButtonIcon.allCases) { icon in
				icon.view()
					.foregroundColor(.clear)
					.read(headerButtonHeightReader)
			}
			
			button
		}
	}
	
	@ViewBuilder
	var button: some View {
		
		Button {
			showAppStatusPopover()
		} label: {
			buttonContent
		}
		.buttonStyle(PlainButtonStyle())
		.background(Color.buttonFill)
		.cornerRadius(30)
		.overlay(
			RoundedRectangle(cornerRadius: 30) // Test this with larger dynamicFontSize
				.stroke(Color.borderColor, lineWidth: 1)
		)
		.onReceive(syncTxManager.statePublisher) {
			syncTxManagerStateChanged($0)
		}
		.onReceive(syncTxManager.pendingSettingsPublisher) {
			syncTxManagerPendingSettingsChanged($0)
		}
	}
	
	@ViewBuilder
	var buttonContent: some View {
		
		let connectionStatus = connectionsManager.connections.global
		if connectionStatus is Lightning_kmpConnection.CLOSED {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				Text(NSLocalizedString("Offline", comment: "Connection state"))
					.font(.caption2)
					.padding(.leading, 10)
					.padding(.trailing, -5)
				AppStatusButtonIcon.disconnected.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
		}
		else if connectionStatus is Lightning_kmpConnection.ESTABLISHING {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				Text(NSLocalizedString("Connecting...", comment: "Connection state"))
					.font(.caption2)
					.padding(.leading, 10)
					.padding(.trailing, -5)
				AppStatusButtonIcon.connecting.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
		} else /* .established */ {
			
			if pendingSettings != nil {
				// The user enabled/disabled cloud sync.
				// We are using a 30 second delay before we start operating on the user's decision.
				// Just in-case it was an accidental change, or the user changes his/her mind.
				AppStatusButtonIcon.waiting.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			} else {
				let (isSyncing, isWaiting, isError) = buttonizeSyncStatus()
				if isSyncing {
					AppStatusButtonIcon.syncing.view()
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else if isWaiting {
					AppStatusButtonIcon.waiting.view()
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else if isError {
					AppStatusButtonIcon.error.view()
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else {
					// Everything is good: connected + {synced|disabled|initializing}
					if connectionsManager.connections.tor != nil {
						AppStatusButtonIcon.connectedWithTor.view()
							.frame(minHeight: headerButtonHeight)
							.squareFrame()
					} else {
						AppStatusButtonIcon.connected.view()
							.frame(minHeight: headerButtonHeight)
							.squareFrame()
					}
				}
			}
		}
	}
	
	func buttonizeSyncStatus() -> (Bool, Bool, Bool) {
		
		var isSyncing = false
		var isWaiting = false
		var isError = false
		
		switch syncState {
			case .initializing: break
			case .updatingCloud: isSyncing = true
			case .downloading: isSyncing = true
			case .uploading: isSyncing = true
			case .waiting(let details):
				switch details.kind {
					case .forInternet: break
					case .forCloudCredentials: break // see discussion below
					case .exponentialBackoff: isError = true
					case .randomizedUploadDelay: isWaiting = true
				}
			case .synced: break
			case .disabled: break
		}
		
		// If the user isn't signed into iCloud, is this an error ?
		// We are choosing to treat it more like the disabled case,
		// since the user has choosed to not sign in,
		// or has ignored Apple's continual "sign into iCloud" popups.
		
		return (isSyncing, isWaiting, isError)
	}
	
	func syncTxManagerStateChanged(_ newState: SyncTxManager_State) -> Void {
		log.trace("syncTxManagerStateChanged()")
		
		syncState = newState
	}
	
	func syncTxManagerPendingSettingsChanged(_ newPendingSettings: SyncTxManager_PendingSettings?) -> Void {
		log.trace("syncTxManagerPendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	func showAppStatusPopover() -> Void {
		log.trace("showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}

fileprivate enum AppStatusButtonIcon: CaseIterable, Identifiable {
	case disconnected
	case connecting
	case connected
	case connectedWithTor
	case syncing
	case waiting
	case error;
	
	var id: Self { self }
	
	@ViewBuilder func view() -> some View {
		switch self {
		case .disconnected:
			Image(systemName: "bolt.slash.fill")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		case .connecting:
			Image(systemName: "bolt.slash")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		case .connected:
			Image(systemName: "bolt.fill")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		case .connectedWithTor:
			Image(systemName: "bolt.shield.fill")
				.imageScale(.large)
				.font(.subheadline) // bigger
				.padding(.all, 0)   // bigger
		case .syncing:
			Image(systemName: "icloud")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		case .waiting:
			Image(systemName: "hourglass")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		case .error:
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		}
	}
}
