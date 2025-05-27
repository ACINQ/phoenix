import SwiftUI

fileprivate let filename = "LockView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LockView: View {
	
	@ObservedObject var lockState = LockState.shared
	
	@State var enabledSecurity: EnabledSecurity = AppSecurity.shared.enabledSecurityPublisher.value
	@State var invalidPin: InvalidPin = InvalidPin.none() // updated in onAppear
	
	@State var isTouchID = true
	@State var isFaceID = false
	@State var biometricsErrorMsg: String? = nil
	@State var biometricsAttemptInProgress = false
	
	@State var pinPromptVisible = false
	@State var pin: String = ""
	@State var numberPadDisabled: Bool = false
	
	@State var vibrationTrigger: Int = 0
	@State var shakeTrigger: Int = 0
	
	@State var firstAppearance = true
	
	let timer = Timer.publish(every: 0.5, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
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
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: geometry.size.height / 4.0)
					.layoutPriority(-2)
				
				logoContent()
				
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: 50)
					.layoutPriority(-1)
				
				accessOptions() // <- Only one of these will be displayed (never both)
				pinPrompt()     // <-
			}
			.frame(maxWidth: .infinity)
		}
		.background(Color(UIColor.systemBackground))
		.onAppear {
			onAppear()
		}
		.onReceive(timer) { _ in
			currentDate = Date()
		}
		.onReceive(willEnterForegroundPublisher) { _ in
			willEnterForeground()
		}
		.onChange(of: shakeTrigger) { _ in
			shakeTriggerChanged()
		}
		.vibrationFeedback(.error, trigger: vibrationTrigger)
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
	func accessOptions() -> some View {
		
		if !pinPromptVisible {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				accessButtons()
				
				if enabledSecurity.contains(.biometrics), let errorMsg = biometricsErrorMsg {
					Spacer(minLength: 0)
						.frame(minHeight: 0, maxHeight: 20)
					Text(errorMsg)
						.foregroundColor(Color.appNegative)
				}
				
			} //</VStack>
			.transition(
				.asymmetric(
					insertion: .opacity,
					removal: .opacity
				)
			)
		}
	}
	
	@ViewBuilder
	func accessButtons() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 50) {
				
			if enabledSecurity.contains(.biometrics) {
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
			} // </.biometrics>
			
			if enabledSecurity.contains(.lockPin) {
				Button {
					showPinPrompt()
				} label: {
					Image(systemName: "circle.grid.3x3")
						.resizable()
						.frame(width: 32, height: 32)
				}
			} // </.customPin>
			
		} // </HStack>
	}
	
	@ViewBuilder
	func pinPrompt() -> some View {
		
		if enabledSecurity.contains(.lockPin), pinPromptVisible {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				numberPadHeader()
				
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: 50)
					.layoutPriority(-1)
				
				NumberPadView(
					buttonPressed: numberPadButtonPressed,
					showHideButton: true,
					disabled: $numberPadDisabled
				)
			}
			.padding(.bottom)
			.transition(
				.asymmetric(
					insertion: .move(edge: .bottom),
					removal: .move(edge: .bottom)
				)
			)
		}
	}
	
	@ViewBuilder
	func numberPadHeader() -> some View {
		
		// When we switch between the `pinPromptCircles` and the `countdown`,
		// we don't want the other UI components to change position.
		// In other words, they shouldn't move up or down on the screen.
		// To accomplish this, we make sure they both have the same height.
		
		ZStack(alignment: Alignment.center) {
			
			let delay = retryDelay()
			
			countdown(delay ?? "1 second", invisible: delay == nil)
			pinPromptCircles(invisible: delay != nil)
		}
	}
	
	@ViewBuilder
	func countdown(_ delay: String, invisible: Bool) -> some View {
		
		Text("Retry in \(delay)")
			.font(.headline)
			.foregroundStyle(invisible ? Color.clear : Color.primary)
	}
	
	@ViewBuilder
	func pinPromptCircles(invisible: Bool) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
		
			let pinCount = pin.count
			ForEach(0 ..< PIN_LENGTH, id: \.self) { idx in
				if idx < pinCount {
					filledCircle(invisible)
				} else {
					emptyCircle(invisible)
				}
			}
			
		} // </HStack>
		.modifier(Shake(animatableData: CGFloat(shakeTrigger)))
	}
	
	@ViewBuilder
	func emptyCircle(_ invisible: Bool) -> some View {
		
		Image(systemName: "circle")
			.renderingMode(.template)
			.resizable()
			.scaledToFit()
			.frame(width: 24, height: 24)
			.foregroundColor(invisible ? Color.clear : circleColor)
	}
	
	@ViewBuilder
	func filledCircle(_ invisible: Bool) -> some View {
		
		Image(systemName: "circle.fill")
			.renderingMode(.template)
			.resizable()
			.scaledToFit()
			.frame(width: 24, height: 24)
			.foregroundColor(invisible ? Color.clear : circleColor)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var logoImageName: String {
		if BusinessManager.isTestnet {
			return "logo_blue"
		} else {
			return "logo_green"
		}
	}
	
	var circleColor: Color {
		return Color.primary.opacity(0.8)
	}
	
	func retryDelay() -> String? {
		
		guard let remaining = invalidPin.waitTimeFrom(currentDate)?.rounded() else {
			return nil
		}
		
		let minutes = Int(remaining / 60.0)
		let seconds = Int(remaining) % 60
		
		let nf = NumberFormatter()
		nf.minimumIntegerDigits = 2
		let secondsStr = nf.string(from: NSNumber(value: seconds)) ?? "00"
		
		return "\(minutes):\(secondsStr)"
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
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
		
		refreshSettings(canPrompt: canPrompt)
		
		if firstAppearance {
			firstAppearance = false
		}
	}
	
	func willEnterForeground() {
		log.trace("willEnterForeground()")
		
		// NB: At this moment in time: UIApplication.shared.applicationState == .background
	
		refreshSettings(canPrompt: true)
	}
	
	func shakeTriggerChanged() {
		log.trace("shakeTriggerChanged()")
		
		// vibrationFeedback()/sensoryFeedback() is only available in iOS 17
		// Use older API for earlier iOS versions.
		if #unavailable(iOS 17.0) {
			let generator = UINotificationFeedbackGenerator()
			generator.notificationOccurred(.error)
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func refreshSettings(canPrompt: Bool) -> Void {
		log.trace("refreshSettings(canPrompt: \(canPrompt))")
		
		// This function is called when:
		// - app is launched for the first time
		// - app returns from being in the background
		//
		// Note that after returning background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		
		let biometricsSupport = AppSecurity.shared.deviceBiometricSupport()
		refreshBiometricsSupport(biometricsSupport)
		
		enabledSecurity = AppSecurity.shared.enabledSecurityPublisher.value
		invalidPin = AppSecurity.shared.getInvalidLockPin() ?? InvalidPin.none()
		
		if canPrompt {
			if enabledSecurity.contains(.biometrics) {
				if biometricsSupport != .notAvailable {
					tryBiometricsLogin()
				} else {
					// Don't auto-show the pinPrompt (if enabled) in this situation.
					// Biometics is enabled, but there's some kind of problem with it.
					// Attention should be on biometricsErrorMsg
				}
			} else if enabledSecurity.contains(.lockPin) {
				let fast = firstAppearance ? true : false
				showPinPrompt(fastAnimation: fast)
			}
		}
	}
	
	func refreshBiometricsSupport(_ biometricsSupport: BiometricSupport) -> Void {
		
		switch biometricsSupport {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : isTouchID = true
			default                    : isTouchID = false
		}
		switch biometricsSupport {
			case .faceID_available    : fallthrough
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : isFaceID = true
			default                   : isFaceID = false
		}
		
		switch biometricsSupport {
			case .touchID_available    : biometricsErrorMsg = nil
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : biometricsErrorMsg = NSLocalizedString(
				"Please enable Touch ID", comment: "Error message in LockView"
			)
			
			case .faceID_available    : biometricsErrorMsg = nil
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : biometricsErrorMsg = NSLocalizedString(
				"Please enable Face ID", comment: "Error message in LockView"
			)
			
			default: biometricsErrorMsg = NSLocalizedString(
				"Unknown biometrics", comment: "Error message in LockView"
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func tryBiometricsLogin() {
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
				closeLockView(recoveryPhrase)
				
			case .failure(let error):
				log.debug("tryUnlockWithBiometrics: error: \(String(describing: error))")
				if enabledSecurity.contains(.lockPin) {
					showPinPrompt()
				}
			}
		}
	}
	
	func showPinPrompt(fastAnimation: Bool = false) {
		log.trace("showPinPrompt(fastAnimation: \(fastAnimation)")
		
		if fastAnimation {
			withAnimation(.linear(duration: 0.15)) {
				pinPromptVisible = true
			}
		} else {
			withAnimation {
				pinPromptVisible = true
			}
		}
	}
	
	func hidePinPrompt() {
		log.trace("hidePinPrompt()")
		
		withAnimation {
			pinPromptVisible = false
		}
	}
	
	func numberPadButtonPressed(_ identifier: NumberPadButton) {
		log.trace("numberPadButtonPressed()")
		
		if identifier == .hide {
			hidePinPrompt()
		} else if pin.count < PIN_LENGTH {
			if identifier == .delete {
				pin = String(pin.dropLast())
			} else {
				pin += identifier.rawValue
			}
			
			if pin.count == PIN_LENGTH {
				verifyPin()
			}
		}
	}
	
	func verifyPin() {
		log.trace("verifyPin()")
		
		let correctPin = AppSecurity.shared.getLockPin()
		if pin == correctPin {
			handleCorrectPin()
		} else {
			handleIncorrectPin()
		}
	}
	
	func handleCorrectPin() {
		log.trace("handleCorrectPin()")
		
		AppSecurity.shared.tryUnlockWithKeychain { recoveryPhrase, configuration, error in
			if let recoveryPhrase {
				closeLockView(recoveryPhrase)
			} else if let error {
				log.error("Failed to unlock keychain: \(error)")
			}
		}
		
		// Delete InvalidPin info from keychain (if it exists)
		AppSecurity.shared.setInvalidLockPin(nil) { _ in }
		invalidPin = InvalidPin.none()
	}
	
	func handleIncorrectPin() {
		log.trace("handleIncorrectPin()")
		
		vibrationTrigger += 1
		withAnimation {
			shakeTrigger += 1
		}
		
		let newInvalidPin: InvalidPin
		if invalidPin.count > 0 && invalidPin.elapsed > 24.hours() {
			// We reset the count after 24 hours
			newInvalidPin = InvalidPin.one()
		} else {
			newInvalidPin = invalidPin.increment()
		}
		
		AppSecurity.shared.setInvalidLockPin(newInvalidPin) { _ in }
		invalidPin = newInvalidPin
		currentDate = Date.now
		
		let delay = newInvalidPin.waitTimeFrom(currentDate) ?? 0.75
		
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			numberPadDisabled = false
			pin = ""
		}
	}
	
	func closeLockView(_ recoveryPhrase: RecoveryPhrase) {
		log.trace("closeLockView()")
		assertMainThread()
		
		Biz.loadWallet(recoveryPhrase: recoveryPhrase)
		withAnimation(.easeInOut) {
			lockState.isUnlocked = true
		}
	}
}
