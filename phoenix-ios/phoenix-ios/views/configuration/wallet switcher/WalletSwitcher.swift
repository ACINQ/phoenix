import SwiftUI

fileprivate let filename = "WalletSwitcher"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let IMG_SIZE: CGFloat = 48

struct WalletSwitcher: View {
	
	@State var metadata: WalletMetadata? = nil
	
	@State var originalName: String = ""
	@State var name: String = ""
	@State var photo: String = ""
	@State var hidden: Bool = false
	
	@State var showImageOptions: Bool = false
	@State var pickerResult: PickerResult? = nil
	@State var doNotUseDiskImage: Bool = false
	
	@State var isSaving: Bool = false
	@State var ignorePhotoChange: Bool = false
	@State var ignoreHiddenChange: Bool = false
	
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
			.navigationTitle(NSLocalizedString("Display options", comment: "Navigation bar title"))
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
			section_currentWallet()
		//	section_switchAccounts()
		//	section_addAccount()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_currentWallet() -> some View {
		
		Section {
			
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				currentWallet_image()
				currentWallet_name()
			}
			
			currentWallet_hidden()
			
		} header: {
			Text("Current wallet")
		}
	}
	
	@ViewBuilder
	func currentWallet_image() -> some View {
		
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
				selectIconOptionSelected()
			} label: {
				Text("Select built-in icon")
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
	func currentWallet_name() -> some View {
		
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
	
	@ViewBuilder
	func currentWallet_hidden() -> some View {
		
		ToggleAlignment {
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
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
			
			Toggle("", isOn: $hidden)
				.labelsHidden()
				.disabled(isSaving)
				.onChange(of: hidden) { _ in
					hiddenChanged()
				}
		}
	}
	
	@ViewBuilder
	func section_switchAccounts() -> some View {
		// Todo...
		EmptyView()
	}
	
	@ViewBuilder
	func section_addAccount() -> some View {
		// Todo...
		EmptyView()
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
		
		let metadata = SecurityFileManager.shared.currentWalletMetadata() ?? WalletMetadata.default()
		self.metadata = metadata
		self.originalName = metadata.name
		self.name = metadata.name
		self.photo = metadata.photo
		self.hidden = metadata.hidden
	}
	
	func walletIconPickerDidSelect(_ icon: WalletIcon) {
		log.trace(#function)
		
		pickerResult = nil
		photo = icon.filename
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
	
	func selectIconOptionSelected() {
		log.trace("selectIconOptionSelected()")
		
		smartModalState.display(dismissable: true) {
			WalletIconPicker(didSelect: walletIconPickerDidSelect)
		}
	}
	
	func selectPhotoOptionSelected() {
		log.trace("selectPhotoOptionSelected()")
		
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
	
	func nameEditingChanged(_ isEditing: Bool) {
		log.trace("nameEditingChanged(\(isEditing))")
		
		guard !isEditing else { return }
		guard let metadata else {
			return
		}
		let newName = sanitizedName
		if newName != metadata.name {
			saveSecurityFile()
		}
	}
	
	func photoChanged() {
		guard !ignorePhotoChange else {
			ignorePhotoChange = false
			return
		}
		log.trace(#function)
		
		guard let metadata else {
			return
		}
		if photo != metadata.photo {
			saveSecurityFile()
		}
	}
	
	func hiddenChanged() {
		guard !ignoreHiddenChange else {
			ignoreHiddenChange = false
			return
		}
		log.trace(#function)
		
		let enabledSecurity = Keychain.current.enabledSecurity
		
		let eligible = enabledSecurity.contains(.lockPin)
		if eligible {
			saveSecurityFile()
		} else {
			smartModalState.display(dismissable: true) {
				HiddenWalletSheet()
			} onWillDisappear: {
				hidden = false
				ignoreHiddenChange = true
			}
		}
	}
	
	func saveSecurityFile() {
		log.trace(#function)
		
		isSaving = true
		Task { @MainActor in
			
			defer {
				isSaving = false
			}
			
			let newName = sanitizedName
			let newHidden = self.hidden
			
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
			
			let securityFile: SecurityFile.Version
			do throws(ReadSecurityFileError) {
				securityFile = try await SecurityFileManager.shared.asyncReadFromDisk()
			} catch {
				log.error("SecurityFile.readFromDisk(): error: \(error)")
				return
			}
			
			guard case .v1(let v1) = securityFile else {
				log.error("SecurityFile.readFromDisk(): v0 found")
				return
			}
			guard let walletId = Biz.walletId else {
				log.error("Biz.walletId is nil")
				return
			}
			guard let wallet = v1.getWallet(walletId) else {
				log.error("v1.getWallet() returned nil")
				return
			}
			
			let updatedWallet = wallet.updated(name: newName, photo: newPhoto, hidden: newHidden)
			let updatedV1 = v1.copyWithWallet(updatedWallet, id: walletId)
			
			self.metadata = WalletMetadata(wallet: updatedWallet)
			
			do throws(WriteSecurityFileError) {
				try await SecurityFileManager.shared.asyncWriteToDisk(updatedV1)
			} catch {
				log.error("SecurityFile.write(): error: \(error)")
			}
			
			if oldPhoto != newPhoto, !WalletIcon.isValidFilename(oldPhoto) {
				log.debug("Deleting old photo from disk...")
				await PhotosManager.shared.deleteFromDisk(fileName: oldPhoto)
			}
		}
	}
}
