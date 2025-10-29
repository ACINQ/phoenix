import SwiftUI

fileprivate let filename = "WalletMetadataView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let IMG_SIZE: CGFloat = 48

struct WalletMetadataView: View {
	
	@State var metadata: WalletMetadata? = nil
	
	@State var originalName: String = ""
	@State var name: String = ""
	@State var photo: String = ""
	@State var isHidden: Bool = false
	@State var isDefault: Bool = false
	
	@State var showImageOptions: Bool = false
	@State var pickerResult: PickerResult? = nil
	@State var doNotUseDiskImage: Bool = false
	
	@State var isSaving: Bool = false
	@State var ignorePhotoChange: Bool = false
	@State var ignorePickerResultChange: Bool = false
	@State var ignoreIsHiddenChange: Bool = false
	@State var ignoreIsDefaultChange: Bool = false
	
	@State var didAppear: Bool = false
	
	enum ActiveSheet {
		case camera
		case imagePicker
	}
	@State var activeSheet: ActiveSheet? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("Current wallet")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack(alignment: Alignment.center) {
			list()
			toast.view()
		}
		.onAppear {
			onAppear()
		}
		.onChange(of: photo) { _ in
			photoChanged()
		}
		.onChange(of: pickerResult) { _ in
			pickerResultChanged()
		}
		.onChange(of: isHidden) { _ in
			isHiddenChanged()
		}
		.onChange(of: isDefault) { _ in
			isDefaultChanged()
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
	func list() -> some View {
		
		List {
			section_metadata()
			section_options()
			section_management()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	// --------------------------------------------------
	// MARK: Section: Metadata
	// --------------------------------------------------
	
	@ViewBuilder
	func section_metadata() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				metadata_image()
				metadata_name()
			}
		}
	}
	
	@ViewBuilder
	func metadata_image() -> some View {
		
		Group {
			if let uiimage = pickerResult?.image {
				Image(uiImage: uiimage)
					.resizable()
					.aspectRatio(contentMode: .fill) // FILL !
			} else {
				WalletImage(filename: photo, size: IMG_SIZE)
			}
		}
		.frame(width: IMG_SIZE, height: IMG_SIZE)
		.clipShape(Circle())
		.onTapGesture {
			if !isSaving {
				showImageOptions = true
			}
		}
		.confirmationDialog("Wallet Image",
			isPresented: $showImageOptions,
			titleVisibility: .automatic
		) {
			Button {
				selectEmojiOptionSelected()
			} label: {
				Text("Select emoji")
			}
			Button {
				selectPhotoOptionSelected()
			} label: {
				Text("Select photo")
			}
			Button {
				takePhotoOptionSelected()
			} label: {
				Label("Take photo", systemImage: "camera")
			}
		} // </confirmationDialog>
	}
	
	@ViewBuilder
	func metadata_name() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			TextField(originalName,
				text: $name,
				onEditingChanged: { nameEditingChanged($0) }
			)
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
	}
	
	// --------------------------------------------------
	// MARK: Section: Options
	// --------------------------------------------------
	
	@ViewBuilder
	func section_options() -> some View {
		
		Section {
			options_default()
		//	options_hidden()
		}
	}
	
	@ViewBuilder
	func options_default() -> some View {
		
		ToggleAlignment {
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Default wallet")
					
					Text(
						"""
						If a default wallet is selected, it will automatically be opened on app launch.
						"""
					)
					.lineLimit(nil)
					.font(.callout)
					.foregroundColor(.secondary)
					
				} // </VStack>
			} icon: {
				Image(systemName: "bookmark.fill")
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $isDefault)
				.labelsHidden()
				.disabled(isSaving)
		}
	}
	
	@ViewBuilder
	func options_hidden() -> some View {
		
		ToggleAlignment {
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Hidden wallet")
					
					Text(
						"""
						Wallet will not be visible in selector screens. \
						To access the wallet you must enter it's lock PIN.
						"""
					)
					.lineLimit(nil)
					.font(.callout)
					.foregroundColor(.secondary)
					
				} // </VStack>
			} icon: {
				Image(systemName: "eye.slash.fill")
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $isHidden)
				.labelsHidden()
				.disabled(isSaving)
		}
	}
	
	// --------------------------------------------------
	// MARK: Section: Management
	// --------------------------------------------------
	
	@ViewBuilder
	func section_management() -> some View {
		
		Section {
			
			Button {
				switchToAnotherWallet()
			} label: {
				Label {
					Text("Switch to another wallet")
				} icon: {
					Image(systemName: "arrow.up.backward.circle")
				}
				.font(.headline)
			}
			
			Button {
				addAnotherWallet()
			} label: {
				Label {
					Text("Add another wallet")
				} icon: {
					Image(systemName: "plus.circle")
				}
				.font(.headline)
			}
		}
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
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace(#function)
		
		guard !didAppear else {
			return
		}
		didAppear = true
		
		let currentWallet = SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
		
		self.metadata = currentWallet
		self.originalName = currentWallet.name
		self.name = currentWallet.name
		self.photo = currentWallet.photo
		self.ignorePhotoChange = true
		
		if currentWallet.isHidden {
			self.isHidden = true
			self.ignoreIsHiddenChange = true
		}
		if currentWallet.isDefault {
			self.isDefault = true
			self.ignoreIsDefaultChange = true
		}
	}
	
	func walletEmojiPickerDidSelect(_ emoji: WalletEmoji) {
		log.trace(#function)
		
		pickerResult = nil
		ignorePickerResultChange = true
		photo = emoji.filename
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	var sanitizedName: String {
		
		let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
		return if trimmedName.isEmpty {
			originalName
		} else {
			trimmedName
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func selectEmojiOptionSelected() {
		log.trace(#function)
		
		smartModalState.display(dismissable: true) {
			WalletEmojiPicker(didSelect: walletEmojiPickerDidSelect)
		}
	}
	
	func selectPhotoOptionSelected() {
		log.trace(#function)
		
		activeSheet = .imagePicker
	}
	
	func takePhotoOptionSelected() {
		log.trace(#function)
		
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
	
	func nameEditingChanged(_ isEditing: Bool) {
		log.trace("nameEditingChanged(\(isEditing))")
		
		guard !isEditing else { return }
		guard let metadata else {
			return
		}
		let newName = sanitizedName
		if newName != metadata.name {
			saveSecurityFile(trigger: .name)
		}
	}
	
	func photoChanged() {
		guard !ignorePhotoChange else {
			log.debug("ignorePhotoChange")
			ignorePhotoChange = false
			return
		}
		log.trace(#function)
		
		guard let metadata else {
			return
		}
		if photo != metadata.photo {
			saveSecurityFile(trigger: .photo)
		}
	}
	
	func pickerResultChanged() {
		guard !ignorePickerResultChange else {
			ignorePickerResultChange = false
			return
		}
		log.trace(#function)
		
		saveSecurityFile(trigger: .photo)
	}
	
	func isHiddenChanged() {
		guard !ignoreIsHiddenChange else {
			log.debug("ignoreIsHiddenChange")
			ignoreIsHiddenChange = false
			return
		}
		log.trace(#function)
		
		let enabledSecurity = Keychain.current.enabledSecurity
		
		let isEligible = enabledSecurity.contains(.lockPin)
		if isEligible {
			saveSecurityFile(trigger: .isHidden)
			
			if isHidden {
				smartModalState.display(dismissable: true) {
					DuressPinOption()
				}
			}
			
		} else if isHidden {
			
			smartModalState.display(dismissable: true) {
				HiddenWalletRequirements()
			} onWillDisappear: {
				isHidden = false
				ignoreIsHiddenChange = true
			}
		}
	}
	
	func isDefaultChanged() {
		guard !ignoreIsDefaultChange else {
			log.debug("ignoreIsDefaultChange")
			ignoreIsDefaultChange = false
			return
		}
		log.trace(#function)
		
		saveSecurityFile(trigger: .isDefault)
	}
	
	func switchToAnotherWallet() {
		log.trace(#function)
		
		SceneDelegate.get().switchToAnotherWallet()
	}
	
	func addAnotherWallet() {
		log.trace(#function)
		
		SceneDelegate.get().addAnotherWallet()
	}
	
	// --------------------------------------------------
	// MARK: Disk IO
	// --------------------------------------------------
	
	enum Trigger: CustomStringConvertible {
		case photo
		case name
		case isHidden
		case isDefault
		
		var description: String { switch self {
			case .photo     : "photo"
			case .name      : "name"
			case .isHidden  : "isHidden"
			case .isDefault : "isDefault"
		}}
	}
	
	func saveSecurityFile(trigger: Trigger) {
		log.trace("saveSecurityFile(trigger: \(trigger))")
		
		isSaving = true
		Task { @MainActor in
			
			defer {
				isSaving = false
			}
			
			guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
				log.error("SecurityFile.current(): is nil")
				return
			}
			guard case .v1(let v1) = securityFile else {
				log.error("SecurityFile.current(): v0 found")
				return
			}
			guard let walletId = Biz.walletId else {
				log.error("Biz.walletId is nil")
				return
			}
			guard let wallet = v1.getWallet(walletId) else {
				log.error("v1.getWallet(): is nil")
				return
			}
			
			let updatedWallet: SecurityFile.V1.Wallet
			let updatedV1: SecurityFile.V1
			
			var photoToDelete: String? = nil
			
			switch trigger {
			case .photo    : fallthrough
			case .name     : fallthrough
			case .isHidden :
				
				let newName = self.sanitizedName
				let newIsHidden = self.isHidden
				
				let oldPhoto: String = self.photo
				let newPhoto: String
				do {
					if let pickerResult {
						newPhoto = try await PhotosManager.shared.writeToDisk(pickerResult)
					} else {
						newPhoto = oldPhoto
					}
				} catch {
					log.error("PhotosManager.writeToDisk(): error: \(error)")
					return
				}
				
				log.debug("oldPhoto: \(oldPhoto)")
				log.debug("newPhoto: \(newPhoto)")
				
				if oldPhoto != newPhoto, !WalletEmoji.isValidFilename(oldPhoto) {
					photoToDelete = oldPhoto
				}
				
				updatedWallet = wallet.updated(name: newName, photo: newPhoto, isHidden: newIsHidden)
				updatedV1 = v1.copyAddingWallet(updatedWallet, id: walletId)
				
			case .isDefault:
				
				updatedWallet = wallet
				if self.isDefault {
					updatedV1 = v1.copySettingDefaultWalletId(walletId)
				} else {
					updatedV1 = v1.copyClearingDefaultWalletId(walletId.chain)
				}
			}
			
			self.metadata = WalletMetadata(
				wallet     : updatedWallet,
				nodeIdHash : walletId.nodeIdHash,
				isDefault  : self.isDefault
			)
			
			do throws(WriteSecurityFileError) {
				try await SecurityFileManager.shared.asyncWriteToDisk(updatedV1)
			} catch {
				log.error("SecurityFile.write(): error: \(error)")
			}
			
			if let photoToDelete {
				log.debug("Deleting old photo from disk...")
				await PhotosManager.shared.deleteFromDisk(fileName: photoToDelete)
			}
		}
	}
}
