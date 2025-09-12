import SwiftUI

struct GlobalEnvironmentView<Content: View>: View {
	
	@ViewBuilder let content: () -> Content
	
	@StateObject var popoverState: PopoverState
	@StateObject var shortSheetState: ShortSheetState
	@StateObject var smartModalState: SmartModalState
	
	@State var popoverItem: PopoverItem? = nil
	@State var shortSheetItem: ShortSheetItem? = nil
	
	init(@ViewBuilder content: @escaping () -> Content) {
		self.content = content
		
		let ps = PopoverState()
		let sss = ShortSheetState()
		let sms = SmartModalState(popoverState: ps, shortSheetState: sss)
		
		self._popoverState = StateObject(wrappedValue: ps)
		self._shortSheetState = StateObject(wrappedValue: sss)
		self._smartModalState = StateObject(wrappedValue: sms)
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			content()
				.zIndex(0) // needed for proper animation
				.accessibilityHidden(shortSheetItem != nil || popoverItem != nil)
			
			if let shortSheetItem = shortSheetItem {
				ShortSheetWrapper(dismissable: shortSheetState.dismissable) {
					shortSheetItem.view
				}
				.zIndex(1) // needed for proper animation
			}

			if let popoverItem = popoverItem {
				PopoverWrapper(dismissable: popoverState.dismissable) {
					popoverItem.view
				}
				.zIndex(2) // needed for proper animation
			}
			
		} // </ZStack>
		.environmentObject(self.popoverState)
		.environmentObject(self.shortSheetState)
		.environmentObject(self.smartModalState)
		.environmentObject(GlobalEnvironment.deviceInfo)
		.environmentObject(GlobalEnvironment.deepLinkManager)
		.onReceive(shortSheetState.itemPublisher) { (item: ShortSheetItem?) in
			withAnimation {
				shortSheetItem = item
			}
		}
		.onReceive(popoverState.itemPublisher) { (item: PopoverItem?) in
			withAnimation {
				popoverItem = item
			}
		}
	}
}

