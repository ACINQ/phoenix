import Foundation
import Combine
import SwiftUI
import PhoenixShared

struct AppAccessView : View {
	
	var cancellables = Set<AnyCancellable>()
	
	@State var biometricStatus = AppSecurity.shared.biometricStatus()
	@State var shutupCompiler = false
	@State var biometricsEnabled = AppSecurity.shared.enabledSecurity.value.contains(.biometrics)
	
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
			
		//	Button(action: {
		//		self.testBiometrics()
		//	}) {
		//		Text("Authenticate with Touch ID")
		//	}
			
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
		
		// Todo: fetch from AppDelegate (or wherever we have this already)
		let databaseKey = AppSecurity.shared.generateDatabaseKey()
		
		if flag { // toggle => ON
			
			AppSecurity.shared.addBiometricsEntry(databaseKey: databaseKey) {(error: Error?) in
				if error != nil {
					self.biometricsEnabled = false // failed to enable biometrics
				}
			}
		} else { // toggle => OFF
			
			AppSecurity.shared.addKeychainEntry(databaseKey: databaseKey) { (error: Error?) in
				if error != nil {
					self.biometricsEnabled = true // failed to disable biometrics
				}
			}
		}
	}
	
	func testBiometrics() {
		print("testBiometrics()")
		
		AppSecurity.shared.tryUnlockWithBiometrics {(result: Result<Data?, Error>) in
			switch result {
				case .success(let databaseKey):
					print("databaseKey: \(databaseKey?.hexEncodedString() ?? "nil")")
				case .failure(let error):
					print("error: \(error)")
			}
		}
	}
}

