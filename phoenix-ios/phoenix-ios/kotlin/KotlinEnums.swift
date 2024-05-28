import PhoenixShared

extension Lightning_kmpFinalFailure {
	
	func localizedDescription() -> String {
		if let _ = self.asAlreadyPaid() {
			return String(localized:
				"This invoice has already been paid.")
		}
		if let _ = self.asChannelClosing() {
			return String(localized:
				"Channels are closing.")
		}
		if let _ = self.asChannelNotConnected() {
			return String(localized:
				"Your channel are not connected yet. Wait for a stable connection and try again.")
		}
		if let _ = self.asChannelOpening() {
			return String(localized:
				"Your channel is still in the process of being opened. Wait and try again.")
		}
		if let _ = self.asFeaturesNotSupported() {
			return String(localized:
				"This invoice uses unsupported features. Make sure you're on the latest Phoenix version.")
		}
		if let _ = self.asInsufficientBalance() {
			return String(localized:
				"This payment exceeds your balance.")
		}
		if let _ = self.asInvalidPaymentAmount() {
			return String(localized:
				"The payment amount is invalid.")
		}
		if let _ = self.asInvalidPaymentId() {
			return String(localized:
				"The ID of the payment is not valid. Try again.")
		}
		if let _ = self.asNoAvailableChannels() {
			return String(localized:
				"The payment could not be sent through your existing channels.")
		}
		if let _ = self.asRecipientUnreachable() {
			return String(localized:
				"Recipient is not reachable, or does not have enough inbound liquidity.")
		}
		if let _ = self.asRetryExhausted() {
			return String(localized:
				"Recipient is not reachable, or does not have enough inbound liquidity.")
		}
		if let _ = self.asWalletRestarted() {
			return String(localized:
				"The wallet was restarted while the payment was processing.")
		}
		
		return String(localized:
			"An unknown error occurred and payment has failed.")
	}
}

extension Lightning_kmpLightningOutgoingPayment.PartStatusFailure {
	
	func localizedDescription() -> String {
		if let _ =  self.asChannelIsClosing() {
			return String(localized:
				"Channels are closing.")
		}
		if let _ = self.asChannelIsSplicing() {
			return String(localized:
				"Channels are already processing a splice. Try again later.")
		}
		if let _ = self.asNotEnoughFees() {
			return String(localized:
				"Fee is insufficient.")
		}
		if let _ = self.asNotEnoughFunds() {
			return String(localized:
				"This payment exceeds your balance.")
		}
		if let _ = self.asPaymentAmountTooBig() {
			return String(localized:
				"The payment amount is too big - try splitting it in several parts.")
		}
		if let _ = self.asPaymentAmountTooSmall() {
			return String(localized:
				"The payment amount is too small.")
		}
		if let _ = self.asPaymentExpiryTooBig() {
			return String(localized:
				"The expiry of this payment is too far in the future.")
		}
		if let _ = self.asRecipientIsOffline() {
			return String(localized:
				"The recipient is offline.")
		}
		if let _ = self.asRecipientLiquidityIssue() {
			return String(localized:
				"The payment could not be relayed to the recipient (probably insufficient inbound liquidity).")
		}
		if let _ = self.asRecipientRejectedPayment() {
			return String(localized:
				"The payment was rejected by the recipient. This particular invoice may have already been paid.")
		}
		if let _ = self.asTemporaryRemoteFailure() {
			return String(localized:
				"An error occurred on a node in the payment route. The payment may succeed if you try again.")
		}
		if let _ = self.asTooManyPendingPayments() {
			return String(localized:
				"You have too many pending payments. Try again once they are settled.")
		}
		if let partFailure = self.asUninterpretable() {
			return partFailure.message
		}
		
		return String(localized:
			"An unknown error occurred and payment has failed.")
	}
}
