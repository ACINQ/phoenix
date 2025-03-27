import SwiftUI
import PhoenixShared

fileprivate let filename = "ManageContact"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate struct OfferRow: Identifiable {
	let raw: ContactOffer
	let identifier: String
	let label: String
	let text: String
	let isReadonly: Bool
	
	init(raw: ContactOffer, isReadonly: Bool) {
		self.raw = raw
		self.identifier = raw.id.toSwiftData().toHex()
		self.label = raw.label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
		self.text = raw.offer.encode()
		self.isReadonly = isReadonly
	}
	
	var id: String {
		return identifier
	}
}

fileprivate struct AddressRow: Identifiable {
	let raw: ContactAddress
	let identifier: String
	let label: String
	let text: String
	let isReadonly: Bool
	
	init(raw: ContactAddress, isReadonly: Bool) {
		self.raw = raw
		self.identifier = raw.id.toSwiftData().toHex()
		self.label = raw.label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
		self.text = raw.address.trimmingCharacters(in: .whitespacesAndNewlines)
		self.isReadonly = isReadonly
	}
	
	var id: String {
		return identifier
	}
}

fileprivate enum InvalidReason {
	case invalidFormat
	case localDuplicate
	case databaseDuplicate(contact: ContactInfo)
}

fileprivate let IMG_SIZE: CGFloat = 150
fileprivate let DEFAULT_TRUSTED: Bool = true
fileprivate let ROW_VERTICAL_SPACING: CGFloat = 15

struct ManageContact: View {
	
	enum Location {
		case smartModal
		case sheet(closeAction: () -> Void)
		case embedded
	}
	
	let location: Location
	let popTo: ((PopToDestination) -> Void)?
	
	let contact: ContactInfo?
	let contactUpdated: (ContactInfo?) -> Void
	
	@State private var name: String
	@State private var trustedContact: Bool
	@State private var showImageOptions: Bool = false
	@State private var pickerResult: PickerResult?
	@State private var doNotUseDiskImage: Bool = false
	
	@State private var isSaving: Bool = false
	@State private var showDiscardChangesConfirmationDialog: Bool = false
	@State private var showDeleteContactConfirmationDialog: Bool = false
	
	@State private var offers: [OfferRow]
	@State private var offers_hasChanges: Bool
	
	@State private var editOffer_index: Int? = nil
	@State private var editOffer_label: String = ""
	@State private var editOffer_text: String = ""
	@State private var editOffer_invalidReason: InvalidReason? = nil
	
	@State private var addresses: [AddressRow]
	@State private var addresses_hasChanges: Bool
	
	@State private var editAddress_index: Int? = nil
	@State private var editAddress_label: String = ""
	@State private var editAddress_text: String = ""
	@State private var editAddress_invalidReason: InvalidReason? = nil
	
	enum FooterType: Int {
		case expanded_standard = 1
		case expanded_squeezed = 2
		case compact_standard = 3
		case compact_squeezed = 4
		case accessible = 5
	}
	@State var footerType: [DynamicTypeSize: FooterType] = [:]
	
	@State var didAppear: Bool = false
	
	enum ActiveSheet {
		case camera
		case imagePicker
	}
	@State var activeSheet: ActiveSheet? = nil
	
	// For the footer buttons: [cancel, save]
	enum FooterButtonWidth: Preference {}
	let footerButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var footerButtonWidth: CGFloat? = nil
	
	enum FooterButtonHeight: Preference {}
	let footerButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<FooterButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var footerButtonHeight: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(
		location: Location,
		popTo: ((PopToDestination) -> Void)?,
		info: AddToContactsInfo?,
		contact: ContactInfo?,
		contactUpdated: @escaping (ContactInfo?) -> Void
	) {
		self.location = location
		self.popTo = popTo
		self.contact = contact
		self.contactUpdated = contactUpdated
		
		self._name = State(initialValue: contact?.name ?? "")
		self._trustedContact = State(initialValue: contact?.useOfferKey ?? DEFAULT_TRUSTED)
		
		do {
			var set = Set<Bitcoin_kmpByteVector32>()
			var rows = Array<OfferRow>()
			
			if let contact {
				for offer in contact.offers {
					if !set.contains(offer.id) {
						set.insert(offer.id)
						rows.append(OfferRow(raw: offer, isReadonly: false))
					}
				}
			}
			var hasNewOffer = false
			if let newOffer = info?.offer {
				let offer = ContactOffer(
					offer: newOffer,
					label: "",
					createdAt: Date.now.toMilliseconds().toKotlinLong()
				)
				if !set.contains(offer.id) {
					set.insert(offer.id)
					rows.append(OfferRow(raw: offer, isReadonly: true))
					hasNewOffer = true
				}
			}

			
			self._offers = State(initialValue: rows)
			self._offers_hasChanges = State(initialValue: (contact != nil && hasNewOffer))
		}
		do {
			var set = Set<Bitcoin_kmpByteVector32>()
			var rows = Array<AddressRow>()
			
			if let contact {
				for address in contact.addresses {
					if !set.contains(address.id) {
						set.insert(address.id)
						rows.append(AddressRow(raw: address, isReadonly: false))
					}
				}
			}
			var hasNewAddress = false
			if let newAddress = info?.address {
				let address = ContactAddress(
					address: newAddress,
					label: "",
					createdAt: Date.now.toMilliseconds().toKotlinLong()
				)
				if !set.contains(address.id) {
					set.insert(address.id)
					rows.append(AddressRow(raw: address, isReadonly: true))
					hasNewAddress = true
				}
			}

			
			self._addresses = State(initialValue: rows)
			self._addresses_hasChanges = State(initialValue: (contact != nil && hasNewAddress))
		}
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
				.navigationBarItems(leading: header_cancelButton(), trailing: header_doneButton())
			//	.background(
			//		Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
			//	)
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
		.onChange(of: offers_hasChanges) { _ in
			paymentOptionsChanged()
		}
		.onChange(of: addresses_hasChanges) { _ in
			paymentOptionsChanged()
		}
		.confirmationDialog("Discard changes?",
			isPresented: $showDiscardChangesConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Discard changes", role: ButtonRole.destructive) {
				close()
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
			header_cancelButton()
			Spacer()
			header_doneButton()
		}
		.padding()
	}
	
	@ViewBuilder
	func header_embedded() -> some View {
		
	//	Spacer().frame(height: 25)
		EmptyView()
	}
	
	@ViewBuilder
	func header_cancelButton() -> some View {
		
		Button {
			cancelButtonTapped()
		} label: {
			Text("Cancel").font(.headline)
		}
		.disabled(isSaving)
		.accessibilityLabel("Discard changes")
	}
	
	@ViewBuilder
	func header_doneButton() -> some View {
		
		Button {
			saveButtonTapped()
		} label: {
			Text("Done").font(.headline)
		}
		.disabled(!hasChanges || !canSave || isSaving)
		.accessibilityLabel("Save changes")
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				content_image()
				content_name()
				content_trusted()
				content_offers()
				content_addresses()
			} // </VStack>
			.padding()
		} // </ScrollView>
		.frame(maxHeight: scrollViewMaxHeight)
		.scrollDismissesKeyboard(.interactively)
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
	}
	
	@ViewBuilder
	func content_trusted() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			Toggle(isOn: $trustedContact) {
				Text("Trusted contact")
			}
			.disabled(isSaving)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("**enabled**: they will be able to tell when payments are from you")
					.font(.subheadline)
					.fixedSize(horizontal: false, vertical: true)
					.foregroundColor(.secondary)
			}
			.padding(.vertical, 8)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("**disabled**: sent payments will be anonymous")
					.font(.subheadline)
					.fixedSize(horizontal: false, vertical: true)
					.foregroundColor(.secondary)
			}
			
		}
		.padding(.bottom, 30)
	}
	
	@ViewBuilder
	func content_offers() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Bolt12 offers:")
				Spacer(minLength: 0)
				Button {
					createNewOffer()
				} label: {
					Image(systemName: "plus")
				}
				.disabled(isSaving)
			} // </HStack>
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0 ..< offers.count, id: \.self) { idx in
					if let editingIndex = editOffer_index, editingIndex == idx {
						content_offer_editRow()
					} else {
						content_offer_row(idx)
					}
				} // </ForEach>
			} // </VStack>
			
			if let index = editOffer_index, index == offers.count {
				content_offer_editRow()
			} else if offers.isEmpty {
				content_offer_emptyRow()
			}
			
		} // </VStack>
		.padding(.bottom, 30)
	}
	
	@ViewBuilder
	func content_offer_row(_ index: Int) -> some View {
		
		let row: OfferRow = offers[index]
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			bullet()
			
			Group {
				if row.label.isEmpty {
					Text(row.text)
						.lineLimit(1)
						.truncationMode(.middle)
						.foregroundStyle(row.isReadonly ? Color.appPositive : Color.primary)
				} else {
					Text(row.label)
						.lineLimit(1)
						.truncationMode(.tail)
						.foregroundStyle(row.isReadonly ? Color.appPositive : Color.primary)
					Text(": \(row.text)")
						.lineLimit(1)
						.truncationMode(.middle)
						.foregroundStyle(Color.secondary)
						.layoutPriority(-1)
				}
			} // </Group>
			.font(.callout)
			
			Spacer(minLength: 8)
			
			Menu {
				Button {
					copyText(row.text)
				} label: {
					Label("Copy", systemImage: "square.on.square")
				}
				Button {
					sendPayment(offer: row.raw.offer)
				} label: {
					Label("Send payment", systemImage: "paperplane.fill")
				}
				Button {
					editOffer(index: index)
				} label: {
					Label("Edit", systemImage: "pencil.line")
				}
				Button {
					deleteOffer(index: index)
				} label: {
					Label("Delete", systemImage: "trash")
				}
				
			} label: {
				Image(systemName: "line.3.horizontal")
			}
			
		} // </HStack>
		.padding(.top, ROW_VERTICAL_SPACING)
	}
	
	@ViewBuilder
	func content_offer_emptyRow() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			bullet()
			Text("none")
				.lineLimit(1)
				.foregroundStyle(Color.secondary)
				.layoutPriority(-1)
				.font(.callout)
		} // </HStack>
		.padding(.top, ROW_VERTICAL_SPACING)
	}
	
	@ViewBuilder
	func content_offer_editRow() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			bullet()
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 2) {
					TextField("label (optional)", text: $editOffer_label)
						.textInputAutocapitalization(.never)
					
					// Clear button (appears when TextField's text is non-empty)
					if !editOffer_label.isEmpty {
						Button {
							editOffer_label = ""
						} label: {
							Image(systemName: "multiply.circle.fill")
								.foregroundColor(.secondary)
						}
					}
				} // </HStack>
				.padding(.all, 8)
				.background(
					RoundedRectangle(cornerRadius: 8)
						.fill(Color(UIColor.systemBackground))
				)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(Color.textFieldBorder, lineWidth: 1)
				)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
					TextField("lno1... (paste offer here)", text: $editOffer_text, axis: .vertical)
						.textInputAutocapitalization(.never)
						.autocorrectionDisabled()
						.lineLimit(4)
						.disabled(editOffer_isReadonly)
					
					// Clear button (appears when TextField's text is non-empty)
					if !editOffer_text.isEmpty && !editOffer_isReadonly {
						Button {
							editOffer_text = ""
						} label: {
							Image(systemName: "multiply.circle.fill")
								.foregroundColor(.secondary)
						}
					}
				} // </HStack>
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
					
					Button {
						cancelEditedOffer()
					} label: {
						Text("Cancel").foregroundStyle(Color.appNegative)
					}
					
					Spacer()
					if let reason = editOffer_invalidReason {
						switch reason {
						case .invalidFormat:
							Text("Invalid format").foregroundColor(.appNegative)
						case .localDuplicate:
							Text("Duplicate within this contact").foregroundColor(.appNegative)
						case .databaseDuplicate(let contact):
							Text("Duplicate in \(contact.name)").foregroundColor(.appNegative)
						}
					}
					Spacer()
					
					Button {
						processEditedOffer()
					} label: {
						Text("Done")
					}
					.disabled(isSaving)
				}
				
			} // </VStack>
			.font(.subheadline)
			
		} // </HStack>
		.padding(.top, ROW_VERTICAL_SPACING)
	}
	
	@ViewBuilder
	func content_addresses() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Lightning addresses:")
				Spacer(minLength: 0)
				Button {
					createNewAddress()
				} label: {
					Image(systemName: "plus")
				}
				.disabled(isSaving)
			} // </HStack>
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0 ..< addresses.count, id: \.self) { idx in
					if let editingIndex = editAddress_index, editingIndex == idx {
						content_address_editRow()
					} else {
						content_address_row(idx)
					}
				} // </ForEach>
			} // </VStack>
			
			if let index = editAddress_index, index == addresses.count {
				content_address_editRow()
			} else if addresses.isEmpty {
				content_address_emptyRow()
			}
			
		} // </VStack>
		.padding(.bottom, 30)
	}
	
	@ViewBuilder
	func content_address_row(_ index: Int) -> some View {
		
		let row: AddressRow = addresses[index]
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			bullet()
			
			Group {
				if row.label.isEmpty {
					Text(row.text)
						.lineLimit(1)
						.truncationMode(.middle)
						.foregroundStyle(row.isReadonly ? Color.appPositive : Color.primary)
				} else {
					Text(row.label)
						.lineLimit(1)
						.truncationMode(.tail)
						.foregroundStyle(row.isReadonly ? Color.appPositive : Color.primary)
					Text(": \(row.text)")
						.lineLimit(1)
						.truncationMode(.middle)
						.foregroundStyle(Color.secondary)
						.layoutPriority(-1)
				}
			}
			.font(.callout)
			
			Spacer(minLength: 8)
			
			Menu {
				Button {
					copyText(row.text)
				} label: {
					Label("Copy", systemImage: "square.on.square")
				}
				Button {
					sendPayment(address: row.raw.address)
				} label: {
					Label("Send payment", systemImage: "paperplane.fill")
				}
				Button {
					editAddress(index: index)
				} label: {
					Label("Edit", systemImage: "pencil.line")
				}
				Button {
					deleteAddress(index: index)
				} label: {
					Label("Delete", systemImage: "trash")
				}
				
			} label: {
				Image(systemName: "line.3.horizontal")
			}
			
		}
		.padding(.top, ROW_VERTICAL_SPACING)
	}
	
	@ViewBuilder
	func content_address_emptyRow() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			bullet()
			Text("none")
				.lineLimit(1)
				.foregroundStyle(Color.secondary)
				.layoutPriority(-1)
				.font(.callout)
		}
		.padding(.top, ROW_VERTICAL_SPACING)
	}
	
	@ViewBuilder
	func content_address_editRow() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			bullet()
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 2) {
					TextField("label (optional)", text: $editAddress_label)
						.textInputAutocapitalization(.never)
					
					// Clear button (appears when TextField's text is non-empty)
					if !editAddress_label.isEmpty {
						Button {
							editAddress_label = ""
						} label: {
							Image(systemName: "multiply.circle.fill")
								.foregroundColor(.secondary)
						}
					}
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
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					TextField(
						"", // ignored
						text: $editAddress_text,
						prompt: Text(verbatim: "user@domain.tld"), // verbatim used to disable email detection
						axis: .vertical
					)
					.textContentType(.emailAddress)
					.textInputAutocapitalization(.never)
					.autocorrectionDisabled()
					.lineLimit(2)
					.disabled(editAddress_isReadonly)
					
					// Clear button (appears when TextField's text is non-empty)
					if !editAddress_text.isEmpty && !editAddress_isReadonly {
						Button {
							editAddress_text = ""
						} label: {
							Image(systemName: "multiply.circle.fill")
								.foregroundColor(.secondary)
						}
					}
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
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					Button {
						cancelEditedAddress()
					} label: {
						Text("Cancel").foregroundStyle(Color.appNegative)
					}
					
					Spacer()
					if let reason = editAddress_invalidReason {
						switch reason {
						case .invalidFormat:
							Text("Invalid format").foregroundColor(.appNegative)
						case .localDuplicate:
							Text("Duplicate within this contact").foregroundColor(.appNegative)
						case .databaseDuplicate(let contact):
							Text("Duplicate in \(contact.name)").foregroundColor(.appNegative)
						}
					}
					Spacer()
					
					Button {
						processEditedAddress()
					} label: {
						Text("Done")
					}
					.disabled(isSaving)
				}
				
			} // </VStack>
			.font(.subheadline)
			
		} // </HStack>
		.padding(.top, ROW_VERTICAL_SPACING)
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
	func footer() -> some View {
		
		if case .smartModal = location {
			footer_smartModal()
		} else {
			footer_navStack()
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
				.frame(width: footerButtonWidth)
				.read(footerButtonWidthReader)
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
				.frame(width: footerButtonWidth)
				.read(footerButtonWidthReader)
			}
			.buttonStyle(.bordered)
			.buttonBorderShape(.capsule)
			.foregroundColor(hasName ? Color.appPositive : Color.appPositive.opacity(0.6))
			.disabled(isSaving || !canSave)
			
		} // </HStack>
		.padding()
		.assignMaxPreference(for: footerButtonWidthReader.key, to: $footerButtonWidth)
	}
	
	@ViewBuilder
	func footer_navStack() -> some View {
		
		if !isNewContact {
			Group {
				let type = footerType[dynamicTypeSize] ?? FooterType.expanded_standard
				switch type {
				case .expanded_standard:
					footer_navStack_standard(compact: false)
				case .expanded_squeezed:
					footer_navStack_squeezed(compact: false)
				case .compact_standard:
					footer_navStack_standard(compact: true)
				case .compact_squeezed:
					footer_navStack_squeezed(compact: true)
				case .accessible:
					footer_navStack_accessible()
				}
			} // </Group>
			.frame(maxWidth: .infinity)
			.background(
				Color(
					colorScheme == ColorScheme.light
					? UIColor.primaryBackground
					: UIColor.secondarySystemGroupedBackground
				)
				.edgesIgnoringSafeArea(.bottom) // background color should extend to bottom of screen
			)
		}
	}
	
	@ViewBuilder
	func footer_navStack_standard(compact: Bool) -> some View {
		
		// We're making both buttons the same size.
		//
		// ---------------------------------
		//  Delete Contact |  Send Payment
		// ---------------------------------
		//        ^                ^        < same size
		
		let type: FooterType = compact ? FooterType.compact_standard : FooterType.expanded_standard
		
		HStack(alignment: VerticalAlignment.centerTopLine, spacing: 10) {
			
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_button_deleteContact(compact: compact, lineLimit: 1)
			} wasTruncated: {
				footerTruncationDetected(type, "delete")
			}
			.frame(minWidth: footerButtonWidth, alignment: Alignment.trailing)
			.read(footerButtonWidthReader)
			.read(footerButtonHeightReader)
			
			if globalSendButtonVisible {
				if let footerButtonHeight {
					Divider().frame(height: footerButtonHeight)
				}
				
				TruncatableView(fixedHorizontal: true, fixedVertical: true) {
					footer_button_sendPayment(compact: compact, lineLimit: 1)
				} wasTruncated: {
					footerTruncationDetected(type, "pay")
				}
				.frame(minWidth: footerButtonWidth, alignment: Alignment.leading)
				.read(footerButtonWidthReader)
				.read(footerButtonHeightReader)
			}
			
		} // </HStack>
		.padding(.all)
		.assignMaxPreference(for: footerButtonWidthReader.key, to: $footerButtonWidth)
		.assignMaxPreference(for: footerButtonHeightReader.key, to: $footerButtonHeight)
	}
	
	@ViewBuilder
	func footer_navStack_squeezed(compact: Bool) -> some View {
		
		// There's not enough space to make both buttons the same size.
		// So we're just trying to put them on one line.
		//
		// -------------------------------
		//  Delete Contact | Send Payment
		// -------------------------------
		//        ^                ^      < NOT the same size
		
		let type: FooterType = compact ? FooterType.compact_squeezed : FooterType.expanded_squeezed
		
		HStack(alignment: VerticalAlignment.centerTopLine, spacing: 10) {
			
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_button_deleteContact(compact: compact, lineLimit: 1)
			} wasTruncated: {
				footerTruncationDetected(type, "delete")
			}
			.read(footerButtonHeightReader)
			
			if globalSendButtonVisible {
				if let footerButtonHeight {
					Divider().frame(height: footerButtonHeight)
				}
				
				TruncatableView(fixedHorizontal: true, fixedVertical: true) {
					footer_button_sendPayment(compact: compact, lineLimit: 1)
				} wasTruncated: {
					footerTruncationDetected(type, "pay")
				}
				.read(footerButtonHeightReader)
			}
		} // </HStack>
		.padding(.all)
		.assignMaxPreference(for: footerButtonHeightReader.key, to: $footerButtonHeight)
	}
	
	@ViewBuilder
	func footer_navStack_accessible() -> some View {
		
		// There's a large font being used, and possibly a small screen too.
		// Horizontal space is so tight that we can't get the 3 buttons on a single line.
		//
		// So we're going to put them on multiple lines.
		//
		// --------------
		// Delete contact
		//  Send payment
		// --------------
		
		VStack(alignment: HorizontalAlignment.center, spacing: 16) {
			footer_button_deleteContact(compact: true, lineLimit: nil)
			if globalSendButtonVisible {
				footer_button_sendPayment(compact: true, lineLimit: nil)
			}
		}
		.padding(.horizontal, 4) // allow content to be closer to edges
		.padding(.vertical)
	}
	
	@ViewBuilder
	func footer_button_deleteContact(compact: Bool, lineLimit: Int?) -> some View {
		
		Button {
			deleteButtonTapped()
		} label: {
			Label(compact ? "Delete" : "Delete contact", systemImage: "trash.fill")
				.foregroundColor(.appNegative)
				.lineLimit(lineLimit)
		}
		.disabled(isSaving)
	}
	
	@ViewBuilder
	func footer_button_sendPayment(compact: Bool, lineLimit: Int?) -> some View {
		
		Button {
			payButtonTapped()
		} label: {
			Label(compact ? "Pay" : "Send payment", systemImage: "paperplane.fill")
				.foregroundColor(.appPositive)
				.lineLimit(lineLimit)
		}
		.disabled(hasChanges || isSaving)
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
	
	var isNewContact: Bool {
		return contact == nil
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
	
	var editOffer_isReadonly: Bool {
		
		if let index = editOffer_index, index < offers.count {
			return offers[index].isReadonly
		} else {
			return false
		}
	}
	
	var editAddress_isReadonly: Bool {
		
		if let index = editAddress_index, index < addresses.count {
			return addresses[index].isReadonly
		} else {
			return false
		}
	}
	
	var globalSendButtonVisible: Bool {
		
		if hasChanges {
			// Because tapping the button takes the user away from this screen.
			// They may want to save those changes first.
			return false
		}
		
		let count = offers.count + addresses.count
		return count == 1
	}
	
	var hasChanges: Bool {
		
		if let contact {
			if name != contact.name {
				return true
			}
			if trustedContact != contact.useOfferKey {
				return true
			}
		} else {
			if name != "" {
				return true
			}
			if trustedContact != DEFAULT_TRUSTED {
				return true
			}
		}
		
		if pickerResult != nil {
			return true
		}
		if doNotUseDiskImage {
			return true
		}
		if offers_hasChanges || addresses_hasChanges {
			return true
		}
		
		return false
	}
	
	var canSave: Bool {
		
		if !hasName {
			return false
		}
		if offers.isEmpty && addresses.isEmpty {
			return false
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
	
	func paymentOptionsChanged() {
		log.trace("paymentOptionsChanged()")
		
		// This method is called when `offers` or `addresses` changes.
		// Which may affect the footer, because the sendButton might appear/disappear.
		// So we reset any measurements/calculations we've done for the footer.
		
		footerType = [:]
		footerButtonWidth = nil
		footerButtonHeight = nil
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func footerTruncationDetected(_ type: FooterType, _ identifier: String) {
		
		switch type {
		case .expanded_standard:
			log.debug("footerTruncationDetected: expanded_standard (\(identifier))")
			footerType[dynamicTypeSize] = .expanded_squeezed
		
		case .expanded_squeezed:
			log.debug("footerTruncationDetected: expanded_squeezed (\(identifier))")
			footerType[dynamicTypeSize] = .compact_standard
			
		case .compact_standard:
			log.debug("footerTruncationDetected: compact_standard (\(identifier))")
			footerType[dynamicTypeSize] = .compact_squeezed
			
		case .compact_squeezed:
			log.debug("footerTruncationDetected: compact_squeezed (\(identifier))")
			footerType[dynamicTypeSize] = .accessible
			
		case .accessible:
			log.debug("footerTruncationDetected: accessible (\(identifier))")
			break
		}
	}
	
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
	
	func deleteButtonTapped() {
		log.trace("deleteButtonTapped()")
		
		showDeleteContactConfirmationDialog = true
	}
	
	func payButtonTapped() {
		log.trace("payButtonTapped()")
		
		// The payButton should only be visible if there is exactly one payment method.
		// So we can just grab the first available here.
		
		if let offer = offers.first {
			sendPayment(offer: offer.raw.offer)
		} else if let address = addresses.first {
			sendPayment(address: address.raw.address)
		}
	}
	
	func cancelButtonTapped() {
		log.trace("cancelButtonTapped()")
		
		if hasChanges {
			showDiscardChangesConfirmationDialog = true
		} else {
			close()
		}
	}
	
	func saveButtonTapped() {
		log.trace("saveButtonTapped()")
		
		if hasChanges && canSave {
			saveContact()
		} else {
			close()
		}
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
	
	// --------------------------------------------------
	// MARK: Actions: Database
	// --------------------------------------------------
	
	func saveContact() {
		log.trace("saveContact()")
		
		isSaving = true
		Task { @MainActor in
			do {
				let contactId = contact?.id ?? Lightning_kmpUUID.companion.randomUUID()
				let updatedName = trimmedName
				let updatedUseOfferKey = trustedContact
				
				let oldPhotoName: String? = contact?.photoUri
				var newPhotoName: String? = nil
				
				if let pickerResult {
					newPhotoName = try await PhotosManager.shared.writeToDisk(pickerResult)
				} else if !doNotUseDiskImage {
					newPhotoName = oldPhotoName
				}
				
				log.debug("oldPhotoName: \(oldPhotoName ?? "<nil>")")
				log.debug("newPhotoName: \(newPhotoName ?? "<nil>")")
				
				let updatedContact = ContactInfo(
					id: contactId,
					name: updatedName,
					photoUri: newPhotoName,
					useOfferKey: updatedUseOfferKey,
					offers: offers.map { $0.raw },
					addresses: addresses.map { $0.raw }
				)
				
				try await Biz.business.contactsManager.saveContact(contact: updatedContact)
				
				if let oldPhotoName, oldPhotoName != newPhotoName {
					log.debug("Deleting old photo from disk...")
					await PhotosManager.shared.deleteFromDisk(fileName: oldPhotoName)
				}
			
				close()
				contactUpdated(updatedContact)
			} catch {
				log.error("contactsManager.saveContact(): error: \(error)")
			}
			
			isSaving = false
		} // </Task>
	}
	
	func deleteContact() {
		log.trace("deleteContact()")
		
		guard let cid = contact?.id else {
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
	
	// --------------------------------------------------
	// MARK: Actions: Offers
	// --------------------------------------------------
	
	func createNewOffer() {
		log.trace("createNewOffer()")
		
		// If the user is currently editing an offer
		if editOffer_index != nil {
			// Then we try to auto-save the changes
			guard processEditedOffer() else {
				// Auto-save failed so we're aborting
				return
			}
		}
		
		editOffer_index = offers.count
		editOffer_label = ""
		editOffer_text = ""
	}
	
	func editOffer(index: Int) {
		log.trace("editOffer(index: \(index))")
		
		// If the user is currently editing an offer
		if editOffer_index != nil {
			// Then we try to auto-save the changes
			guard processEditedOffer() else {
				// Auto-save failed so we're aborting
				return
			}
		}
		
		editOffer_index = index
		let offer = offers[index]
		editOffer_label = offer.label
		editOffer_text = offer.text
	}
	
	func deleteOffer(index: Int) {
		log.trace("deleteOffer(index: \(index))")
		
		guard index < offers.count else {
			log.info("deleteOffer(index: \(index)): ignoring: index out of bounds")
			return
		}
		
		offers.remove(at: index)
		offers_hasChanges = true
	}
	
	@discardableResult
	func processEditedOffer() -> Bool {
		log.trace("processEditedOffer()")
		
		guard let index = editOffer_index else {
			log.info("processEditedOffer(): ignoring: editOffer_index is nil")
			return true
		}
		
		let label = editOffer_label.trimmingCharacters(in: .whitespacesAndNewlines)
		let text = editOffer_text.trimmingCharacters(in: .whitespacesAndNewlines)
		
		guard !text.isEmpty else {
			editOffer_invalidReason = .invalidFormat
			return false
		}
		
		let result: Bitcoin_kmpTry<Lightning_kmpOfferTypesOffer> =
			Lightning_kmpOfferTypesOffer.companion.decode(s: text)

		guard !result.isFailure else {
			editOffer_invalidReason = .invalidFormat
			return false
		}
		
		let offer: Lightning_kmpOfferTypesOffer = result.get()!
		let raw = ContactOffer(
			offer: offer,
			label: label,
			createdAt: Date.now.toMilliseconds().toKotlinLong()
		)
		let row = OfferRow(raw: raw, isReadonly: false)
		
		// Check for local duplicates
		
		var isLocalDuplicate = false
		for (idx, existing) in offers.enumerated() {
			if idx == index {
				// ignore: this is the row we're editing
			} else {
				if existing.identifier == row.identifier {
					isLocalDuplicate = true
				}
			}
		}
		
		guard !isLocalDuplicate else {
			editOffer_invalidReason = .localDuplicate
			return false
		}
		
		// Check for duplicates in the database
		
		var databaseDuplicate: ContactInfo? = nil
		if let matchingContact = Biz.business.contactsManager.contactForOfferId(offerId: row.raw.id) {
			if let currentContact = contact {
				if currentContact.id != matchingContact.id {
					databaseDuplicate = matchingContact
				}
			} else {
				databaseDuplicate = matchingContact
			}
		}
		
		guard databaseDuplicate == nil else {
			editOffer_invalidReason = .databaseDuplicate(contact: databaseDuplicate!)
			return false
		}
		
		// Looks good
		
		if index < offers.count {
			offers[index] = row
		} else {
			offers.append(row)
		}
		offers_hasChanges = true
		
		editOffer_invalidReason = nil
		editOffer_index = nil
		return true
	}
	
	func cancelEditedOffer() {
		log.trace("cancelEditedOffer()")
		
		if editOffer_index != nil {
			editOffer_index = nil
			editOffer_invalidReason = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions: Addresses
	// --------------------------------------------------
	
	func createNewAddress() {
		log.trace("createNewAddress()")
		
		// If the user is currently editing an address
		if editAddress_index != nil {
			// Then we try to auto-save the changes
			guard processEditedAddress() else {
				// Auto-save failed so we're aborting
				return
			}
		}
		
		editAddress_index = addresses.count
		editAddress_label = ""
		editAddress_text = ""
	}
	
	func editAddress(index: Int) {
		log.trace("editAddress(index: \(index))")
		
		// If the user is currently editing an address
		if editAddress_index != nil {
			// Then we try to auto-save the changes
			guard processEditedAddress() else {
				// Auto-save failed so we're aborting
				return
			}
		}
		
		editAddress_index = index
		let address = addresses[index]
		editAddress_label = address.label
		editAddress_text = address.text
	}
	
	func deleteAddress(index: Int) {
		log.trace("deleteAddress(index: \(index))")
		
		guard index < addresses.count else {
			log.info("deleteAddress(index: \(index)): ignoring: index out of bounds")
			return
		}
		
		addresses.remove(at: index)
		addresses_hasChanges = true
	}
	
	@discardableResult
	func processEditedAddress() -> Bool {
		log.trace("processEditedAddress()")
		
		guard let index = editAddress_index else {
			log.info("processEditedAddress(): ignoring: editAddress_index is nil")
			return true
		}
		
		let label = editAddress_label.trimmingCharacters(in: .whitespacesAndNewlines)
		let text = editAddress_text.trimmingCharacters(in: .whitespacesAndNewlines)
		
		guard text.isValidEmailAddress() else {
			editAddress_invalidReason = nil
			return false
		}
		
		let raw = ContactAddress(
			address: text,
			label: label,
			createdAt: Date.now.toMilliseconds().toKotlinLong()
		)
		let row = AddressRow(raw: raw, isReadonly: false)
		
		// Check for local duplicates
		
		var isLocalDuplicate = false
		for (idx, existing) in addresses.enumerated() {
			if idx == index {
				// ignore: this is the row we're editing
			} else {
				if existing.identifier == row.identifier {
					isLocalDuplicate = true
				}
			}
		}
		
		guard !isLocalDuplicate else {
			editAddress_invalidReason = .localDuplicate
			return false
		}
		
		// Check for duplicates in the database
		
		var databaseDuplicate: ContactInfo? = nil
		if let matchingContact = Biz.business.contactsManager.contactForLightningAddress(address: row.raw.address) {
			if let currentContact = contact {
				if currentContact.id != matchingContact.id {
					databaseDuplicate = matchingContact
				}
			} else {
				databaseDuplicate = matchingContact
			}
		}
		
		guard databaseDuplicate == nil else {
			editAddress_invalidReason = .databaseDuplicate(contact: databaseDuplicate!)
			return false
		}
		
		// Looks good
		
		if index < addresses.count {
			addresses[index] = row
		} else {
			addresses.append(row)
		}
		addresses_hasChanges = true
		
		editAddress_invalidReason = nil
		editAddress_index = nil
		return true
	}
	
	func cancelEditedAddress() {
		log.trace("cancelEditedAddress()")
		
		if editAddress_index != nil {
			editAddress_index = nil
			editAddress_invalidReason = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions: Payments
	// --------------------------------------------------
	
	func sendPayment(offer: Lightning_kmpOfferTypesOffer) {
		log.trace("sendPayment(offer:)")
		
		let offerString = offer.encode()
		AppDelegate.get().externalLightningUrlPublisher.send(offerString)
		popViewAfterSendPayment()
	}
	
	func sendPayment(address: String) {
		log.trace("sendPayment(address:)")
		
		AppDelegate.get().externalLightningUrlPublisher.send(address)
		popViewAfterSendPayment()
	}
	
	func popViewAfterSendPayment() {
		log.trace("popViewAfterSendPayment()")
		
		if #available(iOS 17, *) {
		// navCoordinator.path.removeAll()
		// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
		//	Do not do this here. It interferes with navigation.
		//
		// Instead we allow the MainView_X to perform both
		// `path.removeAll()` & `path.append(x)` at the same time.
		// Doing it at the same time allows navigation to work properly.
			
		} else { // iOS 16
			
			if let popTo {
				popTo(.ConfigurationView(followedBy: nil))
			}
			
			if case .sheet(let closeAction) = location {
				closeAction()
			} else {
				close()
			}
		}
	}
}
