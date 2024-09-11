import SwiftUI

fileprivate let filename = "ScanQrCodeSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ScanQrCodeSheet: View {
	
	let didScanQrCode: (String) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer()
				.frame(width: 30)
			
			Spacer() // Text should be centered
			Text("Scan QR Code")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer() // Text should be centered
			
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
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
		
		QrCodeScannerView {(request: String) in
			didScanQrCode(request)
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
