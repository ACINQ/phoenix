import Foundation
import Combine
import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppAccessView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct AppAccessView : View {
	
	@State var biometricSupport = AppSecurity.shared.deviceBiometricSupport()
	@State var biometricsEnabled: Bool
	@State var passcodeFallbackEnabled: Bool
	
	@State var ignoreToggle_biometricsEnabled = false
	@State var ignoreToggle_passcodeFallbackEnabled = false
	
	@Environment(\.colorScheme) var colorScheme
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	init() {
		let enabledSecurity: EnabledSecurity = AppSecurity.shared.enabledSecurityPublisher.value
		
		_biometricsEnabled = State(initialValue: enabledSecurity.contains(.biometrics))
		_passcodeFallbackEnabled = State(initialValue: enabledSecurity.contains(.passcodeFallback))
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("App Access", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_primary()
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
	func section_primary() -> some View {
		
		Section {
			toggle_biometrics()
			
			// Implicit divider added here
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				statusLabel()
					.padding(.top, 5)
				
				if biometricsEnabled {
					passcodeFallbackOption()
						.padding(.top, 30)
				}
				
			} // </VStack>
			.padding(.vertical, 10)
			
		} // </Section>
	}
	
	@ViewBuilder
	func toggle_biometrics() -> some View {
		
		Toggle(isOn: $biometricsEnabled) {
			Label {
				switch biometricSupport {
				case .touchID_available:
					Text("Require Touch ID")
					
				case .touchID_notAvailable:
					Text("Require Touch ID") + Text(" (not available)").foregroundColor(.secondary)
				
				case .touchID_notEnrolled:
					Text("Require Touch ID") + Text(" (not enrolled)").foregroundColor(.secondary)
				
				case .faceID_available:
					Text("Require Face ID")
				
				case .faceID_notAvailable:
					Text("Require Face ID") + Text(" (not available)").foregroundColor(.secondary)
				
				case .faceID_notEnrolled:
					Text("Require Face ID") + Text(" (not enrolled)").foregroundColor(.secondary)
				
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
	func statusLabel() -> some View {
		
		if biometricsEnabled {
			
			Label {
				Text("Access to Phoenix is protected")
					.fixedSize(horizontal: false, vertical: true) // SwiftUI truncating text
			} icon: {
				Image(systemName: "checkmark.shield")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
			
		} else {
		
			Label {
				Text(
					"""
					Phoenix can be accessed without credentials. \
					Make sure that you have enabled adequate protections for iOS.
					"""
				)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncating text
			} icon: {
				Image(systemName: "exclamationmark.triangle")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appWarn)
			}
		}
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
				.padding(.trailing, 2)
				.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
					d[VerticalAlignment.center]
				}
				.onChange(of: passcodeFallbackEnabled) { value in
					self.togglePasscodeFallback(value)
				}
			
		} // </HStack>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
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
		print("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		self.biometricSupport = AppSecurity.shared.deviceBiometricSupport()
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
		
		if flag { // toggle => ON
			
			AppSecurity.shared.setPasscodeFallback(enabled: true) { (error: Error?) in
				if error != nil {
					self.ignoreToggle_passcodeFallbackEnabled = true
					self.passcodeFallbackEnabled = false // failed to enable == disabled
				}
			}
			
		} else { // toggle => OFF
			
			AppSecurity.shared.setPasscodeFallback(enabled: false) { (error: Error?) in
				if error != nil {
					self.ignoreToggle_passcodeFallbackEnabled = true
					self.passcodeFallbackEnabled = true // failed to disable == enabled
				}
			}
		}
	}
}

