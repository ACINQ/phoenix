import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentLayerChoice"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentLayerChoice: View {
	
	let didChooseL1: () -> Void
	let didChooseL2: () -> Void
	
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
				didChooseL2()
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
				didChooseL1()
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
}
