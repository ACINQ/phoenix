import SwiftUI

/// Sheet content with buttons:
///
/// Copy Text (Lightning invoice)
/// Copy Image (QR code)
///
struct CopyOptionsSheet: View {
	
	let textType: String
	let copyText: () -> Void
	let copyImage: () -> Void
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
			Button {
				smartModalState.close {
					copyText()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.on.square")
						.imageScale(.medium)
					Text("Copy Text")
					Spacer()
					Text(textType)
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 16,
					borderStroke: Color.appAccent
				)
			)
			.padding(.bottom, 8)
			
			Button {
				smartModalState.close {
					copyImage()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.on.square")
						.imageScale(.medium)
					Text("Copy Image")
					Spacer()
					Text("(QR code)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 16,
					borderStroke: Color.appAccent
				)
			)
		}
		.padding(.all)
	}
}
