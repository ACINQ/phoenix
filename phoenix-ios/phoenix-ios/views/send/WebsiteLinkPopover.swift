import SwiftUI

fileprivate let filename = "WebsiteLinkPopover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WebsiteLinkPopover: View {
	
	let link: URL
	let didCopyLink: (() -> Void)?
	let didOpenLink: (() -> Void)?
	
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
				.disabled(!canOpenLink)
			}
			.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
		}
		.padding(20)
	}
	
	var canOpenLink: Bool {
		return UIApplication.shared.canOpenURL(link)
	}
	
	func copyLink() {
		log.trace("copyLink()")
		
		popoverState.close(animationCompletion: {
			UIPasteboard.general.string = link.absoluteString
			if let didCopyLink {
				didCopyLink()
			}
		})
	}
	
	func openLink() {
		log.trace("openLink()")
		
		// Strange SwiftUI bug:
		// https://forums.developer.apple.com/forums/thread/750514
		//
		// Simply declaring the following environment variable:
		// @Environment(\.openURL) var openURL: OpenURLAction
		//
		// Somehow causes an infinite loop in SwiftUI !
		// I encountered this multiple times while testing,
		// and the suggested workaround is to use plain UIApplication calls.
		/*
		openURL(url)
		*/
		
		popoverState.close(animationCompletion: {
			if UIApplication.shared.canOpenURL(link) {
				UIApplication.shared.open(link)
			}
			if let didOpenLink {
				didOpenLink()
			}
		})
	}
}
