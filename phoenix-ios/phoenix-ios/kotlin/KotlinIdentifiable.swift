import Foundation
import PhoenixShared


extension WalletPaymentId: Identifiable {
	
	/// Returns a unique identifier, in the form of:
	/// - "outgoing|id"
	/// - "incoming|paymentHash"
	///
	public var id: String {
		return self.identifier // defined in WalletPayment.kt
	}
}

extension WalletPaymentOrderRow: Identifiable {
	
	/// Returns a unique identifier, in the form of:
	/// - "outgoing|id|createdAt|completedAt|metadataModifiedAt"
	/// - "incoming|paymentHash|createdAt|completedAt|metadataModifiedAt"
	///
	public var id: String {
		return self.identifier // defined in SqlitePaymentsDb.kt
	}
}

extension BitcoinUnit: Identifiable {
	
	public var id: String {
		// BitcoinUnit is an enum in Kotlin.
		// So `.name` is guaranteed to be unique.
		return self.name
	}
}

extension FiatCurrency: Identifiable {
	
	public var id: String {
		// FiatCurrency is an enum in Kotlin.
		// So `.name` is guaranteed to be unique.
		return self.name
	}
}
