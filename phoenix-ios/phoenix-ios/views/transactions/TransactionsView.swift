import SwiftUI

struct TransactionsView: View {
	
	@ViewBuilder
	var body: some View {
		
		List {
			
			
		}
		.listStyle(.insetGrouped)
		.navigationBarTitle(
			NSLocalizedString("Transactions", comment: "Navigation bar title"),
			displayMode: .inline
		)
	}
}
