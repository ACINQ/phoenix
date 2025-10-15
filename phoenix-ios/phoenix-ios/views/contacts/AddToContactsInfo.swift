import Foundation
import PhoenixShared

struct AddToContactsInfo: Hashable {
	let offer: Lightning_kmpOfferTypesOffer?
	let address: String?
	let secret: ContactSecret?
}
