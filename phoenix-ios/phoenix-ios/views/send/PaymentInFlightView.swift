import SwiftUI
import PhoenixShared


struct PaymentInFlightView: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>

	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
			VStack {
				Text("Sending Payment...")
					.font(.title)
					.padding()
			}
		}
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.navigationTitle(NSLocalizedString("Sending payment", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
}
