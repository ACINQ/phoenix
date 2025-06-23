import Foundation
import Combine
import CommonCrypto
import CryptoKit
import LocalAuthentication
import SwiftUI

fileprivate let filename = "AppSecurity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class AppSecurity {
	
	private static var instances: [String: AppSecurity_Wallet] = [:]
	
	static var current: AppSecurity_Wallet {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet(PREFS_DEFAULT_ID)
		}
	}
	
	static func wallet(_ walletId: WalletIdentifier) -> AppSecurity_Wallet {
		return wallet(walletId.prefsKeySuffix)
	}
	
	static func wallet(_ id: String) -> AppSecurity_Wallet {
		if let instance = instances[id] {
			return instance
		} else {
			let instance = AppSecurity_Wallet(id: id)
			instances[id] = instance
			return instance
		}
	}
}
