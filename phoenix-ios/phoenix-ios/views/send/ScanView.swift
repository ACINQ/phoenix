import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ScanView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ScanView: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	@ObservedObject var toast: Toast
	
	@Binding var paymentRequest: String?
	
	@State var clipboardHasString = UIPasteboard.general.hasStrings
	
	@State var showingImagePicker = false
	@State var imagePickerSelection: UIImage? = nil
	
	@State var displayWarning: Bool = false
	@State var ignoreScanner: Bool = false
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.shortSheetState) private var shortSheetState: ShortSheetState
	@Environment(\.popoverState) var popoverState: PopoverState
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	// Subtle timing bug:
	//
	// Steps to reproduce:
	// - scan payment without amount (and without trampoline support)
	// - warning popup is displayed
	// - keep QRcode within camera screen while tapping Confirm button
	//
	// What happens:
	// - the validate screen is not displayed as it should be
	//
	// Why:
	// - the warning popup is displayed
	// - user taps "confirm"
	// - we send IntentConfirmEmptyAmount to library
	// - QrCodeScannerView fires
	// - we send IntentParse to library
	// - library sends us ModelValidate
	// - library sends us ModelRequestWithoutAmount

	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			content
			
			if mvi.model is Scan.Model_LnurlServiceFetch {
				LnurlFetchNotice(
					title: NSLocalizedString("Fetching Lightning URL", comment: "Progress title"),
					onCancel: { didCancelLnurlServiceFetch() }
				)
				.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
			}
		}
		.frame(maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Scan a QR code", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .move(edge: .bottom)
			)
		)
		.onReceive(willEnterForegroundPublisher) { _ in
			willEnterForeground()
		}
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onChange(of: displayWarning) { newValue in
			if newValue {
				showWarning()
			}
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack {
			
			QrCodeScannerView {(request: String) in
				didScanQRCode(request)
			}
			
			Button {
				manualInput()
			} label: {
				Label {
					Text("Manual input")
				} icon: {
					Image(systemName: "square.and.pencil")
				}
			}
			.font(.title3)
			.padding(.top, 10)
			
			Divider()
				.padding([.top, .bottom], 10)
			
			Button {
				pasteFromClipboard()
			} label: {
				Label {
					Text("Paste from clipboard")
				} icon: {
					Image(systemName: "arrow.right.doc.on.clipboard")
				}
			}
			.font(.title3)
			.disabled(!clipboardHasString)
			
			Divider()
				.padding([.top, .bottom], 10)
			
			Button {
				showingImagePicker = true
			} label: {
				Label {
					Text("Choose image")
				} icon: {
					Image(systemName: "photo")
				}
			}
			.font(.title3)
			.padding(.bottom, 10)
			.onChange(of: imagePickerSelection) { _ in
				imagePickerDidChooseImage()
			}
		}
		.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
		.sheet(isPresented: $showingImagePicker) {
			 ImagePicker(image: $imagePickerSelection)
		}
	}
	
	func willEnterForeground() {
		log.trace("willEnterForeground()")
		
		clipboardHasString = UIPasteboard.general.hasStrings
	}
	
	func modelDidChange(_ newModel: Scan.Model) {
		log.trace("modelDidChange()")
		
		if ignoreScanner {
			// Flow:
			// - User taps "manual input"
			// - User types in something and taps "OK"
			// - We send Scan.Intent.Parse()
			// - We just got back a response from our request
			//
			ignoreScanner = false
		}
		
		if let _ = newModel as? Scan.Model_InvoiceFlow_DangerousRequest {
			displayWarning = true
		}
	}
	
	func didScanQRCode(_ request: String) {
		
		var isFetchingLnurl = false
		if let _ = mvi.model as? Scan.Model_LnurlServiceFetch {
			isFetchingLnurl = true
		}
		
		if !ignoreScanner && !isFetchingLnurl {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
	}
	
	func didCancelLnurlServiceFetch() {
		log.trace("didCancelLnurlServiceFetch()")
		
		mvi.intent(Scan.Intent_CancelLnurlServiceFetch())
	}
	
	func manualInput() {
		log.trace("manualInput()")
		
		ignoreScanner = true
		shortSheetState.display(dismissable: true) {
			
			ManualInput(mvi: mvi, ignoreScanner: $ignoreScanner)
		}
	}
	
	func pasteFromClipboard() {
		log.trace("pasteFromClipboard()")
		
		if let request = UIPasteboard.general.string {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
	}
	
	func showWarning() {
		log.trace("showWarning()")
		
		guard let model = mvi.model as? Scan.Model_InvoiceFlow_DangerousRequest else {
			return
		}
		
		displayWarning = false
		ignoreScanner = true
		popoverState.display(dismissable: false) {
			
			DangerousInvoiceAlert(
				model: model,
				intent: mvi.intent,
				ignoreScanner: $ignoreScanner
			)
		}
	}
	
	func imagePickerDidChooseImage() {
		log.trace("imagePickerDidChooseImage()")
		
		guard let uiImage = imagePickerSelection else { return }
		imagePickerSelection = nil
		
		if let ciImage = CIImage(image: uiImage) {
			var options: [String: Any]
			let context = CIContext()
			options = [CIDetectorAccuracy: CIDetectorAccuracyHigh]
			let qrDetector = CIDetector(ofType: CIDetectorTypeQRCode, context: context, options: options)
			
			if let orientation = ciImage.properties[(kCGImagePropertyOrientation as String)] {
				options = [CIDetectorImageOrientation: orientation]
			} else {
				options = [CIDetectorImageOrientation: 1]
			}
			let features = qrDetector?.features(in: ciImage, options: options)
			
			var qrCodeString: String? = nil
			if let features = features {
				for case let row as CIQRCodeFeature in features {
					if qrCodeString == nil {
						qrCodeString = row.messageString
					}
				}
			}
			
			if let qrCodeString = qrCodeString {
				mvi.intent(Scan.Intent_Parse(request: qrCodeString))
			} else {
				toast.pop(
					Text("Image doesn't contain a readable QR code.").multilineTextAlignment(.center).anyView,
					colorScheme: colorScheme.opposite,
					style: .chrome,
					duration: 10.0,
					location: .middle,
					showCloseButton: true
				)
			}
		}
	}
}

struct ManualInput: View, ViewName {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	@Binding var ignoreScanner: Bool
	
	@State var input = ""
	
	@Environment(\.shortSheetState) private var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Manual Input")
				.font(.title2)
				.padding(.bottom)
			
			Text(
				"""
				Enter a Lightning invoice, LNURL, or Lightning address \
				you want to send money to.
				"""
			)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField("", text: $input)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					input = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(input == "")
			}
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color(UIColor.separator), lineWidth: 1)
			)
			.padding(.bottom)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Button("Cancel") {
					didCancel()
				}
				.font(.title3)
				
				Divider()
					.frame(maxHeight: 20, alignment: Alignment.center)
					.padding([.leading, .trailing])
				
				Button("OK") {
					didConfirm()
				}
				.font(.title3)
			}
			
		} // </VStack>
		.padding()
	}
	
	func didCancel() -> Void {
		log.trace("[\(viewName)] didCancel()")
		
		shortSheetState.close {
			ignoreScanner = false
		}
	}
	
	func didConfirm() -> Void {
		log.trace("[\(viewName)] didConfirm()")
		
		let request = input.trimmingCharacters(in: .whitespacesAndNewlines)
		if request.count > 0 {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
		
		shortSheetState.close()
	}
}

struct DangerousInvoiceAlert: View, ViewName {

	let model: Scan.Model_InvoiceFlow_DangerousRequest
	let intent: (Scan.Intent) -> Void

	@Binding var ignoreScanner: Bool
	
	@Environment(\.popoverState) var popoverState: PopoverState

	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {

			Text("Warning")
				.font(.title2)
				.padding(.bottom)
			
			if model.reason is Scan.DangerousRequestReasonIsAmountlessInvoice {
				content_amountlessInvoice
			} else if model.reason is Scan.DangerousRequestReasonIsOwnInvoice {
				content_ownInvoice
			} else {
				content_unknown
			}

			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Spacer()
				
				Button("Cancel") {
					didCancel()
				}
				.font(.title3)
				.padding(.trailing)
					
				Button("Continue") {
					didConfirm()
				}
				.font(.title3)
				.disabled(isUnknownType())
			}
			.padding(.top, 30)
			
		} // </VStack>
		.padding()
	}
	
	@ViewBuilder
	var content_amountlessInvoice: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text(styled: NSLocalizedString(
				"""
				The invoice doesn't include an amount. This can be dangerous: \
				malicious nodes may be able to steal your payment. To be safe, \
				**ask the payee to specify an amount**  in the payment request.
				""",
				comment: "SendView"
			))
			.padding(.bottom)

			Text("Are you sure you want to pay this invoice?")
		}
	}
	
	@ViewBuilder
	var content_ownInvoice: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("The invoice is for you. You are about to pay yourself.")
		}
	}
	
	@ViewBuilder
	var content_unknown: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Something is amiss with this invoice...")
		}
	}
	
	func isUnknownType() -> Bool {
		
		if model.reason is Scan.DangerousRequestReasonIsAmountlessInvoice {
			return false
		} else if model.reason is Scan.DangerousRequestReasonIsOwnInvoice {
			return false
		} else {
			return true
		}
	}
	
	func didCancel() -> Void {
		log.trace("[\(viewName)] didCancel()")
		
		popoverState.close {
			ignoreScanner = false
		}
	}
	
	func didConfirm() -> Void {
		log.trace("[\(viewName)] didConfirm()")
		
		intent(Scan.Intent_InvoiceFlow_ConfirmDangerousRequest(
			request: model.request,
			paymentRequest: model.paymentRequest
		))
		popoverState.close()
	}
}
