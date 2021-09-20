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
	
	@State var biometricStatus = AppSecurity.shared.deviceBiometricSupport()
	@State var biometricsEnabled = AppSecurity.shared.enabledSecurity.value.contains(.biometrics)
	@State var advancedSecurityEnabled = AppSecurity.shared.enabledSecurity.value.contains(.advancedSecurity)
	
	@State var ignoreToggle_biometricsEnabled = false
	@State var ignoreToggle_advancedSecurityEnabled = false
	
	@State var showHelpSheet = false
	
	@Environment(\.colorScheme) var colorScheme
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	var body: some View {
		
		form()
		.navigationBarTitle(
			NSLocalizedString("App Access", comment: "Navigation bar title"),
			displayMode: .inline
		)
	}
	
	@ViewBuilder
	func form() -> some View {
		
		Form {
			Section {
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
				
				securityStatus()
			}
			
			Section {
				
				// SwiftUI Design:
				//
				// We have 2 options:
				// Toggle {
				//    HStack {
				//       Text
				//       Button // <- disabled if toggle is disabled
				// }
				//
				// HStack {
				//   Text
				//   Button
				//   Spacer
				//   Toggle.labelsHidden
				// }
				//
				// The problem with the former option is that,
				// when the Toggle is disabled, the Button is automatically disabled as well.
				//
				// We decided we wanted the (?) button to be tappable, even when the Toggle is disabled.
				// So we went with the later design.
				
				HStack(alignment: VerticalAlignment.center) {
					
					Text("Advanced security")
						.padding(.trailing, 2)
					Button {
						advancedSecurityHelpButtonTapped()
					} label: {
						Image(systemName: "questionmark.circle")
							.renderingMode(.template)
							.imageScale(.large)
							.foregroundColor(colorScheme == ColorScheme.light ? Color.black : Color.white)
					}
					Spacer()
					Toggle("", isOn: $advancedSecurityEnabled)
						.labelsHidden()
						.onChange(of: advancedSecurityEnabled) { value in
							self.toggleAdvancedSecurity(value)
						}
						.disabled(!biometricsEnabled)
				}
				.font(.body)
				.buttonStyle(PlainButtonStyle()) // disable row highlight when tapping help button
				
				receiveStatus()
			}
		}
		.sheet(isPresented: $showHelpSheet) {
			
			AdvancedSecurityHelp(isShowing: $showHelpSheet)
		}
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			onWillEnterForeground()
		})
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func securityStatus() -> some View {
		
		HStack(alignment: VerticalAlignment.top) {
			
			if !biometricsEnabled {
				
				Image(systemName: "exclamationmark.triangle")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appWarn)
				Text(
					"""
					Phoenix can be accessed without credentials. \
					Make sure that you have enabled adequate protections for iOS.
					"""
				)
				
			} else {
				
				Image(systemName: "checkmark.shield")
					.imageScale(.medium)
				Text("Access to Phoenix is protected by biometrics.")
			}
		}
		.padding(.top, 5)
		.padding(.bottom, 20)
	}
	
	@ViewBuilder
	func receiveStatus() -> some View {
		
		HStack(alignment: VerticalAlignment.top) {
			
			Image(systemName: "bolt")
				.imageScale(.medium)
			
			VStack(alignment: HorizontalAlignment.leading) {
				if advancedSecurityEnabled {
					Text(
						"""
						To receive incoming payments, Phoenix must be running, \
						and you must have unlocked the app once.
						"""
					)
					
				} else {
					Text("To receive incoming payments, Phoenix must be running.")
				}
				
				Text("(Phoenix can be running in the background.)")
					.foregroundColor(Color.gray)
					.padding(.top, 2)
			}
		}
		.padding(.top, 5)
	}
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		log.debug("enabledSecurity = \(AppSecurity.shared.enabledSecurity.value)")
	}
	
	func onWillEnterForeground() -> Void {
		print("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		self.biometricStatus = AppSecurity.shared.deviceBiometricSupport()
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
			
			let disableSoft = {
				disableSoftBiometrics { (success: Bool) in
					if !success {
						self.ignoreToggle_biometricsEnabled = true
						self.biometricsEnabled = true // failed to disable soft => enabled
					}
				}
			}
			
			if advancedSecurityEnabled {
				
				// What should occur within the UI:
				// - user is prompted for biometrics verification
				// - if SUCCESS:
				//   - biometrics switch remains disabled
				//   - advancedSecurity switch changes to disabled
				// - if FAILURE:
				//   - biometrics switch changes back to enabled
				//   - advancedSecurity switch remains enabled
				
				disableHardBiometrics { (success: Bool) in
					if success {
						self.ignoreToggle_advancedSecurityEnabled = true
						self.advancedSecurityEnabled = false // successfully disabled hard (manually)
						
						disableSoft()
					} else {
						
						self.ignoreToggle_biometricsEnabled = true
						self.biometricsEnabled = true // failed to disable hard, so soft still enabled
					}
				}
				
			} else {
				
				// What should occur within the UI:
				// - user is prompted for biometrics verification
				// - if SUCCESS:
				//   - biometrics switch remains disabled
				// - if FAILURE:
				//   - biometrics switch changes back to enabled
				
				let prompt = localizedDisableBiometricsPrompt()
				
				AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
					
					if case .success(_) = result {
						disableSoft()
					} else {
						self.ignoreToggle_biometricsEnabled = true
						self.biometricsEnabled = true // failed to disable soft => enabled
					}
				}
			}
		}
	}
	
	func toggleAdvancedSecurity(_ flag: Bool) -> Void {
		log.trace("toggleAdvancedSecurity()")
		
		if ignoreToggle_advancedSecurityEnabled {
			ignoreToggle_advancedSecurityEnabled = false
			return
		}
		
		if flag { // toggle => ON
			enableHardBiometrics { (success: Bool) in
				if !success {
					self.ignoreToggle_advancedSecurityEnabled = true
					self.advancedSecurityEnabled = false // failed to enable => disabled
				}
			}
			
		} else { // toggle => OFF
			disableHardBiometrics { (success: Bool) in
				if !success {
					self.ignoreToggle_advancedSecurityEnabled = true
					self.advancedSecurityEnabled = true // failed to disable => enabled
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
	
	func enableHardBiometrics(
		completion: @escaping (_ success: Bool) -> Void
	) -> Void {
		
		log.trace("enableHardBiometrics()")
		
		// CurrentState:
		// - SecurityFile.keychain != nil
		// - SecurityFile.biometrics == nil
		//
		// TargetState:
		// - SecurityFile.keychain == nil
		// - SecurityFile.biometrics != nil
		
		AppSecurity.shared.tryUnlockWithKeychain {(mnemonics: [String]?, _, _) in
			
			guard let mnemnoics = mnemonics else {
				return completion(false)
			}
			
			AppSecurity.shared.addBiometricsEntry(mnemonics: mnemnoics) {(error: Error?) in
				completion(error == nil)
			}
		}
	}
	
	func disableHardBiometrics(
		completion: @escaping (_ success: Bool) -> Void
	) -> Void {
		
		log.trace("disableHardBiometrics()")
		
		// CurrentState:
		// - SecurityFile.keychain == nil
		// - SecurityFile.biometrics != nil
		//
		// TargetState:
		// - SecurityFile.keychain != nil
		// - SecurityFile.biometrics == nil
		
		let prompt = localizedDisableBiometricsPrompt()
		
		AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
			
			guard case .success(let mnemonics) = result else {
				return completion(false)
			}
			
			AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
				completion(error == nil)
			}
		}
	}
	
	func advancedSecurityHelpButtonTapped() -> Void {
		log.trace("advancedSecurityHelpButtonTapped()")
		
		showHelpSheet = true
	}
}

struct AdvancedSecurityHelp: View {
	
	@Binding var isShowing: Bool
	
	var body: some View {
		
		ZStack {
		
			LocalWebView(
				html: AdvancedSecurityHTML(),
				scrollIndicatorInsets: UIEdgeInsets(top: 0, left: 0, bottom: 0, right: -20)
			)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.padding(.leading, 20)
			.padding(.trailing, 20) // must match LocalWebView.scrollIndicatorInsets.right
			
			// close button
			// (required for landscapse mode, where swipe-to-dismiss isn't possible)
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
		}
	}
	
	func close() {
		log.trace("[AdvancedSecurityHelp] close()")
		isShowing = false
	}
}

