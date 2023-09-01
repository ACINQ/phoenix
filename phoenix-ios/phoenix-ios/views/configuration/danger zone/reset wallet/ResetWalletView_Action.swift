import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ResetWalletView_Action"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum DeleteState {
	case waitingToStart
	case waitingForInternet
	case waitingForCloudCredentials
	case disablingPref
	case deletingFromCloud
	case done
}

struct ResetWalletView_Action: View {
	
	let deleteTransactionHistory: Bool
	let deleteSeedBackup: Bool
	let startDelay: TimeInterval
	
	@State private var deleteTxHistoryState = DeleteState.waitingToStart
	@State private var deleteSeedState = DeleteState.waitingToStart
	@State private var deleteLocalProgress = WalletReset.Progress.starting
	
	@State var visible = false
	@State var didAppear = false
	
	@State var syncSeedManager = Biz.syncManager!.syncSeedManager
	@State var syncTxManager = Biz.syncManager!.syncTxManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			// An additional layer for animation purposes
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
				.zIndex(0)
			
			if visible {
				navWrappedContent()
					.zIndex(1)
					.transition(.move(edge: .bottom))
			}
		}
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func navWrappedContent() -> some View {
		
		NavigationWrapper {
			content()
				.navigationTitle(NSLocalizedString("Resetting Wallet", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack {
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			// On iPad, the list looks goofy when full-screen.
			list()
				.frame(maxWidth: DeviceInfo.textColumnMaxWidth)
		}
		.onReceive(syncTxManager.pendingSettingsPublisher) {
			syncTx_pendingSettingsChanged($0)
		}
		.onReceive(syncTxManager.statePublisher) {
			syncTx_stateChanged($0)
		}
		.onReceive(syncSeedManager.statePublisher) {
			syncSeed_stateChanged($0)
		}
		.onReceive(WalletReset.shared.progress) {
			walletCloser_progressChanged($0)
		}
	}
	
	@ViewBuilder
	func list() -> some View {
		
		List {
			let showSteps = deleteTransactionHistory || deleteSeedBackup
			if showSteps {
				
				var idx: Int = 0
				let getIdx = { () -> Int in
					idx = idx + 1
					return idx
				}
				
				if deleteTransactionHistory {
					section_transactionHistory(getIdx())
				}
				if deleteSeedBackup {
					section_seedBackup(getIdx())
				}
				section_localData(getIdx())
				
			} else {
				section_localData(nil)
			}
			
			section_button()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	private func section_transactionHistory(_ idx: Int) -> some View {
		
		Section {
			
			Label {
				Text(styled: NSLocalizedString(
					"""
					Deleting **payment history** from iCloud
					""",
					comment: "ResetWalletView_Delete_Action"
				))
			} icon: {
				Image(systemName: "icloud.fill")
			}
			.font(.title3)
			
			subsection_deleteState(deleteTxHistoryState)
			
		} header: {
			
			Text("Step #\(idx)")
			
		} // </Section>
	}
	
	@ViewBuilder
	private func section_seedBackup(_ idx: Int) -> some View {
		
		Section {
			
			Label {
				Text(styled: NSLocalizedString(
					"""
					Deleting **recovery phrase** from iCloud
					""",
					comment: "ResetWalletView_Delete_Action"
				))
			} icon: {
				Image(systemName: "icloud.fill")
			}
			.font(.title3)
			
			subsection_deleteState(deleteSeedState)
			
		} header: {
			
			Text("Step #\(idx)")
			
		} // </Section>
	}
	
	@ViewBuilder
	private func subsection_deleteState(_ state: DeleteState) -> some View {
		
		switch state {
		case .waitingToStart:
			Label {
				Text("Waiting to start…")
			} icon: {
				Image(systemName: "clock")
			}
			
		case .waitingForInternet:
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
					Text("Waiting for internet.")
					Text("Please check your connection.")
						.foregroundColor(.appNegative)
				}
			} icon: {
				Image(systemName: "clock")
			}
			
		case .waitingForCloudCredentials:
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
					Text("Waiting for iCloud credentials.")
					Text("Please sign into iCloud.")
						.foregroundColor(.appNegative)
				}
			} icon: {
				Image(systemName: "clock")
			}
			
		case .disablingPref:
			Label {
				Text("Disabling backup…")
			} icon: {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
			}
		
		case .deletingFromCloud:
			Label {
				Text("Deleting data from iCloud…")
			} icon: {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
			}
			
		case .done:
			Label {
				Text("Done")
			} icon: {
				Image(systemName: "checkmark")
			}
			.foregroundColor(.appPositive)
			
		} // </switch>
	}
	
	@ViewBuilder
	private func section_localData(_ idx: Int?) -> some View {
		
		Section {
			
			Label {
				Text(styled: NSLocalizedString(
					"""
					Deleting data from **this device**
					""",
					comment: "ResetWalletView_Delete_Action"
				))
			} icon: {
				Image(systemName: "folder")
			}
			.font(.title3)
			
			if deleteLocalProgress.rawValue > WalletReset.Progress.deletingDatabaseFiles.rawValue {
				Label {
					Text("Deleted database files")
				} icon: {
					Image(systemName: "checkmark.circle")
				}
				.foregroundColor(.appPositive)
			} else {
				Label {
					Text("Deleting database files…")
				} icon: {
					Image(systemName: "circle")
				}
				.foregroundColor(.primary)
			}
			
			if deleteLocalProgress.rawValue > WalletReset.Progress.resetingUserDefaults.rawValue {
				Label {
					Text("Reset user preferences")
				} icon: {
					Image(systemName: "checkmark.circle")
				}
				.foregroundColor(.appPositive)
			} else {
				Label {
					Text("Resetting user preferences…")
				} icon: {
					Image(systemName: "circle")
				}
				.foregroundColor(.primary)
			}
			
			if deleteLocalProgress.rawValue > WalletReset.Progress.deletingKeychainItems.rawValue {
				Label {
					Text("Deleted keychain items")
				} icon: {
					Image(systemName: "checkmark.circle")
				}
				.foregroundColor(.appPositive)
			} else {
				Label {
					Text("Deleting keychain items…")
				} icon: {
					Image(systemName: "circle")
				}
				.foregroundColor(.primary)
			}
			
		} header: {
			
			if let idx = idx {
				Text("Step #\(idx)")
			} else {
				EmptyView()
			}
			
		} // </Section>
	}
	
	@ViewBuilder
	private func section_button() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center) {
				
				Button {
					doneButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "checkmark")
							.imageScale(.medium)
						Text("Done")
					}
					.font(.headline)
				}
				.disabled(doneButtonDisabled())
				
			} // </VStack>
			.padding(.vertical, 5)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func doneButtonDisabled() -> Bool {
		
		if deleteTransactionHistory && (deleteTxHistoryState != .done) {
			return true
		}
		if deleteSeedBackup && (deleteSeedState != .done) {
			return true
		}
		
		return deleteLocalProgress != .done
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		guard !didAppear else {
			return
		}
		didAppear = true

		withAnimation {
			visible = true
		}

		DispatchQueue.main.asyncAfter(deadline: .now() + startDelay) {
			action_next()
		}
	}
	
	func syncTx_pendingSettingsChanged(_ pendingSettings: SyncTxManager_PendingSettings?) {
		log.trace("syncTx_pendingSettingsChanged()")
		assertMainThread() // SyncTxManager promises to always publish on the main thread
		
		guard let pendingSettings = pendingSettings else {
			return
		}
		
		if pendingSettings.paymentSyncing == .willDisable && deleteTransactionHistory {
			log.debug("Found pendingSettings.willDisabled => invoking approve() to skip delay")
			pendingSettings.approve()
		}
	}
	
	func syncTx_stateChanged(_ state: SyncTxManager_State) {
		log.trace("syncTx_stateChanged(\(state.description))")
		assertMainThread() // SyncTxManager promises to always publish on the main thread
		
		guard deleteTransactionHistory else {
			log.debug("ignoring => !deleteTransactionHistory")
			return
		}
		
		switch state {
			case .waiting(let details):
				if details.kind == .forInternet {
					deleteTxHistoryState = .waitingForInternet
				} else if details.kind == .forCloudCredentials {
					deleteTxHistoryState = .waitingForCloudCredentials
				}
			case .updatingCloud(let details):
				if details.kind == .deletingRecordZone {
					deleteTxHistoryState = .deletingFromCloud
				}
			case .disabled:
				deleteTxHistoryState = .done
				action_next()
			default:
				break
		}
	}
	
	func syncSeed_stateChanged(_ state: SyncSeedManager_State) {
		log.trace("syncTx_stateChanged(\(state.description)")
		assertMainThread() // SyncSeedManager promises to always publish on the main thread
		
		guard deleteSeedBackup else {
			log.debug("ignoring => !deleteSeedBackup")
			return
		}
		
		switch state {
		case .waiting(let details):
			if details.kind == .forInternet {
				deleteSeedState = .waitingForInternet
			} else if details.kind == .forCloudCredentials {
				deleteSeedState = .waitingForCloudCredentials
			}
		case .deleting:
			deleteSeedState = .deletingFromCloud
		case .disabled:
			deleteSeedState = .done
			action_next()
		default:
			break
		} // </switch>
	}
	
	func walletCloser_progressChanged(_ progress: WalletReset.Progress) {
		log.trace("walletCloser_progressChanged(\(progress.description))")
		
		self.deleteLocalProgress = progress
	}
	
	// --------------------------------------------------
	// MARK: Logic
	// --------------------------------------------------
	
	func action_next() {
		log.trace("action_next()")
		
		if deleteTransactionHistory && (deleteTxHistoryState != .done) {
			action_deleteTransactionHistory()
		}
		else if deleteSeedBackup && (deleteSeedState != .done) {
			action_deleteSeedBackup()
		} else {
			action_deleteLocalData()
		}
	}
	
	func action_deleteTransactionHistory() {
		log.trace("action_deleteTransactionHistory()")
		
		self.deleteTxHistoryState = .disablingPref
		Prefs.shared.backupTransactions.isEnabled = false
	}
	
	func action_deleteSeedBackup() {
		log.trace("action_deleteSeedBackup()")
		
		self.deleteSeedState = .disablingPref
		Prefs.shared.backupSeed.isEnabled = false
	}
	
	func action_deleteLocalData() {
		log.trace("action_deleteLocalData()")
		
		WalletReset.shared.start()
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func doneButtonTapped() {
		log.trace("doneButtonTapped()")
		
		if let scene = UIApplication.shared.connectedScenes.first,
			let sceneDelegate = scene.delegate as? UIWindowSceneDelegate,
			let mySceneDelegate = sceneDelegate as? SceneDelegate
		{
			if mySceneDelegate.transitionBackToMainWindow() {
				withAnimation {
					visible = false
				}
			}
		}
	}
}
