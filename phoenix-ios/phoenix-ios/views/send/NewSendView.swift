import SwiftUI
import PhoenixShared

fileprivate let filename = "NewSendView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct NewSendView: View {
	
	enum Location {
		case MainView
		case ReceiveView
	}
	let location: Location
	
	@State var inputFieldText: String = ""
	
	@State var imagePickerResult: PickerResult? = nil
	
	@State var autocompleteSuggestions: [String] = []
	
	@State var sortedContacts: [ContactInfo] = []
	@State var filteredContacts: [ContactInfo]? = nil
	
	@State var search_offers: [String: [String]] = [:]
	@State var search_lnid: [String: [String]] = [:]
	@State var search_domains: [String] = []
	
	enum ActiveSheet {
		case imagePicker
		case qrCodeScanner
	}
	@State var activeSheet: ActiveSheet? = nil
	
	// For the cicular buttons: [paste, select_image, scan_qr_code]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Color.primaryBackground.ignoresSafeArea(.all, edges: .all)
			content()
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationTitle("Send")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			list()
		}
		.onAppear() {
			onAppear()
		}
		.onReceive(Biz.business.contactsManager.contactsListPublisher()) {
			contactsListChanged($0)
		}
		.onChange(of: inputFieldText) { _ in
			inputFieldTextChanged()
		}
		.onChange(of: imagePickerResult) { _ in
			imagePickerDidChooseImage()
		}
		.sheet(isPresented: activeSheetBinding()) { // SwiftUI only allows for 1 ".sheet"
			switch activeSheet! {
			case .imagePicker:
				ImagePicker(copyFile: false, result: $imagePickerResult)
			
			case .qrCodeScanner:
				ScanQrCodeSheet(didScanQrCode: didScanQrCode)
			
			} // </switch>
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				smartInputField()
				actionButtons()
			}
		}
		.groupBoxStyle(InsetGroupBoxStyle())
		.padding(.top)
	}
	
	@ViewBuilder
	func list() -> some View {
		
		List {
			section_suggestions()
			section_contacts()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func smartInputField() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			TextField("name, lightning address, offer...", text: $inputFieldText)
				.disableAutocorrection(true)
				.textInputAutocapitalization(.never)
			
			// Clear button (appears when TextField's text is non-empty)
			Button {
				clearInputField()
			} label: {
				Image(systemName: "multiply.circle.fill")
					.foregroundColor(.secondary)
			}
			.isHidden(inputFieldText.isEmpty)
		}
		.padding([.top, .bottom], 8)
		.padding(.leading, 16)
		.padding(.trailing, 8)
		.background(
			Capsule().strokeBorder(Color.textFieldBorder)
		)
		.padding(.horizontal, 0)
		.padding(.bottom, 16)
	}
	
	@ViewBuilder
	func actionButtons() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Spacer()
			actionButton_paste()
			Spacer()
			actionButton_chooseImage()
			Spacer()
			actionButton_scanQrCode()
			Spacer()
		}
		.padding(.horizontal)
		.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
	}
	
	@ViewBuilder
	func actionButtonFactory(
		text: String,
		image: Image,
		width: CGFloat = 20,
		height: CGFloat = 20,
		xOffset: CGFloat = 0,
		yOffset: CGFloat = 0,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				ZStack {
					Color.buttonFill
						.frame(width: 40, height: 40)
						.cornerRadius(50)
						.overlay(
							RoundedRectangle(cornerRadius: 50)
								.stroke(Color(UIColor.separator), lineWidth: 1)
						)
					
					image
						.renderingMode(.template)
						.resizable()
						.scaledToFit()
						.frame(width: width, height: height)
						.offset(x: xOffset, y: yOffset)
				} // </ZStack>
				
				Text(text.lowercased())
					.font(.caption)
					.foregroundColor(Color.secondary)
					.padding(.top, 2)
				
			} // </VStack>
		} // </Button>
		.frame(width: maxButtonWidth)
		.read(maxButtonWidthReader)
		.accessibilityElement()
		.accessibilityLabel(text)
		.accessibilityAddTraits(.isButton)
	}
	
	@ViewBuilder
	func actionButton_paste() -> some View {
		
		actionButtonFactory(
			text: NSLocalizedString("paste", comment: "button label - try to make it short"),
			image: Image(systemName: "arrow.right.doc.on.clipboard"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			pasteFromClipboard()
		}
	//	.disabled(!clipboardHasString)
	}
	
	@ViewBuilder
	func actionButton_chooseImage() -> some View {
		
		actionButtonFactory(
			text: NSLocalizedString("choose image", comment: "button label - try to make it short"),
			image: Image(systemName: "photo"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			chooseImage()
		}
	}
	
	@ViewBuilder
	func actionButton_scanQrCode() -> some View {
		
		actionButtonFactory(
			text: NSLocalizedString("scan qr code", comment: "button label - try to make it short"),
			image: Image(systemName: "qrcode.viewfinder"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			scanQrCode()
		}
	}
	
	@ViewBuilder
	func section_suggestions() -> some View {
		
		if !autocompleteSuggestions.isEmpty {
			Section {
				ForEach(autocompleteSuggestions, id: \.self) { suggestion in
					Text(suggestion)
				}
			} // </Section>
		}
	}
	
	@ViewBuilder
	func section_contacts() -> some View {
		
		if !visibleContacts.isEmpty {
			Section {
				ForEach(visibleContacts) { item in
					Button {
						selectContact(item)
					} label: {
						contactRow(item)
					}
				}
			} // </Section>
		}
	}
	
	@ViewBuilder
	func contactRow(_ item: ContactInfo) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 8) {
			ContactPhoto(fileName: item.photoUri, size: 32)
			Text(item.name)
				.font(.title3)
				.foregroundColor(.primary)
			Spacer()
		}
		.padding(.all, 4)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var visibleContacts: [ContactInfo] {
		return filteredContacts ?? sortedContacts
	}
	
	var hasZeroMatchesForSearch: Bool {
		
		guard let filteredContacts else {
			return false
		}
		
		return filteredContacts.isEmpty && !sortedContacts.isEmpty
	}
	
	func activeSheetBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { activeSheet != nil },
			set: { if !$0 { activeSheet = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func contactsListChanged(_ updatedList: [ContactInfo]) {
		log.trace("contactsListChanged()")
		
		sortedContacts = updatedList
		
		var offers: [String: [String]] = [:]
		for contact in sortedContacts {
			let key: String = contact.id
			let values: [String] = contact.offers.map { $0.encode().lowercased() }
			
			offers[key] = values
		}
		
		search_offers = offers
		
		// Todo:
		// - update search_lnid after we add support
		// - update search_domains after we add support
		//
		// Temp: For now, search_domains will just contain the list of "well known" domains
		
		var domains: [String] = []
		if BusinessManager.isTestnet {
			domains.append("testnet.phoenixwallet.me")
		}
		domains.append("phoenixwallet.me")
		domains.append("bitrefill.me")
		domains.append("strike.me")
		domains.append("coincorner.io")
		domains.append("sparkwallet.me")
		domains.append("ln.tips")
		domains.append("getalby.com")
		domains.append("walletofsatoshi.com")
		domains.append("stacker.news")
		
		search_domains = domains
	}
	
	func inputFieldTextChanged() {
		log.trace("inputFieldTextChanged()")
		
		guard !inputFieldText.isEmpty else {
			filteredContacts = nil
			autocompleteSuggestions = []
			return
		}
		
		let searchtext = inputFieldText.lowercased()
		filteredContacts = sortedContacts.filter { (contact: ContactInfo) in
			
			// `localizedCaseInsensitiveContains` doesn't properly ignore diacritic marks.
			// For example: search text of "belen" doesn't match name "Belén".
			//
			// `localizedStandardContains`:
			// > This is the most appropriate method for doing user-level string searches,
			// > similar to how searches are done generally in the system. The search is
			// > locale-aware, case and diacritic insensitive. The exact list of search
			// > options applied may change over time.
			
			if contact.name.localizedStandardContains(searchtext) {
				return true
			}
			
			if let offers = search_offers[contact.id] {
				if offers.contains(searchtext) {
					return true
				}
			}
			
			return false
		}
		
		var suggestions: [String] = []
		if let atRange = inputFieldText.range(of: "@") {
			
			let prefixRange = inputFieldText.startIndex ..< atRange.upperBound
			let prefix = String(inputFieldText[prefixRange])
			
			let domainRange = atRange.upperBound ..< inputFieldText.endIndex
			let domainPrefix = String(inputFieldText[domainRange]).lowercased()
			
			if !domainPrefix.isEmpty {
			
				for domain in search_domains {
					if domain.lowercased().hasPrefix(domainPrefix) {
						
						let suggestion = prefix + domain
						suggestions.append(suggestion)
					}
				}
			}
		}
		
		autocompleteSuggestions = suggestions
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func clearInputField() {
		log.trace("clearInputField()")
		
		inputFieldText = ""
	}
	
	func pasteFromClipboard() {
		log.trace("pasteFromClipboard()")
		
		if let _ = UIPasteboard.general.string {
			// Todo...
		}
	}
	
	func chooseImage() {
		log.trace("chooseImage()")
		activeSheet = .imagePicker
	}
	
	func scanQrCode() {
		log.trace("scanQrCode()")
		activeSheet = .qrCodeScanner
	}
	
	func imagePickerDidChooseImage() {
		log.trace("imagePickerDidChooseImage()")
		
		guard let uiImage = imagePickerResult?.image else { return }
		imagePickerResult = nil
		
		guard let ciImage = CIImage(image: uiImage) else { return }
		
		let context = CIContext()
		var options: [String: Any] = [CIDetectorAccuracy: CIDetectorAccuracyHigh]
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
		
		if let _ = qrCodeString {
			// Todo...
		//	mvi.intent(Scan.Intent_Parse(request: qrCodeString))
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
	
	func didScanQrCode(result: String) {
		log.trace("didScanQrCode()")
		
		activeSheet = nil
		// Todo...
	}
	
	func selectContact(_ contact: ContactInfo) {
		log.trace("selectContact: \(contact.id)")
		
		// Todo...
	}
}

struct InsetGroupBoxStyle: GroupBoxStyle {
	
	func makeBody(configuration: GroupBoxStyleConfiguration) -> some View {
		VStack(alignment: .leading) {
			configuration.label
			configuration.content
		}
		.padding()
		.background(Color(.secondarySystemGroupedBackground))
		.cornerRadius(10)
		.padding(.horizontal)
	}
}
