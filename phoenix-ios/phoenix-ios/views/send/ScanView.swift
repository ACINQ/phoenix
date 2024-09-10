import SwiftUI
import PhoenixShared

fileprivate let filename = "ScanView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ScanView: View {
	
	let location: SendView.Location
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	@ObservedObject var toast: Toast
	
	@State var showingFullMenu = false
	@State var chevronPosition: AnimatedChevron.Position = .pointingUp
	@State var clipboardHasString = UIPasteboard.general.hasStrings
	@State var clipboardContent: Scan.ClipboardContent? = nil
	
	@State var showingImagePicker = false
	@State var imagePickerResult: PickerResult? = nil
	
	@State var ignoreScanner: Bool = false
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	@State var voiceOverEnabled = UIAccessibility.isVoiceOverRunning
	let voiceOverStatusPublisher = NotificationCenter.default.publisher(for:
		UIAccessibility.voiceOverStatusDidChangeNotification
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

	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------

	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			content()
			
			if mvi.model is Scan.Model_LnurlServiceFetch {
				FetchActivityNotice(
					title: NSLocalizedString("Fetching Lightning URL", comment: "Progress title"),
					onCancel: { didCancelLnurlServiceFetch() }
				)
				.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
			}
		}
		.frame(maxHeight: .infinity)
		.navigationTitle(NSLocalizedString("Scan a QR code", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .move(edge: .bottom)
			)
		)
		.onReceive(voiceOverStatusPublisher) { _ in
			voiceOverStatusChanged()
		}
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack(alignment: Alignment.bottom) {
			
			QrCodeScannerView { (request: String) in
				didScanQRCode(request)
			} ready: {
				didEnableCamera()
			}
			
			menu()
		}
		.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
		.sheet(isPresented: $showingImagePicker) {
			 ImagePicker(copyFile: false, result: $imagePickerResult)
		}
	}
	
	@ViewBuilder
	func menu() -> some View {

		VStack(alignment: HorizontalAlignment.center, spacing: 0) {

			if !voiceOverEnabled {
				menuButton()
			}
			
			menuOptions()
				.zIndex(0)
		}
	}
	
	@ViewBuilder
	func menuButton() -> some View {
		
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
		.accessibilityElement()
		.accessibilityLabel(showingFullMenu ? "Less options" : "More options")
		.accessibilityAddTraits(.isButton)
	}
	
	@ViewBuilder
	func menuOptions() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			menuOption_paste()
				.padding(.horizontal, 20)
				.padding(.top, 20)
				.padding(.bottom, 12)
				.accessibilitySortPriority(4)
			
			Divider()
			
			menuOption_contacts()
				.padding(.horizontal, 20)
				.padding(.vertical, 12)
				.accessibilitySortPriority(3)
			
			if showingFullMenu || voiceOverEnabled {
				VStack(alignment: HorizontalAlignment.center, spacing: 0) {
					
					Divider()
					
					menuOption_chooseImage()
						.padding(.horizontal, 20)
						.padding(.vertical, 12)
						.accessibilitySortPriority(2)
					
					Divider()
					
					menuOption_manualInput()
						.padding(.horizontal, 20)
						.padding(.vertical, 12)
						.accessibilitySortPriority(1)
				}
				.transition(.move(edge: .bottom).combined(with: .opacity))
			}
		}
		.frame(maxWidth: .infinity)
		.background(
			ZStack {
				Color.primaryBackground
					.ignoresSafeArea()

				if BusinessManager.showTestnetBackground {
					Image("testnet_bg")
						.resizable(resizingMode: .tile)
						.ignoresSafeArea()
						.accessibilityHidden(true)
				}
			}
		) // </.background>
	}
	
	@ViewBuilder
	func menuOption_paste() -> some View {

    //	menuOption_paste_ios16() // this looks horrible; thanks apple :(
        menuOption_paste_pre16()
	}

	@ViewBuilder
	func menuOption_paste_ios16() -> some View {

		PasteButton(payloadType: String.self) { strings in
			if let string = strings.first {
				pasteFromClipboard_ios16(string)
			}
		}
		.buttonStyle(.borderedProminent)
		.buttonBorderShape(ButtonBorderShape.capsule)

		// There's not a lot of customization we can do here.
		// According to the docs:
		//
		// > you can use view modifiers like buttonBorderShape(_:), labelStyle(_:), and tint(_:)
		// > to customize the button in some contexts.
		//
		// However, we *CANNOT* remove the background color of the button.
		// .tint(.clear)             // <- doesn't work
		// .ting(.primaryBackground) // <- also doesn't work
		//
		// This means we cannot make the PasteButton match our other buttons.
		// Which means, unless we want the UI to look ugly,
		// we have to completely re-design the entire button menu...
	}

	@ViewBuilder
	func menuOption_paste_pre16() -> some View {
		
		Button {
			pasteFromClipboard_pre16()
		} label: {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Label {
					Text("Paste from clipboard")
				} icon: {
					Image(systemName: "arrow.right.doc.on.clipboard")
				}

				menuOption_paste_clipboardPreview()
			} // </VStack>

		} // </Button>
		.font(.title3)
		.disabled(!clipboardHasString)
	}

	@ViewBuilder
	func menuOption_paste_clipboardPreview() -> some View {

		if clipboardContent != nil {
			Group {
				if let content = clipboardContent as? Scan.ClipboardContent_Bolt11InvoiceRequest {

					let desc = content.invoice.description_?.trimmingCharacters(in: .whitespaces) ?? ""

					if let msat = content.invoice.amount {
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
					} else {
						Text("Pay Invoice")
					}

				} else if let content = clipboardContent as? Scan.ClipboardContent_BitcoinRequest {

					let addrInfo: BitcoinUri = content.address

					let desc: String = {
						return addrInfo.label ?? addrInfo.message
					}()?.trimmingCharacters(in: .whitespaces) ?? ""

					if let sat = addrInfo.amount {
						let amt = Utils.format(currencyPrefs, sat: sat)

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
					} else {
						let addr = addrInfo.address.prefix(6) + "..." + addrInfo.address.suffix(6)

						Text("Pay ") +
						Text(Image(systemName: "arrow.forward")) +
						Text(verbatim: " \(addr)")
					}

				} else if let content = clipboardContent as? Scan.ClipboardContent_LoginRequest {

					let title = content.auth.actionPromptTitle
					let domain = content.auth.initialUrl.host

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
	}
	
	@ViewBuilder
	func menuOption_contacts() -> some View {
		
		Button {
			showContactsList()
		} label: {
			Label {
				Text("Contacts")
			} icon: {
				Image(systemName: "person.2")
			}
		}
		.font(.title3)
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
		.onChange(of: imagePickerResult) { _ in
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
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func voiceOverStatusChanged() {
		log.trace("voiceOverStatusChanged()")
		
		voiceOverEnabled = UIAccessibility.isVoiceOverRunning
	}

	func modelDidChange(_ newModel: Scan.Model) {
		log.trace("modelDidChange()")
		
		switch mvi.model {
		case _ as Scan.Model_Ready,
			  _ as Scan.Model_BadRequest:
			
			if ignoreScanner {
				// Flow:
				// - User taps "manual input"
				// - User types in something and taps "OK"
				// - We send Scan.Intent.Parse()
				// - We just got back a response from our request
				//
				ignoreScanner = false
			}
			
		case _ as Scan.Model_Bolt11InvoiceFlow,
		     _ as Scan.Model_OnChainFlow,
		     _ as Scan.Model_LnurlPayFlow,
		     _ as Scan.Model_LnurlAuthFlow:
			
			if location == .ReceiveView {
				// The user tapped the "Receive" button, and then tapped the "Scan withdraw" button.
				// But then they might proceed to scan the QR code to send a payment.
				// When this happens, the ScanView remains on screen,
				// while the user is prompted to accept/reject the "send payment" flow.
				//
				// In this scenario, we want to pause/ignore the scanner.
				if !ignoreScanner {
					ignoreScanner = true
				}
			}
			
		default:
			break
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
	
	func didEnableCamera() {
		log.trace("didEnableCamera()")
		
		UIAccessibility.post(notification: .announcement, argument: "Your camera is open to scan a QR code")
	}
	
	func didCancelLnurlServiceFetch() {
		log.trace("didCancelLnurlServiceFetch()")
		
		mvi.intent(Scan.Intent_CancelLnurlServiceFetch())
	}

	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------

	func pasteFromClipboard_ios16(_ string: String) {
		log.trace("pasteFromClipboard_ios16()")

		mvi.intent(Scan.Intent_Parse(request: string))
	}

	func pasteFromClipboard_pre16() {
		log.trace("pasteFromClipboard_pre16()")

		if let request = UIPasteboard.general.string {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
	}
	
	func showContactsList() {
		log.trace("showContactsList()")
		
		ignoreScanner = true
		smartModalState.display(dismissable: true) {
			ContactsListSheet(didSelectContact: didSelectContact)
		} onDidDisappear: {
			ignoreScanner = false
		}
	}
	
	func didSelectContact(_ contact: ContactInfo) {
		log.trace("didSelectContact()")
		
		if let offer = contact.mostRelevantOffer {
			mvi.intent(Scan.Intent_Parse(request: offer.encode()))
		}
	}

	func manualInput() {
		log.trace("manualInput()")
		
		ignoreScanner = true
		smartModalState.display(dismissable: true) {
			ManualInput(mvi: mvi)
		} onDidDisappear: {
			ignoreScanner = false
		}
	}
	
	func imagePickerDidChooseImage() {
		log.trace("imagePickerDidChooseImage()")
		
		guard let uiImage = imagePickerResult?.image else { return }
		imagePickerResult = nil
		
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
					NSLocalizedString("Image doesn't contain a readable QR code.", comment: "Toast message"),
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
