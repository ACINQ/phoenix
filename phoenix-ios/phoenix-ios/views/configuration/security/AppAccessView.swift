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
	@State var biometricsEnabled = AppSecurity.shared.enabledSecurityPublisher.value.contains(.biometrics)
	
	@State var ignoreToggle_biometricsEnabled = false
	
	@Environment(\.colorScheme) var colorScheme
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("App Access", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			Section {
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
				
				// Implicit divider added here
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					securityLabel()
						.padding(.top, 5)
					
					receiveLabel()
						.padding(.top, 20)
					
				} // </VStack>
				.padding(.vertical, 10)
				
			} // </Section>
		} // </List>
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			onWillEnterForeground()
		})
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func securityLabel() -> some View {
		
		if biometricsEnabled {
			
			Label {
				Text("Access to Phoenix is protected by biometrics.")
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
	func receiveLabel() -> some View {
		
		Label {
			VStack(alignment: HorizontalAlignment.leading) {
				Text("To receive incoming payments, Phoenix must be running.")
					.fixedSize(horizontal: false, vertical: true) // SwiftUI truncating text
				
				Text("(Phoenix can be running in the background.)")
					.fixedSize(horizontal: false, vertical: true) // SwiftUI truncating text
					.foregroundColor(.gray)
					.padding(.top, 2)
			}
			
		} icon: {
			Image(systemName: "bolt")
				.renderingMode(.template)
				.imageScale(.medium)
				.foregroundColor(Color.appAccent)
		}
	}
	
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
	
	func toggleBiometrics(_ flag: Bool) {
		log.trace("toggleBiometrics()")
		
		if ignoreToggle_biometricsEnabled {
			ignoreToggle_biometricsEnabled = false
			return
		}
		
		if flag { // toggle => ON
			
			enableSoftBiometrics { (success: Bool) in
				if !success {
					self.ignoreToggle_biometricsEnabled = true
					self.biometricsEnabled = false // failed to enable soft => disabled
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
				self.biometricsEnabled = true // failed to disable soft => enabled
			}
			
			let prompt = localizedDisableBiometricsPrompt()
			
			AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
				
				if case .success(_) = result {
					disableSoftBiometrics { (success: Bool) in
						if !success {
							failedToDisable()
						}
					}
				} else {
					failedToDisable()
				}
			}
		}
	}
	
	func localizedDisableBiometricsPrompt() -> String {
		
		return NSLocalizedString("Authenticate to disable biometrics.", comment: "User prompt")
	}
	
	func enableSoftBiometrics(
		completion: @escaping (_ success: Bool) -> Void
	) -> Void {
		
		log.trace("enableSoftBiometrics()")
		
		// CurrentState:
		// - SecurityFile.keychain != nil
		// - AppSecurity.shared.softBiometrics = false
		//
		// TargetState:
		// - SecurityFile.keychain != nil
		// - AppSecurity.shared.softBiometrics = true
		
		AppSecurity.shared.setSoftBiometrics(enabled: true) { (error: Error?) in
			completion(error == nil)
		}
	}
	
	func disableSoftBiometrics(
		completion: @escaping (_ success: Bool) -> Void
	) -> Void {
		
		log.trace("disableSoftBiometrics()")
		
		// CurrentState:
		// - SecurityFile.keychain != nil
		// - AppSecurity.shared.softBiometrics = true
		//
		// TargetState:
		// - SecurityFile.keychain != nil
		// - AppSecurity.shared.softBiometrics = false
		
		AppSecurity.shared.setSoftBiometrics(enabled: false) { (error: Error?) in
			completion(error == nil)
		}
	}
}

