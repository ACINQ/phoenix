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
	
	@State var timer: Timer? = nil
	@State var showText: Bool = false
	let showTextDelay: TimeInterval = 10 // seconds
	
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	let syncTxManager = Biz.syncManager!.syncTxManager

	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			ForEach(AppStatusButtonIcon.allCases) { icon in
				icon.view()
					.foregroundColor(.clear)
					.read(headerButtonHeightReader)
					.accessibilityHidden(true)
			} // </ForEach>
			
			button()
		}
		.onAppear {
			onAppear()
		}
		.onChange(of: connectionsMonitor.disconnectedAt) { _ in
			updateTimer()
		}
		.onChange(of: connectionsMonitor.connectingAt) { _ in
			updateTimer()
		}
		.onReceive(syncTxManager.statePublisher) {
			syncTxManagerStateChanged($0)
		}
		.onReceive(syncTxManager.pendingSettingsPublisher) {
			syncTxManagerPendingSettingsChanged($0)
		}
	}
	
	@ViewBuilder
	func button() -> some View {
		
		Button {
			showAppStatusPopover()
		} label: {
			buttonContent()
		}
		.buttonStyle(PlainButtonStyle())
		.background(Color.buttonFill)
		.cornerRadius(30)
		.overlay(
			RoundedRectangle(cornerRadius: 30) // Test this with larger dynamicFontSize
				.stroke(Color.borderColor, lineWidth: 1)
		)
		.accessibilityLabel("App status")
	}
	
	@ViewBuilder
	func buttonContent() -> some View {
		
		let connectionStatus = connectionsMonitor.connections.global
		if connectionStatus.isClosed() {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				if showText {
					Text(NSLocalizedString("Offline", comment: "Connection state"))
						.font(.caption2)
						.padding(.leading, 10)
						.padding(.trailing, -5)
				}
				AppStatusButtonIcon.disconnected.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
		}
		else if connectionStatus.isEstablishing() {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				if showText {
					Text(NSLocalizedString("Connecting…", comment: "Connection state"))
						.font(.caption2)
						.padding(.leading, 10)
						.padding(.trailing, -5)
				}
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
					if connectionsMonitor.connections.torEnabled {
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
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
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
			case .shutdown: break
		}
		
		// If the user isn't signed into iCloud, is this an error ?
		// We are choosing to treat it more like the disabled case,
		// since the user has choosed to not sign in,
		// or has ignored Apple's continual "sign into iCloud" popups.
		
		return (isSyncing, isWaiting, isError)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		updateTimer()
	}
	
	func syncTxManagerStateChanged(_ newState: SyncTxManager_State) {
		log.trace("syncTxManagerStateChanged()")
		
		syncState = newState
	}
	
	func syncTxManagerPendingSettingsChanged(_ newPendingSettings: SyncTxManager_PendingSettings?) {
		log.trace("syncTxManagerPendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	// --------------------------------------------------
	// MARK: User Actions
	// --------------------------------------------------
	
	func showAppStatusPopover() {
		log.trace("showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
	
	// --------------------------------------------------
	// MARK: Timer
	// --------------------------------------------------
	
	func updateTimer() {
		
		if timer != nil {
			timer?.invalidate()
			timer = nil
		}
		
		if let diff = showTextDelayDiff(), diff > 0 {
			log.trace("updateTimer(): seconds=\(diff)")
			
			timer = Timer.scheduledTimer(withTimeInterval: diff, repeats: false) { _ in
				log.debug("timer fire")
				updateShowText()
			}
		} else {
			log.trace("updateTimer(): nil")
		}
	}
	
	func updateShowText() {
		
		if let diff = showTextDelayDiff() {
			showText = diff <= 0.0
			log.trace("updateShowText(): \(showText) (diff: \(diff))")
		} else {
			showText = false
			log.trace("updateShowText(): false (diff == nil)")
		}
	}
	
	func showTextDelayDiff() -> TimeInterval? {
		
		if let connectingAt = connectionsMonitor.connectingAt {
			// connectingAt => Date/time at which we started a connection attempt
			return connectingAt.addingTimeInterval(showTextDelay).timeIntervalSinceNow
			
		} else if let disconnectedAt = connectionsMonitor.disconnectedAt {
			// disconnectedAt => Date/time at which we first disconnected
			return disconnectedAt.addingTimeInterval(showTextDelay).timeIntervalSinceNow
			
		} else {
			return nil
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
