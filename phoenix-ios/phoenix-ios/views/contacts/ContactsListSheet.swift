import SwiftUI
import PhoenixShared

fileprivate let filename = "ContactsListSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ContactsListSheet: View {
	
	let didSelectContact: (ContactInfo) -> Void
	
	@State var sortedContacts: [ContactInfo] = []
	@State var offers: [String: [String]] = [:]
	
	@State var searchText = ""
	@State var filteredContacts: [ContactInfo]? = nil
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
				.frame(maxHeight: (deviceInfo.windowSize.height / 2.0))
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Contacts")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeSheet()
			} label: {
				Image(systemName: "xmark").imageScale(.medium).font(.title2)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
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
			searchField()
			ForEach(visibleContacts) { item in
				Button {
					selectItem(item)
				} label: {
					row(item)
				}
			}
			if hasZeroMatchesForSearch {
				zeroMatches()
					.deleteDisabled(true)
			}
		} // </List>
		.listStyle(.plain)
	}
	
	@ViewBuilder
	func searchField() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Image(systemName: "magnifyingglass")
				.foregroundColor(.gray)
				.padding(.trailing, 4)
			
			TextField("Search", text: $searchText)
			
			// Clear button (appears when TextField's text is non-empty)
			Button {
				searchText = ""
			} label: {
				Image(systemName: "multiply.circle.fill")
					.foregroundColor(.secondary)
			}
			.accessibilityLabel("Clear textfield")
			.isHidden(searchText == "")
		}
		.padding(.all, 8)
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.textFieldBorder, lineWidth: 1)
		)
		.padding(.horizontal, 4)
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
	
	func selectItem(_ item: ContactInfo) {
		log.trace("selectItem: \(item.name)")
		
		didSelectContact(item)
		smartModalState.close()
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
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

