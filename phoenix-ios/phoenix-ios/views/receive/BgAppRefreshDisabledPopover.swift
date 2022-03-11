import SwiftUI


struct BgAppRefreshDisabledPopover: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled Background App Refresh for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"""
					This means you will not be able to receive payments when Phoenix \
					is in the background. To receive payments, Phoenix must be open and in the foreground.
					"""
				)
				.lineLimit(nil)
				.minimumScaleFactor(0.5) // problems with "foreground" being truncated
				.padding(.bottom, 4)
				
				Text(
					"""
					To fix this re-enable Background App Refresh via: \
					Settings -> General -> Background App Refresh
					"""
				)
				
			}
			.padding(.bottom)
			
			HStack {
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
}
