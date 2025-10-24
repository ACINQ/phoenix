import SwiftUI
import PhoenixShared

fileprivate let filename = "CardOptionsSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct CardOptionsSheet: View {
	
	let didSelectVersion: (BoltCardVersion) -> Void
	let didSelectSimulator: () -> Void
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
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
			Text("Card options")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
			Spacer()
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			button_v1()
			button_v1_and_v2()
			button_v2()
		#if !targetEnvironment(simulator)
			button_simulator()
		#endif
		} // </VStack>
		.frame(maxWidth: .infinity)
		.padding(.all)
	}
	
	@ViewBuilder
	func button_v1() -> some View {
		
		Button {
			didTapVersion(BoltCardVersion.V1)
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Version 1 (lnurl-withdraw)")
						.font(.body)
					
					Text(verbatim: "https://phoenix.app/lnurlw?id=abc123&picc_data=X&cmac=Y")
						.font(.footnote)
						.lineLimit(2)
						.truncationMode(.tail)
						.foregroundColor(.secondary)
				} // </VStack>
				.multilineTextAlignment(.leading)
				
				Spacer()
			} // </HStack>
			.padding(.all, 8)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(.bordered)
		.buttonBorderShape(.roundedRectangle(radius: 16))
	}
	
	@ViewBuilder
	func button_v1_and_v2() -> some View {
		
		Button {
			didTapVersion(BoltCardVersion.V1AndV2)
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Version 1 & 2 (lnurl-withdraw + v2 param)")
					
					Text(verbatim: "https://phoenix.app/lnurlw?id=abc123&v2=alice@phoenixwallet.me&picc_data=X&cmac=Y")
						.font(.footnote)
						.lineLimit(2)
						.truncationMode(.tail)
						.foregroundColor(.secondary)
				} // </VStack>
				.multilineTextAlignment(.leading)
				
				Spacer()
			} // </HStack>
			.padding(.all, 8)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(.bordered)
		.buttonBorderShape(.roundedRectangle(radius: 16))
	}
	
	@ViewBuilder
	func button_v2() -> some View {
		
		Button {
			didTapVersion(BoltCardVersion.V2)
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Version 2 (bip-353 & onion messages)")
						.font(.body)
					
					Text(verbatim: "₿alice@phoenixwallet.me?picc_data=X&cmac=Y")
						.font(.footnote)
						.lineLimit(2)
						.truncationMode(.tail)
						.foregroundColor(.secondary)
				} // </VStack>
				.multilineTextAlignment(.leading)
				
				Spacer()
			} // </HStack>
			.padding(.all, 8)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(.bordered)
		.buttonBorderShape(.roundedRectangle(radius: 16))
	}
	
	@ViewBuilder
	func button_simulator() -> some View {
		
		Button {
			didTapSimulator()
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					Text("Write card for simulator")
						.font(.body)
					
					Text(verbatim: "Copy/paste info from simulator")
						.font(.footnote)
						.lineLimit(2)
						.truncationMode(.tail)
						.foregroundColor(.secondary)
				} // </VStack>
				.multilineTextAlignment(.leading)
				
				Spacer()
			} // </HStack>
			.padding(.all, 8)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(.bordered)
		.buttonBorderShape(.roundedRectangle(radius: 16))
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapVersion(_ version: BoltCardVersion) {
		log.trace("didTapVersion(\(version))")
		
		smartModalState.close(animationCompletion: {
			didSelectVersion(version)
		})
	}
	
	func didTapSimulator() {
		log.trace(#function)
		
		smartModalState.close(animationCompletion: {
			didSelectSimulator()
		})
	}
	
	func closeButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
