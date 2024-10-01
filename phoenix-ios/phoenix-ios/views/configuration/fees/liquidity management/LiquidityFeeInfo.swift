import Foundation
import PhoenixShared

struct LiquidityFeeParams {
	let amount: Bitcoin_kmpSatoshi
	let feerate: Lightning_kmpFeeratePerKw
	let fundingRate: Lightning_kmpLiquidityAdsFundingRate
}

struct LiquidityFeeEstimate {
	let minerFee: Bitcoin_kmpSatoshi
	let serviceFee: Bitcoin_kmpSatoshi
}

struct LiquidityFeeInfo {
	let params: LiquidityFeeParams
	let estimate: LiquidityFeeEstimate
}
