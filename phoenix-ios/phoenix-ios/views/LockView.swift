import SwiftUI

struct LockView : View {
	
	@Binding var isUnlocked: Bool
	
	@State var isTouchID = true
	@State var isFaceID = false
	@State var errorMsg: String? = nil
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	var body: some View {
		
		VStack {
			
			Image("logo")
			.resizable()
			.frame(width: 96, height: 96)
			.padding([.top, .bottom], 0)

			Text("Phoenix")
			.font(Font.title2)
			.padding(.top, -10)
			.padding(.bottom, 40)
			
			if isTouchID || isFaceID {
				
				Button {
					tryBiometricsLogin()
				} label: {
					if isTouchID {
						Image(systemName: "touchid")
							.resizable()
							.frame(width: 32, height: 32)
					} else {
						Image(systemName: "faceid")
							.resizable()
							.frame(width: 32, height: 32)
					}
				}
				
			} else {
				
				Button {
					tryBiometricsLogin()
				} label: {
					Image(systemName: "ant")
						.resizable()
						.frame(width: 32, height: 32)
				}
				.disabled(true)
			}
			
			if let errorMsg = errorMsg {
				Text(errorMsg)
					.foregroundColor(Color.appRed)
					.padding(.top, 10)
			}
		}
		.offset(x: 0, y: -80) // move center upwards; logo not hidden by TouchID popover
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(Color(UIColor.systemBackground))
		.edgesIgnoringSafeArea(.all)
		.onAppear {
			onAppear()
		}
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			onWillEnterForeground()
		})
	}
	
	func onAppear() -> Void {
		
		// This function is called when:
		// - app is launched for the first time
		// - app has just been backgrounded
		
		let status = AppSecurity.shared.biometricStatus()
		updateBiometricsStatus(status)
		
		if (status != .notAvailable) && (UIApplication.shared.applicationState == .active) {
			tryBiometricsLogin()
		}
	}
	
	func onWillEnterForeground() -> Void {
		print("onWillEnterForeground()")
		
		// When the app returns from being in the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		let status = AppSecurity.shared.biometricStatus()
		updateBiometricsStatus(status)
		
		if status != .notAvailable {
			tryBiometricsLogin()
		}
	}
	
	func updateBiometricsStatus(_ status: BiometricStatus) -> Void {
		
		switch status {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : isTouchID = true
			default                    : isTouchID = false
		}
		
		switch status {
			case .touchID_available    : errorMsg = nil
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : errorMsg = NSLocalizedString(
				"Please enable Touch ID", comment: "Error message in LockView"
			)
			
			case .faceID_available    : errorMsg = nil
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : errorMsg = NSLocalizedString(
				"Please enabled Face ID", comment: "Error message in LockView"
			)
			
			default: errorMsg = NSLocalizedString(
				"Unknown biometrics", comment: "Error message in LockView"
			)
		}
	}
	
	func tryBiometricsLogin() -> Void {
		
		AppSecurity.shared.tryUnlockWithBiometrics {(result: Result<Data?, Error>) in
			
			switch result {
				case .success(let databaseKey):
					if databaseKey != nil {
						withAnimation(.easeInOut) {
							isUnlocked = true
						}
					}
				case .failure(let error):
					print("error: \(error)")
			}
		}
	}
}
