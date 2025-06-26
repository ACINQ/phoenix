import Foundation

enum AccessGroup {
	
	/// Represents the keychain domain for this app.
	/// I.E. can NOT be accessed by our app extensions.
	case appOnly
	
	/// Represents the keychain domain for our app group.
	/// I.E. can be accessed by our app extensions (e.g. notification-service-extension).
	case appAndExtensions
	
	var value: String { switch self {
		case .appOnly          : "XD77LN4376.co.acinq.phoenix"
		case .appAndExtensions : "group.co.acinq.phoenix"
	}}
	
	var debugName: String { switch self {
		case .appOnly          : "appOnly"
		case .appAndExtensions : "appAndExtensions"
  }}
}

/// Names of entries stored within the iOS keychain
/// 
enum AppSecurityKey: CaseIterable {
	case lockingKey_keychain
	case softBiometrics
	case passcodeFallback
	case lockPin
	case invalidLockPin
	case spendingPin
	case invalidSpendingPin
	case bip353Address
	
	var prefix: String { switch self {
		case .lockingKey_keychain   : return "securityFile_keychain"
		case .softBiometrics        : return "biometrics"
		case .passcodeFallback      : return "passcodeFallback"
		case .lockPin               : return "customPin"
		case .invalidLockPin        : return "invalidPin"
		case .spendingPin           : return "spendingPin"
		case .invalidSpendingPin    : return "invalidSpendingPin"
		case .bip353Address         : return "bip353Address"
	}}
	
	var debugName: String { switch self {
		case .lockingKey_keychain   : return "lockingKey_keychain"
		case .softBiometrics        : return "softBiometrics"
		case .passcodeFallback      : return "passcodeFallback"
		case .lockPin               : return "lockPin"
		case .invalidLockPin        : return "invalidLockPin"
		case .spendingPin           : return "spendingPin"
		case .invalidSpendingPin    : return "invalidSpendingPin"
		case .bip353Address         : return "bip353Address"
	}}
	
	/// From before we had a per-wallet design
	var deprecatedValue: String { prefix }
	
	var accessGroup: AccessGroup { switch self {
		case .lockingKey_keychain   : return .appAndExtensions
		case .softBiometrics        : return .appOnly
		case .passcodeFallback      : return .appOnly
		case .lockPin               : return .appOnly
		case .invalidLockPin        : return .appOnly
		case .spendingPin           : return .appOnly
		case .invalidSpendingPin    : return .appOnly
		case .bip353Address         : return .appOnly
	}}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}

enum AppSecurityKeyDeprecated: String {
	case lockingKey_biometrics = "securityFile_biometrics"
}
