import Foundation
import PhoenixShared


struct PaymentsSection: Identifiable {
	let year: Int
	let month: Int
	let name: String
	var payments: [WalletPaymentOrderRow] = []
	
	var id: String {
		"\(year)-\(month)"
	}
}
