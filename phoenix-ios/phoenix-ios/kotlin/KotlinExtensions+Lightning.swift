import Foundation
import PhoenixShared

extension Lightning_kmpConnection {
	
	func isClosed() -> Bool {
		return self is Lightning_kmpConnection.CLOSED
	}
	func isEstablishing() -> Bool {
		return self is Lightning_kmpConnection.ESTABLISHING
	}
	func isEstablished() -> Bool {
		return self is Lightning_kmpConnection.ESTABLISHED
	}
	
	func localizedText() -> String {
		switch self {
		case is CLOSED       : return NSLocalizedString("Offline", comment: "Connection state")
		case is ESTABLISHING : return NSLocalizedString("Connecting…", comment: "Connection state")
		case is ESTABLISHED  : return NSLocalizedString("Connected", comment: "Connection state")
		default              : return NSLocalizedString("Unknown", comment: "Connection state")
		}
	}
}

extension Lightning_kmpPaymentRequest {
	
	var createdAtDate: Date? {
		
		if let bolt11 = self as? Lightning_kmpBolt11Invoice {
			return bolt11.timestampDate
		} else { // todo: Bolt12
			return nil
		}
	}
	
	var invoiceDescription_: String? {
		if let bolt11 = self as? Lightning_kmpBolt11Invoice {
			return bolt11.description_
		} else { // todo: Bolt12
			return nil
		}
	}
}

extension Lightning_kmpWalletState.WalletWithConfirmations {
	
	var unconfirmedBalance: Bitcoin_kmpSatoshi {
		let balance = unconfirmed.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var weaklyConfirmedBalance: Bitcoin_kmpSatoshi {
		let balance = weaklyConfirmed.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var deeplyConfirmedBalance: Bitcoin_kmpSatoshi {
		let balance = deeplyConfirmed.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var lockedUntilRefundBalance: Bitcoin_kmpSatoshi {
		let balance = 	lockedUntilRefund.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var readyForRefundBalance: Bitcoin_kmpSatoshi {
		let balance = readyForRefund.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var anyConfirmedBalance: Bitcoin_kmpSatoshi {
		let anyConfirmedTx = weaklyConfirmed + deeplyConfirmed
		let balance = anyConfirmedTx.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var totalBalance: Bitcoin_kmpSatoshi {
		let allTx = unconfirmed + weaklyConfirmed + deeplyConfirmed
		let balance = allTx.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	/// The `deeplyConfirmed` property contains UTXO's that are also represented in
	/// `lockedUntilRefund` & `readyForRefund`. This property is a subset of
	/// `deeplyConfirmed` that excludes those 2 categories.
	///
	var readyForSwap: [Lightning_kmpWalletState.Utxo] {
		let timedOut = Set(self.lockedUntilRefund + self.readyForRefund)
		return deeplyConfirmed.filter {
			!timedOut.contains($0)
		}
	}
	
	var readyForSwapBalance: Bitcoin_kmpSatoshi {
		let balance = readyForSwap.map { $0.amount.toLong() }.sum()
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	/// Returns non-nil if any "ready for swap" UTXO's have an expiration date that
	/// is less than 30 days away.
	func expirationWarningInDays() -> Int? {
		
		let maxConfirmations = swapInParams.maxConfirmations
		let remainingConfirmationsList = readyForSwap.map {
			maxConfirmations - confirmations(utxo: $0)
		}
		
		if let minRemainingConfirmations = remainingConfirmationsList.min() {
			let days: Double = Double(minRemainingConfirmations) / 144.0
			let result = Int(days.rounded(.awayFromZero))
			if result < 30 {
				return result
			}
		}
		
		return nil
	}
	
	#if DEBUG
	func fakeBlockHeight(plus diff: Int32) -> Lightning_kmpWalletState.WalletWithConfirmations {
		
		return Lightning_kmpWalletState.WalletWithConfirmations(
			swapInParams: self.swapInParams,
			currentBlockHeight: self.currentBlockHeight + diff,
			all: self.all
		)
	}
	#endif
	
	static func empty() -> Lightning_kmpWalletState.WalletWithConfirmations {
		return Lightning_kmpWalletState.WalletWithConfirmations(
			swapInParams: LightningExposureKt.defaultSwapInParams(),
			currentBlockHeight: 1,
			all: []
		)
	}
}
