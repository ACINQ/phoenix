import Foundation
import Combine
import SwiftUI
import PhoenixShared

struct AppAccessView : View {
	
	var cancellables = Set<AnyCancellable>()
	
	@State var biometricStatus = AppSecurity.shared.biometricStatus()
	@State var biometricsEnabled = AppSecurity.shared.enabledSecurity.value.contains(.biometrics)
	@State var ignoreToggle = false
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	var body: some View {
		
		Form {
			
			Toggle(isOn: $biometricsEnabled) {
				
				switch biometricStatus {
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
				
			}
			.onChange(of: biometricsEnabled) { value in
				self.toggleBiometrics(value)
			}
			.disabled(!biometricStatus.isAvailable())
			
		}
		.navigationBarTitle("App Access", displayMode: .inline)
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			onWillEnterForeground()
		})
	}
	
	func onWillEnterForeground() -> Void {
		print("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		self.biometricStatus = AppSecurity.shared.biometricStatus()
	}
	
	func toggleBiometrics(_ flag: Bool) {
		print("toggleBiometrics()")
		
		if ignoreToggle {
			ignoreToggle = false
			return
		}
		
		if flag { // toggle => ON
			enableBiometrics()
			
		} else { // toggle => OFF
			disableBiometrics()
		}
	}
	
	func enableBiometrics() -> Void {
		
		// state is: enabledSecurity == .none
		
		let Fail: () -> Void = {
			self.ignoreToggle = true
			self.biometricsEnabled = false // failed to enable biometrics
		}
		
		AppSecurity.shared.tryUnlockWithKeychain {(mnemonics: [String]?, _) in
			
			guard let mnemnoics = mnemonics else {
				return Fail()
			}
			
			AppSecurity.shared.addBiometricsEntry(mnemonics: mnemnoics) {(error: Error?) in
				if error != nil { Fail() }
			}
		}
	}
	
	func disableBiometrics() -> Void {
		
		// state is: enabledSecurity == .biometrics
		
		let Fail: () -> Void = {
			self.ignoreToggle = true
			self.biometricsEnabled = true // failed to disable biometrics
		}
		
		let prompt = NSLocalizedString("Authenticate to disable biometrics.", comment: "User prompt")
		
		AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
			
			guard case .success(let mnemonics) = result else {
				return Fail()
			}
			
			AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
				if error != nil { Fail() }
			}
		}
	}
}

