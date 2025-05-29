import SwiftUI

fileprivate let filename = "SetNewPinView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .info)
#endif

struct SetNewPinView: View {
	
	enum EndResult: CustomStringConvertible {
		case UserCancelled
		case PinSet
		case Failed;
		
		var description: String {
			switch self {
				case .UserCancelled : return "UserCancelled"
				case .PinSet        : return "PinSet"
				case .Failed        : return "Failed"
			}
		}
	}
	
	let type: PinType
	let willClose: (PinType, EndResult) -> Void
	
	enum EditMode {
		case Pin1
		case Pin2
	}
	
	@State var editMode: EditMode = .Pin1
	@State var pin1: String = ""
	@State var pin2: String = ""
	
	@State var numberPadDisabled: Bool = false
	@State var isMismatch: Bool = false
	
	@State var vibrationTrigger: Int = 0
	@State var shakeTrigger: Int = 0
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(String(localized: "Set PIN", comment: "Navigation bar title"))
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			prompt()
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
	func prompt() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Spacer()
			
			Group {
				switch editMode {
				case .Pin1:
					if isMismatch {
						Text("PIN mismatch!").foregroundStyle(Color.appNegative)
					} else {
						Text("Enter new PIN")
					}
				case .Pin2:
					if pin2.count < PIN_LENGTH {
						Text("Confirm new PIN")
					} else {
						Text(verbatim: "✔️").foregroundStyle(Color.appPositive)
					}
				}
			}
			.font(.title2)
			.padding(.bottom)
			
			promptCircles()
			
			Spacer()
		} // </VStack>
	}
	
	@ViewBuilder
	func promptCircles() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
			
			let pinCount = self.pinCount
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
			case .Pin1: return pin1.count
			case .Pin2: return pin2.count
		}
	}
	
	var circleColor: Color {
		
		if isMismatch {
			return Color.appNegative
		} else {
			return Color.primary.opacity(0.8)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func numberPadButtonPressed(_ identifier: NumberPadButton) {
		log.trace("numberPadButtonPressed()")
		
		if pin1.count < PIN_LENGTH {
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
	
	func nextPin() {
		log.trace("nextPin()")
		
		// It looks weird to **immediately** transition the UI to Pin2.
		// The last circle never gets filled in, and it doesn't feel complete.
		// So it feels cleaner to do the transition on a timer.
		
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			
			self.numberPadDisabled = false
			self.editMode = .Pin2
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
		
		AppSecurity.shared.setPin(pin1, type) { error in
			if error != nil {
				self.dismissView(.Failed)
			} else {
				if type == .lockPin {
					// Enabling the lock pin implies disabling the passcode fallback.
					AppSecurity.shared.setPasscodeFallback(enabled: false) { _ in }
				}
				
				// If there still exists an InvalidPin in the keychain, it should be cleared now.
				AppSecurity.shared.setInvalidPin(nil, type) { _ in
					self.dismissView(.PinSet)
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
