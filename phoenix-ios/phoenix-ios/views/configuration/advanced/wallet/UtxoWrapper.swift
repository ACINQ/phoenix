import Swift
import PhoenixShared

struct UtxoWrapper: Identifiable {
	let utxo: Lightning_kmpWalletState.Utxo
	let confirmationCount: Int64
	
	var amount: Bitcoin_kmpSatoshi {
		return utxo.amount
	}
	
	var txid: Bitcoin_kmpTxId {
		return utxo.previousTx.txid
	}
	
	var id: String {
		return utxo.id
	}
}
