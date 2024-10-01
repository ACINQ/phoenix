import Foundation
import PhoenixShared

enum ChannelFundingProblem: Error {
	case insufficientFunds
	case spliceAlreadyInProgress
	case spliceAborted
	case sessionError
	case disconnected
	case other
	
	func localizedDescription() -> String {
		
		switch self {
		case .insufficientFunds:
			return String(localized: "Insufficient funds")
		case .spliceAlreadyInProgress:
			return String(localized: "Splice already in progress")
		case .spliceAborted:
			return String(localized: "Splice has been aborted")
		case .sessionError:
			return String(localized: "Splice-out session error")
		case .disconnected:
			return String(localized: "Disconnected during splice-out attempt")
		case .other:
			return String(localized: "Unknown splice-out error")
		}
	}
	
	static func fromResponse(
		_ response: Lightning_kmpChannelFundingResponse?
	) -> ChannelFundingProblem? {
		
		guard let response else {
			return .other
		}
		
		guard let failure = response.asFailure() else {
			return nil // not a failure
		}
		
		if let _ = failure.asInsufficientFunds() {
			return .insufficientFunds
		}
		if let _ = failure.asInvalidSpliceOutPubKeyScript() {
			return .sessionError
		}
		if let _ = failure.asSpliceAlreadyInProgress() {
			return .spliceAlreadyInProgress
		}
		if let _ = failure.asConcurrentRemoteSplice() {
			return .spliceAborted
		}
		if let _ = failure.asChannelNotQuiescent() {
			return .spliceAborted
		}
		if let _ = failure.asInvalidChannelParameters() {
			return .sessionError
		}
		if let _ = failure.asInvalidLiquidityAds() {
			return .sessionError
		}
		if let _ = failure.asFundingFailure() {
			return .sessionError
		}
		if let _ = failure.asCannotStartSession() {
			return .sessionError
		}
		if let _ = failure.asInteractiveTxSessionFailed() {
			return .sessionError
		}
		if let _ = failure.asCannotCreateCommitTx() {
			return .sessionError
		}
		if let _ = failure.asAbortedByPeer() {
			return .sessionError
		}
		if let _ = failure.asUnexpectedMessage() {
			return .sessionError
		}
		if let _ = failure.asDisconnected() {
			return .disconnected
		}
		
		return .other
	}
}
