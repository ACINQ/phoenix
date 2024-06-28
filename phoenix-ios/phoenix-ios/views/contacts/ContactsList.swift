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
	
	@State var searchText = ""
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Address Book")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarItems(trailing: plusButton())
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			ForEach(sortedContacts) { item in
				Button {
					didSelectItem(item)
				} label: {
					row(item: item)
				}
			}
		} // </List>
		.listStyle(.plain)
		.searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
		.onReceive(Biz.business.contactsManager.contactsListPublisher()) {
			contactsListChanged($0)
		}
	}
	
	@ViewBuilder
	func row(item: ContactInfo) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text(item.name)
				.font(.title3.bold())
				.foregroundColor(.primary)
			Spacer()
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
	}
	
	@ViewBuilder
	func plusButton() -> some View {
		
		Button {
			// proof-of-concept
		} label: {
			Image(systemName: "plus")
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func contactsListChanged(_ updatedList: [ContactInfo]) {
		log.trace("contactsListChanged()")
		
		sortedContacts = updatedList
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func addContact() {
		log.trace("addContact()")
		
		// Todo...
	}
	
	func didSelectItem(_ item: ContactInfo) {
		log.trace("didSelectItem: \(item.name)")
		
		// Todo...
	}
}
