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
		let balance = unconfirmed.map { $0.amount }.reduce(Int64(0)) { $0 + $1.toLong() }
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var weaklyConfirmedBalance: Bitcoin_kmpSatoshi {
		let balance = weaklyConfirmed.map { $0.amount }.reduce(Int64(0)) { $0 + $1.toLong() }
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var deeplyConfirmedBalance: Bitcoin_kmpSatoshi {
		let balance = deeplyConfirmed.map { $0.amount }.reduce(Int64(0)) { $0 + $1.toLong() }
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var anyConfirmedBalance: Bitcoin_kmpSatoshi {
		let anyConfirmedTx = weaklyConfirmed + deeplyConfirmed
		let balance = anyConfirmedTx.map { $0.amount }.reduce(Int64(0)) { $0 + $1.toLong() }
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	var totalBalance: Bitcoin_kmpSatoshi {
		let allTx = unconfirmed + weaklyConfirmed + deeplyConfirmed
		let balance = allTx.map { $0.amount }.reduce(Int64(0)) { $0 + $1.toLong() }
		return Bitcoin_kmpSatoshi(sat: balance)
	}
	
	static func empty() -> Lightning_kmpWalletState.WalletWithConfirmations {
		return Lightning_kmpWalletState.WalletWithConfirmations(
			swapInParams: LightningExposureKt.defaultSwapInParams(),
			currentBlockHeight: 1,
			all: []
		)
	}
}
