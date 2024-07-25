import SwiftUI
import PhoenixShared

fileprivate let filename = "ManageContact"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct OfferRow: Identifiable {
	let offer: String
	let isCurrentOffer: Bool
	
	var id: String {
		return offer
	}
}

fileprivate let IMG_SIZE: CGFloat = 150
fileprivate let DEFAULT_TRUSTED: Bool = true

struct ManageContact: View {
	
	enum Location {
		case smartModal
		case sheet
		case embedded
	}
	
	let location: Location
	
	let offer: Lightning_kmpOfferTypesOffer?
	let contact: ContactInfo?
	let contactUpdated: (ContactInfo?) -> Void
	let isNewContact: Bool
	
	@State var name: String
	@State var trustedContact: Bool
	@State var showImageOptions: Bool = false
	@State var pickerResult: PickerResult?
	@State var doNotUseDiskImage: Bool = false
	
	@State var isSaving: Bool = false
	@State var showDiscardChangesConfirmationDialog: Bool = false
	@State var showDeleteContactConfirmationDialog: Bool = false
	
	@State var showingOffers: Bool = false
	@State var chevronPosition: AnimatedChevron.Position = .pointingDown
	
	@State var pastedOffer: String = ""
	@State var pastedOfferIsInvalid: Bool = false
	@State var parsedOffer: Lightning_kmpOfferTypesOffer? = nil
	
	@State var didAppear: Bool = false
	
	enum ActiveSheet {
		case camera
		case imagePicker
	}
	@State var activeSheet: ActiveSheet? = nil
	
	// For the footer buttons: [cancel, save]
	enum MaxFooterButtonWidth: Preference {}
	let maxFooterButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxFooterButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxFooterButtonWidth: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(
		location: Location,
		offer: Lightning_kmpOfferTypesOffer?,
		contact: ContactInfo?,
		contactUpdated: @escaping (ContactInfo?) -> Void
	) {
		self.location = location
		self.offer = offer
		self.contact = contact
		self.contactUpdated = contactUpdated
		self.isNewContact = (contact == nil)
		
		self._name = State(initialValue: contact?.name ?? "")
		self._trustedContact = State(initialValue: contact?.useOfferKey ?? DEFAULT_TRUSTED)
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .smartModal:
			main()
			
		case .sheet:
			main()
				.navigationBarHidden(true)
			
		case .embedded:
			main()
				.navigationTitle(self.title)
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarBackButtonHidden(true)
				.navigationBarItems(leading: header_backButton(), trailing: header_trailingButtons())
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		ZStack(alignment: Alignment.center) {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				header()
				content()
				footer()
			}
			toast.view()
		} // </ZStack>
		.onAppear {
			onAppear()
		}
		.sheet(isPresented: activeSheetBinding()) { // SwiftUI only allows for 1 ".sheet"
			switch activeSheet! {
			case .camera:
				CameraPicker(result: $pickerResult)
			
			case .imagePicker:
				ImagePicker(copyFile: true, result: $pickerResult)
			
			} // </switch>
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		Group {
			switch location {
			case .smartModal:
				header_smartModal()
				
			case .sheet:
				header_sheet()
				
			case .embedded:
				header_embedded()
			}
		}
		.confirmationDialog("Discard changes?",
			isPresented: $showDiscardChangesConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Discard changes", role: ButtonRole.destructive) {
				discardChanges()
			}
		}
		.confirmationDialog("Delete contact?",
			isPresented: $showDeleteContactConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Delete contact", role: ButtonRole.destructive) {
				deleteContact()
			}
		}
	}
	
	@ViewBuilder
	func header_smartModal() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text(self.title)
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
			
			Spacer(minLength: 0)
			
			if !isNewContact {
				Button {
					showDeleteContactConfirmationDialog = true
				} label: {
					Image(systemName: "trash.fill")
						.imageScale(.medium)
						.font(.title2)
						.foregroundColor(.appNegative)
				}
				.disabled(isSaving)
				.accessibilityLabel("Delete contact")
			}
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
	func header_sheet() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			header_backButton()
			Spacer()
			header_trailingButtons()
		}
		.padding()
	}
	
	@ViewBuilder
	func header_embedded() -> some View {
		
		Spacer()
			.frame(height: 25)
	}
	
	@ViewBuilder
	func header_backButton() -> some View {
		
		Button {
			saveButtonTapped()
		} label: {
			HStack(alignment: .center, spacing: 4) {
				Image(systemName: "chevron.backward")
					.imageScale(.medium)
					.font(.headline.weight(.semibold))
				if hasChanges() {
					if canSave() {
						Text("Save").font(.title3)
					} else {
						Text("Cancel").font(.title3)
					}
				}
			}
		}
		.disabled(isSaving)
	}
	
	@ViewBuilder
	func header_trailingButtons() -> some View {
		
		if !isNewContact {
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				Button {
					showDiscardChangesConfirmationDialog = true
				} label: {
					Image(systemName: "eraser")
						.imageScale(.medium)
						.font(.title2)
						.foregroundColor(.gray)
				}
				.disabled(!hasChanges())
				.accessibilityLabel("Discard changes")
				
				Button {
					showDeleteContactConfirmationDialog = true
				} label: {
					Image(systemName: "trash.fill")
						.imageScale(.medium)
						.font(.title2)
						.foregroundColor(.appNegative)
				}
				.disabled(isSaving)
				.accessibilityLabel("Delete contact")
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				content_image()
				content_name()
				content_trusted()
				if showOffers {
					content_offers()
				}
				if showPasteOffer {
					content_pasteOffer()
				}
			} // </VStack>
			.padding()
		} // </ScrollView>
		.frame(maxHeight: scrollViewMaxHeight)
		.scrollingDismissesKeyboard(.interactively)
	}
	
	@ViewBuilder
	func content_image() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			Group {
				if useDiskImage && didAppear {
					ContactPhoto(fileName: contact?.photoUri, size: IMG_SIZE, useCache: false)
				} else if let uiimage = pickerResult?.image {
					Image(uiImage: uiimage)
						.resizable()
						.aspectRatio(contentMode: .fill) // FILL !
				} else {
					Image(systemName: "person.circle")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.foregroundColor(Color(UIColor.systemGray3))
				}
			}
			.frame(width: IMG_SIZE, height: IMG_SIZE)
			.clipShape(Circle())
			.onTapGesture {
				if !isSaving {
					showImageOptions = true
				}
			}
			Spacer(minLength: 0)
		}
		.padding(.bottom)
		.background(backgroundColor)
		.zIndex(1)
		.confirmationDialog("Contact Image",
			isPresented: $showImageOptions,
			titleVisibility: .automatic
		) {
			Button {
				selectImageOptionSelected()
				activeSheet = .imagePicker
			} label: {
				Text("Select image")
			}
			Button {
				takePhotoOptionSelected()
			} label: {
				Text("Take photo")
			}
			if hasImage {
				Button("Clear image", role: ButtonRole.destructive) {
					pickerResult = nil
					doNotUseDiskImage = true
				}
			}
		} // </confirmationDialog>
	}
	
	@ViewBuilder
	func content_name() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			TextField("Name", text: $name)
				.disabled(isSaving)
			
			// Clear button (appears when TextField's text is non-empty)
			Button {
				name = ""
			} label: {
				Image(systemName: "multiply.circle.fill")
					.foregroundColor(Color(UIColor.tertiaryLabel))
			}
			.disabled(isSaving)
			.isHidden(name == "")
		}
		.padding(.all, 8)
		.background(
			RoundedRectangle(cornerRadius: 8)
				.fill(Color(UIColor.systemBackground))
		)
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.textFieldBorder, lineWidth: 1)
		)
		.padding(.bottom, 30)
		.background(backgroundColor)
		.zIndex(1)
	}
	
	@ViewBuilder
	func content_trusted() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			Toggle(isOn: $trustedContact) {
				Text("Trusted contact")
			}
			.disabled(isSaving)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
				Text(verbatim: "•")
					.font(.title2)
				Text("**enabled**: they will be able to tell when payments are from you")
					.font(.subheadline)
					.fixedSize(horizontal: false, vertical: true)
			}
			.foregroundColor(.secondary)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
				Text(verbatim: "•")
					.font(.title2)
				Text("**disabled**: sent payments will be anonymous")
					.font(.subheadline)
					.fixedSize(horizontal: false, vertical: true)
			}
			.foregroundColor(.secondary)
		}
		.padding(.bottom, 30)
		.background(backgroundColor)
		.zIndex(1)
	}
	
	@ViewBuilder
	func content_offers() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Bolt12 offers")
				Spacer(minLength: 0)
				AnimatedChevron(
					position: $chevronPosition,
					color: Color(UIColor.systemGray2),
					lineWidth: 20,
					lineThickness: 2,
					verticalOffset: 8
				)
			} // </HStack>
			.background(backgroundColor)
			.contentShape(Rectangle()) // make Spacer area tappable
			.onTapGesture {
				withAnimation {
					if showingOffers {
						showingOffers = false
						chevronPosition = .pointingDown
					} else {
						showingOffers = true
						chevronPosition = .pointingUp
					}
				}
			}
			.zIndex(1)
			
			if showingOffers {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					ForEach(offerRows()) { row in
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(row.offer)
								.lineLimit(1)
								.truncationMode(.middle)
								.foregroundColor(row.isCurrentOffer ? Color.appPositive : Color.primary)
							Spacer(minLength: 8)
							Button {
								copyText(row.offer)
							} label: {
								Image(systemName: "square.on.square")
							}
						}
						.font(.subheadline)
						.padding(.vertical, 8)
						.padding(.leading, 20)
					} // </ForEach>
				} // </VStack>
				.zIndex(0)
				.transition(.move(edge: .top).combined(with: .opacity))
			}
			
		} // </VStack>
		.padding(.bottom)
	}
	
	@ViewBuilder
	func content_pasteOffer() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			Text("Bolt12 offer:")
				.padding(.bottom, 4)
			
			TextEditor(text: $pastedOffer)
				.frame(minHeight: 80, maxHeight: 80)
				.padding(.all, 8)
				.background(
					RoundedRectangle(cornerRadius: 8)
						.fill(Color(UIColor.systemBackground))
				)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(Color.textFieldBorder, lineWidth: 1)
				)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				if pastedOfferIsInvalid {
					Text("Invalid offer")
						.font(.subheadline)
						.foregroundColor(.appNegative)
				} else {
					Text(verbatim: " ")
				}
			}
			
		} // </VStack>
		.padding(.bottom)
		.onChange(of: pastedOffer) { _ in
			pastedOfferChanged()
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		if case .smartModal = location {
			footer_smartModal()
		}
	}
	
	@ViewBuilder
	func footer_smartModal() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Button {
				cancelButtonTapped()
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
					Image(systemName: "xmark")
					Text("Cancel")
				}
				.frame(width: maxFooterButtonWidth)
				.read(maxFooterButtonWidthReader)
			}
			.buttonStyle(.bordered)
			.buttonBorderShape(.capsule)
			.foregroundColor(hasName ? Color.appNegative : Color.appNegative.opacity(0.6))
			.disabled(isSaving)
			
			Spacer().frame(maxWidth: 16)
			
			Button {
				saveButtonTapped()
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
					Image(systemName: "checkmark")
					Text("Save")
				}
				.frame(width: maxFooterButtonWidth)
				.read(maxFooterButtonWidthReader)
			}
			.buttonStyle(.bordered)
			.buttonBorderShape(.capsule)
			.foregroundColor(hasName ? Color.appPositive : Color.appPositive.opacity(0.6))
			.disabled(isSaving || !canSave())
			
		} // </HStack>
		.padding()
		.assignMaxPreference(for: maxFooterButtonWidthReader.key, to: $maxFooterButtonWidth)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func activeSheetBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { activeSheet != nil },
			set: { if !$0 { activeSheet = nil }}
		)
	}
	
	var title: String {
		
		if isNewContact {
			return String(localized: "New contact")
		} else {
			return String(localized: "Edit contact")
		}
	}
	
	var scrollViewMaxHeight: CGFloat {
		
		if case .smartModal = location {
			if deviceInfo.isShortHeight {
				return CGFloat.infinity
			} else {
				return deviceInfo.windowSize.height * 0.6
			}
		} else {
			return CGFloat.infinity
		}
	}
	
	var backgroundColor: Color {
		switch location {
			case .smartModal : return Color(UIColor.systemBackground)
			case .sheet      : return Color(UIColor.systemBackground)
			case .embedded   : return Color.primaryBackground
		}
	}
	
	var trimmedName: String {
		return name.trimmingCharacters(in: .whitespacesAndNewlines)
	}
	
	var hasName: Bool {
		return !trimmedName.isEmpty
	}
	
	var useDiskImage: Bool {
		
		if doNotUseDiskImage {
			return false
		} else if let _ = pickerResult {
			return false
		} else {
			return true
		}
	}
	
	var hasImage: Bool {
		
		if doNotUseDiskImage {
			return pickerResult != nil
		} else if let _ = pickerResult {
			return true
		} else {
			return contact?.photoUri != nil
		}
	}
	
	var showOffers: Bool {
		
		if offer != nil {
			return true
		} else if let contact {
			return !contact.offers.isEmpty
		} else {
			return false
		}
	}
	
	var showPasteOffer: Bool {
		
		return (offer == nil) && (contact == nil)
	}
	
	func offerRows() -> [OfferRow] {
		
		var offers = Set<String>()
		var results = Array<OfferRow>()
		
		if let offer {
			let offerStr = offer.encode()
			offers.insert(offerStr)
			results.append(OfferRow(offer: offerStr, isCurrentOffer: true))
		}
		if let contact {
			for offer in contact.offers {
				let offerStr = offer.encode()
				if !offers.contains(offerStr) {
					offers.insert(offerStr)
					results.append(OfferRow(offer: offerStr, isCurrentOffer: false))
				}
			}
		}
		
		return results
	}
	
	func hasChanges() -> Bool {
		
		if let contact {
			if name != contact.name {
				return true
			}
			if pickerResult != nil {
				return true
			}
			if doNotUseDiskImage {
				return true
			}
			if trustedContact != contact.useOfferKey {
				return true
			}
			
			return false
			
		} else {
			return true
		}
	}
	
	func canSave() -> Bool {
		
		if !hasName {
			return false
		}
		if contact == nil {
			if offer == nil && parsedOffer == nil {
				return false
			}
		}
		
		return true
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		switch location {
		case .smartModal:
			smartModalState.onNextDidAppear {
				log.trace("didAppear()")
				didAppear = true
			}
			
		case .sheet:
			didAppear = true
			
		case .embedded:
			didAppear = true
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func selectImageOptionSelected() {
		log.trace("selectImageOptionSelected()")
		
		activeSheet = .imagePicker
	}
	
	func takePhotoOptionSelected() {
		log.trace("takePhotoOptionSelected()")
		
	#if targetEnvironment(simulator)
		toast.pop(
			"Camera not supported on simulator",
			colorScheme: colorScheme.opposite,
			alignment: .none
		)
	#else
		activeSheet = .camera
	#endif
	}
	
	func copyText(_ text: String) {
		log.trace("copyText()")
		
		UIPasteboard.general.string = text
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			style: .chrome
		)
	}
	
	func pastedOfferChanged() {
		log.trace("pastedOfferChanged()")
		
		let text = pastedOffer.trimmingCharacters(in: .whitespacesAndNewlines)
		if text.isEmpty {
			pastedOfferIsInvalid = true
		} else {
			let result: Bitcoin_kmpTry<Lightning_kmpOfferTypesOffer> =
				Lightning_kmpOfferTypesOffer.companion.decode(s: text)
			
			if result.isFailure {
				pastedOfferIsInvalid = true
			} else {
				pastedOfferIsInvalid = false
				parsedOffer = result.get()
			}
		}
	}
	
	func cancelButtonTapped() {
		log.trace("cancelButtonTapped")
		
		close()
	}
	
	func saveButtonTapped() {
		log.trace("saveButtonTapped()")
		
		if hasChanges() && canSave() {
			saveContact()
		} else {
			close()
		}
	}
	
	func discardChanges() {
		log.trace("discardChages()")
		
		name = contact?.name ?? ""
		pickerResult = nil
		doNotUseDiskImage = false
		trustedContact = contact?.useOfferKey ?? DEFAULT_TRUSTED
	}
	
	func saveContact() {
		log.trace("saveContact()")
		
		isSaving = true
		Task { @MainActor in
			
			var updatedContact: ContactInfo? = nil
			var success = false
			do {
				let updatedContactName = trimmedName
				let updatedUseOfferKey = trustedContact
				
				var oldPhotoName: String? = contact?.photoUri
				var newPhotoName: String? = nil
				
				if let pickerResult {
					newPhotoName = try await PhotosManager.shared.writeToDisk(pickerResult)
				} else if !doNotUseDiskImage {
					newPhotoName = oldPhotoName
				}
				
				log.debug("oldPhotoName: \(oldPhotoName ?? "<nil>")")
				log.debug("newPhotoName: \(newPhotoName ?? "<nil>")")
				
				let contactsManager = Biz.business.contactsManager
				
				// There are 3 ways the ManageContact view is initialized:
				//
				// 1. With a non-nil contact, and possibly a non-nil offer.
				//    In this case we're updating the contact.
				//    The given offer may be highlighted in the UI.
				//
				// 2. With a nil contact, and a non-nil offer.
				//    In this case the user wishes to create a new contact
				//    associated with the given offer.
				//
				// 3. With a nil contact, and a nil offer.
				//    In this case, the user must paste a Bolt12 offer.
				//    And we'll create the new contact with the pasted offer.
				
				if let existingContact = contact {
					updatedContact = try await contactsManager.updateContact(
						contactId: existingContact.uuid,
						name: updatedContactName,
						photoUri: newPhotoName,
						useOfferKey: updatedUseOfferKey,
						offers: existingContact.offers
					)
				} else if let newOffer = offer ?? parsedOffer {
					let existingContact = try await contactsManager.getContactForOffer(offer: newOffer)
					if let existingContact {
						// The newOffer is actually NOT new.
						// It already exists in the database and is attached to a contact.
						// For now, we will update the details of that contact.
						// In the future, it would be better to display some kind of error message,
						// and then update the UI with this existing contact.
						updatedContact = try await contactsManager.updateContact(
							contactId: existingContact.uuid,
							name: updatedContactName,
							photoUri: newPhotoName,
							useOfferKey: updatedUseOfferKey,
							offers: existingContact.offers
						)
						oldPhotoName = existingContact.photoUri
					} else {
						updatedContact = try await contactsManager.saveNewContact(
							name: updatedContactName,
							photoUri: newPhotoName,
							useOfferKey: updatedUseOfferKey,
							offer: newOffer
						)
					}
				}
				
				if let oldPhotoName, oldPhotoName != newPhotoName {
					log.debug("Deleting old photo from disk...")
					try await PhotosManager.shared.deleteFromDisk(fileName: oldPhotoName)
				}
				
				success = true
			} catch {
				log.error("contactsManager: error: \(error)")
			}
			
			isSaving = false
			if success {
				close()
			}
			if let updatedContact {
				contactUpdated(updatedContact)
			}
			
		} // </Task>
	}
	
	func deleteContact() {
		log.trace("deleteContact()")
		
		guard let cid = contact?.uuid else {
			return
		}
		
		isSaving = true
		Task { @MainActor in
			
			let contactsManager = Biz.business.contactsManager
			do {
				try await contactsManager.deleteContact(contactId: cid)
				
			} catch {
				log.error("contactsManager: error: \(error)")
			}
			
			isSaving = false
			close()
			contactUpdated(nil)
			
		} // </Task>
	}
	
	func close() {
		log.trace("close()")
		
		switch location {
		case .smartModal:
			smartModalState.close()
		case .sheet:
			presentationMode.wrappedValue.dismiss()
		case .embedded:
			presentationMode.wrappedValue.dismiss()
		}
	}
}
