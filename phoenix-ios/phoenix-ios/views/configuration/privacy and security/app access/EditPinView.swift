import SwiftUI

fileprivate let filename = "EditPinView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .info)
#endif

struct EditPinView: View {
	
	enum EndResult: CustomStringConvertible {
		case UserCancelled
		case PinChanged
		case Failed;
		
		var description: String {
			switch self {
				case .UserCancelled : return "UserCancelled"
				case .PinChanged    : return "PinChanged"
				case .Failed        : return "Failed"
			}
		}
	}
	
	let type: PinType
	let willClose: (PinType, EndResult) -> Void
	
	enum EditMode {
		case Pin0
		case Pin1
		case Pin2
	}
	
	@State var editMode: EditMode = .Pin0
	@State var pin0: String = ""
	@State var pin1: String = ""
	@State var pin2: String = ""
	
	@State var numberPadDisabled: Bool = false
	@State var isCorrectPin: Bool = false
	@State var isMismatch: Bool = false
	
	@State var vibrationTrigger: Int = 0
	@State var shakeTrigger: Int = 0
	
	@State var invalidPin: InvalidPin = InvalidPin.none() // updated in onAppear
	
	let timer = Timer.publish(every: 0.5, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(String(localized: "Edit PIN", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarBackButtonHidden(true)
			.navigationBarItems(leading: cancelButton())
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			Color.primaryBackground.edgesIgnoringSafeArea(.all)
			content()
		}
		.onAppear {
			onAppear()
		}
		.onReceive(timer) { _ in
			currentDate = Date()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			numberPadHeader()
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
	func numberPadHeader() -> some View {
		
		// When we switch between the `prompt` and the `countdown`,
		// we don't want the other UI components to change position.
		// In other words, they shouldn't move up or down on the screen.
		// To accomplish this, we make sure they both have the same height.
		
		ZStack(alignment: Alignment.center) {
			
			let delay = retryDelay()
			
			countdown(delay ?? "1 second", invisible: delay == nil)
			prompt(invisible: delay != nil)
		}
	}
	
	@ViewBuilder
	func countdown(_ delay: String, invisible: Bool) -> some View {
		
		Text("Retry in \(delay)")
			.font(.headline)
			.foregroundStyle(invisible ? Color.clear : Color.primary)
	}
	
	@ViewBuilder
	func prompt(invisible: Bool) -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Spacer()
			
			Group {
				switch editMode {
				case .Pin0:
					if pin0.count < PIN_LENGTH {
						Text("Enter PIN to confirm")
							.foregroundStyle(invisible ? Color.clear : Color.primary)
					} else if isCorrectPin {
						Label("Correct", systemImage: "checkmark")
							.foregroundStyle(invisible ? Color.clear : Color.appPositive)
					} else {
						Label("Incorrect", systemImage: "xmark")
							.foregroundStyle(invisible ? Color.clear : Color.appNegative)
					}
					
				case .Pin1:
					if isMismatch {
						Text("PIN mismatch!")
							.foregroundStyle(invisible ? Color.clear : Color.appNegative)
					} else {
						Text("Enter new PIN")
							.foregroundStyle(invisible ? Color.clear : Color.primary)
					}
				case .Pin2:
					if pin2.count < PIN_LENGTH {
						Text("Confirm new PIN")
							.foregroundStyle(invisible ? Color.clear : Color.primary)
					} else {
						Text(verbatim: "✔️")
							.foregroundStyle(invisible ? Color.clear : Color.appPositive)
					}
				}
			}
			.padding(.bottom)
			
			promptCircles(invisible)
			
			Spacer()
		} // </VStack>
	}
	
	@ViewBuilder
	func promptCircles(_ invisible: Bool) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
			
			let pinCount = self.pinCount
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
	
	@ViewBuilder
	func cancelButton() -> some View {
		
		Button {
			didTapCancelButton()
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Image(systemName: "chevron.backward")
					.font(.headline.weight(.semibold))
				Text("Cancel")
					.padding(.leading, 3)
			}
			.foregroundColor(.appNegative)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var pinCount: Int {
		switch editMode {
			case .Pin0: return pin0.count
			case .Pin1: return pin1.count
			case .Pin2: return pin2.count
		}
	}
	
	var circleColor: Color {
		
		switch editMode {
		case .Pin0:
			if pin0.count < PIN_LENGTH {
				return Color.primary.opacity(0.8)
			} else if isCorrectPin {
				return Color.appPositive
			} else {
				return Color.appNegative
			}
			
		case .Pin1:
			if isMismatch {
				return Color.appNegative
			} else {
				return Color.primary.opacity(0.8)
			}
			
		case .Pin2:
			return Color.primary.opacity(0.8)
			
		}
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
		
		invalidPin = AppSecurity.shared.getInvalidPin(type) ?? InvalidPin.none()
		currentDate = Date.now
		
		if let delay = invalidPin.waitTimeFrom(currentDate) {
			
			numberPadDisabled = true
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				numberPadDisabled = false
				pin1 = ""
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func numberPadButtonPressed(_ identifier: NumberPadButton) {
		log.trace("numberPadButtonPressed()")
		
		if pin0.count < PIN_LENGTH {
			if identifier == .delete {
				pin0 = String(pin0.dropLast())
			} else {
				pin0 += identifier.rawValue
			}
			
			if pin0.count == PIN_LENGTH {
				verifyPin()
			}
			
		} else if pin1.count < PIN_LENGTH {
			if identifier == .delete {
				pin1 = String(pin1.dropLast())
			} else {
				pin1 += identifier.rawValue
			}
			
			if pin1.count == PIN_LENGTH {
				nextPin()
			}
			
		} else if pin2.count < PIN_LENGTH {
			if identifier == .delete {
				pin2 = String(pin2.dropLast())
			} else {
				pin2 += identifier.rawValue
			}
			
			if pin2.count == PIN_LENGTH {
				checkPin()
			}
		}
	}
		
	func verifyPin() {
		log.trace("verifyPin(type: \(type))")
		
		let correctPin = AppSecurity.shared.getPin(type)
		if pin0 == correctPin {
			handleCorrectPin()
		} else {
			handleIncorrectPin()
		}
	}
	
	func handleCorrectPin() {
		log.trace("handleCorrectPin()")
		
		numberPadDisabled = true
		isCorrectPin = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			numberPadDisabled = false
			editMode = .Pin1
		}
	}
	
	func handleIncorrectPin() {
		log.trace("handleIncorrectPin()")
		
		vibrationTrigger += 1
		withAnimation {
			shakeTrigger += 1
		}
		
		isCorrectPin = false
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			pin0 = ""
			numberPadDisabled = false
			incrementInvalidPin()
		}
	}
	
	func incrementInvalidPin() {
		log.trace("incrementInvalidPin()")
		
		let newInvalidPin: InvalidPin
		if invalidPin.count > 0 && invalidPin.elapsed > 24.hours() {
			// We reset the count after 24 hours
			newInvalidPin = InvalidPin.one()
		} else {
			newInvalidPin = invalidPin.increment()
		}
		
		AppSecurity.shared.setInvalidPin(newInvalidPin, type) { _ in }
		invalidPin = newInvalidPin
		currentDate = Date.now
		
		if newInvalidPin.hasWaitTime(currentDate) && type == .lockPin {
			
			dismissView(.UserCancelled)
			SceneDelegate.get().lockWallet()
		}
	}
	
	func nextPin() {
		log.trace("nextPin()")
		
		// It looks weird to **immediately** transition the UI to Pin2.
		// The last circle never gets filled in, and it doesn't feel complete.
		// So it feels cleaner to do the transition on a timer.
		
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			numberPadDisabled = false
			editMode = .Pin2
		}
	}
	
	func checkPin() {
		log.trace("checkPin()")
		
		if pin1 == pin2 {
			savePinAndDismiss()
		} else {
			triggerMismatch()
		}
	}
	
	func triggerMismatch() {
		log.trace("triggerMismatch()")
		
		vibrationTrigger += 1
		withAnimation {
			shakeTrigger += 1
		}
		
		editMode = .Pin1
		isMismatch = true
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
			
			isMismatch = false
			numberPadDisabled = false
			pin1 = ""
			pin2 = ""
		}
	}
	
	func savePinAndDismiss() {
		log.trace("savePinAndDismiss()")
		
		AppSecurity.shared.setInvalidPin(nil, type) { _ in }
		AppSecurity.shared.setPin(pin1, type) { error in
			let result: EndResult = (error == nil) ? .PinChanged : .Failed
			dismissView(result)
		}
	}
	
	func didTapCancelButton() {
		log.trace("didTapCancelButton()")
		
		dismissView(.UserCancelled)
	}
	
	func dismissView(_ result: EndResult) {
		log.info("dismissView(\(result))")
		
		willClose(type, result)
		presentationMode.wrappedValue.dismiss()
	}
}
