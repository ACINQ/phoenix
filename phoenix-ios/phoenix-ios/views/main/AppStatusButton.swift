import SwiftUI
import PhoenixShared

fileprivate let filename = "AppStatusButton"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct AppStatusButton: View {
	
	let headerButtonHeightReader: GeometryPreferenceReader<AppendValue<HeaderButtonHeight>, [CGFloat]>
	@Binding var headerButtonHeight: CGFloat?
	
	@State var isTorEnabled: Bool = Biz.business.appConfigurationManager.isTorEnabledValue
	@State var electrumConfig: ElectrumConfig = Biz.business.appConfigurationManager.electrumConfigValue
	
	@State var syncState: SyncBackupManager_State = .initializing
	@State var pendingSettings: SyncBackupManager_PendingSettings? = nil
	
	@State var timer: Timer? = nil
	@State var showText: Bool = false
	let showTextDelay: TimeInterval = 10 // seconds
	
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@State var channels = Biz.business.peerManager.channelsValue()
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	let syncBackupManager = Biz.syncManager!.syncBackupManager

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
		.onReceive(syncBackupManager.statePublisher) {
			syncBackupManagerStateChanged($0)
		}
		.onReceive(syncBackupManager.pendingSettingsPublisher) {
			syncBackupManagerPendingSettingsChanged($0)
		}
		.onReceive(Biz.business.appConfigurationManager.isTorEnabledPublisher()) {
			isTorEnabledChanged($0)
		}
		.onReceive(Biz.business.appConfigurationManager.electrumConfigPublisher()) {
			electrumConfigChanged($0)
		}
		.onReceive(channelsPublisher) {
			channelsChanged($0)
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
		
		if isInvalidElectrumAddress {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				Text(NSLocalizedString("Invalid address", comment: "Connection state"))
					.font(.caption2)
					.padding(.leading, 10)
					.padding(.trailing, -5)
				AppStatusButtonIcon.invalidElectrumAddress.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
		} else if connectionStatus.isClosed() {
			HStack(alignment: .firstTextBaseline, spacing: 0) {
				if showText {
					Text("Offline", comment: "Connection state")
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
					Text("Connecting…", comment: "Connection state")
						.font(.caption2)
						.padding(.leading, 10)
						.padding(.trailing, -5)
				}
				AppStatusButtonIcon.connecting.view()
					.frame(minHeight: headerButtonHeight)
					.squareFrame()
			}
		} else /* .established */ {
			
			let inFlightPaymentsCount = channels.inFlightPaymentsCount()
			if inFlightPaymentsCount > 0 {
				HStack(alignment: .firstTextBaseline, spacing: 0) {
					Text(verbatim: "\(inFlightPaymentsCount)")
						.font(.footnote)
						.padding(.leading, 10)
						.padding(.trailing, -5)
					AppStatusButtonIcon.paymentsInFlight.view()
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				}
				
			} else if pendingSettings != nil {
				// The user enabled/disabled cloud sync.
				// We are using a 30 second delay before we start operating on the user's decision.
				// This is a safety measure, in case it was an accidental change, or the user changes their mind.
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
					AppStatusButtonIcon.connected.view()
						.frame(minHeight: headerButtonHeight)
						.squareFrame()
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isInvalidElectrumAddress: Bool {
		
		if isTorEnabled {
			if let customConfig = electrumConfig as? ElectrumConfig.Custom {
				return !customConfig.server.isOnion && customConfig.requireOnionIfTorEnabled
			}
		}
	
		return false
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
	
	func syncBackupManagerStateChanged(_ newState: SyncBackupManager_State) {
		log.trace("syncBackupManagerStateChanged()")
		
		syncState = newState
	}
	
	func syncBackupManagerPendingSettingsChanged(_ newPendingSettings: SyncBackupManager_PendingSettings?) {
		log.trace("syncBackupManagerPendingSettingsChanged()")
		
		pendingSettings = newPendingSettings
	}
	
	func isTorEnabledChanged(_ newValue: Bool) {
		log.trace("isTorEnabledChanged(\(newValue))")
		
		isTorEnabled = newValue
	}
	
	func electrumConfigChanged(_ newValue: ElectrumConfig) {
		log.trace("electrumConfigChanged()")
		
		electrumConfig = newValue
	}
	
	func channelsChanged(_ newChannels: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		
		channels = newChannels
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
	case paymentsInFlight
	case syncing
	case waiting
	case error
	case invalidElectrumAddress;

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
		case .paymentsInFlight:
			Image(systemName: "paperplane")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
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
		case .invalidElectrumAddress:
			Image(systemName: "shield.lefthalf.filled.slash")
				.imageScale(.large)
				.font(.caption2)
				.padding(.all, 7)
		}
	}
}
