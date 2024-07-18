import SwiftUI
import PhoenixShared

fileprivate let filename = "ManageContactSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ManageContactSheet: View {
	
	let offer: Lightning_kmpOfferTypesOffer?
	
	let contact: ContactInfo?
	let contactUpdated: (ContactInfo?) -> Void
	let isNewContact: Bool
	
	let IMG_SIZE: CGFloat = 150
	
	@StateObject var toast = Toast()
	
	@State var name: String
	@State var updatedImage: UIImage?
	@State var useUpdatedImage: Bool = false
	
	@State var showImageOptions: Bool = false
	@State var isSaving: Bool = false
	@State var showDeleteContactConfirmationDialog: Bool = false
	
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
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	init(
		offer: Lightning_kmpOfferTypesOffer?,
		contact: ContactInfo?,
		contactUpdated: @escaping (ContactInfo?) -> Void
	) {
		
		self.offer = offer
		self.contact = contact
		self.contactUpdated = contactUpdated
		self.isNewContact = (contact == nil)
		
		self._name = State(initialValue: contact?.name ?? "")
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.center) {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				header()
				content()
				footer()
			}
			toast.view()
		} // </ZStack>
		.sheet(isPresented: activeSheetBinding()) { // SwiftUI only allows for 1 ".sheet"
			switch activeSheet! {
			case .camera:
				CameraPicker(image: $updatedImage)
			
			case .imagePicker:
				ImagePicker(image: $updatedImage)
			
			} // </switch>
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Group {
				if isNewContact {
					Text("Add contact")
				} else {
					Text("Edit contact")
				}
			}
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
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			content_image().padding(.bottom)
			content_textField().padding(.bottom)
			content_details()
		}
		.padding()
	}
	
	@ViewBuilder
	func content_image() -> some View {
		
		Group {
			if useDiskImage {
				ContactPhoto(fileName: contact?.photoUri, size: IMG_SIZE, useCache: false)
			} else if let uiimage = updatedImage {
				Image(uiImage: uiimage)
					.resizable()
					.aspectRatio(contentMode: .fill) // FILL !
			} else {
				Image(systemName: "person.circle")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(.gray)
			}
		}
		.frame(width: IMG_SIZE, height: IMG_SIZE)
		.clipShape(Circle())
		.onTapGesture {
			if !isSaving {
				showImageOptions = true
			}
		}
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
					updatedImage = nil
					useUpdatedImage = true
				}
			}
		} // </confirmationDialog>
	}
	
	@ViewBuilder
	func content_textField() -> some View {
		
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
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.textFieldBorder, lineWidth: 1)
		)
	}
	
	@ViewBuilder
	func content_details() -> some View {
		
		if let offer {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				Text("Offer:")
				Text(offer.encode())
					.lineLimit(2)
					.multilineTextAlignment(.leading)
					.truncationMode(.middle)
					.font(.subheadline)
					.padding(.leading, 20)
			}
			.frame(maxWidth: .infinity)
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Button {
				cancel()
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
				saveContact()
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
			.disabled(isSaving || !hasName)
			
		} // </HStack>
		.padding(.vertical)
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
	
	var trimmedName: String {
		return name.trimmingCharacters(in: .whitespacesAndNewlines)
	}
	
	var hasName: Bool {
		return !trimmedName.isEmpty
	}
	
	var useDiskImage: Bool {
		
		if useUpdatedImage {
			return false
		} else if let _ = updatedImage {
			return false
		} else {
			return true
		}
	}
	
	var hasImage: Bool {
		
		if useUpdatedImage {
			return updatedImage != nil
		} else if let _ = updatedImage {
			return true
		} else {
			return contact?.photoUri != nil
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
	
	func cancel() {
		log.trace("cancel")
		smartModalState.close()
	}
	
	func saveContact() {
		log.trace("saveContact()")
		
		isSaving = true
		Task { @MainActor in
			
			var updatedContact: ContactInfo? = nil
			var success = false
			do {
				let updatedContactName = trimmedName
				
				let oldPhotoName: String? = contact?.photoUri
				var newPhotoName: String? = nil
				
				if let newImg = updatedImage {
					newPhotoName = try await PhotosManager.shared.writeToDisk(image: newImg)
				} else if !useUpdatedImage {
					newPhotoName = oldPhotoName
				}
				
				log.debug("oldPhotoName: \(oldPhotoName ?? "<nil>")")
				log.debug("newPhotoName: \(newPhotoName ?? "<nil>")")
				
				let contactsManager = Biz.business.contactsManager
				if let offer {
					let existingContact = try await contactsManager.getContactForOffer(offer: offer)
					if let existingContact {
						updatedContact = try await contactsManager.updateContact(
							contactId: existingContact.uuid,
							name: updatedContactName,
							photoUri: newPhotoName,
							offers: existingContact.offers
						)
						
					} else {
						updatedContact = try await contactsManager.saveNewContact(
							name: updatedContactName,
							photoUri: newPhotoName,
							offer: offer
						)
					}
				} else if let existingContact = contact {
					updatedContact = try await contactsManager.updateContact(
						contactId: existingContact.uuid,
						name: updatedContactName,
						photoUri: newPhotoName,
						offers: existingContact.offers
					)
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
				smartModalState.close()
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
			smartModalState.close()
			contactUpdated(nil)
			
		} // </Task>
	}
}
