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
	
	enum NavLinkTag: String, Codable {
		case SetCustomPinView
		case EditCustomPinView
		case DisableCustomPinView
	}
	
	@State var biometricSupport = AppSecurity.shared.deviceBiometricSupport()
	@State var biometricsEnabled: Bool = false
	@State var passcodeFallbackEnabled: Bool = false
	@State var customPinEnabled: Bool = false
	@State var customPinSet: Bool = false
	
	@State var ignoreToggle_biometricsEnabled = false
	@State var ignoreToggle_passcodeFallbackEnabled = false
	@State var ignoreToggle_customPinEnabled = false
	
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
		let enabledSecurity: EnabledSecurity = AppSecurity.shared.enabledSecurityPublisher.value
		
		_biometricsEnabled = State(initialValue: enabledSecurity.contains(.biometrics))
		_passcodeFallbackEnabled = State(initialValue: enabledSecurity.contains(.passcodeFallback))
		_customPinEnabled = State(initialValue: enabledSecurity.contains(.customPin))
		
		_customPinSet = State(initialValue: AppSecurity.shared.hasCustomPin())
		
		if let walletId = Biz.walletId {
			backupSeedStatePublisher = Prefs.shared.backupSeedStatePublisher(walletId)
		} else {
			backupSeedStatePublisher = PassthroughSubject<BackupSeedState, Never>().eraseToAnyPublisher()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("App Access", comment: "Navigation bar title"))
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
			section_biometrics()
			section_pin()
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
	func section_biometrics() -> some View {
		
		Section {
			toggle_biometrics()
			
			// Implicit divider added here
			
			toggle_passcodeFallbackOption()
				.padding(.top, 5)
				.padding(.bottom, 10)
			
		} header: {
			Text("Biometrics")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_pin() -> some View {
		
		Section {
			toggle_customPin()
			
			// Implicit divider added here
			
			button_changePin()
				.padding(.top, 5)
				.padding(.bottom, 10)
			
		} header: {
			Text("PIN")
		} // </Section>
	}
	
	@ViewBuilder
	func toggle_biometrics() -> some View {
		
		ToggleAlignment {
			
			LabelAlignment {
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
				}
				
			} icon: {
				Image(systemName: isTouchID() ? "touchid" : "faceid")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
			
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
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Allow passcode fallback")
					
					Group {
						if isTouchID() {
							Text("If Touch ID fails, you can enter your iOS passcode to access Phoenix.")
								.padding(.top, 8)
						} else {
							Text("If Face ID fails, you can enter your iOS passcode to access Phoenix.")
								.padding(.top, 8)
						}
					}
					.lineLimit(nil)
					.font(.callout)
					.foregroundColor(.secondary)
					
				} // </VStack>
			} icon: {
				Image(systemName: "circle.grid.3x3")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </Label>
			
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
	func toggle_customPin() -> some View {
		
		Toggle(isOn: $customPinEnabled) {
			Label {
				Text("Custom PIN")
			} icon: {
				Image(systemName: "circle.grid.3x3")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
		} // </Toggle>
		.disabled(!isRecoveryPhrasedBackedUp)
		.onChange(of: customPinEnabled) { value in
			self.toggleCustomPin(value)
		}
	}
	
	@ViewBuilder
	func button_changePin() -> some View {
		
		Button {
			changePin()
		} label: {
			Label {
				Text("Change PIN")
			} icon: {
				Image(systemName: "arrow.triangle.2.circlepath")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
		}
		.disabled(!customPinEnabled || !customPinSet || !isRecoveryPhrasedBackedUp)
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
			case .SetCustomPinView     : SetNewPinView(willClose: setNewPinView_willClose)
			case .EditCustomPinView    : EditPinView(willClose: editPinView_willClose)
			case .DisableCustomPinView : DisablePinView(willClose: disablePinView_willClose)
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
		
		log.debug("enabledSecurity = \(AppSecurity.shared.enabledSecurityPublisher.value)")
	}
	
	func onWillEnterForeground() -> Void {
		log.trace("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		self.biometricSupport = AppSecurity.shared.deviceBiometricSupport()
	}
	
	func backupSeedStateChanged(_ newState: BackupSeedState) {
		log.trace("backupSeedStateChanged()")
		
		backupSeedState = newState
	}
	
	func setNewPinView_willClose(_ result: SetNewPinView.EndResult) {
		log.trace("setNewPinView_willClose(\(result))")
		
		switch result {
		case .Failed: fallthrough
		case .UserCancelled:
			ignoreToggle_customPinEnabled = true
			customPinEnabled = false
		
		case .PinSet:
			customPinSet = true
		}
	}
	
	func editPinView_willClose(_ result: EditPinView.EndResult) {
		log.trace("editPinView_willClose()")
		
		// Nothing to do here (UI remains the same)
	}
	
	func disablePinView_willClose(_ result: DisablePinView.EndResult) {
		log.trace("disablePinView_willClose(\(result))")
		
		switch result {
		case .Failed: fallthrough
		case .UserCancelled:
			ignoreToggle_customPinEnabled = true
			customPinEnabled = true
		
		case .PinDisabled:
			customPinSet = false
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
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
			
			AppSecurity.shared.setSoftBiometrics(enabled: true) { (error: Error?) in
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
			
			AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
				
				switch result {
				case .success(_):
					AppSecurity.shared.setSoftBiometrics(enabled: false) { (error: Error?) in
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
		
		if flag && customPinEnabled {
			
			// User is trying to enable "system passcode fallback",
			// but they already have the "custom pin" enabled.
			
			smartModalState.display(dismissable: true) {
				WhichPinSheet(currentChoice: .customPin)
			} onWillDisappear: {
				failedToEnable()
			}
			
		} else if flag { // toggle => ON
			
			AppSecurity.shared.setPasscodeFallback(enabled: true) { (error: Error?) in
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
			
			let prompt = NSLocalizedString("Authenticate to disable passcode fallback.", comment: "User prompt")
			
			AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
				
				switch result {
				case .failure(_):
					failedToDisable()
				case .success(_):
					AppSecurity.shared.setPasscodeFallback(enabled: false) { (error: Error?) in
						if error != nil {
							failedToDisable()
						}
					}
				} // </switch>
			} // </tryUnlockWithBiometrics>
		}
	}
	
	func toggleCustomPin(_ flag: Bool) {
		log.trace("toggleCustomPin()")
		
		if ignoreToggle_customPinEnabled {
			ignoreToggle_customPinEnabled = false
			return
		}
		
		if flag && passcodeFallbackEnabled {
			
			// User is trying to enable "system passcode fallback",
			// but they already have the "custom pin" enabled.
			
			smartModalState.display(dismissable: true) {
				WhichPinSheet(currentChoice: .systemPasscode)
				
			} onWillDisappear: {
				ignoreToggle_customPinEnabled = true
				customPinEnabled = false
			}
			
		} else if flag { // toggle => ON
			navigateTo(.SetCustomPinView)
			
		} else { // toggle => OFF
			navigateTo(.DisableCustomPinView)
		}
	}
	
	func changePin() {
		log.trace("changePin()")
		
		navigateTo(.EditCustomPinView)
	}
}

