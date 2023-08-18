import SwiftUI

/// Sheet content with buttons:
///
/// Share Text (Lightning invoice)
/// Share Image (QR code)
///
struct ShareOptionsSheet: View {
	
	let textType: String
	let shareText: () -> Void
	let shareImage: () -> Void
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
			Button {
				smartModalState.close {
					shareText()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.and.arrow.up")
						.imageScale(.medium)
					Text("Share Text")
					Spacer()
					Text(verbatim: textType)
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
					shareImage()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.and.arrow.up")
						.imageScale(.medium)
					Text("Share Image")
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

