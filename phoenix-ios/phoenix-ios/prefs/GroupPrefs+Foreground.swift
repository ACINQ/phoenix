import Foundation

/// This file is **ONLY** for the main Phoenix app
///
extension GroupPrefs {
	
	static var current: GroupPrefs_Wallet {
		if let walletId = Biz.walletId {
			return wallet(walletId)
		} else {
			return wallet(PREFS_DEFAULT_ID)
		}
	}
}
