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

fileprivate enum NavLinkTag: String {
	case SetCustomPinView
	case EditCustomPinView
	case DisableCustomPinView
}

struct AppAccessView : View {
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var biometricSupport = AppSecurity.shared.deviceBiometricSupport()
	@State var biometricsEnabled: Bool = false
	@State var passcodeFallbackEnabled: Bool = false
	@State var customPinEnabled: Bool = false
	@State var customPinSet: Bool = false
	
	@State var ignoreToggle_biometricsEnabled = false
	@State var ignoreToggle_passcodeFallbackEnabled = false
	@State var ignoreToggle_customPinEnabled = false
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	init() {
		let enabledSecurity: EnabledSecurity = AppSecurity.shared.enabledSecurityPublisher.value
		
		_biometricsEnabled = State(initialValue: enabledSecurity.contains(.biometrics))
		_passcodeFallbackEnabled = State(initialValue: enabledSecurity.contains(.passcodeFallback))
		_customPinEnabled = State(initialValue: enabledSecurity.contains(.customPin))
		
		_customPinSet = State(initialValue: AppSecurity.shared.hasCustomPin())
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("App Access", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			
			if #unavailable(iOS 16.0) {
				// iOS 14 & 15 have bugs when using NavigationLink.
				// The suggested workarounds include using only a single NavigationLink.
				NavigationLink(
					destination: navLinkView(),
					isActive: navLinkTagBinding()
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			content()
			
		} // </ZStack>
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // For iOS 16+
			navLinkView()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_biometrics()
			section_pin()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(willEnterForegroundPublisher) { _ in
			onWillEnterForeground()
		}
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func section_biometrics() -> some View {
		
		Section {
			toggle_biometrics()
			
			// Implicit divider added here
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				passcodeFallbackOption()
				
			} // </VStack>
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
		
		Toggle(isOn: $biometricsEnabled) {
			Label {
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
			
		} // </Toggle>
		.onChange(of: biometricsEnabled) { value in
			self.toggleBiometrics(value)
		}
		.disabled(!biometricSupport.isAvailable())
	}
	
	@ViewBuilder
	func passcodeFallbackOption() -> some View {
		
		HStack(alignment: VerticalAlignment.centerTopLine) { // <- Custom VerticalAlignment
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Allow passcode fallback")
						.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
							d[VerticalAlignment.center]
						}
					
					Group {
						if isTouchID() {
							Text("If Touch ID fails, you can enter your iOS passcode to access Phoenix.")
								.padding(.top, 8)
								.padding(.bottom, 16)
							Text("This is less secure, but more durable. Touch ID can stop working due to hardware damage.")
						} else {
							Text("If Face ID fails, you can enter your iOS passcode to access Phoenix.")
								.padding(.top, 8)
								.padding(.bottom, 16)
							Text("This is less secure, but more durable. Face ID can stop working due to hardware damage.")
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
			
			Spacer()
			
			Toggle("", isOn: $passcodeFallbackEnabled)
				.labelsHidden()
				.disabled(!biometricsEnabled)
				.padding(.trailing, 2)
				.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
					d[VerticalAlignment.center]
				}
				.onChange(of: passcodeFallbackEnabled) { value in
					self.togglePasscodeFallback(value)
				}
			
		} // </HStack>
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
		.disabled(!customPinEnabled || !customPinSet)
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
			navLinkTag = .SetCustomPinView
			
		} else { // toggle => OFF
			navLinkTag = .DisableCustomPinView
		}
	}
	
	func changePin() {
		log.trace("changePin()")
		
		navLinkTag = .EditCustomPinView
	}
}

