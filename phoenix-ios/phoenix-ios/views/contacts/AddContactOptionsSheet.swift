import SwiftUI

struct AddContactOptionsSheet: View {
	
	let createNewContact: () -> Void
	let addToExistingContact: () -> Void
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			title()
			createNewContactRow()
			addToExistingContactRow()
		}
		.padding(.all)
	}
	
	@ViewBuilder
	func title() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 4)
			Text("Add contact")
			Spacer(minLength: 4)
		}
		.font(.title2)
	}
	
	@ViewBuilder
	func createNewContactRow() -> some View {
		
		Button {
			smartModalState.close {
				createNewContact()
			}
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Text("Create new contact")
				Spacer()
			}
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(
			ScaleButtonStyle(
				cornerRadius: 16,
				borderStroke: Color.appAccent
			)
		)
	}
	
	@ViewBuilder
	func addToExistingContactRow() -> some View {
		
		Button {
			smartModalState.close {
				addToExistingContact()
			}
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Text("Add to existing contact")
				Spacer()
			}
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(
			ScaleButtonStyle(
				cornerRadius: 16,
				borderStroke: Color.appAccent
			)
		)
	}
}
