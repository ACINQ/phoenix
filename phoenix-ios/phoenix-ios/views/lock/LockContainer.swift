import SwiftUI

struct LockContainer: View {
	
	// If we need popoverState or shortSheetState,
	// then we can easily add them here.
	// For now, they're not needed in the LockView.
	
	@ViewBuilder
	var body: some View {
		
		LockView()
			.environmentObject(GlobalEnvironment.deviceInfo)
	}
}
