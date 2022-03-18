import SwiftUI

/// Sheet content with buttons:
///
/// Copy Text (Lightning invoice)
/// Copy Image (QR code)
///
struct CopyOptionsSheet: View {
	
	let copyText: () -> Void
	let copyImage: () -> Void
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
			Button {
				shortSheetState.close {
					copyText()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.on.square")
						.imageScale(.medium)
					Text("Copy Text")
					Spacer()
					Text("(Lightning invoice)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					borderStroke: Color.appAccent
				)
			)
			.padding(.bottom, 8)
			
			Button {
				shortSheetState.close {
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
					borderStroke: Color.appAccent
				)
			)
		}
		.padding(.all)
	}
}
