import SwiftUI

fileprivate let filename = "ScanBitcoinAddressSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ScanBitcoinAddressSheet: View {
	
	let didScanQrCode: (String) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Spacer()
					.frame(width: 30)
				
				Spacer() // Text should be centered
				Text("Scan Bitcoin Address")
					.font(.headline)
				Spacer() // Text should be centered
				
				Button {
					didTapClose()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			
			} // </HStack>
			.padding([.leading, .trailing], 20)
			.padding([.top, .bottom], 8)
			
			QrCodeScannerView {(request: String) in
				didScanQrCode(request)
			}
		
		} // </VStack>
	}
	
	func didTapClose() -> Void {
		log.trace("didTapClose()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
