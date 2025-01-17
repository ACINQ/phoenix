import SwiftUI
import PhoenixShared

fileprivate let filename = "EditInfoView"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct EditInfoView: View {
	
	let location: PaymentView.Location
	@Binding var paymentInfo: WalletPaymentInfo
	
	let defaultDescText: String
	let originalDescText: String?
	@State var descText: String
	
	let maxDescCount: Int = 64
	@State var remainingDescCount: Int
	
	let originalNotesText: String?
	@State var notesText: String
	
	let maxNotesCount: Int = 280
	@State var remainingNotesCount: Int
	
	@State var showDiscardChangesConfirmationDialog: Bool = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(location: PaymentView.Location, paymentInfo: Binding<WalletPaymentInfo>) {
		
		self.location = location
		_paymentInfo = paymentInfo
		
		let pi = paymentInfo.wrappedValue
		
		defaultDescText = pi.paymentDescription(options: .none) ?? ""
		let realizedDesc = pi.paymentDescription(options: .userDescription) ?? ""
		
		if realizedDesc == defaultDescText {
			originalDescText = nil
			_descText = State(initialValue: "")
			_remainingDescCount = State(initialValue: maxDescCount)
		} else {
			originalDescText = realizedDesc
			_descText = State(initialValue: realizedDesc)
			_remainingDescCount = State(initialValue: maxDescCount - realizedDesc.count)
		}
		
		originalNotesText = pi.metadata.userNotes
		let notes = pi.metadata.userNotes ?? ""
		
		_notesText = State(initialValue: notes)
		_remainingNotesCount = State(initialValue: maxNotesCount - notes.count)
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			main()
				.navigationTitle(String(localized: "Edit Info", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
			
		case .embedded:
			main()
				.navigationTitle(String(localized: "Edit Info", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarBackButtonHidden(true)
				.navigationBarItems(leading: header_cancelButton(), trailing: header_doneButton())
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			ScrollView {
				content()
			}
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		Group {
			switch location {
			case .sheet:
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					header_cancelButton()
					Spacer()
					header_doneButton()
				}
				.padding()
				
			case .embedded:
				Spacer().frame(height: 25)
			}
		}
		.confirmationDialog("Discard changes?",
			isPresented: $showDiscardChangesConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Discard changes", role: ButtonRole.destructive) {
				close()
			}
		}
	}
	
	@ViewBuilder
	func header_cancelButton() -> some View {
		
		Button {
			cancelButtonTapped()
		} label: {
			Text("Cancel").font(.headline)
		}
		.accessibilityLabel("Discard changes")
	}
	
	@ViewBuilder
	func header_doneButton() -> some View {
		
		Button {
			saveButtonTapped()
		} label: {
			Text("Done").font(.headline)
		}
		.disabled(!hasChanges)
		.accessibilityLabel("Save changes")
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Description:")
				.padding(.leading, 8)
				.padding(.bottom, 4)
				.accessibilityHint("Maximum length is \(maxDescCount) characters")
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(defaultDescText, text: $descText)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					descText = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(descText == "")
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
				Spacer()
				
				Text("\(remainingDescCount) remaining")
					.font(.callout)
					.foregroundColor(remainingDescCount >= 0 ? Color.secondary : Color.appNegative)
					.accessibilityLabel("\(remainingDescCount) characters remaining")
			}
			.padding([.leading, .trailing], 8)
			.padding(.top, 4)
			
			Text("Notes:")
				.padding(.top, 20)
				.padding(.leading, 8)
				.padding(.bottom, 4)
				.accessibilityHint("Maximum length is \(maxNotesCount) characters")
				
			TextEditor(text: $notesText)
				.frame(minHeight: 80, maxHeight: 320)
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
				
				Text("\(remainingNotesCount) remaining")
					.font(.callout)
					.foregroundColor(remainingNotesCount >= 0 ? Color.secondary : Color.appNegative)
					.accessibilityLabel("\(remainingNotesCount) characters remaining")
			}
			.padding([.leading, .trailing], 8)
			.padding(.top, 4)
		}
		.padding(.top)
		.padding([.leading, .trailing])
		.onChange(of: descText) {
			descTextDidChange($0)
		}
		.onChange(of: notesText) {
			notesTextDidChange($0)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var hasChanges: Bool {
		
		if descText != (originalDescText ?? "") {
			return true
		}
		if notesText != (originalNotesText ?? "") {
			return true
		}
		
		return false
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func descTextDidChange(_ newText: String) {
		log.trace("descTextDidChange()")
		
		remainingDescCount = maxDescCount - newText.count
	}
	
	func notesTextDidChange(_ newText: String) {
		log.trace("notesTextDidChange()")
		
		remainingNotesCount = maxNotesCount - newText.count
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
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
		
		let paymentId: Lightning_kmpUUID = paymentInfo.payment.id
		var desc = descText.trimmingCharacters(in: .whitespacesAndNewlines)
		
		if desc.count > maxDescCount {
			let endIdx = desc.index(desc.startIndex, offsetBy: maxDescCount)
			let substr = desc[desc.startIndex ..< endIdx]
			desc = String(substr)
		}
		
		let newDesc = (desc == defaultDescText) ? nil : (desc.count == 0 ? nil : desc)
		
		var notes = notesText.trimmingCharacters(in: .whitespacesAndNewlines)
		
		if notes.count > maxNotesCount {
			let endIdx = notes.index(notes.startIndex, offsetBy: maxNotesCount)
			let substr = notes[notes.startIndex ..< endIdx]
			notes = String(substr)
		}
		
		let newNotes = notes.count == 0 ? nil : notes
		
		if (originalDescText != newDesc) || (originalNotesText != newNotes) {
		
			let business = Biz.business
			business.databaseManager.paymentsDb { (paymentsDb: SqlitePaymentsDb?, _) in
				
				paymentsDb?.updateUserInfo(id: paymentId, userDescription: newDesc, userNotes: newNotes) { (err) in
					
					if let err = err {
						log.error("paymentsDb.updateMetadata: \(String(describing: err))")
					}
				}
			}
		} else {
			log.debug("no changes - nothing to save")
		}
		
		close()
	}
	
	func close() {
		log.trace("close()")
		
		presentationMode.wrappedValue.dismiss()
	}
}
