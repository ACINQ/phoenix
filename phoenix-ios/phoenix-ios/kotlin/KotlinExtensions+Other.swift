import Foundation
import PhoenixShared
import Combine


extension PeerManager {
	
	func peerStateValue() -> Lightning_kmpPeer? {
		return peerState.value_ as? Lightning_kmpPeer
	}
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value_ as? [Bitcoin_kmpByteVector32: LocalChannelInfo] {
			return value
		} else {
			return [:]
		}
	}
	
	func channelsValue() -> [LocalChannelInfo] {
		return channelsFlowValue().map { $1 }
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
	
	func keyManagerValue() -> Lightning_kmpLocalKeyManager? {
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

extension PhoenixShared.Notification {
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value_ as! Connections
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
		
		if self.internet.isEstablishing() {
			return true
		}
		if self.peer.isEstablishing() {
			return true
		}
		if self.electrum.isEstablishing() {
			return true
		}
		if self.torEnabled && self.tor.isEstablishing() {
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

extension PlatformContext {
	
	static var `default`: PlatformContext {
		return PlatformContext(logger: KotlinLogger.shared.logger)
	}
}

