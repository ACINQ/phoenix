import SwiftUI
import PhoenixShared

fileprivate let filename = "ManageContactSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ManageContactSheet: View {
	
	let offer: Lightning_kmpOfferTypesOffer
	@Binding var contact: ContactInfo?
	let isNewContact: Bool
	
	@StateObject var toast = Toast()
	
	@State var name: String
	@State var image: UIImage?
	
	@State var showImageOptions: Bool = false
	@State var isSaving: Bool = false
	
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
	
	init(offer: Lightning_kmpOfferTypesOffer, contact: Binding<ContactInfo?>) {
		self.offer = offer
		self._contact = contact
		self.isNewContact = (contact.wrappedValue == nil)
		
		if let existingContact = contact.wrappedValue {
			self._name = State(initialValue: existingContact.name)
			if let photo = existingContact.photo {
				self._image = State(initialValue: UIImage(data: photo.toSwiftData()))
			} else {
				self._image = State(initialValue: nil)
			}
		} else {
			self._name = State(initialValue: "")
			self._image = State(initialValue: nil)
		}
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
				CameraPicker(image: $image)
			
			case .imagePicker:
				ImagePicker(image: $image)
			
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
			if let uiimage = image {
				Image(uiImage: uiimage)
					.resizable()
					.aspectRatio(contentMode: .fill) // FILL !
			} else {
				Image(systemName: "person.circle")
					.resizable()
					.aspectRatio(contentMode: .fit)
			}
		}
		.frame(width: 150, height: 150)
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
			if image != nil {
				Button("Clear image", role: ButtonRole.destructive) {
					image = nil
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
			
			let c_name = trimmedName
			let c_photo = image?.jpegData(compressionQuality: 1.0)?.toKotlinByteArray()
			
			let contactsManager = Biz.business.contactsManager
			do {
				let existingContact = try await contactsManager.getContactForOffer(offer: offer)
				if let existingContact {
					contact = try await contactsManager.updateContact(
						contactId: existingContact.id,
						name: c_name,
						photo: c_photo,
						offers: existingContact.offers
					)
					
				} else {
					contact = try await contactsManager.saveNewContact(
						name: c_name,
						photo: c_photo,
						offer: offer
					)
				}
			} catch {
				log.error("contactsManager: error: \(error)")
			}
			
			isSaving = false
			if contact != nil {
				smartModalState.close()
			}
			
		} // </Task>
	}
}
