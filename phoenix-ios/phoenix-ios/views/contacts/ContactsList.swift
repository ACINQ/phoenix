import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactsList"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactsList: View {
	
	enum NavLinkTag: String, Codable {
		case AddItem
		case SelectedItem
	}
	
	let popTo: (PopToDestination) -> Void
	
	@State var sortedContacts: [ContactInfo] = []
	
	@State var search_offers: [Lightning_kmpUUID: [String]] = [:]
	@State var search_addresses: [Lightning_kmpUUID: [String]] = [:]
	
	@State var searchText = ""
	@State var filteredContacts: [ContactInfo]? = nil
	
	@State var selectedItem: ContactInfo? = nil
	@State var pendingDelete: ContactInfo? = nil
	
	@State var didAppear = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("Contacts")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarItems(trailing: plusButton())
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
		}
		.onAppear() {
			onAppear()
		}
		.onReceive(Biz.business.databaseManager.contactsListPublisher()) {
			contactsListChanged($0)
		}
		.onChange(of: searchText) { _ in
			searchTextChanged()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			ForEach(visibleContacts) { item in
				Button {
					selectContact(item)
				} label: {
					row(item)
				}
				.swipeActions(allowsFullSwipe: false) {
					Button {
						selectContact(item)
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
				zeroMatchesRow()
					.deleteDisabled(true)
				
			} else if hasZeroContacts {
				zeroContactsRow()
					.deleteDisabled(true)
			}
		} // </List>
		.listStyle(.plain)
		.searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
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
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				Text("No Contacts")
					.font(.title3)
					.foregroundColor(.primary)
				Text("Add contacts for easy & quick payments")
					.font(.subheadline)
					.foregroundColor(.secondary)
			}
			Spacer()
		}
		.padding(.all, 4)
	}
	
	@ViewBuilder
	func plusButton() -> some View {
		
		Button {
			navigateTo(.AddItem)
		} label: {
			Image(systemName: "plus")
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
		case .AddItem:
			ManageContact(
				location: .embedded,
				popTo: popToWrapper,
				info: nil,
				contact: nil,
				contactUpdated: { _ in }
			)
			
		case .SelectedItem:
			if let selectedItem {
				ManageContact(
					location: .embedded,
					popTo: popToWrapper,
					info: nil,
					contact: selectedItem,
					contactUpdated: { _ in }
				)
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
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	func confirmationDialogBinding() -> Binding<Bool> {
		
		return Binding(
			get: { pendingDelete != nil },
			set: { if !$0 { pendingDelete = nil }}
		)
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
	}
	
	func searchTextChanged() {
		log.trace("searchTextChanged: \(searchText)")
		
		guard !searchText.isEmpty else {
			filteredContacts = nil
			return
		}
		
		let searchtext = searchText.lowercased()
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
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popToWrapper(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
			popTo(destination)
		}
	}
	
	func selectContact(_ contact: ContactInfo) {
		log.trace("selectContact: \(contact.id)")
		
		selectedItem = contact
		navigateTo(.SelectedItem)
	}
	
	func deleteContact() {
		log.trace("deleteContact: \(pendingDelete?.name ?? "<nil>")")
		
		guard let contact = pendingDelete else {
			return
		}
		
		Task { @MainActor in
			
			do {
				let contactsDb = try await Biz.business.databaseManager.contactsDb()
				try await contactsDb.deleteContact(contactId: contact.id)
			} catch {
				log.error("contactsDb.deleteContact(): error: \(error)")
			}
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
