import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CommentSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct CommentSheet: View {
	
	@State var text: String
	@Binding var comment: String
	
	let maxCommentLength: Int
	@State var remainingCount: Int
	
	let sendButtonAction: (() -> Void)?
	
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	
	init(comment: Binding<String>, maxCommentLength: Int, sendButtonAction: (() -> Void)? = nil) {
		self._comment = comment
		self.maxCommentLength = maxCommentLength
		self.sendButtonAction = sendButtonAction
		
		text = comment.wrappedValue
		remainingCount = maxCommentLength - comment.wrappedValue.count
	}
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Add optional comment")
					.font(.title3)
				Spacer()
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			}
			.padding(.horizontal)
			.padding(.vertical, 8)
			.background(
				Color(UIColor.secondarySystemBackground)
					.cornerRadius(15, corners: [.topLeft, .topRight])
			)
			.padding(.bottom, 4)
			
			content
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Your comment will be sent when you pay.")
				.font(.callout)
				.foregroundColor(.secondary)
				.padding(.bottom)
			
			TextEditor(text: $text)
				.frame(maxWidth: .infinity, maxHeight: 75)
				.padding(.all, 4)
				.background(
					RoundedRectangle(cornerRadius: 4)
						.stroke(Color.textFieldBorder, lineWidth: 1)
				)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("\(remainingCount) remaining")
					.foregroundColor(remainingCount >= 0 ? Color.primary : Color.appNegative)
				
				Spacer()
				
				Button {
					clearButtonTapped()
				} label: {
					Text("Clear")
				}
				.disabled(text.count == 0)
			}
			.padding(.top, 4)
			
			if sendButtonAction != nil {
				sendButton
					.padding(.top, 16)
			}
			
		} // </VStack>
		.padding()
		.onChange(of: text) {
			textDidChange($0)
		}
	}
	
	@ViewBuilder
	var sendButton: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				sendButtonTapped()
			} label: {
				HStack {
					Image("ic_send")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.foregroundColor(Color.white)
						.frame(width: 22, height: 22)
					Text("Pay")
						.font(.title2)
						.foregroundColor(Color.white)
				}
				.padding(.top, 4)
				.padding(.bottom, 5)
				.padding([.leading, .trailing], 24)
			}
			.buttonStyle(ScaleButtonStyle(
				cornerRadius: 100,
				backgroundFill: Color.appAccent,
				disabledBackgroundFill: Color.gray
			))
			Spacer()
			
		} // </HStack>
	}
	
	func textDidChange(_ newText: String) {
		log.trace("textDidChange()")
		
		if newText.count <= maxCommentLength {
			comment = newText
		} else {
			let endIdx = newText.index(newText.startIndex, offsetBy: maxCommentLength)
			let substr = newText[newText.startIndex ..< endIdx]
			comment = String(substr)
		}
		
		remainingCount = maxCommentLength - newText.count
	}
	
	func clearButtonTapped() {
		log.trace("clearButtonTapped()")
		
		text = ""
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
	
	func sendButtonTapped() {
		log.trace("sendButtonTapped()")
		
		smartModalState.close {
			sendButtonAction!()
		}
	}
}
