import Foundation
import Combine
import SwiftUI
import PhoenixShared

fileprivate let filename = "AppAccessView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct AppAccessView : View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case SetPinView(type: PinType)
		case EditPinView(type: PinType)
		case DisablePinView(type: PinType)
		
		var description: String {
			switch self {
				case .SetPinView(let type)     : return "SetPinView(\(type))"
				case .EditPinView(let type)    : return "EditPinView(\(type))"
				case .DisablePinView(let type) : return "DisablePinView(\(type))"
			}
		}
	}
	
	@State var biometricSupport = DeviceInfo.biometricSupport()
	@State var biometricsEnabled: Bool = false
	@State var passcodeFallbackEnabled: Bool = false
	@State var lockPinEnabled: Bool = false
	@State var lockPinSet: Bool = false
	@State var spendingPinEnabled: Bool = false
	@State var spendingPinSet: Bool = false
	
	@State var ignoreToggle_biometricsEnabled = false
	@State var ignoreToggle_passcodeFallbackEnabled = false
	@State var ignoreToggle_lockPinEnabled = false
	@State var ignoreToggle_spendingPinEnabled = false
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init() {
		let enabledSecurity: EnabledSecurity = Keychain.current.enabledSecurity
		
		_biometricsEnabled = State(initialValue: enabledSecurity.contains(.biometrics))
		_passcodeFallbackEnabled = State(initialValue: enabledSecurity.contains(.passcodeFallback))
		_lockPinEnabled = State(initialValue: enabledSecurity.contains(.lockPin))
		_lockPinSet = State(initialValue: enabledSecurity.contains(.lockPin))
		_spendingPinEnabled = State(initialValue: enabledSecurity.contains(.spendingPin))
		_spendingPinSet = State(initialValue: enabledSecurity.contains(.spendingPin))
		
		backupSeedStatePublisher = Prefs.current.backupSeed.statePublisher()
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(String(localized: "App Access", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
		}
		.onAppear {
			onAppear()
		}
		.onReceive(willEnterForegroundPublisher) { _ in
			onWillEnterForeground()
		}
		.onReceive(backupSeedStatePublisher) {(state: BackupSeedState) in
			backupSeedStateChanged(state)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if !isRecoveryPhrasedBackedUp {
				section_warning()
			}
			section_openingTheApp()
			section_spendingControl()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
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
							Please perform a backup before enabling any options here. \
							If you lock yourself out of Phoenix without a backup of your \
							recovery phrase, you will **lose your funds**!
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
	}
	
	@ViewBuilder
	func section_openingTheApp() -> some View {
		
		Section {
			toggle_biometrics()
			toggle_passcodeFallbackOption()
			toggle_lockPin()
			button_changeLockPin()
			
		} header: {
			Text("Opening the app")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_spendingControl() -> some View {
		
		Section {
			toggle_spendingPin()
			button_changeSpendingPin()
			
		} header: {
			Text("Spending Control")
			
		} // </Section>
	}
	
	@ViewBuilder
	func toggle_biometrics() -> some View {
		
		ToggleAlignment {
			
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					
					switch biometricSupport {
					case .touchID_available:
						Text("Touch ID")
						
					case .touchID_notAvailable:
						Text("Touch ID") + Text(" (not available)").foregroundColor(.secondary)
						
					case .touchID_notEnrolled:
						Text("Touch ID") + Text(" (not enrolled)").foregroundColor(.secondary)
						
					case .faceID_available:
						Text("Face ID")
						
					case .faceID_notAvailable:
						Text("Face ID") + Text(" (not available)").foregroundColor(.secondary)
						
					case .faceID_notEnrolled:
						Text("Face ID") + Text(" (not enrolled)").foregroundColor(.secondary)
						
					default:
						Text("Biometrics") + Text(" (not available)").foregroundColor(.secondary)
					} // </switch>
					
					Text("Pass iOS biometrics to open the app.")
						.lineLimit(nil)
						.font(.callout)
						.foregroundColor(.secondary)
					
				} // </VStack>
				
			} icon: {
				Image(systemName: isTouchID() ? "touchid" : "faceid")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $biometricsEnabled)
				.labelsHidden()
				.disabled(!biometricSupport.isAvailable() || !isRecoveryPhrasedBackedUp)
				.onChange(of: biometricsEnabled) { value in
					self.toggleBiometrics(value)
				}
			
		} // </ToggleAlignment>
	}
	
	@ViewBuilder
	func toggle_passcodeFallbackOption() -> some View {
		
		ToggleAlignment {
			
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Allow passcode fallback")
					
					Group {
						if isTouchID() {
							Text("If Touch ID fails, you can enter your iOS passcode to open the app.")
						} else {
							Text("If Face ID fails, you can enter your iOS passcode to open the app.")
						}
					}
					.lineLimit(nil)
					.font(.callout)
					.foregroundColor(.secondary)
					
				} // </VStack>
			} icon: {
				Image(systemName: "keyboard")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $passcodeFallbackEnabled)
				.labelsHidden()
				.disabled(!biometricsEnabled || !isRecoveryPhrasedBackedUp)
				.onChange(of: passcodeFallbackEnabled) { value in
					self.togglePasscodeFallback(value)
				}
			
		} // </ToggleAlignment>
	}
	
	@ViewBuilder
	func toggle_lockPin() -> some View {
		
		ToggleAlignment {
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Lock PIN")
					Text("Enter custom PIN to open the app.")
						.lineLimit(nil)
						.font(.callout)
						.foregroundColor(.secondary)
				}
			} icon: {
				Image(systemName: "circle.grid.3x3")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
		} toggle: {
			Toggle("", isOn: $lockPinEnabled)
				.labelsHidden()
				.disabled(!isRecoveryPhrasedBackedUp)
				.onChange(of: lockPinEnabled) { value in
					self.toggleLockPin(value)
				}
		} // </ToggleAlignment>
	}
	
	@ViewBuilder
	func button_changeLockPin() -> some View {
		
		Button {
			changeLockPin()
		} label: {
			Label {
				Text("Change lock PIN")
			} icon: {
				Image(systemName: "arrow.triangle.2.circlepath")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
		}
		.disabled(!lockPinEnabled || !lockPinSet || !isRecoveryPhrasedBackedUp)
	}
	
	@ViewBuilder
	func toggle_spendingPin() -> some View {
		
		ToggleAlignment {
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Spending PIN")
					Text("Enter a PIN code to be able to spend funds.")
						.lineLimit(nil)
						.font(.callout)
						.foregroundColor(.secondary)
				}
			} icon: {
				Image(systemName: "circle.grid.3x3")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
		} toggle: {
			Toggle("", isOn: $spendingPinEnabled)
				.labelsHidden()
				.disabled(!isRecoveryPhrasedBackedUp)
				.onChange(of: spendingPinEnabled) { value in
					self.toggleSpendingPin(value)
				}
		} // </ToggleAlignment>
	}
	
	@ViewBuilder
	func button_changeSpendingPin() -> some View {
		
		Button {
			changeSpendingPin()
		} label: {
			LabelAlignment {
				Text("Change spending PIN")
			} icon: {
				Image(systemName: "arrow.triangle.2.circlepath")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
		} // </Button>
		.disabled(!spendingPinEnabled || !spendingPinSet || !isRecoveryPhrasedBackedUp)
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
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .SetPinView(let type):
			SetNewPinView(type: type, willClose: setNewPinView_willClose)
			
		case .EditPinView(let type):
			EditPinView(type: type, willClose: editPinView_willClose)
			
		case .DisablePinView(let type):
			DisablePinView(type: type, willClose: disablePinView_willClose)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	private func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	func isTouchID() -> Bool {
		
		// We're using the same logic here as in ConfigurationView.
		// That is, we shouldn't show a TouchID symbol here if we're
		// showing a FaceID symbol in the other view.
		
		switch biometricSupport {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : return true
			default                    : return false
		}
	}
	
	var isRecoveryPhrasedBackedUp: Bool {
		
		return backupSeedState == .safelyBackedUp
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		log.debug("enabledSecurity = \(Keychain.current.enabledSecurity)")
	}
	
	func onWillEnterForeground() -> Void {
		log.trace("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		self.biometricSupport = DeviceInfo.biometricSupport()
	}
	
	func backupSeedStateChanged(_ newState: BackupSeedState) {
		log.trace("backupSeedStateChanged()")
		
		backupSeedState = newState
	}
	
	func setNewPinView_willClose(_ type: PinType, _ result: SetNewPinView.EndResult) {
		log.trace("setNewPinView_willClose(\(type), \(result))")
		
		switch type {
		case .lockPin:
			switch result {
			case .Failed: fallthrough
			case .UserCancelled:
				ignoreToggle_lockPinEnabled = true
				lockPinEnabled = false
			
			case .PinSet:
				lockPinSet = true
			}
			
		case .spendingPin:
			switch result {
			case .Failed: fallthrough
			case .UserCancelled:
				ignoreToggle_spendingPinEnabled = true
				spendingPinEnabled = false
			
			case .PinSet:
				spendingPinSet = true
			}
		}
	}
	
	func editPinView_willClose(_ type: PinType, _ result: EditPinView.EndResult) {
		log.trace("editPinView_willClose(\(type), \(result))")
		
		// Nothing to do here (UI remains the same)
	}
	
	func disablePinView_willClose(_ type: PinType, _ result: DisablePinView.EndResult) {
		log.trace("disablePinView_willClose(\(type), \(result))")
		
		switch type {
		case .lockPin:
			switch result {
			case .Failed: fallthrough
			case .UserCancelled:
				ignoreToggle_lockPinEnabled = true
				lockPinEnabled = true
			
			case .PinDisabled:
				lockPinSet = false
			}
			
		case .spendingPin:
			switch result {
			case .Failed: fallthrough
			case .UserCancelled:
				ignoreToggle_spendingPinEnabled = true
				spendingPinEnabled = true
			
			case .PinDisabled:
				spendingPinSet = false
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func toggleBiometrics(_ flag: Bool) {
		log.trace("toggleBiometrics()")
		
		if ignoreToggle_biometricsEnabled {
			ignoreToggle_biometricsEnabled = false
			return
		}
		
		if flag { // toggle => ON
			
			Keychain.current.setSoftBiometrics(enabled: true) { (error: Error?) in
				if error != nil {
					self.ignoreToggle_biometricsEnabled = true
					self.biometricsEnabled = false // failed to enable == disabled
				}
			}
			
		} else { // toggle => OFF
			
			// User just disabled biometrics switch.
			// What should occur within the UI:
			// - user is prompted for biometrics verification
			// - if SUCCESS:
			//   - biometrics switch remains disabled
			// - if FAILURE:
			//   - biometrics switch changes back to enabled
			
			let failedToDisable = {
				self.ignoreToggle_biometricsEnabled = true
				self.biometricsEnabled = true // failed to disable == enabled
			}
			
			let prompt = NSLocalizedString("Authenticate to disable biometrics.", comment: "User prompt")
			
			Keychain.current.unlockWithBiometrics(prompt: prompt) { result in
				
				switch result {
				case .success(_):
					Keychain.current.setSoftBiometrics(enabled: false) { (error: Error?) in
						if error != nil {
							failedToDisable()
						}
					}
					
				case .failure(_):
					failedToDisable()
				}
			}
		}
	}
	
	func togglePasscodeFallback(_ flag: Bool) {
		log.trace("togglePasscodeFallback()")
		
		if ignoreToggle_passcodeFallbackEnabled {
			ignoreToggle_passcodeFallbackEnabled = false
			return
		}
		
		let failedToEnable = {
			log.trace("togglePasscodeFallback: failedToEnable")
			
			self.ignoreToggle_passcodeFallbackEnabled = true
			self.passcodeFallbackEnabled = false // failed to enable == disabled
		}
		
		let failedToDisable = {
			log.trace("togglePasscodeFallback: failedToDisable")
			
			self.ignoreToggle_passcodeFallbackEnabled = true
			self.passcodeFallbackEnabled = true // failed to disable == enabled
		}
		
		if flag && lockPinEnabled {
			
			// User is trying to enable "system passcode fallback",
			// but they already have the "lock pin" enabled.
			
			smartModalState.display(dismissable: true) {
				WhichPinSheet(currentChoice: .customPin)
			} onWillDisappear: {
				failedToEnable()
			}
			
		} else if flag { // toggle => ON
			
			Keychain.current.setPasscodeFallback(enabled: true) { (error: Error?) in
				if error != nil {
					failedToEnable()
				}
			}
			
		} else { // toggle => OFF
			
			// What should occur within the UI:
			// - user is prompted for biometrics verification
			// - if SUCCESS:
			//   - switch remains disabled
			// - if FAILURE:
			//   - switch changes back to enabled
			
			let prompt = String(localized: "Authenticate to disable passcode fallback.", comment: "User prompt")
			
			Keychain.current.unlockWithBiometrics(prompt: prompt) { result in
				
				switch result {
				case .failure(_):
					failedToDisable()
				case .success(_):
					Keychain.current.setPasscodeFallback(enabled: false) { (error: Error?) in
						if error != nil {
							failedToDisable()
						}
					}
				} // </switch>
			} // </tryUnlockWithBiometrics>
		}
	}
	
	func toggleLockPin(_ flag: Bool) {
		log.trace("toggleLockPin()")
		
		if ignoreToggle_lockPinEnabled {
			ignoreToggle_lockPinEnabled = false
			return
		}
		
		if flag && passcodeFallbackEnabled {
			
			// User is trying to enable "system passcode fallback",
			// but they already have the "lock pin" enabled.
			
			smartModalState.display(dismissable: true) {
				WhichPinSheet(currentChoice: .systemPasscode)
				
			} onWillDisappear: {
				ignoreToggle_lockPinEnabled = true
				lockPinEnabled = false
			}
			
		} else if flag { // toggle => ON
			navigateTo(.SetPinView(type: .lockPin))
			
		} else { // toggle => OFF
			navigateTo(.DisablePinView(type: .lockPin))
		}
	}
	
	func changeLockPin() {
		log.trace("changeLockPin()")
		
		navigateTo(.EditPinView(type: .lockPin))
	}
	
	func toggleSpendingPin(_ flag: Bool) {
		log.trace("toggleSpendingPin()")
		
		if ignoreToggle_spendingPinEnabled {
			ignoreToggle_spendingPinEnabled = false
			return
		}
		
		if flag { // toggle => ON
			navigateTo(.SetPinView(type: .spendingPin))
			
		} else { // toggle => OFF
			navigateTo(.DisablePinView(type: .spendingPin))
		}
	}
	
	func changeSpendingPin() {
		log.trace("changeSpendingPin()")
		
		navigateTo(.EditPinView(type: .spendingPin))
	}
}

