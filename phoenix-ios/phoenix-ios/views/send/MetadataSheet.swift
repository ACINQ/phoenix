import SwiftUI
import PhoenixShared

fileprivate let filename = "MetadataSheet"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MetadataSheet: View {
	
	let lnurlPay: LnurlPay.Intent
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Metadata")
					.font(.title3)
					.accessibilityAddTraits(.isHeader)
				
				Spacer()
				
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
				.accessibilityLabel("Close")
				.accessibilityHidden(smartModalState.dismissable)
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
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.leading, spacing: 20) {
			
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					
					Text("Short Description")
						.font(Font.system(.body, design: .serif))
						.bold()
					
					Text(lnurlPay.metadata.plainText)
						.multilineTextAlignment(.leading)
						.lineLimit(nil)
						.padding(.leading)
				}
				
				if let longDesc = lnurlPay.metadata.longDesc {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Long Description")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						Text(longDesc)
							.multilineTextAlignment(.leading)
							.lineLimit(nil)
							.padding(.leading)
					}
				}
				
				if let imagePng = lnurlPay.metadata.imagePng {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Image")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						if let data = Data(base64Encoded: imagePng), let image = UIImage(data: data) {
							Image(uiImage: image)
								.padding(.leading)
						} else {
							Text("Malformed PNG image data")
								.padding(.leading)
						}
					}
				}
				
				if let imageJpg = lnurlPay.metadata.imageJpg {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Image")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						if let data = Data(base64Encoded: imageJpg), let image = UIImage(data: data) {
							Image(uiImage: image)
								.padding(.leading)
						} else {
							Text("Malformed JPG image data")
								.padding(.leading)
						}
					}
				}
				
			} // </VStack>
			.padding(.horizontal)
			
		} // </ScrollView>
		.frame(maxHeight: 250)
		.padding(.vertical)
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
