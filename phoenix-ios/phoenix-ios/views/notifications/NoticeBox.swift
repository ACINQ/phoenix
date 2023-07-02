import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NoticeBox"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct NoticeBox<Content: View>: View {
	
	let backgroundColor: Color?
	let content: Content
	
	init(@ViewBuilder builder: () -> Content) {
		self.backgroundColor = nil
		self.content = builder()
	}
	
	init(backgroundColor: Color?, @ViewBuilder builder: () -> Content) {
		self.backgroundColor = backgroundColor
		self.content = builder()
	}
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			content
			Spacer(minLength: 0) // ensure content takes up full width of screen
		}
		.padding(12)
		.background(
			RoundedRectangle(cornerRadius: 8)
				.fill(backgroundColor ?? Color.clear)
		)
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.appAccent, lineWidth: 1)
		)
	}
}
