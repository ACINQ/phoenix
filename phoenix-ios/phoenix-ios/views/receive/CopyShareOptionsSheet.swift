import SwiftUI

/// Sheet content with buttons:
///
/// Copy: Lightning invoice   (text)
/// Copy: QR code            (image)
///
struct CopyShareOptionsSheet: View {
	
	enum ActionType {
		case copy
		case share
	}
	
	let type: ActionType
	let sources: [SourceInfo]
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			title()
			ForEach(sources.indices, id: \.self) { idx in
				button(sources[idx])
			}
		} // </VStack>
		.padding(.all)
	}
	
	@ViewBuilder
	func title() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 4)
			switch type {
			case .copy:
				Text("Copy")
			case .share:
				Text("Share")
			}
			Spacer(minLength: 4)
		}
		.font(.title2)
	}
	
	@ViewBuilder
	func button(_ source: SourceInfo) -> some View {
		
		Button {
			smartModalState.close {
				source.callback()
			}
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				
				Group {
					switch type {
					case .copy:
						Image(systemName: "square.on.square")
					case .share:
						Image(systemName: "square.and.arrow.up")
					}
				}
				.imageScale(.large)
				.font(.title3)
				.padding(.trailing, 4)

				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text(source.title)
					if let subtitle = source.subtitle {
						Text(subtitle)
							.font(.footnote)
							.lineLimit(1)
							.truncationMode(.tail)
							.foregroundColor(.secondary)
					}
				}
				
				Spacer()
				
				Group {
					switch source.type {
					case .text:
						Text("(text)")
					case .image:
						Text("(image)")
					}
				}
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
}
