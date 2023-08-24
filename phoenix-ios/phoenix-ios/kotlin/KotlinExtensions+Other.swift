import Foundation
import PhoenixShared
import Combine
import CryptoKit

extension PhoenixBusiness {
	
	func getPeer() -> Lightning_kmpPeer? {
		self.peerManager.peerState.value_ as? Lightning_kmpPeer
	}
}

extension PeerManager {
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value_ as? [Bitcoin_kmpByteVector32: LocalChannelInfo] {
			return value
		} else {
			return [:]
		}
	}
	
	func finalWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.finalWallet.value_ as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension BalanceManager {
	
	func swapInWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.swapInWallet.value_ as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension WalletManager {
	
	func getKeyManager() -> Lightning_kmpLocalKeyManager? {
		if let value = keyManager.value_ as? Lightning_kmpLocalKeyManager {
			return value
		} else {
			return nil
		}
	}
}

extension WalletBalance {
	
	var confirmed: Bitcoin_kmpSatoshi {
		return weaklyConfirmed.plus(other: deeplyConfirmed)
	}
}

extension Lightning_kmpConnection {
	
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
		return Lightning_kmpWalletState.WalletWithConfirmations(minConfirmations: 1, currentBlockHeight: 1, all: [])
	}
}

extension Bitcoin_kmpByteVector32 {
	
	static func random() -> Bitcoin_kmpByteVector32 {
		
		let key = SymmetricKey(size: .bits256) // 256 / 8 = 32
		
		let data = key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		
		return Bitcoin_kmpByteVector32(bytes: data.toKotlinByteArray())
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value_ as! Connections
	}
	
	var publisher: CurrentValueSubject<Connections, Never> {

		let publisher = CurrentValueSubject<Connections, Never>(currentValue)

		let swiftFlow = SwiftFlow<Connections>(origin: connections)
		swiftFlow.watch {[weak publisher](connections: Connections?) in
			publisher?.send(connections!)
		}

		return publisher
	}
	
	var asyncStream: AsyncStream<Connections> {
		
		return AsyncStream<Connections>(bufferingPolicy: .bufferingNewest(1)) { continuation in
			
			let swiftFlow = SwiftFlow<Connections>(origin: self.connections)

			let watcher = swiftFlow.watch {(connections: Connections?) in
				if let connections {
					continuation.yield(connections)
				}
			}
			
			continuation.onTermination = { _ in
				DispatchQueue.main.async {
					// I'm not sure what thread this will be called from.
					// And I've witnessed crashes when invoking `watcher.close()` from  a non-main thread.
					watcher.close()
				}
			}
		}
	}
}

extension Connections {
	
	func oneOrMoreEstablishing() -> Bool {
		
		if self.internet is Lightning_kmpConnection.ESTABLISHING {
			return true
		}
		if self.peer is Lightning_kmpConnection.ESTABLISHING {
			return true
		}
		if self.electrum is Lightning_kmpConnection.ESTABLISHING {
			return true
		}
		return false
	}
}

extension LnurlAuth {
	
	static var defaultActionPromptTitle: String {
		return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
	}
	
	var actionPromptTitle: String {
		if let action = self.action {
			switch action {
				case .register_ : return NSLocalizedString("Register",     comment: "lnurl-auth: login button title")
				case .login     : return NSLocalizedString("Login",        comment: "lnurl-auth: login button title")
				case .link      : return NSLocalizedString("Link",         comment: "lnurl-auth: login button title")
				case .auth      : return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
				default         : break
			}
		}
		return LnurlAuth.defaultActionPromptTitle
	}
	
	static var defaultActionSuccessTitle: String {
		return NSLocalizedString("Authenticated", comment: "lnurl-auth: success text")
	}
	
	var actionSuccessTitle: String {
		if let action = self.action {
			switch action {
				case .register_ : return NSLocalizedString("Registered",    comment: "lnurl-auth: success text")
				case .login     : return NSLocalizedString("Logged In",     comment: "lnurl-auth: success text")
				case .link      : return NSLocalizedString("Linked",        comment: "lnurl-auth: success text")
				case .auth      : return NSLocalizedString("Authenticated", comment: "lnurl-auth: success text")
				default         : break
			}
		}
		return LnurlAuth.defaultActionSuccessTitle
	}
}

