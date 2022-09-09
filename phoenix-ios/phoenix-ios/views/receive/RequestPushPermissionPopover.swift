import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RequestPushPermissionPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum PushPermissionPopoverResponse: String {
	case ignored
	case denied
	case accepted
}

struct RequestPushPermissionPopover: View {
	
	let callback: (PushPermissionPopoverResponse) -> Void
	
	@State private var userIsIgnoringPopover: Bool = true
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Would you like to be notified when somebody sends you a payment?")
			}
			.padding(.bottom)
			.accessibilityAddTraits(.isHeader)
			.accessibilitySortPriority(1)
			
			HStack {
				Button {
					didDeny()
				} label : {
					Text("No").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					didAccept()
				} label : {
					Text("Yes").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(popoverState.publisher) { item in
			if item  == nil {
				willClose()
			}
		}
	}
	
	func didDeny() -> Void {
		log.trace("didDeny()")
		
		callback(.denied)
		userIsIgnoringPopover = false
		popoverState.close()
	}
	
	func didAccept() -> Void {
		log.trace("didAccept()")
		
		callback(.accepted)
		userIsIgnoringPopover = false
		popoverState.close()
	}
	
	func willClose() -> Void {
		log.trace("willClose()")
		
		if userIsIgnoringPopover {
			callback(.ignored)
		}
	}
}
