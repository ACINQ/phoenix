import SwiftUI
import CloudKit
import CircularCheckmarkProgress
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RecoveryPhraseView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct RecoveryPhraseView: View {
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			RecoveryPhraseList(scrollViewProxy: scrollViewProxy)
		}
	}
}

struct RecoveryPhraseList: View {
	
	let scrollViewProxy: ScrollViewProxy
	let encryptedNodeId: String
	
	@State var manualBackup_taskDone: Bool
	@State var backupSeed_enabled: Bool
	
	let syncSeedManager: SyncSeedManager
	@State var syncState: SyncSeedManager_State = .disabled
	@State var syncStateWasEnabled = false
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var mnemonics: [String] = []
	
	@State var legal_taskDone: Bool
	@State var legal_lossRisk: Bool
	@State var animatingLegalToggleColor = false
	
	@State var didAppear = false
	
	@Namespace var sectionID_warning
	@Namespace var sectionID_info
	@Namespace var sectionID_button
	@Namespace var sectionID_legal
	@Namespace var sectionID_cloudBackup

	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(scrollViewProxy: ScrollViewProxy) {
		self.scrollViewProxy = scrollViewProxy
		
		let appDelegate = AppDelegate.get()
		let encryptedNodeId = appDelegate.encryptedNodeId! 
		self.encryptedNodeId = encryptedNodeId
		self.syncSeedManager = appDelegate.syncManager!.syncSeedManager
		
		let manualBackup_taskDone = Prefs.shared.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		self._manualBackup_taskDone = State<Bool>(initialValue: manualBackup_taskDone)
		
		let backupSeed_enabled = Prefs.shared.backupSeed_isEnabled
		self._backupSeed_enabled = State<Bool>(initialValue: backupSeed_enabled)
		
		self._legal_taskDone = State<Bool>(initialValue: manualBackup_taskDone)
		self._legal_lossRisk = State<Bool>(initialValue: manualBackup_taskDone)
	}
	
	var body: some View {
		
		List {
			if !backupSeed_enabled && !(legal_taskDone && legal_lossRisk) {
				section_warning()
					.id(sectionID_warning)
			}
			section_info()
				.id(sectionID_info)
			section_button()
				.id(sectionID_button)
			if !backupSeed_enabled {
				section_legal()
					.id(sectionID_legal)
			}
			CloudBackupSection(
				backupSeed_enabled: $backupSeed_enabled,
				syncState: $syncState,
				syncStateWasEnabled: $syncStateWasEnabled
			)
			.id(sectionID_cloudBackup)
		}
		.listStyle(.insetGrouped)
		.sheet(isPresented: $revealSeed) {
			
			RecoveryPhraseReveal(
				isShowing: $revealSeed,
				mnemonics: $mnemonics
			)
		}
		.navigationBarTitle(
			NSLocalizedString("Recovery Phrase", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onAppear {
			onAppear()
		}
		.onReceive(syncSeedManager.statePublisher) {
			syncStateChanged($0)
		}
	}
	
	@ViewBuilder
	func section_warning() -> some View {
		
		Section {
			Label {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
						Text(
							"""
							You have not backed up your recovery phrase!
							"""
						)
						.font(.callout)
						.bold()
						
						Text(styled: NSLocalizedString(
							"""
							If you do not back it up and you lose access to Phoenix \
							you will **lose your funds**!
							""",
							comment: "BackupView"
						))
						.font(.subheadline)
					} // </VStack>
					Spacer() // ensure label takes up full width
				}// </HStack>
			} icon: {
				Image(systemName: "exclamationmark.circle")
					.renderingMode(.template)
					.imageScale(.large)
					.foregroundColor(Color.appWarn)
			}
			.padding()
			.overlay(
				RoundedRectangle(cornerRadius: 10)
					.strokeBorder(Color.appWarn, lineWidth: 1)
			)
			.listRowBackground(Color.clear)
			.listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
		
		} // </Section>
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			
			VStack(alignment: .leading, spacing: 35) {
				Text(
					"""
					The recovery phrase (sometimes called a seed), is a list of 12 English words. \
					It allows you to recover full access to your funds if needed.
					"""
				)
				
				Text(
					"Only you alone possess this seed. Keep it private."
				)
				.fontWeight(.bold)
				
				Text(styled: NSLocalizedString(
					"""
					**Do not share this seed with anyone.** \
					Beware of phishing. The developers of Phoenix will never ask for your seed.
					""",
					comment: "ManualBackupView"
				))
				
				Text(styled: NSLocalizedString(
					"""
					**Do not lose this seed.** \
					Save it somewhere safe (not on this phone). \
					If you lose your seed and your phone, you've lost your funds.
					""",
					comment: "ManualBackupView"
				))
					
			} // </VStack>
			.padding(.vertical, 15)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					decrypt()
				} label: {
					HStack {
						Image(systemName: "key")
							.imageScale(.medium)
						Text("Display seed")
							.font(.headline)
					}
				}
				.disabled(isDecrypting)
				.padding(.vertical, 5)
				
				let enabledSecurity = AppSecurity.shared.enabledSecurity.value
				if enabledSecurity != .none {
					Text("(requires authentication)")
						.font(.footnote)
						.foregroundColor(.secondary)
						.padding(.top, 5)
						.padding(.bottom, 10)
				}
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_legal() -> some View {
		
		Section(header: Text("Legal")) {
			
			Toggle(isOn: $legal_taskDone) {
				Text(
					"""
					I have saved my recovery phrase somewhere safe.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			.onChange(of: legal_taskDone) { _ in
				legalToggleChanged()
			}
			
			Toggle(isOn: $legal_lossRisk) {
				Text(
					"""
					I understand that if I lose my phone & my recovery phrase, \
					then I will lose the funds in my wallet.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			.onChange(of: legal_lossRisk) { _ in
				legalToggleChanged()
			}
			
		} // </Section>
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.renderingMode(.template)
			.imageScale(.large)
			.foregroundColor(animatingLegalToggleColor ? Color.red : Color.primary)
	}
	
	func onAppear(){
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if deepLinkManager.deepLink == .backup {
				// Reached our destination
				deepLinkManager.broadcast(nil)
			}
			
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
				withAnimation(Animation.linear(duration: 1.0).repeatForever(autoreverses: true)) {
					animatingLegalToggleColor = true
				}
			}
		}
	}
	
	func syncStateChanged(_ newSyncState: SyncSeedManager_State) {
		log.trace("syncStateChanged()")
		
		syncState = newSyncState
		if newSyncState != .disabled {
			syncStateWasEnabled = true
		}
		if newSyncState == .deleting {
			log.debug("newSyncState == .deleting")
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
				scrollViewProxy.scrollTo(sectionID_cloudBackup, anchor: .top)
			}
		}
	}
	
	func decrypt() {
		log.trace("decrypt()")
		
		isDecrypting = true
		
		let Succeed = {(result: [String]) in
			mnemonics = result
			revealSeed = true
			isDecrypting = false
		}
		
		let Fail = {
			isDecrypting = false
		}
		
		let enabledSecurity = AppSecurity.shared.enabledSecurity.value
		if enabledSecurity == .none {
			AppSecurity.shared.tryUnlockWithKeychain { (mnemonics, _, _) in
				
				if let mnemonics = mnemonics {
					Succeed(mnemonics)
				} else {
					Fail()
				}
			}
		} else {
			let prompt = NSLocalizedString("Unlock your seed.", comment: "Biometrics prompt")
			
			AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
				if case .success(let mnemonics) = result {
					Succeed(mnemonics)
				} else {
					Fail()
				}
			}
		}
	}
	
	func legalToggleChanged() {
		log.trace("legalToggleChanged()")
		
		let taskDone = legal_taskDone && legal_lossRisk
		log.debug("taskDone = \(taskDone ? "true" : "false")")
		
		if taskDone != manualBackup_taskDone {
			
			manualBackup_taskDone = taskDone
			Prefs.shared.manualBackup_setTaskDone(taskDone, encryptedNodeId: encryptedNodeId)
		}
	}
}

fileprivate struct CloudBackupSection: View {
	
	@Binding var backupSeed_enabled: Bool
	@Binding var syncState: SyncSeedManager_State
	@Binding var syncStateWasEnabled: Bool
	
	@ViewBuilder
	var body: some View {
		
		Section {
			
			NavigationLink(destination: CloudBackupView(backupSeed_enabled: $backupSeed_enabled)) {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Label("iCloud backup", systemImage: "icloud")
					Spacer()
					if backupSeed_enabled {
						if syncState != .synced {
							Image(systemName: "exclamationmark.triangle")
								.renderingMode(.template)
								.foregroundColor(Color.appWarn)
								.padding(.trailing, 4)
						}
						Image(systemName: "checkmark")
							.foregroundColor(Color.appAccent)
							.font(Font.body.weight(Font.Weight.heavy))
					}
				}
			}
			
			// Implicit divider added here
			
			if backupSeed_enabled || syncState != .disabled || syncStateWasEnabled {
				Group {
					if syncState == .synced {
						status_uploaded()
					} else if syncState != .disabled {
						status_syncState()
					} else {
						status_deleted()
					}
				}
				.padding(.vertical, 10)
			}
		}
	}
	
	@ViewBuilder
	func status_uploaded() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your recovery phrase is stored in iCloud.")
					
					Text(
						"""
						Phoenix can restore your funds automatically.
						"""
					)
					.foregroundColor(Color.gray)
				}
			} icon: {
				Image(systemName: "externaldrive.badge.checkmark")
					.renderingMode(.template)
					.imageScale(.medium)
			}
		}
	}
	
	@ViewBuilder
	func status_deleted() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your recovery phrase was deleted from iCloud.")
				}
			} icon: {
				Image(systemName: "externaldrive.badge.minus")
					.renderingMode(.template)
					.imageScale(.medium)
			}
		}
	}
	
	@ViewBuilder
	func status_syncState() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 20) {
			
			if backupSeed_enabled {
				Label {
					Text("Uploading your recovery phrase to iCloud…")
				} icon: {
					Image(systemName: "externaldrive.badge.plus")
						.renderingMode(.template)
						.imageScale(.medium)
				}
			} else {
				Label {
					Text("Deleting your recovery phrase from iCloud…")
				} icon: {
					Image(systemName: "externaldrive.badge.minus")
						.renderingMode(.template)
						.imageScale(.medium)
				}
			}
			
			if syncState == .uploading {
				Label {
					Text("Sending…")
				} icon: {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				}
				
			} else if syncState == .deleting {
				Label {
					Text("Deleting…")
				} icon: {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				}
				
			} else if case .waiting(let details) = syncState  {
				
				switch details.kind {
				case .forInternet:
					Label {
						Text("Waiting for internet…")
					} icon: {
						ProgressView()
							.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
					}
					
				case .forCloudCredentials:
					Label {
						Text("Please sign into iCloud")
					} icon: {
						Image(systemName: "exclamationmark.triangle.fill")
							.renderingMode(.template)
							.foregroundColor(Color.appWarn)
					}
					
				case .exponentialBackoff(let error):
					SyncErrorDetails(waiting: details, error: error)
					
				} // </switch>
			} // </case .waiting>
		} // </VStack>
	}
}

fileprivate struct SyncErrorDetails: View, ViewName {
	
	let waiting: SyncSeedManager_State_Waiting
	let error: Error
	
	let timer = Timer.publish(every: 0.5, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	@ViewBuilder
	var body: some View {
		
		Label {
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text("Error - retry in:")
				
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
						.frame(width: 20, height: 20, alignment: .center)
					
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
		} icon: {
			Image(systemName: "exclamationmark.triangle.fill")
				.renderingMode(.template)
				.foregroundColor(Color.appWarn)
		}
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

fileprivate struct RecoveryPhraseReveal: View {
	
	@Binding var isShowing: Bool
	@Binding var mnemonics: [String]
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	var body: some View {
		
		ZStack {
			
			// close button
			// (required for landscapse mode, where swipe to dismiss isn't possible)
			VStack {
				HStack {
					Spacer()
					Button {
						close()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
				}
				Spacer()
			}
			.padding()
			
			main
		}
	}
	
	var main: some View {
		
		VStack {
			
			Spacer()
			
			Text("KEEP THIS SEED SAFE.")
				.font(.title2)
				.multilineTextAlignment(.center)
				.padding(.bottom, 2)
			Text("DO NOT SHARE.")
				.multilineTextAlignment(.center)
				.font(.title2)
			
			Spacer()
			
			HStack {
				Spacer()
				
				VStack {
					ForEach(0..<6, id: \.self) { idx in
						Text(verbatim: "#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(0..<6, id: \.self) { idx in
						Text(mnemonic(idx))
							.font(.headline)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 4) // boost spacing a wee bit
				
				Spacer()
				
				VStack {
					ForEach(6..<12, id: \.self) { idx in
						Text(verbatim: "#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(6..<12, id: \.self) { idx in
						Text(mnemonic(idx))
							.font(.headline)
							.padding(.bottom, 2)
					}
				}
				
				Spacer()
			}
			.environment(\.layoutDirection, .leftToRight) // issue #237
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			Spacer()
			Spacer()
			
			Text("BIP39 seed with standard BIP84 derivation path")
				.font(.footnote)
				.foregroundColor(.secondary)
			
		}
		.padding(.top, 20)
		.padding([.leading, .trailing], 30)
		.padding(.bottom, 20)
	}
	
	func close() {
		log.trace("[RecoverySeedReveal] close()")
		isShowing = false
	}
}

class RecoveryPhraseView_Previews: PreviewProvider {
	
	@State static var revealSeed: Bool = true
	@State static var testMnemonics = [
		"witch", "collapse", "practice", "feed", "shame", "open",
		"despair", "creek", "road", "again", "ice", "least"
	]
	
	static var previews: some View {
		
		RecoveryPhraseReveal(isShowing: $revealSeed, mnemonics: $testMnemonics)
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		RecoveryPhraseReveal(isShowing: $revealSeed, mnemonics: $testMnemonics)
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
	}
}
