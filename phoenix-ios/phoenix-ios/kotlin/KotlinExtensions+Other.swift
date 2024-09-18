import Foundation
import PhoenixShared
import Combine

extension WalletBalance {
	
	var confirmed: Bitcoin_kmpSatoshi {
		return weaklyConfirmed.plus(other: deeplyConfirmed)
	}
}

extension WalletManager.WalletInfo {
	
	/// All data from a user's wallet are stored in the user's privateCloudDatabase.
	/// And within the privateCloudDatabase, we create a dedicated CKRecordZone for each wallet,
	/// where recordZone.name == encryptedNodeId.
	/// 
	var encryptedNodeId: String {
		
		// For historical reasons, this is the cloudKeyHash, and NOT the nodeIdHash.
		// The cloudKeyHash is created via: Hash160(cloudKey)
		return self.cloudKeyHash
	}
}

extension PhoenixShared.Notification {
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
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
		return String(localized: "Authenticate", comment: "lnurl-auth: login button title")
	}
	
	var actionPromptTitle: String {
		guard let action else {
			return LnurlAuth.defaultActionPromptTitle
		}
		
		switch action {
		case .register : return String(localized: "Register",     comment: "lnurl-auth: login button title")
		case .login    : return String(localized: "Login",        comment: "lnurl-auth: login button title")
		case .link     : return String(localized: "Link",         comment: "lnurl-auth: login button title")
		case .auth     : return String(localized: "Authenticate", comment: "lnurl-auth: login button title")
		}
	}
	
	static var defaultActionSuccessTitle: String {
		return String(localized: "Authenticated", comment: "lnurl-auth: success text")
	}
	
	var actionSuccessTitle: String {
		guard let action else {
			return LnurlAuth.defaultActionSuccessTitle
		}
		
		switch action {
		case .register  : return String(localized: "Registered",    comment: "lnurl-auth: success text")
		case .login     : return String(localized: "Logged In",     comment: "lnurl-auth: success text")
		case .link      : return String(localized: "Linked",        comment: "lnurl-auth: success text")
		case .auth      : return String(localized: "Authenticated", comment: "lnurl-auth: success text")
		}
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
            if let code = locale.language.languageCode?.identifier {
				
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
