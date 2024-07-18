import SwiftUI
import PhoenixShared

fileprivate let filename = "ManualInput"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ManualInput: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@State var input = ""
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Manual Input")
				.font(.title2)
				.padding(.bottom)
				.accessibilityAddTraits(.isHeader)
			
			Text(
				"""
				Enter a Lightning invoice, LNURL, or Lightning address \
				you want to send money to.
				"""
			)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField("", text: $input)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					input = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.accessibilityLabel("Clear textfield")
				.isHidden(input == "")
			}
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			.padding(.bottom)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button("Cancel") {
					didCancel()
				}
				.font(.title3)
				
				Divider()
					.frame(maxHeight: 20, alignment: Alignment.center)
					.padding([.leading, .trailing])
				
				Button("OK") {
					didConfirm()
				}
				.font(.title3)
			}
			
		} // </VStack>
		.padding()
	}
	
	func didCancel() -> Void {
		log.trace("didCancel()")
		
		smartModalState.close()
	}
	
	func didConfirm() {
		log.trace("didConfirm()")
		
		let request = input.trimmingCharacters(in: .whitespacesAndNewlines)
		if request.count > 0 {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
		
		smartModalState.close()
	}
}

