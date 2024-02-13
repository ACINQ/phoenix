import SwiftUI

fileprivate let filename = "PaymentWarningPopover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentWarningPopover: View {
	
	let cancelAction: ()->Void
	let continueAction: ()->Void
	
	enum ButtonHeight: Preference {}
	let buttonHeightReader = GeometryPreferenceReader(
		key: AppendValue<ButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var buttonHeight: CGFloat? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text(
				"""
				This is not a withdraw request. Do you want to **send money** to somebody?
				"""
			)
			.padding(.bottom, 15)
			
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				Spacer(minLength: 0)
				
				Button {
					cancelButtonTapped()
				} label: {
					Text("Cancel")
				}
				.font(.title3)
				.read(buttonHeightReader)
				
				if let buttonHeight {
					Divider()
						.frame(width: 1, height: buttonHeight)
						.background(Color.borderColor)
				}
				
				Button {
					continueButtonTapped()
				} label: {
					Text("Send Money")
				}
				.font(.title3)
				.read(buttonHeightReader)
			} // </HStack>
			.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
			
		} // </VStack>
		.padding(.all, 20)
	}
	
	func cancelButtonTapped() {
		log.trace("cancelButtonTapped()")
		
		popoverState.close(animationCompletion: {
			cancelAction()
		})
	}
	
	func continueButtonTapped() {
		log.trace("continueButtonTapped()")
		
		popoverState.close(animationCompletion: {
			continueAction()
		})
	}
}
