import Foundation

/// Names of entries stored within the iOS keychain
/// 
enum SecKey: CaseIterable {
	case lockingKey_keychain
	case lockingKey_biometrics
	case softBiometrics
	case passcodeFallback
	case lockPin
	case invalidLockPin
	case spendingPin
	case invalidSpendingPin
	case bip353Address
	
	var prefix: String { switch self {
		case .lockingKey_keychain   : return "securityFile_keychain"
		case .lockingKey_biometrics : return "securityFile_biometrics"
		case .softBiometrics        : return "biometrics"
		case .passcodeFallback      : return "passcodeFallback"
		case .lockPin               : return "customPin"
		case .invalidLockPin        : return "invalidPin"
		case .spendingPin           : return "spendingPin"
		case .invalidSpendingPin    : return "invalidSpendingPin"
		case .bip353Address         : return "bip353Address"
	}}
	
	/// From before we had a per-wallet design
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}
