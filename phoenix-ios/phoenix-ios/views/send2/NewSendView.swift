import SwiftUI
import PhoenixShared

fileprivate let filename = "NewSendView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct NewSendView: View {
	
	enum NavLinkTag: String, Codable {
		case NextView
	}
	
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
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	@State var parseResult: SendManager.ParseResult? = nil
	
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
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var popoverState: PopoverState
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			content()
			
			if parseProgress != nil {
				FetchActivityNotice(
					title: self.fetchActivityTitle,
					onCancel: { self.cancelParseRequest() }
				)
				.ignoresSafeArea(.keyboard) // disable keyboard avoidance on this view
			}
			
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationTitle("Send")
		.navigationBarTitleDisplayMode(.inline)
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
			navLinkView()
		}
		.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
			navLinkView(tag)
		}
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
			TextField(
				"name, lightning address, offer...",
				text: $inputFieldText,
				onCommit: { commitInputField() }
			)
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
					Button {
						selectSuggestion(suggestion)
					} label: {
						Text(suggestion)
					}
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
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .NextView:
			
			if let flow = parseResult as? SendManager.ParseResult_Lnurl_Auth {
				NewLoginView(flow: flow)
			} else if let flow = parseResult {
				NewValidateView(flow: flow)
			} else {
				EmptyView()
			}
		}
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
	
	var fetchActivityTitle: String {
		
		if parseProgress is SendManager.ParseProgress_LnurlServiceFetch {
			return String(localized: "Fetching Lightning URL", comment: "Progress title")
		} else {
			return String(localized: "Resolving lightning address", comment: "Progress title")
		}
	}
	
	func navLinkTagBinding() -> Binding<Bool> {
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
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
		
		if let qrCodeString {
			parseUserInput(qrCodeString)
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
	
	func didScanQrCode(_ request: String) {
		log.trace("didScanQrCode()")
		
		activeSheet = nil
		if !isParsing {
			parseUserInput(request)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func clearInputField() {
		log.trace("clearInputField()")
		
		inputFieldText = ""
	}
	
	func commitInputField() {
		log.trace("clearInputField()")
		
		if !inputFieldText.isEmpty {
			parseUserInput(inputFieldText)
		}
	}
	
	func pasteFromClipboard() {
		log.trace("pasteFromClipboard()")
		
		if let request = UIPasteboard.general.string {
			parseUserInput(request)
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
	
	func selectSuggestion(_ suggestion: String) {
		log.trace("selectSuggestion()")
		
		inputFieldText = suggestion
		parseUserInput(suggestion)
	}
	
	func selectContact(_ contact: ContactInfo) {
		log.trace("selectContact: \(contact.id)")
		
		if let offer = contact.mostRelevantOffer {
			parseUserInput(offer.encode())
		}
	}
	
	func cancelParseRequest() {
		log.trace("cancelParseRequest()")
		
		isParsing = false
		parseIndex += 1
		parseProgress = nil
	}
	
	func copyLink(_ url: URL) {
		log.trace("copyLink()")
		
		UIPasteboard.general.string = url.absoluteString
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
	
	func openLink(_ url: URL) {
		log.trace("openLink()")
		
		// Strange SwiftUI bug:
		// https://forums.developer.apple.com/forums/thread/750514
		//
		// Simply declaring the following environment variable:
		// @Environment(\.openURL) var openURL: OpenURLAction
		//
		// Somehow causes an infinite loop in SwiftUI !
		// I encountered this multiple times while testing,
		// and the suggested workaround is to use plain UIApplication calls.
		/*
		openURL(url)
		*/
		
		if UIApplication.shared.canOpenURL(url) {
			UIApplication.shared.open(url)
		}
	}
	
	// --------------------------------------------------
	// MARK: SendManger
	// --------------------------------------------------
	
	func parseUserInput(_ input: String) {
		log.trace("parseUserInput()")
		
		guard !isParsing else {
			log.warning("parseUserInput: ignoring: isParsing == true")
			return
		}
		
		isParsing = true
		parseIndex += 1
		let index = parseIndex
		
		Task { @MainActor in
			do {
				let progressHandler = {(progress: SendManager.ParseProgress) -> Void in
					if index == parseIndex {
						self.parseProgress = progress
					} else {
						log.warning("parseUserInput: progressHandler: ignoring: cancelled")
					}
				}
				
				let result: SendManager.ParseResult = try await Biz.business.sendManager.parse(
					request: input,
					progress: progressHandler
				)
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
					
					if let badRequest = result as? SendManager.ParseResult_BadRequest {
						showErrorToast(badRequest)
					} else {
						parseResult = result
						navigateTo(.NextView)
					}
				} else {
					log.warning("parseUserInput: result: ignoring: cancelled")
				}
				
			} catch {
				log.error("parseUserInput: error: \(error)")
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
				}
			}
		}
	}
	
	func showErrorToast(_ result: SendManager.ParseResult_BadRequest) {
		log.trace("showErrorToast()")
		
		let msg: String
		var websiteLink: URL? = nil
		
		switch result.reason {
		case is SendManager.BadRequestReason_Expired:
			
			msg = NSLocalizedString(
				"Invoice is expired",
				comment: "Error message - scanning lightning invoice"
			)
			
		case let chainMismatch as SendManager.BadRequestReason_ChainMismatch:
			
			msg = NSLocalizedString(
				"The invoice is not for \(chainMismatch.expected.name)",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is SendManager.BadRequestReason_UnsupportedLnurl:
			
			msg = NSLocalizedString(
				"Phoenix does not support this type of LNURL yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is SendManager.BadRequestReason_AlreadyPaidInvoice:
			
			msg = NSLocalizedString(
				"You've already paid this invoice. Paying it again could result in stolen funds.",
				comment: "Error message - scanning lightning invoice"
			)

		case is SendManager.BadRequestReason_Bip353InvalidOffer:
			
			msg = NSLocalizedString(
				"This address uses an invalid Bolt12 offer.",
				comment: "Error message - dns record contains an invalid offer"
			)
			
		case is SendManager.BadRequestReason_Bip353NoDNSSEC:
			
			msg = NSLocalizedString(
				"This address is hosted on an unsecure DNS. DNSSEC must be enabled.",
				comment: "Error message - dns issue"
			)

		case let serviceError as SendManager.BadRequestReason_ServiceError:
			
			let remoteFailure: LnurlError.RemoteFailure = serviceError.error
			let origin = remoteFailure.origin
			
			let isLightningAddress = serviceError.url.description.contains("/.well-known/lnurlp/")
			let lightningAddressErrorMessage = NSLocalizedString(
				"The service (\(origin)) doesn't support Lightning addresses, or doesn't know this user",
				comment: "Error message - scanning lightning invoice"
			)
			
			switch remoteFailure {
			case is LnurlError.RemoteFailure_CouldNotConnect:
				msg = NSLocalizedString(
					"Could not connect to service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case is LnurlError.RemoteFailure_Unreadable:
				let scheme = serviceError.url.protocol.name.lowercased()
				if scheme == "https" || scheme == "http" {
					websiteLink = URL(string: serviceError.url.description())
				}
				msg = NSLocalizedString(
					"Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case let rfDetailed as LnurlError.RemoteFailure_Detailed:
				if isLightningAddress {
					msg = lightningAddressErrorMessage
				} else {
					msg = NSLocalizedString(
						"The service (\(origin)) returned error message: \(rfDetailed.reason)",
						comment: "Error message - scanning lightning invoice"
					)
				}
				
			case let rfCode as LnurlError.RemoteFailure_Code:
				if isLightningAddress {
					msg = lightningAddressErrorMessage
				} else {
					msg = NSLocalizedString(
						"The service (\(origin)) returned error code: \(rfCode.code.value)",
						comment: "Error message - scanning lightning invoice"
					)
				}
				
			default:
				msg = NSLocalizedString(
					"The service (\(origin)) appears to be offline, or they have a down server",
					comment: "Error message - scanning lightning invoice"
				)
			}
			
		default:
			
			msg = NSLocalizedString(
				"This doesn't appear to be a Lightning invoice",
				comment: "Error message - scanning lightning invoice"
			)
		}
		
		if let websiteLink {
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					copyAction: copyLink,
					openAction: openLink
				)
			}
			
		} else {
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 30.0,
				alignment: .middle,
				showCloseButton: true
			)
		}
	}
}
