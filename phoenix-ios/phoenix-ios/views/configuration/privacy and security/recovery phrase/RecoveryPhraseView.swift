import SwiftUI
import CloudKit
import CircularCheckmarkProgress
import PhoenixShared

fileprivate let filename = "RecoveryPhraseView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
	
	enum NavLinkTag: String, Codable {
		case CloudBackup
	}
	
	let scrollViewProxy: ScrollViewProxy
	let walletId: WalletIdentifier
	
	@State var manualBackup_taskDone: Bool
	@State var backupSeed_enabled: Bool
	
	let syncSeedManager: SyncSeedManager
	@State var syncState: SyncSeedManager_State = .disabled
	@State var syncStateWasEnabled = false
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var recoveryPhrase: RecoveryPhrase? = nil
	
	@State var legal_taskDone: Bool
	@State var legal_lossRisk: Bool
	@State var animatingLegalToggleColor = false
	
	@State var didAppear = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@Namespace var sectionID_warning
	@Namespace var sectionID_info
	@Namespace var sectionID_button
	@Namespace var sectionID_legal
	@Namespace var sectionID_cloudBackup

	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var smartModalState: SmartModalState
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(scrollViewProxy: ScrollViewProxy) {
		self.scrollViewProxy = scrollViewProxy
		
		let walletId = Biz.walletId!
		self.walletId = walletId
		self.syncSeedManager = Biz.syncManager!.syncSeedManager
		
		let manualBackup_taskDone = Prefs.current.backupSeed.manualBackupDone
		self._manualBackup_taskDone = State<Bool>(initialValue: manualBackup_taskDone)
		
		let backupSeed_enabled = Prefs.current.backupSeed.isEnabled
		self._backupSeed_enabled = State<Bool>(initialValue: backupSeed_enabled)
		
		self._legal_taskDone = State<Bool>(initialValue: manualBackup_taskDone)
		self._legal_lossRisk = State<Bool>(initialValue: manualBackup_taskDone)
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Recovery Phrase", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if !backupSeed_enabled && !(legal_taskDone && legal_lossRisk) {
				section_warning()
			}
			section_info()
			section_button()
			if !backupSeed_enabled {
				section_legal()
			}
			section_cloudBackup()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.sheet(isPresented: $revealSeed) {
			
			if let recoveryPhrase = recoveryPhrase {
				RecoveryPhraseReveal(
					isShowing: $revealSeed,
					recoveryPhrase: recoveryPhrase
				)
			} else {
				EmptyView()
			}
		}
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
						
						Text(
							"""
							If you do not back it up and you lose access to Phoenix \
							you will **lose your funds**!
							"""
						)
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
		.id(sectionID_warning)
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			
			VStack(alignment: .leading, spacing: 35) {
				Text(
					"""
					The recovery phrase (sometimes called a seed), is a list of 12 words. \
					It allows you to recover full access to your funds if needed.
					"""
				)
				
				Text(
					"Only you alone possess this seed. Keep it private."
				)
				.fontWeight(.bold)
				
				Text(
					"""
					**Do not share this seed with anyone.** \
					Beware of phishing. The developers of Phoenix will never ask for your seed.
					"""
				)
				
				Text(
					"""
					**Do not lose this seed.** \
					Save it somewhere safe (not on this phone). \
					If you lose your seed and your phone, you've lost your funds.
					"""
				)
					
			} // </VStack>
			.padding(.vertical, 15)
			
		} // </Section>
		.id(sectionID_info)
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
				
				let enabledSecurity = Keychain.current.enabledSecurity
				if enabledSecurity.hasAppLock() || enabledSecurity.hasSpendingPin() {
					Text("(requires authentication)")
						.font(.footnote)
						.foregroundColor(.secondary)
						.padding(.top, 5)
						.padding(.bottom, 10)
				}
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
		.id(sectionID_button)
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
		.id(sectionID_legal)
	}
	
	@ViewBuilder
	func section_cloudBackup() -> some View {
		
		Section {
			
			navLink(.CloudBackup) {
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
						cloudBackup_status_uploaded()
					} else if syncState != .disabled {
						cloudBackup_status_syncState()
					} else {
						cloudBackup_status_deleted()
					}
				}
				.padding(.vertical, 10)
			}
		} // </Section>
		.id(sectionID_cloudBackup)
	}
	
	@ViewBuilder
	func cloudBackup_status_uploaded() -> some View {
		
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
	func cloudBackup_status_deleted() -> some View {
		
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
	func cloudBackup_status_syncState() -> some View {
		
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
	
	@ViewBuilder
	func navLink<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .CloudBackup:
			CloudBackupView(backupSeed_enabled: $backupSeed_enabled)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink, deepLink == .backup {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
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
	
	func legalToggleChanged() {
		log.trace("legalToggleChanged()")
		
		let taskDone = legal_taskDone && legal_lossRisk
		log.debug("taskDone = \(taskDone ? "true" : "false")")
		
		if taskDone != manualBackup_taskDone {
			
			manualBackup_taskDone = taskDone
			Prefs.current.backupSeed.manualBackupDone = taskDone
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func decrypt() {
		log.trace("decrypt()")
		
		isDecrypting = true
		
		let Succeed = {(result: RecoveryPhrase) in
			recoveryPhrase = result
			revealSeed = true
			isDecrypting = false
		}
		
		let Fail = {
			isDecrypting = false
		}
		
		let AuthWithPin = { (type: PinType) in
			smartModalState.display(dismissable: false) {
				AuthenticateWithPinSheet(type: type) { result in
					switch result {
					case .Authenticated:
						Keychain.current.unlockWithKeychain { result, _ in
							if case .success(let recoveryPhrase) = result, let recoveryPhrase {
								Succeed(recoveryPhrase)
							} else {
								Fail()
							}
						}
					case .UserCancelled:
						Fail()
					case .Failed:
						Fail()
					}
				}
			}
		}
		
		let enabledSecurity = Keychain.current.enabledSecurity
		if enabledSecurity == .none {
			Keychain.current.unlockWithKeychain { result, _ in
				if case .success(let recoveryPhrase) = result, let recoveryPhrase {
					Succeed(recoveryPhrase)
				} else {
					Fail()
				}
			}
			
		} else if enabledSecurity.contains(.spendingPin) {
			
			// The spendingPin takes precedence over the various locking mechanisms.
			// For example:
			// - for "opening the app", the user has enabled:
			//   - Face ID
			//   - Lock PIN
			// - for "spending control", the user has enabled:
			//   - Spending PIN
			//
			// Thus, regular employees (with access to lock PIN) should NOT
			// be allowed to display the seed (which would allow them to spend
			// the funds from another wallet).
			
			AuthWithPin(.spendingPin)
			
		} else if enabledSecurity.contains(.biometrics) {
			let prompt = String(localized: "Unlock your seed.", comment: "Biometrics prompt")
			
			Keychain.current.unlockWithBiometrics(prompt: prompt) { result in
				
				if case .success(let recoveryPhrase) = result {
					Succeed(recoveryPhrase)
				} else if enabledSecurity.contains(.lockPin) { // lock pin fallback
					AuthWithPin(.lockPin)
				} else {
					Fail()
				}
			}
		} else if enabledSecurity.contains(.lockPin) {
			AuthWithPin(.lockPin)
			
		} else {
			log.error("Unhandled security configuration")
			Fail()
		}
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
	
	let recoveryPhrase: RecoveryPhrase
	let language: MnemonicLanguage
	
	@State var truncationDetected: Bool = false
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	init(isShowing: Binding<Bool>, recoveryPhrase: RecoveryPhrase) {
		self._isShowing = isShowing
		self.recoveryPhrase = recoveryPhrase
		self.language = recoveryPhrase.language ?? MnemonicLanguage.english
	}
	
	func mnemonic(_ idx: Int) -> String {
		let mnemonics = recoveryPhrase.mnemonicsArray
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			GeometryReader { geometry in
				ScrollView(.vertical) {
					content()
						.frame(width: geometry.size.width)
						.frame(minHeight: geometry.size.height)
				}
			}
			
			header()
			toast.view()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		VStack {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text(verbatim: "\(language.flag) \(language.displayName)")
					.font(.callout)
					.foregroundColor(.secondary)
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack {
			
			Spacer()
			
			Text("KEEP THIS SEED SAFE.")
				.font(.title2)
				.lineLimit(nil)
				.multilineTextAlignment(.center)
				.fixedSize(horizontal: false, vertical: true)
				.padding(.bottom, 2)
			Text("DO NOT SHARE.")
				.font(.title2)
				.lineLimit(nil)
				.multilineTextAlignment(.center)
				.fixedSize(horizontal: false, vertical: true)
			
			Spacer()
			
			Group {
				if truncationDetected {
					singleColumnLayout()
				} else {
					twoColumnLayout()
				}
			}
			.environment(\.layoutDirection, .leftToRight) // issue #237
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			Spacer()
			Spacer()
			
			copyButton()
				.padding(.bottom, 6)

			Text("BIP39 seed with standard BIP84 derivation path")
				.font(.footnote)
				.foregroundColor(.secondary)
				.fixedSize(horizontal: false, vertical: true)
			
		}
		.padding(.top, 20)
		.padding([.leading, .trailing], 30)
		.padding(.bottom, 20)
	}
	
	@ViewBuilder
	func twoColumnLayout() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			
			VStack {
				ForEach(0..<6, id: \.self) { idx in
					Text(verbatim: "#\(idx + 1) ")
						.font(.headline)
						.lineLimit(1)
						.foregroundColor(.secondary)
						.padding(.bottom, 2)
				}
			}
			.padding(.trailing, 2)
			
			VStack(alignment: .leading) {
				ForEach(0..<6, id: \.self) { idx in
					TruncatableView(
						fixedHorizontal: true,
						fixedVertical: true
					) {
						Text(mnemonic(idx))
							.font(.headline)
							.lineLimit(1)
					} wasTruncated: {
						self.wasTruncated(visibleIdx: idx+1)
					}
					.padding(.bottom, 2)
				} // </ForEach>
			} // </VStack>
			.padding(.trailing, 4) // boost spacing a wee bit
			
			Spacer()
			
			VStack {
				ForEach(6..<12, id: \.self) { idx in
					Text(verbatim: "#\(idx + 1) ")
						.font(.headline)
						.lineLimit(1)
						.foregroundColor(.secondary)
						.padding(.bottom, 2)
				}
			}
			.padding(.trailing, 2)
			
			VStack(alignment: .leading) {
				ForEach(6..<12, id: \.self) { idx in
					TruncatableView(
						fixedHorizontal: true,
						fixedVertical: true
					) {
						Text(mnemonic(idx))
							.font(.headline)
							.lineLimit(1)
					} wasTruncated: {
						self.wasTruncated(visibleIdx: idx+1)
					}
					.padding(.bottom, 2)
				} // </ForEach>
			} // </VStack>
			
			Spacer()
		}
	}
	
	@ViewBuilder
	func singleColumnLayout() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0..<12, id: \.self) { idx in
					Text(verbatim: "#\(idx + 1) ")
						.font(.headline)
						.foregroundColor(.secondary)
						.padding(.bottom, 2)
				} // </ForEach>
			} // </VStack>
			.padding(.trailing, 2)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0..<12, id: \.self) { idx in
					Text(mnemonic(idx))
						.font(.headline)
						.padding(.bottom, 2)
				} // </ForEach>
			} // </VStack>
			
		} // </HStack>
	}
	
	@ViewBuilder
	func copyButton() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
				
			Button {
				copyRecoveryPhrase()
			} label: {
				Text("Copy").font(.title3)
			}
			
			Spacer()
		} // </HStack>
	}

	
	func wasTruncated(visibleIdx: Int) {
		log.trace("[RecoverySeedReveal] wasTruncated(#: \(visibleIdx))")
		
		DispatchQueue.main.async {
			truncationDetected = true
		}
	}
	
	func copyRecoveryPhrase() {
		log.trace("[RecoverySeedReveal] copyRecoveryPhrase()")
		
		copy(recoveryPhrase.mnemonics)
	}
	
	private func copy(_ string: String) {
		log.trace("[RecoverySeedReveal] copy()")
		
		UIPasteboard.general.string = string
		AppDelegate.get().clearPasteboardOnReturnToApp = true
		toast.pop(
			"Pasteboard will be cleared when you return to Phoenix.",
			colorScheme: colorScheme.opposite,
			duration: 4.0 // seconds
		)
	}
	
	func close() {
		log.trace("[RecoverySeedReveal] close()")
		isShowing = false
	}
}
