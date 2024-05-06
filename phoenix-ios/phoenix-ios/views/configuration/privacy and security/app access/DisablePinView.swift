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
	
	let willClose: (EndResult) -> Void
	let correctPin: String
	
	@State var pin: String = ""
	@State var numberPadDisabled: Bool = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(willClose: @escaping (EndResult) -> Void) {
		self.willClose = willClose
		self.correctPin = AppSecurity.shared.getCustomPin() ?? "1234567890"
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Disable PIN", comment: "Navigation bar title"))
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
	}
	
	@ViewBuilder
	func prompt() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Spacer()
			
			Group {
				if pin.count < PIN_LENGTH {
					Label {
						Text("Enter PIN to confirm")
					} icon: {
						Image(systemName: "checkmark").foregroundStyle(Color.clear)
					}
				} else if pin == correctPin {
					Label("Correct", systemImage: "checkmark").foregroundStyle(Color.appPositive)
				} else {
					Label("Incorrect", systemImage: "xmark").foregroundStyle(Color.appNegative)
				}
			}
			.padding(.bottom)
			
			promptCircles()
			
			Spacer()
		} // </VStack>
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
	
	var circleColor: Color {
		return Color.primary.opacity(0.8)
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
		
		if pin == correctPin {
			handleCorrectPin()
		} else {
			handleIncorrectPin()
		}
	}
	
	func handleIncorrectPin() {
		log.trace("handleIncorrectPin()")
		
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			self.dismissView(.UserCancelled)
		}
	}
	
	func handleCorrectPin() {
		log.trace("handleCorrectPin()")
		
		numberPadDisabled = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
			AppSecurity.shared.setCustomPin(pin: nil) { error in
				let result: EndResult = (error == nil) ? .PinDisabled : .Failed
				self.dismissView(result)
			}
		}
	}
	
	func didTapCancelButton() {
		log.trace("didTapCancelButton()")
		
		dismissView(.UserCancelled)
	}
	
	func dismissView(_ result: EndResult) {
		log.info("dismissView(\(result))")
		
		willClose(result)
		presentationMode.wrappedValue.dismiss()
	}
}
