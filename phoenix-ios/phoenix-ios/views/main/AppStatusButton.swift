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
	
	let systemImagesUsed = [
		"bolt.fill", "bolt.slash", "bolt.slash.fill", "hourglass", "icloud", "exclamationmark.triangle"
	]
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			ForEach(systemImagesUsed, id: \.self) { systemImageName in
				Image(systemName: systemImageName)
					.renderingMode(.template)
					.imageScale(.large)
					.font(.caption2)
					.foregroundColor(.clear)
					.padding(.all, 7)
					.read(headerButtonHeightReader)
			} // </ForEach>
			
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
					.padding(.leading, 10)
					.padding(.trailing, -5)
				Image(systemName: "bolt.slash.fill")
					.imageScale(.large)
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
			.font(.caption2)
		}
		else if connectionStatus is Lightning_kmpConnection.ESTABLISHING {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				Text(NSLocalizedString("Connecting...", comment: "Connection state"))
					.padding(.leading, 10)
					.padding(.trailing, -5)
				Image(systemName: "bolt.slash")
					.imageScale(.large)
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
			.font(.caption2)
		} else /* .established */ {
			
			if pendingSettings != nil {
				// The user enabled/disabled cloud sync.
				// We are using a 30 second delay before we start operating on the user's decision.
				// Just in-case it was an accidental change, or the user changes his/her mind.
				Image(systemName: "hourglass")
					.imageScale(.large)
					.font(.caption2)
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			} else {
				let (isSyncing, isWaiting, isError) = buttonizeSyncStatus()
				if isSyncing {
					Image(systemName: "icloud")
						.imageScale(.large)
						.font(.caption2)
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else if isWaiting {
					Image(systemName: "hourglass")
						.imageScale(.large)
						.font(.caption2)
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else if isError {
					Image(systemName: "exclamationmark.triangle")
						.imageScale(.large)
						.font(.caption2)
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				} else {
					// Everything is good: connected + {synced|disabled|initializing}
					Image(systemName: "bolt.fill")
						.imageScale(.large)
						.font(.caption2)
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
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
