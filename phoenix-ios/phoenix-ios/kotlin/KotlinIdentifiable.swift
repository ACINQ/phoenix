import Foundation
import PhoenixShared


extension Lightning_kmpWalletPayment {

	var identifiable: String {
		// @see LightningExtensions.kt: `fun WalletPayment.id()`
		return self.id()
	}
}

extension Lightning_kmpIncomingPayment.ReceivedWith {
	
	var identifiable: Int {
		return self.hash
	}
}

extension WalletPaymentId {
	
	var identifiable: String {
		return self.identifier
	}
}

extension WalletPaymentOrderRow {
	
	/// Returns a unique identifier, in the form of:
	/// - "outgoing|id|createdAt|completedAt|metadataModifiedAt"
	/// - "incoming|paymentHash|createdAt|completedAt|metadataModifiedAt"
	///
	var identifiable: String {
		return self.identifier
	}
}
