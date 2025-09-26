import SwiftUI

fileprivate let filename = "DisablePinView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .info)
#endif

struct DisablePinView: View {
	
	enum EndResult: CustomStringConvertible {
		case UserCancelled
		case PinDisabled
		case Failed;
		
		var description: String {
			switch self {
				case .UserCancelled : return "UserCancelled"
				case .PinDisabled   : return "PinDisabled"
				case .Failed        : return "Failed"
			}
		}
	}
	
	let type: PinType
	let willClose: (PinType, EndResult) -> Void
	
	@State var pin: String = ""
	@State var isCorrectPin: Bool = false
	@State var numberPadDisabled: Bool = false
	
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
			.navigationTitle(self.navigationTitle)
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
		// To accomplish this, we put them both in a ZStack (where only one is visible).
		
		ZStack(alignment: Alignment.center) {
			
			let delay = retryDelayString()
			
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
				if pin.count < PIN_LENGTH {
					Text("Enter PIN to confirm")
						.foregroundStyle(invisible ? Color.clear : Color.primary)
				} else if isCorrectPin {
					Label("Correct", systemImage: "checkmark")
						.foregroundStyle(invisible ? Color.clear : Color.appPositive)
				} else {
					Label("Incorrect", systemImage: "xmark")
						.foregroundStyle(invisible ? Color.clear : Color.appNegative)
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
	
	var navigationTitle: String {
		
		switch type {
		case .lockPin:
			return String(localized: "Disable Lock PIN", comment: "Navigation bar title")
		case .spendingPin:
			return String(localized: "Disable Spending PIN", comment: "Navigation bar title")
		}
	}
	
	var circleColor: Color {
		
		if pin.count < PIN_LENGTH {
			return Color.primary.opacity(0.8)
		} else if isCorrectPin {
			return Color.appPositive
		} else {
			return Color.appNegative
		}
	}
	
	func retryDelayString() -> String? {
		
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
		
		invalidPin = Keychain.current.getInvalidPin(type) ?? InvalidPin.none()
		currentDate = Date.now
		
		if let delay = invalidPin.waitTimeFrom(currentDate) {
			
			numberPadDisabled = true
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				numberPadDisabled = false
				pin = ""
			}
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
		
		let correctPin = Keychain.current.getPin(type)
		if pin == correctPin {
			handleCorrectPin()
		} else {
			handleIncorrectPin()
		}
	}
	
	func handleCorrectPin() {
		log.trace("handleCorrectPin()")
		
		invalidPin = InvalidPin.none()
		
		isCorrectPin = true
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			Keychain.current.setInvalidPin(nil, type) { _ in }
			Keychain.current.setPin(nil, type) { error in
				let result: EndResult = (error == nil) ? .PinDisabled : .Failed
				dismissView(result)
			}
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
			pin = ""
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
		
		Keychain.current.setInvalidPin(newInvalidPin, type) { _ in }
		invalidPin = newInvalidPin
		currentDate = Date.now
		
		if let delay = invalidPin.waitTimeFrom(currentDate) {
			switch type {
			case .lockPin:
				dismissView(.UserCancelled)
				SceneDelegate.get().lockWallet()
				
			case .spendingPin:
				numberPadDisabled = true
				DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
					numberPadDisabled = false
				}
			}
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
