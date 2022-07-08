import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "FetchActivityNotice"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

/// Designed to go into a small sub-view
///
struct FetchActivityNotice: View {
	
	let title: String
	let onCancel: () -> Void
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			Text(title)
			
			ZStack {
				Divider()
				HorizontalActivity(color: .appAccent, diameter: 10, speed: 1.6)
			}
			.frame(width: 125, height: 10)
			
			Button {
				didTapCancel()
			} label: {
				Text("Cancel")
			}
		}
		.padding()
		.background(Color(UIColor.systemBackground))
		.cornerRadius(16)
	}
	
	func didTapCancel() {
		log.trace("didTapCancel()")
		onCancel()
	}
}
