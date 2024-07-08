import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactsList"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactsList: View {
	
	@State var sortedContacts: [ContactInfo] = []
	@State var offers: [String: [String]] = [:]
	
	@State var searchText = ""
	@State var filteredContacts: [ContactInfo]? = nil
	
	@State var pendingDelete: ContactInfo? = nil
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Address Book")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		list()
			.onReceive(Biz.business.contactsManager.contactsListPublisher()) {
				contactsListChanged($0)
			}
			.onChange(of: searchText) { _ in
				searchTextChanged()
			}
		
	}
	
	@ViewBuilder
	func list() -> some View {
		
		List {
			ForEach(visibleContacts) { item in
				Button {
					selectItem(item)
				} label: {
					row(item)
				}
				.swipeActions(allowsFullSwipe: false) {
					Button {
						selectItem(item)
					} label: {
						Label("Edit", systemImage: "square.and.pencil")
					}
					Button {
						pendingDelete = item // prompt for confirmation
					} label: {
						Label("Delete", systemImage: "trash.fill")
					}
					.tint(.red)
				}
			}
			if hasZeroMatchesForSearch {
				zeroMatches()
					.deleteDisabled(true)
			}
		} // </List>
		.listStyle(.plain)
		.searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .automatic))
		.confirmationDialog("Delete contact?",
			isPresented: confirmationDialogBinding(),
			titleVisibility: Visibility.automatic
		) {
			Button("Delete contact", role: ButtonRole.destructive) {
				deleteContact()
			}
		}
	}
	
	@ViewBuilder
	func row(_ item: ContactInfo) -> some View {
		
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
	func zeroMatches() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("No matches for search").foregroundStyle(.secondary)
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
	
	func confirmationDialogBinding() -> Binding<Bool> {
		
		return Binding(
			get: { pendingDelete != nil },
			set: { if !$0 { pendingDelete = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func contactsListChanged(_ updatedList: [ContactInfo]) {
		log.trace("contactsListChanged()")
		
		sortedContacts = updatedList
		
		var updatedOffers: [String: [String]] = [:]
		for contact in sortedContacts {
			let key: String = contact.id
			let values: [String] = contact.offers.map { $0.encode().lowercased() }
			
			updatedOffers[key] = values
		}
		
		offers = updatedOffers
	}
	
	func searchTextChanged() {
		log.trace("searchTextChanged: \(searchText)")
		
		guard !searchText.isEmpty else {
			filteredContacts = nil
			return
		}
		
		let searchtext = searchText.lowercased()
		filteredContacts = sortedContacts.filter { (contact: ContactInfo) in
			if contact.name.localizedCaseInsensitiveContains(searchtext) {
				return true
			}
			
			if let offers = offers[contact.id] {
				if offers.contains(searchtext) {
					return true
				}
			}
			
			return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func deleteContact() {
		log.trace("deleteContact: \(pendingDelete?.name ?? "<nil>")")
		
		guard let contact = pendingDelete else {
			return
		}
		
		Task { @MainActor in
			
			let contactsManager = Biz.business.contactsManager
			do {
				try await contactsManager.deleteContact(contactId: contact.uuid)
			} catch {
				log.error("contactsManager.deleteContact(): error: \(error)")
			}
		}
	}
	
	func selectItem(_ item: ContactInfo) {
		log.trace("selectItem: \(item.name)")
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: false) {
			ManageContactSheet(offer: nil, contact: item, contactUpdated: { _ in })
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func dismissKeyboardIfVisible() -> Void {
		log.trace("dismissKeyboardIfVisible()")
		
		let keyWindow = UIApplication.shared.connectedScenes
			.filter({ $0.activationState == .foregroundActive })
			.map({ $0 as? UIWindowScene })
			.compactMap({ $0 })
			.first?.windows
			.filter({ $0.isKeyWindow }).first
		keyWindow?.endEditing(true)
	}
}
