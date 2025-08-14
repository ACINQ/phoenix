import SwiftUI

fileprivate let filename = "LockView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LockView: View {
	
	@ObservedObject var appState = AppState.shared
	
	@State var enabledSecurity: EnabledSecurity = EnabledSecurity.none // updated in onAppear
	@State var invalidPin: InvalidPin = InvalidPin.none()              // updated in onAppear
	@State var altInvalidPin: InvalidPin = InvalidPin.none()           // updated in onAppear
	
	@State var isTouchID = false
	@State var isFaceID = false
	@State var biometricSupport: BiometricSupport? = nil
	@State var biometricsErrorMsg: String? = nil
	@State var biometricsAttemptInProgress = false
	
	@State var visibleWallets: [WalletMetadata] = []
	@State var hiddenWallets: [WalletMetadata] = []
	@State var selectedWallet: WalletMetadata? = nil
	
	@State var pin: String = ""
	@State var isCorrectPin: Bool = false
	@State var numberPadDisabled: Bool = false
	
	@State var vibrationTrigger: Int = 0
	@State var shakeTrigger: Int = 0
	
	enum VisibleContent {
		case walletSelector
		case accessOptions
		case pinPrompt
	}
	@State var visibleContent: VisibleContent? = nil
	
	@State var didAppear: Bool = false
	@State var firstAppearance: Bool = true
	
	@State var hiddenWalletPins: [String: [WalletMetadata]] = [:]
	
	let timer = Timer.publish(every: 0.5, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
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
			
			if !appState.isUnlocked {
				layers()
					.zIndex(1)
					.transition(.asymmetric(
						insertion : .identity,
						removal   : .move(edge: .bottom)
					))
			}
		}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack(alignment: Alignment.top) {
			
			Color(UIColor.systemBackground)
				.edgesIgnoringSafeArea(.all)
			
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			if let visibleContent, visibleContent != .walletSelector {
				Button {
					showContent(.walletSelector)
				} label: {
					if let selectedWallet {
						WalletImage(filename: selectedWallet.photo, size: 48)
					} else {
						WalletImage(filename: ":eye.slash", size: 48)
					}
				}
				.transition(.opacity)
			}
		}
		.padding(.horizontal)
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
				
				if let visibleContent {
					switch visibleContent {
					case .walletSelector:
						Spacer(minLength: 0)
							.layoutPriority(-3)
						walletSelector()
						
					case .accessOptions:
						accessOptions()
						
					case .pinPrompt:
						Spacer(minLength: 0)
							.layoutPriority(-3)
						pinPrompt()
					}
				}
			}
			.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
		}
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
			
			Image(Biz.isTestnet ? "logo_blue" : "logo_green")
				.resizable()
				.frame(width: 96, height: 96)

			Text("Phoenix")
				.font(Font.title2)
				.padding(.top, -5)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders: Wallet Selector
	// --------------------------------------------------
	
	@ViewBuilder
	func walletSelector() -> some View {
		
		WalletSelector(
			visibleWallets: $visibleWallets,
			hiddenWallets: $hiddenWallets,
			didSelectWallet: self.didSelectWallet,
			hiddenWallet: self.hiddenWallet
		)
		.transition(
			.move(edge: .bottom)
			.combined(with: .opacity)
		)
	}
	
	// --------------------------------------------------
	// MARK: View Builders: Access Options
	// --------------------------------------------------
	
	@ViewBuilder
	func accessOptions() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			accessButtons()
			
			if enabledSecurity.contains(.biometrics), let errorMsg = biometricsErrorMsg {
				Spacer(minLength: 0)
					.frame(minHeight: 0, maxHeight: 20)
				Text(errorMsg)
					.foregroundColor(Color.appNegative)
			}
			
		} //</VStack>
		.transition(.opacity)
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
					showContent(.pinPrompt)
				} label: {
					Image(systemName: "circle.grid.3x3")
						.resizable()
						.frame(width: 32, height: 32)
				}
			} // </.customPin>
			
		} // </HStack>
	}
	
	// --------------------------------------------------
	// MARK: View Builders: Pin Prompt
	// --------------------------------------------------
	
	@ViewBuilder
	func pinPrompt() -> some View {
		
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
			.move(edge: .bottom)
			.combined(with: .offset(y: deviceInfo.windowSafeArea.bottom))
			.combined(with: .opacity)
		)
	}
	
	@ViewBuilder
	func numberPadHeader() -> some View {
		
		// When we switch between the `pinPromptCircles` and the `countdown`,
		// we don't want the other UI components to change position.
		// In other words, they shouldn't move up or down on the screen.
		// To accomplish this, we put them both in a ZStack (where only one is visible).
		
		ZStack(alignment: Alignment.center) {
			
			let delay = retryDelayString()
			
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
	
	var circleColor: Color {
		
		if pin.count < PIN_LENGTH {
			return Color.primary.opacity(0.8)
		} else if isCorrectPin {
			return Color.appPositive
		} else {
			return Color.appNegative
		}
	}
	
	func retryDelay() -> TimeInterval? {
		
		// When the user is entering a PIN for a specific wallet,
		// then we will use the maximum between:
		// - selectedKeychain.getInvalidPin()
		// - Keychain.global.getHiddenWalletInvalidPin()
		//
		// This makes things a bit harder for an attacker.
		// For example:
		// - if there are 4 visible wallets
		// - and attacker is searching for a hidden wallet
		// - they can't just switch between the visible wallets trying different PINs
		
		let remaining1 = invalidPin.waitTimeFrom(currentDate)?.rounded()
		let remaining2 = altInvalidPin.waitTimeFrom(currentDate)?.rounded()
		
		if let remaining1 {
			if let remaining2 {
				return max(remaining1, remaining2)
			} else {
				return remaining1
			}
		} else {
			return remaining2
		}
	}
	
	func retryDelayString() -> String? {
		
		guard let remaining = retryDelay() else {
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
		log.trace(#function)
		
		guard !didAppear else {
			return
		}
		didAppear = true
		
		refreshBiometricsSupport()
		
		let allWallets = SecurityFileManager.shared.sortedWallets()
		
		visibleWallets = allWallets.filter { !$0.isHidden }
		selectedWallet = SecurityFileManager.shared.currentWallet()
		
		let hiddenWallets = allWallets.filter({ $0.isHidden })
		log.debug("hiddenWallets.count = \(hiddenWallets.count)")
				
		if !hiddenWallets.isEmpty {
			DispatchQueue.global(qos: .default).async {
				
				var result = [String: [WalletMetadata]]()
				
				for hiddenWallet in hiddenWallets {
					let id = hiddenWallet.keychainKeyId
					if let lockPin = Keychain.wallet(id).getLockPin() {
						
						if let existing = result[lockPin] {
							result[lockPin] = existing + [hiddenWallet]
						} else {
							result[lockPin] = [hiddenWallet]
						}
					}
				}
				
				DispatchQueue.main.async {
					self.hiddenWalletPins = result
				}
			}
		}
		
		DispatchQueue.main.async {
			performFirstAppearance()
		}
	}
	
	func performFirstAppearance() {
		log.trace(#function)
		
		guard firstAppearance else {
			log.warning("performFirstAppearance(): ignoring: not firstAppearance")
			return
		}
		firstAppearance = false
		
		if selectedWallet == nil {
			showContent(.walletSelector, fastAnimation: true)
		} else {
			visibleContent = .accessOptions // no animation here
			refreshUserSecurity()
			
			// This function may be called when:
			//
			// - The application has just finished launching.
			//   In this case: applicationState == .active
			//
			// - The user is backgrounding the app, so we're switching in the LockView for security.
			//   In this case: applicationState == .background
			
			log.debug("UIApplication.shared.applicationState = \(UIApplication.shared.applicationState)")
			if UIApplication.shared.applicationState != .background {
				promptUser(fastAnimation: true)
			}
		}
	}
	
	func willEnterForeground() {
		log.trace(#function)
		
		// After returning from the background, the biometric status may have changed.
		// For example: .touchID_notEnrolled => .touchID_available
		//
		refreshBiometricsSupport()
		
		if visibleContent == .accessOptions {
			promptUser()
		}
	}
	
	func shakeTriggerChanged() {
		log.trace(#function)
		
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
	
	func selectedKeychain() -> Keychain_Wallet? {
		
		guard let selectedWallet else {
			return nil
		}
		
		return Keychain.wallet(selectedWallet.keychainKeyId)
	}
	
	func refreshBiometricsSupport() {
		log.trace(#function)
		
		let support = DeviceInfo.biometricSupport()
		self.biometricSupport = support
		
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
	
	func refreshUserSecurity() {
		log.trace(#function)
		
		if let keychain = selectedKeychain() {
			// Refreshing security for a specific wallet
			
			enabledSecurity = keychain.enabledSecurity
			invalidPin = keychain.getInvalidLockPin() ?? InvalidPin.none()
			altInvalidPin = Keychain.global.getHiddenWalletInvalidPin() ?? InvalidPin.none()
			
		} else {
			// Refreshing security for a hidden wallet
			
			enabledSecurity = EnabledSecurity.lockPin
			invalidPin = Keychain.global.getHiddenWalletInvalidPin() ?? InvalidPin.none()
			altInvalidPin = InvalidPin.none()
		}
		
		currentDate = Date.now
		if let delay = retryDelay() {
			
			numberPadDisabled = true
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				numberPadDisabled = false
				pin = ""
			}
		}
	}
	
	func promptUser(fastAnimation: Bool = false) {
		log.trace(#function)
		
		if enabledSecurity.contains(.biometrics) {
			showContent(.accessOptions, fastAnimation: fastAnimation)
			if let biometricSupport, biometricSupport != .notAvailable {
				tryBiometricsLogin()
			} else {
				// Don't auto-show the pinPrompt (if enabled) in this situation.
				// Biometics is enabled, but there's some kind of problem with it.
				// Attention should be on biometricsErrorMsg
			}
		} else if enabledSecurity.contains(.lockPin) {
			showContent(.pinPrompt, fastAnimation: fastAnimation)
		}
	}
	
	// --------------------------------------------------
	// MARK: Transitions
	// --------------------------------------------------
	
	func showContent(_ content: VisibleContent, fastAnimation: Bool = false) {
		
		guard visibleContent != content else {
			return
		}
		log.trace("showContent(\(content), fastAnimation: \(fastAnimation))")
		
		if fastAnimation {
			withAnimation(.linear(duration: 0.15)) {
				visibleContent = content
			}
		} else {
			withAnimation {
				visibleContent = content
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions: Wallet selector
	// --------------------------------------------------
	
	func didSelectWallet(_ wallet: WalletMetadata) {
		log.trace(#function)
		
		selectedWallet = wallet
		refreshUserSecurity()
		
		if enabledSecurity.hasAppLock() {
			promptUser()
			
		} else {
			SceneDelegate.get().selectWallet(wallet)
		}
	}
	
	func hiddenWallet() {
		log.trace(#function)
		
		selectedWallet = nil
		refreshUserSecurity()
		showContent(.pinPrompt)
	}
	
	// --------------------------------------------------
	// MARK: Actions: PIN prompt
	// --------------------------------------------------
	
	func numberPadButtonPressed(_ identifier: NumberPadButton) {
		log.trace(#function)
		
		if identifier == .hide {
			showContent(.accessOptions)
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
		log.trace(#function)
		
		let keychain = selectedKeychain()
		
		if let keychain, let correctPin = keychain.getLockPin(), pin == correctPin {
			tryUnlockKeychain(keychain)
			handleCorrectPin(keychain, isHiddenWallet: false)
			
		} else if let hiddenWalletMatches = hiddenWalletPins[pin] {
			
			if hiddenWalletMatches.count == 1 {
				let hiddenWallet = hiddenWalletMatches[0]
				let hiddenWalletKeychain = Keychain.wallet(hiddenWallet.keychainKeyId)
		
				tryUnlockKeychain(hiddenWalletKeychain)
				handleCorrectPin(hiddenWalletKeychain, isHiddenWallet: true)
		
			} else {
				handleCorrectPin(nil, isHiddenWallet: true)
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
					hiddenWallets = hiddenWalletMatches
					showContent(.walletSelector)
				}
			}
			
		} else {
			handleIncorrectPin(keychain)
		}
	}
	
	func tryUnlockKeychain(_ keychain: Keychain_Wallet) {
		
		keychain.unlockWithKeychain { result in
			switch result {
			case .failure(let reason):
				log.error("Failed to unlock keychain: \(reason)")
				
			case .success(let recoveryPhrase):
				if let recoveryPhrase {
					closeLockView(recoveryPhrase, delay: 0.15)
				} else {
					log.error("Failed to unlock keychain: wallet does not exist")
				}
			}
		}
	}
	
	func handleCorrectPin(_ keychain: Keychain_Wallet?, isHiddenWallet: Bool) {
		log.trace(#function)
		
		// Delete InvalidPin info from keychain (if it exists)
		if let keychain {
			keychain.setInvalidLockPin(nil) { _ in }
		}
		if isHiddenWallet {
			Keychain.global.setHiddenWalletInvalidPin(nil) { _ in }
		}
		invalidPin = InvalidPin.none()
		
		// Animate UI
		isCorrectPin = true
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
			numberPadDisabled = false
			pin = ""
			isCorrectPin = false
		}
	}
	
	func handleIncorrectPin(_ keychain: Keychain_Wallet?) {
		log.trace(#function)
		
		vibrationTrigger += 1
		withAnimation {
			shakeTrigger += 1
		}
		
		isCorrectPin = false
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			numberPadDisabled = false
			pin = ""
			incrementInvalidPin(keychain)
		}
	}
	
	func incrementInvalidPin(_ keychain: Keychain_Wallet?) {
		log.trace(#function)
		
		let newInvalidPin: InvalidPin
		if invalidPin.count > 0 && invalidPin.elapsed > 24.hours() {
			// We reset the count after 24 hours
			newInvalidPin = InvalidPin.one()
		} else {
			newInvalidPin = invalidPin.increment()
		}
		
		if let keychain {
			keychain.setInvalidLockPin(newInvalidPin) { _ in }
		} else {
			Keychain.global.setHiddenWalletInvalidPin(newInvalidPin) { _ in }
		}
		invalidPin = newInvalidPin
		
		currentDate = Date.now
		if let delay = retryDelay() {
			numberPadDisabled = true
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				numberPadDisabled = false
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func tryBiometricsLogin() {
		log.trace(#function)
		
		guard let keychain = selectedKeychain() else {
			log.warning("tryBiometricsLogin(): ignoring: selectedWallet is nil")
			return
		}
		guard !biometricsAttemptInProgress else {
			log.warning("tryBiometricsLogin(): ignoring: already in progress")
			return
		}
		biometricsAttemptInProgress = true
		
		keychain.unlockWithBiometrics {(result: Result<RecoveryPhrase, Error>) in
			
			biometricsAttemptInProgress = false
			switch result {
			case .success(let recoveryPhrase):
				closeLockView(recoveryPhrase, delay: 0.15)
				
			case .failure(let error):
				log.debug("tryUnlockWithBiometrics: error: \(error)")
				if enabledSecurity.contains(.lockPin) {
					showContent(.pinPrompt)
				}
			}
		}
	}
	
	func closeLockView(_ recoveryPhrase: RecoveryPhrase, delay: TimeInterval) {
		log.trace(#function)
		assertMainThread()
		
		Biz.loadWallet(trigger: .appUnlock, recoveryPhrase: recoveryPhrase)
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			withAnimation(.easeInOut) {
				appState.isUnlocked = true
			}
		}
	}
}

