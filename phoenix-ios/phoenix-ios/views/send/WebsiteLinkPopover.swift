import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "WebsiteLinkPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct WebsiteLinkPopover: View {
	
	let link: URL
	let copyAction: (URL)->Void
	let openAction: (URL)->Void
	
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
			
			Text("This appears to be a website (not a lightning invoice):")
				.padding(.bottom, 10)
			
			Text(verbatim: link.absoluteString)
				.foregroundColor(Color(UIColor.link))
				.multilineTextAlignment(.leading)
				.padding(.bottom, 20)
			
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				Spacer(minLength: 0)
				
				Button {
					copyLink()
				} label: {
					Label { Text("Copy") } icon: { Image(systemName: "square.on.square") }
				}
				.read(buttonHeightReader)
				
				if let buttonHeight {
					Divider()
						.frame(width: 1, height: buttonHeight)
						.background(Color.borderColor)
				}
				
				Button {
					openLink()
				} label: {
					Label { Text("Open") } icon: { Image(systemName: "network") }
				}
				.read(buttonHeightReader)
			}
			.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
		}
		.padding(20)
	}
	
	func copyLink() {
		log.trace("copyLink()")
		
		popoverState.close(animationCompletion: {
			copyAction(self.link)
		})
	}
	
	func openLink() {
		log.trace("openLink()")
		
		popoverState.close(animationCompletion: {
			openAction(self.link)
		})
	}
}
