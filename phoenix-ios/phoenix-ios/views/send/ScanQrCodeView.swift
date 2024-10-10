import SwiftUI

fileprivate let filename = "ScanQrCodeView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ScanQrCodeView: View {
	
	enum Location {
		case sheet
		case embedded
	}
	let location: Location
	
	let didScanQrCode: (String) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			body_sheet()
			
		case .embedded:
			body_embedded()
		}
	}
	
	@ViewBuilder
	func body_sheet() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			sheetHeader()
			content()
		}
	}
	
	@ViewBuilder
	func body_embedded() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			Color.primaryBackground.frame(height: 15)
			content()
		}
		.navigationTitle("Scan QR Code")
		.navigationBarTitleDisplayMode(.inline)
		
	}
	
	@ViewBuilder
	func sheetHeader() -> some View {
		
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
		
		QrCodeScannerView { (request: String) in
			didScanQrCode(request)
		} ready: {
			didEnableCamera()
		}
	}
	
	func didEnableCamera() {
		log.trace("didEnableCamera()")
		
		UIAccessibility.post(
			notification: .announcement,
			argument: "Your camera is open to scan a QR code"
		)
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
