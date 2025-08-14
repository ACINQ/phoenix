import Foundation

/// Names of keys stored in the iOS UserDefaults system.
///
enum PrefsKey: CaseIterable {
//	Wallet:
	case defaultPaymentDescription
	case recentTipPercents
	case isNewWallet
	case invoiceExpirationDays
	case hideAmounts
	case showOriginalFiatAmount
	case recentPaymentsConfig
	case swapInAddressIndex
	case hasUpgradedSeedCloudBackups
	case serverMessageReadIndex
	case allowOverpayment
	case doNotShowChannelImpactWarning
	case watchTower_lastAttemptDate
	case watchTower_lastAttemptFailed
//	BackupSeed:
	case backupSeed_enabled
	case backupSeed_hasUploadedSeed
	case backupSeed_name
	case manualBackupDone
//	BackupTransactions:
	case backupTxs_enabled
	case backupTxs_useCellularData
	case backupTxs_useUploadDelay
	case recordZoneCreated
	case hasDownloadedPayments
	case hasDownloadedContacts
	case hasReUploadedPayments
//	Global:
	case theme
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String { switch self {
	// Wallet:
		case .defaultPaymentDescription     : return "defaultPaymentDescription"
		case .recentTipPercents             : return "recentTipPercents"
		case .isNewWallet                   : return "isNewWallet"
		case .invoiceExpirationDays         : return "invoiceExpirationDays"
		case .hideAmounts                   : return "hideAmountsOnHomeScreen"
		case .showOriginalFiatAmount        : return "showOriginalFiatAmount"
		case .recentPaymentsConfig          : return "recentPaymentsConfig"
		case .swapInAddressIndex            : return "swapInAddressIndex"
		case .hasUpgradedSeedCloudBackups   : return "hasUpgradedSeedCloudBackups_v2"
		case .serverMessageReadIndex        : return "serverMessageReadIndex"
		case .allowOverpayment              : return "allowOverpayment"
		case .doNotShowChannelImpactWarning : return "doNotShowChannelImpactWarning"
		case .watchTower_lastAttemptDate    : return "watchTower_lastAttemptDate"
		case .watchTower_lastAttemptFailed  : return "watchTower_lastAttemptFailed"
	// BackupSeed:
		case .backupSeed_enabled            : return "backupSeed_enabled"
		case .backupSeed_hasUploadedSeed    : return "backupSeed_hasUploadedSeed"
		case .backupSeed_name               : return "backupSeed_name"
		case .manualBackupDone              : return "manualBackup_taskDone"
	//	BackupTransactions:
		case .backupTxs_enabled             : return "backupTransactions_enabled"
		case .backupTxs_useCellularData     : return "backupTransactions_useCellularData"
		case .backupTxs_useUploadDelay      : return "backupTransactions_useUploadDelay"
		case .recordZoneCreated             : return "hasCKRecordZone_v2"
		case .hasDownloadedPayments         : return "hasDownloadedCKRecords"
		case .hasDownloadedContacts         : return "hasDownloadedContacts_v2"
		case .hasReUploadedPayments         : return "hasReUploadedPayments"
	//	Global:
		case .theme                         : return "theme"
	}}
	
	enum Group {
		case wallet
		case backupSeed
		case backupTransactions
		case global
	}
	
	var group: Group { switch self {
	//	Wallet:
		case .defaultPaymentDescription     : return .wallet
		case .recentTipPercents             : return .wallet
		case .isNewWallet                   : return .wallet
		case .invoiceExpirationDays         : return .wallet
		case .hideAmounts                   : return .wallet
		case .showOriginalFiatAmount        : return .wallet
		case .recentPaymentsConfig          : return .wallet
		case .swapInAddressIndex            : return .wallet
		case .hasUpgradedSeedCloudBackups   : return .wallet
		case .serverMessageReadIndex        : return .wallet
		case .allowOverpayment              : return .wallet
		case .doNotShowChannelImpactWarning : return .wallet
		case .watchTower_lastAttemptDate    : return .wallet
		case .watchTower_lastAttemptFailed  : return .wallet
	// BackupSeed:
		case .backupSeed_enabled            : return .backupSeed
		case .backupSeed_hasUploadedSeed    : return .backupSeed
		case .backupSeed_name               : return .backupSeed
		case .manualBackupDone              : return .backupSeed
	//	BackupTransactions:
		case .backupTxs_enabled             : return .backupTransactions
		case .backupTxs_useCellularData     : return .backupTransactions
		case .backupTxs_useUploadDelay      : return .backupTransactions
		case .recordZoneCreated             : return .backupTransactions
		case .hasDownloadedPayments         : return .backupTransactions
		case .hasDownloadedContacts         : return .backupTransactions
		case .hasReUploadedPayments         : return .backupTransactions
		//	Global:
		case .theme                         : return .global
	}}
	
	/// From before we had a per-wallet design
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

enum PrefsKeyDeprecated: String {
	case showChannelsRemoteBalance
	case recentPaymentSeconds
	case maxFees
	case hasUpgradedSeedCloudBackups_v1 = "hasUpgradedSeedCloudBackups"
	case hasDownloadedContacts_v1 = "hasDownloadedContacts"
}
