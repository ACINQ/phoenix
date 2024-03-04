import Foundation
import PhoenixShared
import Combine


extension PeerManager {
	
	func peerStateValue() -> Lightning_kmpPeer? {
		return self.peerState.value
	}
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value {
			return value
		} else {
			return [:]
		}
	}
	
	func channelsValue() -> [LocalChannelInfo] {
		return channelsFlowValue().map { $1 }
	}
	
	func finalWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.finalWallet.value {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension BalanceManager {
	
	func swapInWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.swapInWallet.value {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension WalletManager {
	
	func keyManagerValue() -> Lightning_kmpLocalKeyManager? {
		if let value = keyManager.value {
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
		return connections.value
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
	
	func targetsEstablished(_ target: AppConnectionsDaemon.ControlTarget) -> Bool {
		
		if !self.internet.isEstablished() {
			return false
		}
		if target.containsPeer {
			if !self.peer.isEstablished() {
				return false
			}
		}
		if target.containsElectrum {
			if !self.electrum.isEstablished() {
				return false
			}
		}
		if target.containsTor && self.torEnabled {
			if !self.tor.isEstablished() {
				return false
			}
		}
		
		return true
	}
}

extension LnurlAuth {
	
	static var defaultActionPromptTitle: String {
		return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
	}
	
	var actionPromptTitle: String {
		if let action = self.action {
			switch action {
				case .register : return NSLocalizedString("Register",     comment: "lnurl-auth: login button title")
				case .login    : return NSLocalizedString("Login",        comment: "lnurl-auth: login button title")
				case .link     : return NSLocalizedString("Link",         comment: "lnurl-auth: login button title")
				case .auth     : return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
				default        : break
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
				case .register : return NSLocalizedString("Registered",    comment: "lnurl-auth: success text")
				case .login    : return NSLocalizedString("Logged In",     comment: "lnurl-auth: success text")
				case .link     : return NSLocalizedString("Linked",        comment: "lnurl-auth: success text")
				case .auth     : return NSLocalizedString("Authenticated", comment: "lnurl-auth: success text")
				default        : break
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

extension AppConnectionsDaemon.ControlTargetCompanion {
	
	var ElectrumPlusTor: AppConnectionsDaemon.ControlTarget {
		return AppConnectionsDaemon.ControlTarget.companion.Electrum.plus(other: AppConnectionsDaemon.ControlTarget.companion.Tor
		)
	}
	
	var AllMinusElectrum: AppConnectionsDaemon.ControlTarget {
		var flags = AppConnectionsDaemon.ControlTarget.companion.All.flags
		flags ^= AppConnectionsDaemon.ControlTarget.companion.Electrum.flags
		
		return AppConnectionsDaemon.ControlTarget(flags: flags)
	}
}

