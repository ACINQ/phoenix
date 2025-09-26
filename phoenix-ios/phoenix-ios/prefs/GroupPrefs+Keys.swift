import Foundation

/// Names of keys stored in the iOS UserDefaults system.
///
enum GroupPrefsKey: CaseIterable {
//	Wallet:
	case currencyType
	case fiatCurrency
	case bitcoinUnit
	case currencyConverterList
	case electrumConfig
	case isTorEnabled
	case discreetNotifications
	case liquidityPolicy
	case srvExtConnection
//	Global:
	case badgeCount
	
	/// We used to declare, `enum Key: String`, but discovered that it's a bit of a footgun.
	/// It's just too easy to type `Key.name.rawValue`, as we've done so many times before.
	/// So we switched to a variable name that puts the value in the proper context.
	///
	var prefix: String { switch self {
	//	Wallet:
		case .currencyType          : return "currencyType"
		case .fiatCurrency          : return "fiatCurrency"
		case .bitcoinUnit           : return "bitcoinUnit"
		case .currencyConverterList : return "currencyConverterList"
		case .electrumConfig        : return "electrumConfig"
		case .isTorEnabled          : return "isTorEnabled"
		case .discreetNotifications : return "discreetNotifications"
		case .liquidityPolicy       : return "liquidityPolicy"
		case .srvExtConnection      : return "srvExtConnection"
	//	Global:
		case .badgeCount            : return "badgeCount"
	}}
	
	enum Group {
		case wallet
		case global
	}
	
	var group: Group { switch self {
	//	Wallet:
		case .currencyType          : return .wallet
		case .fiatCurrency          : return .wallet
		case .bitcoinUnit           : return .wallet
		case .currencyConverterList : return .wallet
		case .electrumConfig        : return .wallet
		case .isTorEnabled          : return .wallet
		case .discreetNotifications : return .wallet
		case .liquidityPolicy       : return .wallet
		case .srvExtConnection      : return .wallet
	//	Global:
		case .badgeCount            : return .global
	}}
	
	/// From before we had a per-wallet design
	var deprecatedValue: String {
		return prefix
	}
	
	func value(_ suffix: String) -> String {
		return "\(self.prefix)-\(suffix)"
	}
}
