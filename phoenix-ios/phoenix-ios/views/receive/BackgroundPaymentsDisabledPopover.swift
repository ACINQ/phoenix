import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BackgroundPaymentsDisabledPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct BackgroundPaymentsDisabledPopover: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled background payments.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"""
					Normally Phoenix can receive payments as long as you have internet. \
					But with background payments disabled, Phoenix must be open and \
					in the foreground.
					"""
				)
				.lineLimit(nil)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				.padding(.bottom, 4)
				
				Text(
					"""
					To fix this re-enable notifications in iOS via: \
					Settings -> Phoenix -> Notifications
					"""
				)
				.lineLimit(nil)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				.padding(.bottom, 4)
				
				Text(
					"""
					Be sure to enable at least "Lock Screen" or "Notification Center".
					"""
				)
				.font(.subheadline)
				.lineLimit(nil)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				.padding(.bottom, 4)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					fixIt()
					popoverState.close()
				} label : {
					Text("Settings").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					popoverState.close()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close()
		})
	}
	
	func fixIt() -> Void {
		log.trace("fixIt()")
		
		if let bundleIdentifier = Bundle.main.bundleIdentifier,
		   let appSettings = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
		{
			if UIApplication.shared.canOpenURL(appSettings) {
				UIApplication.shared.open(appSettings)
			}
		}
	}
}
