import Foundation

enum InboundFeeWarning {
	case liquidityPolicyDisabled
	case overAbsoluteFee(
		canRequestLiquidity: Bool,
		maxAbsoluteFeeSats: Int64,
		swapFeeSats: Int64
	)
	case overRelativeFee(
		canRequestLiquidity: Bool,
		maxRelativeFeePercent: Double,
		swapFeeSats: Int64
	)
	case feeExpected(
		swapFeeSats: Int64
	)
	case unknownFeeExpected;
	
	var type: InboundFeeWarningType {
		switch self {
		case .liquidityPolicyDisabled:
			return .willFail
		case .overAbsoluteFee(_, _, _):
			return .willFail
		case .overRelativeFee(_, _, _):
			return .willFail
		case .feeExpected(_):
			return .feeExpected
		case .unknownFeeExpected:
			return .feeExpected
		}
	}
}

enum InboundFeeWarningType {
	case willFail
	case feeExpected
}
