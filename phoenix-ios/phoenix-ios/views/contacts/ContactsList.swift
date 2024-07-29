import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactsList"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactsList: View {
	
	let popTo: (PopToDestination) -> Void
	
	@State var sortedContacts: [ContactInfo] = []
	@State var offers: [String: [String]] = [:]
	
	@State var searchText = ""
	@State var filteredContacts: [ContactInfo]? = nil
	
	@State var addItem: Bool = false
	@State var selectedItem: ContactInfo? = nil
	@State var pendingDelete: ContactInfo? = nil
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: selectedItemView(),
					isActive: selectedItemBinding()
				) {
					EmptyView()
				}
				.isDetailLink(false)
			} // else: uses.navigationStackDestination()
			
			content()
		}
		.navigationStackDestination(isPresented: selectedItemBinding()) { // For iOS 16+
			selectedItemView()
		}
		.navigationTitle("Contacts")
		.navigationBarTitleDisplayMode(.inline)
		.navigationBarItems(trailing: plusButton())
	}
	
	@ViewBuilder
	func content() -> some View {
		
		list()
			.onAppear() {
				onAppear()
			}
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
					selectedItem = item
				} label: {
					row(item)
				}
				.swipeActions(allowsFullSwipe: false) {
					Button {
						selectedItem = item
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
	func zeroMatches() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("No matches for search").foregroundStyle(.secondary)
			Spacer()
		}
		.padding(.all, 4)
	}
	
	@ViewBuilder
	func selectedItemView() -> some View {
		
		if let selectedItem {
			ManageContact(
				location: .embedded,
				popTo: popToWrapper,
				offer: nil,
				contact: selectedItem,
				contactUpdated: { _ in }
			)
		} else if addItem {
			ManageContact(
				location: .embedded,
				popTo: popToWrapper,
				offer: nil,
				contact: nil,
				contactUpdated: { _ in }
			)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func plusButton() -> some View {
		
		Button {
			addItem = true
		} label: {
			Image(systemName: "plus")
		}
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
	
	func selectedItemBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { selectedItem != nil || addItem },
			set: { if !$0 { selectedItem = nil; addItem = false }}
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
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		popToDestination = destination
		popTo(destination)
	}
	
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
