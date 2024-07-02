import Foundation
import PhoenixShared
import Combine


extension PeerManager {
	
	func peerStateValue() -> Lightning_kmpPeer? {
		return peerState.value as? Lightning_kmpPeer
	}
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value as? [Bitcoin_kmpByteVector32: LocalChannelInfo] {
			return value
		} else {
			return [:]
		}
	}
	
	func channelsValue() -> [LocalChannelInfo] {
		return channelsFlowValue().map { $1 }
	}
	
	func finalWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.finalWallet.value as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension BalanceManager {
	
	func swapInWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.swapInWallet.value as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension WalletManager {
	
	func keyManagerValue() -> Lightning_kmpLocalKeyManager? {
		if let value = keyManager.value as? Lightning_kmpLocalKeyManager {
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
		return connections.value as! Connections
	}
	
	func asyncStream() -> AsyncStream<Connections> {
		
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
				case .register  : return NSLocalizedString("Register",     comment: "lnurl-auth: login button title")
				case .login     : return NSLocalizedString("Login",        comment: "lnurl-auth: login button title")
				case .link      : return NSLocalizedString("Link",         comment: "lnurl-auth: login button title")
				case .auth      : return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
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
				case .register  : return NSLocalizedString("Registered",    comment: "lnurl-auth: success text")
				case .login     : return NSLocalizedString("Logged In",     comment: "lnurl-auth: success text")
				case .link      : return NSLocalizedString("Linked",        comment: "lnurl-auth: success text")
				case .auth      : return NSLocalizedString("Authenticated", comment: "lnurl-auth: success text")
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

extension MnemonicLanguage {
	
	var flag: String { switch self {
		case .english : return "🇬🇧"
		case .spanish : return "🇪🇸"
		case .french  : return "🇫🇷"
		case .czech   : return "🇨🇿"
	}}
	
	var displayName: String {
		
		if let result = Locale.current.localizedString(forLanguageCode: self.code) {
			return result
		}
		
		switch self {
			case .english : return "English"
			case .spanish : return "Spanish"
			case .french  : return "French"
			case .czech   : return "Czech"
		}
	}

	static func fromLanguageCode(_ code: String) -> MnemonicLanguage? {
		
		return MnemonicLanguage.allCases
			.first(where: { $0.code.caseInsensitiveCompare(code) == .orderedSame })
	}

	static var defaultCase: MnemonicLanguage {
		
		let available = self.allCases
		
		// Locale.preferredLanguages returns an ordered list,
		// according to the user's configured preferences within the OS.
		//
		// For example:
		// - [0] Arabic
		// - [1] Spanish
		// - [2] Portuguese
		//
		// Thus, absent a MnemonicLanguage for Arabic, we would choose Spanish.
		
		for identifier in Locale.preferredLanguages {
			
			let locale = Locale(identifier: identifier)
			if let code = locale.languageCode {
				
				for lang in available {
					if lang.code.caseInsensitiveCompare(code) == .orderedSame {
						return lang
					}
				}
			}
		}
		
		return .english
	}
}

extension Array where Element == LocalChannelInfo {
	
	func availableForReceive() -> Lightning_kmpMilliSatoshi? {
		return LocalChannelInfo.companion.availableForReceive(channels: self)
	}
	
	func canRequestLiquidity() -> Bool {
		return LocalChannelInfo.companion.canRequestLiquidity(channels: self)
	}
	
	func inFlightPaymentsCount() -> Int32 {
		return LocalChannelInfo.companion.inFlightPaymentsCount(channels: self)
	}
}
