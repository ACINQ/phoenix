import SwiftUI
import CoreNFC
import PhoenixShared

fileprivate let filename = "SendView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SendView: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case LoginView(flow: SendManager.ParseResult_Lnurl_Auth)
		case ValidateView(flow: SendManager.ParseResult)
		
		var description: String {
			switch self {
			case .LoginView(_)    : return "LoginView"
			case .ValidateView(_) : return "ValidateView"
			}
		}
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
	
	@State var selectedContactId: Lightning_kmpUUID? = nil
	
	@State var search_offers: [Lightning_kmpUUID: [String]] = [:]
	@State var search_addresses: [Lightning_kmpUUID: [String]] = [:]
	@State var search_domains: [String] = []
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	
	@State var needsAcceptWarning = true
	
	@State var walletInfoPresented: Bool = false
	
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
	
	@ScaledMetric var labelImageSize: CGFloat = 22
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		Group {
			switch location {
			case .MainView:
				body_mainView()
				
			case .ReceiveView:
				body_receiveView()
			}
		}
		.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
			navLinkView()
		}
		.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
			navLinkView(tag)
		}
	}
	
	@ViewBuilder
	func body_mainView() -> some View {
		
		layers()
			.navigationTitle("Send")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarItems(trailing: walletIconButton())
	}
	
	@ViewBuilder
	func body_receiveView() -> some View {
		
		// Flow:
		// - user tapped "Receive" button (expects to receive a payment)
		// - user tapped "scan qr code" button (expecting to scan a lnurl-withdraw)
		//
		// So we should only show the QR code scanner here.
		// And if they scan anything that will send money,
		// then we should display a warning to the user.
		
		ScanQrCodeView(location: .embedded, didScanQrCode: didScanQrCode)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			list()
			footer()
		}
		.onAppear() {
			onAppear()
		}
		.onReceive(Biz.business.databaseManager.contactsListPublisher()) {
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
				ScanQrCodeView(location: .sheet, didScanQrCode: didScanQrCode)
			
			} // </switch>
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		GroupBox {
			smartInputField()
		}
		.groupBoxStyle(InsetGroupBoxStyle())
		.padding(.top)
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
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
		.padding(.vertical, 8)
		.padding(.horizontal, 16)
		.background(
			Capsule().strokeBorder(Color.textFieldBorder)
		)
	}
	
	@ViewBuilder
	func list() -> some View {
		
		List {
			section_suggestions()
			section_contacts()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		ViewThatFits {
			footer_standard()
			footer_compact()
			footer_accessibility()
		}
		.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
		.background(
			Color.mutedBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
	}
	
	@ViewBuilder
	func footer_standard() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Spacer()
			roundButton_paste()
				.frame(width: maxButtonWidth)
				.read(maxButtonWidthReader)
			Spacer()
			roundButton_chooseImage()
				.frame(width: maxButtonWidth)
				.read(maxButtonWidthReader)
			Spacer()
			roundButton_nfc()
				.frame(width: maxButtonWidth)
				.read(maxButtonWidthReader)
			Spacer()
			roundButton_scanQrCode()
				.frame(width: maxButtonWidth)
				.read(maxButtonWidthReader)
			Spacer()
		}
		.padding(.top, 20)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
	}
	
	@ViewBuilder
	func footer_compact() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			roundButton_paste()
			Spacer()
			roundButton_chooseImage()
			Spacer()
			roundButton_nfc()
			Spacer()
			roundButton_scanQrCode()
		}
		.padding(.horizontal)
		.padding(.top, 20)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
	}
	
	@ViewBuilder
	func footer_accessibility() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 20) {
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer()
				labelButton_paste()
					.frame(width: maxButtonWidth, alignment: .leading)
					.read(maxButtonWidthReader)
				Spacer()
				Spacer()
				labelButton_scanQrCode()
					.frame(width: maxButtonWidth, alignment: .leading)
					.read(maxButtonWidthReader)
				Spacer()
			}
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer()
				labelButton_chooseImage()
					.frame(width: maxButtonWidth, alignment: .leading)
					.read(maxButtonWidthReader)
				Spacer()
				Spacer()
				labelButton_nfc()
					.frame(width: maxButtonWidth, alignment: .leading)
					.read(maxButtonWidthReader)
				Spacer()
			}
		}
		.padding(.top, 10)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
	}
	
	@ViewBuilder
	func roundButtonFactory(
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
		.accessibilityElement()
		.accessibilityLabel(text)
		.accessibilityAddTraits(.isButton)
	}
	
	@ViewBuilder
	func roundButton_paste() -> some View {
		
		roundButtonFactory(
			text: String(localized: "paste", comment: "button label - try to make it short"),
			image: Image(systemName: "arrow.right.doc.on.clipboard"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			pasteFromClipboard()
		}
	}
	
	@ViewBuilder
	func roundButton_chooseImage() -> some View {
		
		roundButtonFactory(
			text: String(localized: "choose image", comment: "button label - try to make it short"),
			image: Image(systemName: "photo"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			chooseImage()
		}
	}
	
	@ViewBuilder
	func roundButton_nfc() -> some View {
		
		roundButtonFactory(
			text: String(localized: "nfc", comment: "button label - try to make it short"),
			image: Image(systemName: "dot.radiowaves.forward"),
			width: 21, height: 21,
			xOffset: 0, yOffset: 0
		) {
			startNfc()
		}
	}
	
	@ViewBuilder
	func roundButton_scanQrCode() -> some View {
		
		roundButtonFactory(
			text: String(localized: "scan qr code", comment: "button label - try to make it short"),
			image: Image(systemName: "qrcode.viewfinder"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			scanQrCode()
		}
	}
	
	@ViewBuilder
	func labelButton_paste() -> some View {
		
		Button {
			pasteFromClipboard()
		} label: {
			Label {
				Text("paste", comment: "button label - try to make it short")
					.foregroundColor(.primaryForeground)
			} icon: {
				Image(systemName: "arrow.right.doc.on.clipboard")
					.resizable()
					.scaledToFit()
					.frame(width: labelImageSize, height: labelImageSize)
					.foregroundColor(.appAccent)
			}
		}
	}
	
	@ViewBuilder
	func labelButton_chooseImage() -> some View {
		
		Button {
			chooseImage()
		} label: {
			Label {
				Text("choose image", comment: "button label - try to make it short")
					.foregroundColor(.primaryForeground)
			} icon: {
				Image(systemName: "photo")
					.resizable()
					.scaledToFit()
					.frame(width: labelImageSize, height: labelImageSize)
					.foregroundColor(.appAccent)
			}
		}
	}
	
	@ViewBuilder
	func labelButton_nfc() -> some View {
		
		Button {
			startNfc()
		} label: {
			Label {
				Text("nfc", comment: "button label - try to make it short")
					.foregroundColor(.primaryForeground)
			} icon: {
				Image(systemName: "dot.radiowaves.forward")
					.resizable()
					.scaledToFit()
					.frame(width: labelImageSize, height: labelImageSize)
					.foregroundColor(.appAccent)
			}
		}
	}
	
	@ViewBuilder
	func labelButton_scanQrCode() -> some View {
		
		Button {
			scanQrCode()
		} label: {
			Label {
				Text("scan qr code", comment: "button label - try to make it short")
					.foregroundColor(.primaryForeground)
			} icon: {
				Image(systemName: "qrcode.viewfinder")
					.resizable()
					.scaledToFit()
					.frame(width: labelImageSize, height: labelImageSize)
					.foregroundColor(.appAccent)
			}
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
			} header: {
				Text("Suggestions")
			} // </Section>
		}
	}
	
	@ViewBuilder
	func section_contacts() -> some View {
		
		Section {
			ForEach(visibleContacts) { item in
				if let selectedContactId, selectedContactId == item.id {
					contactRow_selected(item)
				} else {
					contactRow_unselected(item)
				}
			}
			if hasZeroMatchesForSearch {
				zeroMatchesRow()
			} else if hasZeroContacts {
				zeroContactsRow()
			}
		} header: {
			Text("Contacts")
		} // </Section>
	}
	
	@ViewBuilder
	func contactRow_unselected(_ item: ContactInfo) -> some View {
		
		Button {
			selectContact(item)
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				ContactPhoto(filename: item.photoUri, size: 32)
				Text(item.name)
					.font(.title3)
					.foregroundColor(.primary)
				Spacer()
			}
			.padding(.all, 4)
		}
	}
	
	@ViewBuilder
	func contactRow_selected(_ item: ContactInfo) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 12) {
			
			Button {
				unselectContact()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 8) {
					ContactPhoto(filename: item.photoUri, size: 32)
					Text(item.name)
						.font(.title3)
						.foregroundColor(.primary)
					Spacer()
				}
			}
			
			ForEach(item.offers) { offer in
				contactRow_offer(offer)
			}
			ForEach(item.addresses) { address in
				contactRow_address(address)
			}
		}
		.padding(.all, 4)
	}
	
	@ViewBuilder
	func contactRow_offer(_ offer: ContactOffer) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			bullet()
			
			Button {
				payOffer(offer)
			} label: {
				
				let label = offer.label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
				let text = offer.offer.encode()
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Group {
						if label.isEmpty {
							Text(text)
								.lineLimit(1)
								.truncationMode(.middle)
						} else {
							Text(label)
								.lineLimit(1)
								.truncationMode(.tail)
							Text(": \(text)")
								.lineLimit(1)
								.truncationMode(.middle)
								.foregroundStyle(Color.secondary)
								.layoutPriority(-1)
						}
					} // </Group>
					.font(.callout)
					
					Spacer()
					
					Image(systemName: "paperplane.fill")
				}
			} // </Button>
			.buttonStyle(.borderless) // prevents trigger when row tapped
			
		} // </HStack>
	}
	
	@ViewBuilder
	func contactRow_address(_ address: ContactAddress) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			bullet()
			
			Button {
				payAddress(address)
			} label: {
				
				let label = address.label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
				let text = address.address.trimmingCharacters(in: .whitespacesAndNewlines)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Group {
						if label.isEmpty {
							Text(text)
								.lineLimit(1)
								.truncationMode(.middle)
						} else {
							Text(label)
								.lineLimit(1)
								.truncationMode(.tail)
							Text(": \(text)")
								.lineLimit(1)
								.truncationMode(.middle)
								.foregroundStyle(Color.secondary)
								.layoutPriority(-1)
						}
					} // </Group>
					.font(.callout)
					
					Spacer()
					
					Image(systemName: "paperplane.fill")
				}
			} // </Button>
			.buttonStyle(.borderless) // prevents trigger when row tapped
			
		} // </HStack>
	}
	
	@ViewBuilder
	func bullet() -> some View {
		
		Image(systemName: "circlebadge.fill")
			.imageScale(.small)
			.font(.system(size: 10))
			.foregroundStyle(.tertiary)
			.padding(.leading, 4)
			.padding(.trailing, 8)
	}
	
	@ViewBuilder
	func zeroMatchesRow() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 8) {
			Image(systemName: "person.crop.circle.badge.questionmark")
				.resizable()
				.scaledToFit()
				.frame(width: 32, height: 32)
			Text("No matches for search")
			Spacer()
		}
		.foregroundStyle(.secondary)
		.padding(.all, 4)
	}
	
	@ViewBuilder
	func zeroContactsRow() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 8) {
			Image(systemName: "person.crop.circle.fill")
				.resizable()
				.scaledToFit()
				.frame(width: 32, height: 32)
			Text("No Contacts")
				.font(.title3)
			Spacer()
		}
		.foregroundStyle(.secondary)
		.padding(.all, 4)
	}
	
	@ViewBuilder
	func walletIconButton() -> some View {
		
		if #available(iOS 17, *) {
			
			Button {
				walletInfoPresented = true
			} label: {
				let wallet = currentWalletMetadata()
				WalletImage(filename: wallet.photo, size: 48, useCache: true)
			}
			.popover(isPresented: $walletInfoPresented) {
				WalletInfoSend()
					.frame(maxWidth: deviceInfo.windowSize.width * 0.6)
					.presentationCompactAdaptation(.popover)
			}
			
		} else {
			
			Button {
				walletInfoPresented = true
			} label: {
				let wallet = currentWalletMetadata()
				WalletImage(filename: wallet.photo, size: 48, useCache: true)
			}
			.popover(present: $walletInfoPresented) {
				InfoPopoverWindow {
					WalletInfoSend()
						.frame(maxWidth: deviceInfo.windowSize.width * 0.6)
				}
			}
		}
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
		case .LoginView(let flow):
			LoginView(flow: flow, popTo: self.popTo)
			
		case .ValidateView(let flow):
			ValidateView(flow: flow, popTo: self.popTo)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var visibleContacts: [ContactInfo] {
		return filteredContacts ?? sortedContacts
	}
	
	var hasZeroMatchesForSearch: Bool {
		if sortedContacts.isEmpty {
			// User has zero contacts.
			// This is different from zero search results.
			return false
		} else if let filteredContacts {
			return filteredContacts.isEmpty
		} else {
			// Not searching
			return false
		}
	}
	
	var hasZeroContacts: Bool {
		return sortedContacts.isEmpty
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
	
	func currentWalletMetadata() -> WalletMetadata {
		return SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		if let destination = popToDestination {
			log.debug("popToDestination: \(destination)")
			
			popToDestination = nil
			presentationMode.wrappedValue.dismiss()
		}
		
		if selectedContactId != nil {
			selectedContactId = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func contactsListChanged(_ updatedList: [ContactInfo]) {
		log.trace("contactsListChanged()")
		
		sortedContacts = updatedList.sorted {(a, b) in
			// return true if `a` should be ordered before `b`; otherwise return false
			return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
		}
		
		do {
			var offers: [Lightning_kmpUUID: [String]] = [:]
			for contact in sortedContacts {
				let key: Lightning_kmpUUID = contact.id
				let values: [String] = contact.offers.map {
					$0.offer.encode().lowercased()
				}
				offers[key] = values
			}
			
			search_offers = offers
		}
		do {
			var addresses: [Lightning_kmpUUID: [String]] = [:]
			for contact in sortedContacts {
				let key: Lightning_kmpUUID = contact.id
				let values: [String] = contact.addresses.map {
					$0.address.trimmingCharacters(in: .whitespacesAndNewlines)
				}
				addresses[key] = values
			}
			
			search_addresses = addresses
		}
		do {
			var domains = ContactAddress.wellKnownDomains(includeTestnet: Biz.isTestnet)
			for contact in sortedContacts {
				contact.addresses.forEach { address in
					if let domain = address.domain {
						domains.insert(domain)
					}
				}
			}
			
			search_domains = domains.sorted()
		}
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
			
			if let addresses = search_addresses[contact.id] {
				if addresses.contains(where: { $0.contains(searchtext) }) {
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
		log.trace("navigateTo(\(tag.description))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else { // iOS 16
			popToDestination = destination
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
			inputFieldText = request
			parseUserInput(request)
		}
	}
	
	func chooseImage() {
		log.trace("chooseImage()")
		activeSheet = .imagePicker
	}
	
	func startNfc() {
		log.trace("startNfc()")
		
		NfcReader.shared.readCard { (result: Result<NFCNDEFMessage, NfcReader.ReadError>) in
			
			switch result {
			case .success(let ndefMsg):
				handleNdefMessage(ndefMsg)
			case .failure(let error):
				handleNfcError(error)
			}
		}
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
		
		let count = contact.offers.count + contact.addresses.count
		if count == 1 {
			// When there's exactly one payment option available, we just immediately use it.
			if let offer = contact.offers.first {
				payOffer(offer)
			} else if let address = contact.addresses.first {
				payAddress(address)
			}
			
		} else {
			// Otherwise we show the list of payment options to the user
			withAnimation {
				selectedContactId = contact.id
			}
		}
	}
	
	func unselectContact() {
		log.trace("unselectContact()")
		
		withAnimation {
			selectedContactId = nil
		}
	}
	
	func payOffer(_ offer: ContactOffer) {
		log.trace("payOffer()")
		parseUserInput(offer.offer.encode())
	}
	
	func payAddress(_ address: ContactAddress) {
		log.trace("payAddress()")
		parseUserInput(address.address)
	}
	
	func cancelParseRequest() {
		log.trace("cancelParseRequest()")
		
		isParsing = false
		parseIndex += 1
		parseProgress = nil
	}
	
	func showSendPaymentWarning(_ result: SendManager.ParseResult) {
		log.trace("showSendPaymentWarning()")
		
		popoverState.display(dismissable: false) {
			PaymentWarningPopover(
				cancelAction: {
					presentationMode.wrappedValue.dismiss()
				},
				continueAction: {
					needsAcceptWarning = false
					handleParseResult(result)
				}
			)
		}
	}
	
	func didCopyLink() {
		log.trace("didCopyLink()")
		
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
	
	// --------------------------------------------------
	// MARK: Parsing
	// --------------------------------------------------
	
	func handleNdefMessage(_ ndefMsg: NFCNDEFMessage) {
		log.trace("handleNdefMessage()")
		
		var uri_values: [URL] = []
		var text_values: [String] = []
		
		ndefMsg.records.forEach { payload in
			if let uri = payload.wellKnownTypeURIPayload() {
				log.debug("found uri = \(uri)")
				uri_values.append(uri)
				
			} else if let text = payload.wellKnownTypeTextPayload().0 {
				log.debug("found text = \(text)")
				text_values.append(text)
			}
		}
		
		if let uri = uri_values.first {
			parseUserInput(uri.absoluteString)
		} else if let text = text_values.first {
			parseUserInput(text)
		} else {
			
			let msg = String(localized: "No URI or text detected in NFC tag")
			toast.pop(msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 5.0,
				alignment: .middle,
				showCloseButton: true
			)
		}
	}
	
	func handleNfcError(_ error: NfcReader.ReadError) {
		log.trace("handleNfcError()")
		
		let msg: String
		switch error {
		case .readingNotAvailable:
			msg = String(localized: "NFC cababilities not available on this device")
		case .alreadyStarted:
			msg = String(localized: "NFC reader is already scanning")
		case .scanningTerminated(_):
			msg = String(localized: "Nothing scanned")
		case .errorReadingTag:
			msg = String(localized: "Error reading tag")
		}

		toast.pop(msg,
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 5.0,
			alignment: .middle,
			showCloseButton: true
		)
	}
	
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
					handleParseResult(result)
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
	
	func handleParseResult(_ result: SendManager.ParseResult) {
		
		if let badRequest = result as? SendManager.ParseResult_BadRequest {
			showErrorMessage(badRequest)
		} else {
			
			if location == .ReceiveView && needsAcceptWarning {
				
				var willSendMoney = false
				switch result {
					case is SendManager.ParseResult_Bolt11Invoice : fallthrough
					case is SendManager.ParseResult_Bolt12Offer   : fallthrough
					case is SendManager.ParseResult_Uri           : fallthrough
					case is SendManager.ParseResult_Lnurl_Pay     : willSendMoney = true
					default                                       : break
				}
				
				guard !willSendMoney else {
					showSendPaymentWarning(result)
					return
				}
			}
			
			if let auth = result as? SendManager.ParseResult_Lnurl_Auth {
				navigateTo(.LoginView(flow: auth))
			} else {
				navigateTo(.ValidateView(flow: result))
			}
		}
	}
	
	func showErrorMessage(_ result: SendManager.ParseResult_BadRequest) {
		log.trace("showErrorMessage()")
		
		let either = ParseResultHelper.processBadRequest(result)
		switch either {
		case .Left(let msg):
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 30.0,
				alignment: .middle,
				showCloseButton: true
			)
			
		case .Right(let websiteLink):
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					didCopyLink: didCopyLink,
					didOpenLink: nil
				)
			}
		}
	}
}
