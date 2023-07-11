import Foundation
import PhoenixShared

enum SpliceOutProblem: Error {
	case insufficientFunds
	case spliceAlreadyInProgress
	case channelNotIdle
	case sessionError
	case disconnected
	case other
	
	func localizedDescription() -> String {
		
		switch self {
		case .insufficientFunds:
			return String(localized: "Insufficient funds")
		case .spliceAlreadyInProgress:
			return String(localized: "Splice already in progress")
		case .channelNotIdle:
			return String(localized: "Channel not idle")
		case .sessionError:
			return String(localized: "Splice-out session error")
		case .disconnected:
			return String(localized: "Disconnected during splice-out attempt")
		case .other:
			return String(localized: "Unknown splice-out error")
		}
	}
	
	static func fromResponse(
		_ response: Lightning_kmpChannelCommand.CommitmentSpliceResponse?
	) -> SpliceOutProblem? {
		
		guard let response else {
			return .other
		}
		
		guard let failure = response.asFailure() else {
			return nil // not a failure
		}
		
		if let _ = failure.asInsufficientFunds() {
			return .insufficientFunds
		}
		if let _ = failure.asSpliceAlreadyInProgress() {
			return .spliceAlreadyInProgress
		}
		if let _ = failure.asChannelNotIdle() {
			return .channelNotIdle
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
		if let _ = failure.asDisconnected() {
			return .disconnected
		}
		
		return .other
	}
}
