import SwiftUI

fileprivate let filename = "AuthenticateWithPinSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct AuthenticateWithPinSheet: View {
	
	enum EndResult: CustomStringConvertible {
		case UserCancelled
		case Authenticated(recoveryPhrase: RecoveryPhrase)
		case Failed;
		
		var description: String {
			switch self {
				case .UserCancelled : return "UserCancelled"
				case .Authenticated : return "Authenticated"
				case .Failed        : return "Failed"
			}
		}
	}
	
	let willClose: (EndResult) -> Void
	
	@State var pin: String = ""
	@State var isCorrectPin: Bool = false
	@State var numberPadDisabled: Bool = false
	
	@State var vibrationTrigger: Int = 0
	@State var shakeTrigger: Int = 0
	@State var failCount: Int = 0
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Enter PIN to authenticate")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeSheet(.UserCancelled)
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
				.frame(minHeight: 0, maxHeight: 30)
			
			promptCircles()
			
			Spacer(minLength: 0)
				.frame(minHeight: 0, maxHeight: 30)
			
			NumberPadView(
				buttonPressed: numberPadButtonPressed,
				showHideButton: false,
				disabled: $numberPadDisabled
			)
		}
		.padding(.bottom)
		.vibrationFeedback(.error, trigger: vibrationTrigger)
	}
	
	@ViewBuilder
	func promptCircles() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
		
			let pinCount = pin.count
			ForEach(0 ..< PIN_LENGTH, id: \.self) { idx in
				if idx < pinCount {
					filledCircle()
				} else {
					emptyCircle()
				}
			}
			
		} // </HStack>
		.modifier(Shake(animatableData: CGFloat(shakeTrigger)))
	}
	
	@ViewBuilder
	func emptyCircle() -> some View {
		
		Image(systemName: "circle")
			.renderingMode(.template)
			.resizable()
			.scaledToFit()
			.frame(width: 24, height: 24)
			.foregroundColor(circleColor)
	}
	
	@ViewBuilder
	func filledCircle() -> some View {
		
		Image(systemName: "circle.fill")
			.renderingMode(.template)
			.resizable()
			.scaledToFit()
			.frame(width: 24, height: 24)
			.foregroundColor(circleColor)
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func numberPadButtonPressed(_ identifier: NumberPadButton) {
		log.trace("numberPadButtonPressed()")
		
		if pin.count < PIN_LENGTH {
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
	
	func handleIncorrectPin() {
		log.trace("handleIncorrectPin()")
		
		vibrationTrigger += 1
		withAnimation {
			shakeTrigger += 1
		}
		
		failCount += 1
		isCorrectPin = false
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			if failCount < 3 {
				pin = ""
				numberPadDisabled = false
			} else {
				closeSheet(.UserCancelled)
				SceneDelegate.get().lockWallet()
			}
		}
	}
	
	func handleCorrectPin() {
		log.trace("handleCorrectPin()")
		
		isCorrectPin = true
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			AppSecurity.shared.tryUnlockWithKeychain { (recoveryPhrase, _, _) in
				if let recoveryPhrase {
					closeSheet(.Authenticated(recoveryPhrase: recoveryPhrase))
				} else {
					closeSheet(.Failed)
				}
			}
		}
	}
	
	func closeSheet(_ result: EndResult) {
		log.trace("closeSheet(\(result))")
		
		willClose(result)
		smartModalState.close()
	}
}
