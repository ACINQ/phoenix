import Foundation
import PhoenixShared

extension Lightning_kmpUUID: @retroactive Identifiable {
	
	public var id: String {
		return self.description
	}
}

extension WalletPaymentInfo: @retroactive Identifiable {
	
	/// Returns a unique identifier, in the form of:
	/// "paymentId|paymentHash|contactHash|metadataModifiedAt"
	///
	public var id: String {
		
		let paymentId: String = payment.id.description()
		let paymentHash: Int = payment.hash
		let contactHash: Int = contact?.hash ?? 0
		let metadataHash: Int = metadata.hash
		
		return "\(paymentId)|\(paymentHash)|\(contactHash)|\(metadataHash)"
	}
}

extension BitcoinUnit: @retroactive Identifiable {
	
	public var id: String {
		// BitcoinUnit is an enum in Kotlin.
		// So `.name` is guaranteed to be unique.
		return self.name
	}
}

extension FiatCurrency: @retroactive Identifiable {
	
	public var id: String {
		// FiatCurrency is an enum in Kotlin.
		// So `.name` is guaranteed to be unique.
		return self.name
	}
}

extension ContactOffer: @retroactive Identifiable {}
extension ContactAddress: @retroactive Identifiable {}
extension ContactInfo: @retroactive Identifiable {}

extension BoltCardInfo: @retroactive Identifiable {
}

extension Lightning_kmpWalletState.Utxo: @retroactive Identifiable {
	
	public var id: String {
		return "\(previousTx.txid.toHex()):\(outputIndex):\(blockHeight)"
	}
}

extension Lightning_kmpSensitiveTaskEventsTaskIdentifier.InteractiveTx: @retroactive Identifiable {
	
	public var id: String {
		return "\(self.channelId.toHex()):\(self.fundingTxIndex)"
	}
}
