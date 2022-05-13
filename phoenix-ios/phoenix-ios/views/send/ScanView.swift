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
	
	@State var showingFullMenu = false
	@State var chevronPosition: AnimatedChevron.Position = .pointingUp
	@State var clipboardHasString = UIPasteboard.general.hasStrings
	@State var clipboardContent: Scan.ClipboardContent? = nil
	
	@State var showingImagePicker = false
	@State var imagePickerSelection: UIImage? = nil
	
	@State var displayWarning: Bool = false
	@State var ignoreScanner: Bool = false
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
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
			
			content()
			
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
		.onAppear {
			onAppear()
		}
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
	func content() -> some View {
		
		ZStack(alignment: Alignment.bottom) {
			
			QrCodeScannerView {(request: String) in
				didScanQRCode(request)
			}
			
			menu()
		}
		.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
		.sheet(isPresented: $showingImagePicker) {
			 ImagePicker(image: $imagePickerSelection)
		}
	}
	
	@ViewBuilder
	func menu() -> some View {

		VStack(alignment: HorizontalAlignment.center, spacing: 0) {

			ZStack(alignment: Alignment.top) {
				
				TopTab(
					color: Color.primaryBackground,
					size: CGSize(width: 72, height: 21)
				)
				.offset(y: 1)
				.onTapGesture {
					withAnimation {
						if showingFullMenu {
							showingFullMenu = false
							chevronPosition = .pointingUp
						} else {
							showingFullMenu = true
							chevronPosition = .pointingDown
						}
					}
				}
				
				AnimatedChevron(
					position: $chevronPosition,
					color: Color.appAccent,
					lineWidth: 22,
					lineThickness: 3,
					verticalOffset: 6
				)
				.offset(y: 7)
				
			} // </ZStack>
			.zIndex(1)
			
			menuOptions()
				.zIndex(0)
		}
	}
	
	@ViewBuilder
	func menuOptions() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			menuOption_pasteFromClipboard()
				.padding(.horizontal, 20)
				.padding(.top, 20)
				.padding(.bottom, 12)
			
			if showingFullMenu {
				VStack(alignment: HorizontalAlignment.center, spacing: 0) {
					
					Divider()
					
					menuOption_chooseImage()
						.padding(.horizontal, 20)
						.padding(.vertical, 12)
					
					Divider()
					
					menuOption_manualInput()
						.padding(.horizontal, 20)
						.padding(.vertical, 12)
				}
				.transition(.move(edge: .bottom).combined(with: .opacity))
			}
		}
		.frame(maxWidth: .infinity)
		.background(
			ZStack {
				Color.primaryBackground
					.edgesIgnoringSafeArea(.all)
				
				if AppDelegate.showTestnetBackground {
					Image("testnet_bg")
						.resizable(resizingMode: .tile)
						.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
				}
			}
		)
	}
	
	@ViewBuilder
	func menuOption_pasteFromClipboard() -> some View {
		
		Button {
			pasteFromClipboard()
		} label: {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Label {
					Text("Paste from clipboard")
				} icon: {
					Image(systemName: "arrow.right.doc.on.clipboard")
				}
				if clipboardContent != nil {
					Group {
						if let content = clipboardContent as? Scan.ClipboardContent_InvoiceRequest {
						
							let desc = content.paymentRequest.description_?.trimmingCharacters(in: .whitespaces) ?? ""
							
							if let msat = content.paymentRequest.amount {
								let amt = Utils.format(currencyPrefs, msat: msat)
							
								if desc.isEmpty {
									Text("Pay \(amt.string)")
								} else {
									Text("Pay \(amt.string) ") +
									Text(Image(systemName: "arrow.forward")) +
									Text(verbatim: " \(desc)")
								}
							} else if !desc.isEmpty {
								Text("Pay ") +
								Text(Image(systemName: "arrow.forward")) +
								Text(verbatim: " \(desc)")
							}
						
						} else if let content = clipboardContent as? Scan.ClipboardContent_LoginRequest {
							
							let title = content.auth.actionPromptTitle
							let domain = content.auth.url.host
							
							Text(verbatim: "\(title) ") +
							Text(Image(systemName: "arrow.forward")) +
							Text(verbatim: " \(domain)")
						
						} else if let content = clipboardContent as? Scan.ClipboardContent_LnurlRequest {
							
							let domain = content.url.host
							
							Text(verbatim: "LnUrl ") +
							Text(Image(systemName: "bolt.fill")) +
							Text(verbatim: " \(domain)")
						}
						
					} // </Group>
					.font(.footnote)
					.foregroundColor(.secondary)
					.lineLimit(1)
					.truncationMode(.tail)
					.padding(.top, 4)
					
				} // </if clipboardContent != nil>
			} // </VStack>
			
		} // </Button>
		.font(.title3)
		.disabled(!clipboardHasString)
	}
	
	@ViewBuilder
	func menuOption_chooseImage() -> some View {
		
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
		.onChange(of: imagePickerSelection) { _ in
			imagePickerDidChooseImage()
		}
	}
	
	@ViewBuilder
	func menuOption_manualInput() -> some View {
		
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
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		checkClipboard()
	}

	func willEnterForeground() {
		log.trace("willEnterForeground()")
		
		checkClipboard()
	}
	
	func checkClipboard() {
		if UIPasteboard.general.hasStrings {
			clipboardHasString = true
			
			guard let string = UIPasteboard.general.string else {
				// iOS lied to us ?!?
				clipboardHasString = false
				clipboardContent = nil
				return
			}
			
			let controller = mvi.controller as! AppScanController
			controller.inspectClipboard(string: string) {(result, _) in
				self.clipboardContent = result
			}
			
		} else {
			clipboardHasString = false
			clipboardContent = nil
		}
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
					alignment: .middle,
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
