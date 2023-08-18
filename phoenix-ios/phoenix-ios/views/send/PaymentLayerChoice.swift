import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentLayerChoice"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct PaymentLayerChoice: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@EnvironmentObject var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Label {
				Text("Choose payment type")
			} icon: {
				Image(systemName: "questionmark.circle")
			}
			.font(.title3)
			Spacer()
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Button {
				payWithL2()
			} label: {
				Label {
					Text("Pay with Lightning")
				} icon: {
					Image(systemName: "hare.fill")
				}
			}
			.padding(.bottom, 4)
			
			Label {
				Text(
					"""
					Lightning payments are fast and best suited for smaller payments.
					"""
				)
				.foregroundColor(.secondary)
				.font(.subheadline)
			} icon: {
				Image(systemName: "hare.fill")
					.foregroundColor(.clear)
			}
			.padding(.bottom, 25)
			
			Button {
				payWithL1()
			} label: {
				Label {
					Text("Pay on-chain")
				} icon: {
					Image(systemName: "tortoise.fill")
				}
			}
			.padding(.bottom, 4)
			
			Label {
				Text(
					"""
					On-chain transactions are slow, \
					but the fees can be less for large payments.
					"""
				)
				.foregroundColor(.secondary)
				.font(.subheadline)
			} icon: {
				Image(systemName: "hare.fill")
					.foregroundColor(.clear)
			}
		
		} // </VStack>
		.padding()
	}
	
	func payWithL1() {
		log.trace("payWithL1()")
		
		popoverState.close()
	}
	
	func payWithL2() {
		log.trace("payWithL2()")
		
		if let model = mvi.model as? Scan.Model_OnChainFlow,
		   let paymentRequest = model.uri.paymentRequest
		{
			mvi.intent(Scan.Intent_Parse(request: paymentRequest.write()))
		}
		popoverState.close()
	}
}
