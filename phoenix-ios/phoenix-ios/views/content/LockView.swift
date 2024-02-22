import SwiftUI

fileprivate let filename = "LockView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LockView: View {
	
	@ObservedObject var lockState = LockState.shared
	
	@State var isTouchID = true
	@State var isFaceID = false
	@State var errorMsg: String? = nil
	
	@State var biometricsAttemptInProgress = false
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.top) {
			
			Color.clear   // an additional layer
				.zIndex(0) // for animation purposes
			
			if !lockState.isUnlocked {
				content()
					.zIndex(1)
					.transition(.asymmetric(
						insertion : .identity,
						removal   : .move(edge: .bottom)
					))
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		// The layout & math here is designed to match the LoadingView.
		//
		// That is, both the LoadingView & LockView are designed to place
		// the logoContent at the exact same coordinates.
		// So that a transition from the LoadingView to the LockView is smooth.
		
		GeometryReader { geometry in
			
			let topPadding = geometry.size.height / 4.0
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				logoContent()
					.padding(.bottom, 40)
				lockedContent()
			}
			.padding(.top, topPadding)
			.frame(maxWidth: .infinity)
		}
		.background(Color(UIColor.systemBackground))
		.edgesIgnoringSafeArea(.all)
		.onAppear {
			onAppear()
		}
		.onReceive(willEnterForegroundPublisher, perform: { _ in
			willEnterForeground()
		})
	}
	
	@ViewBuilder
	func logoContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Image(logoImageName)
				.resizable()
				.frame(width: 96, height: 96)

			Text("Phoenix")
				.font(Font.title2)
				.padding(.top, -5)
		}
	}
	
	@ViewBuilder
	func lockedContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 20) {
			
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
					.foregroundColor(Color.appNegative)
					.padding(.top, 10)
			}
		}
	}
	
	var logoImageName: String {
		if BusinessManager.isTestnet {
			return "logo_blue"
		} else {
			return "logo_green"
		}
	}
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		// This function may be called when:
		//
		// - The application has just finished launching.
		//   In this case: applicationState == .active
		//
		// - The user is backgrounding the app, so we're switching in the LockView for security.
		//   In this case: applicationState == .background
		
		log.debug("UIApplication.shared.applicationState = \(UIApplication.shared.applicationState)")
		let canPrompt = UIApplication.shared.applicationState != .background
		
		checkStatus(canPrompt: canPrompt)
	}
	
	func willEnterForeground() -> Void {
		log.trace("willEnterForeground()")
		
		// NB: At this moment in time: UIApplication.shared.applicationState == .background
	
		checkStatus(canPrompt: true)
	}
	
	func checkStatus(canPrompt: Bool) -> Void {
		log.trace("checkStatusAndMaybePrompt()")
		
		// This function is called when:
		// - app is launched for the first time
		// - app returns from being in the background
		//
		// Note that after returning background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		let support = AppSecurity.shared.deviceBiometricSupport()
		updateBiometricsSupport(support)
		
		if (support != .notAvailable) && canPrompt {
			tryBiometricsLogin()
		}
	}
	
	func updateBiometricsSupport(_ support: BiometricSupport) -> Void {
		
		switch support {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : isTouchID = true
			default                    : isTouchID = false
		}
		switch support {
			case .faceID_available    : fallthrough
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : isFaceID = true
			default                   : isFaceID = false
		}
		
		switch support {
			case .touchID_available    : errorMsg = nil
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : errorMsg = NSLocalizedString(
				"Please enable Touch ID", comment: "Error message in LockView"
			)
			
			case .faceID_available    : errorMsg = nil
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : errorMsg = NSLocalizedString(
				"Please enable Face ID", comment: "Error message in LockView"
			)
			
			default: errorMsg = NSLocalizedString(
				"Unknown biometrics", comment: "Error message in LockView"
			)
		}
	}
	
	func tryBiometricsLogin() -> Void {
		log.trace("tryBiometricsLogin()")
		
		if biometricsAttemptInProgress {
			log.debug("tryBiometricsLogin(): ignoring - already in progress")
			return
		}
		biometricsAttemptInProgress = true
		
		AppSecurity.shared.tryUnlockWithBiometrics {(result: Result<RecoveryPhrase, Error>) in
			
			biometricsAttemptInProgress = false
			
			switch result {
				case .success(let recoveryPhrase):
					Biz.loadWallet(recoveryPhrase: recoveryPhrase)
					withAnimation(.easeInOut) {
						lockState.isUnlocked = true
					}
				case .failure(let error):
					log.debug("tryUnlockWithBiometrics: error: \(String(describing: error))")
			}
		}
	}
}
