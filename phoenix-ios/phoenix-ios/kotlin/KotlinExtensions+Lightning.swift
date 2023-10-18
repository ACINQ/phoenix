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
	
	func readyForSwapWallet() -> Lightning_kmpWalletState.WalletWithConfirmations {
		
		let timedOut = Set(self.lockedUntilRefund + self.readyForRefund)
		let readyForSwap = self.deeplyConfirmed.filter {
			!timedOut.contains($0)
		}
		
		return Lightning_kmpWalletState.WalletWithConfirmations(
			swapInParams: self.swapInParams,
			currentBlockHeight: self.currentBlockHeight,
			all: readyForSwap
		)
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
