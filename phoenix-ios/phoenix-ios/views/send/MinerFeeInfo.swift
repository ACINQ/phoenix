import Foundation
import PhoenixShared

struct MinerFeeInfo {
	let pubKeyScript: Bitcoin_kmpByteVector
	let feerate: Lightning_kmpFeeratePerKw
	let minerFee: Bitcoin_kmpSatoshi
}
