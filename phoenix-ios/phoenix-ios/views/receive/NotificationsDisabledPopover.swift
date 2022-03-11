import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NotificationsDisabledPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct NotificationsDisabledPopover: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled notifications for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"""
					This means you will not be notified if you receive a payment while \
					Phoenix is in the background.
					"""
				)
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
