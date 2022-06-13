import SwiftUI
import PhoenixShared


enum CurrencyType: String, CaseIterable, Codable {
	case fiat
	case bitcoin
}

enum Theme: String, CaseIterable, Codable {
	case light
	case dark
	case system
	
	func localized() -> String {
		switch self {
		case .light  : return NSLocalizedString("Light", comment: "App theme option")
		case .dark   : return NSLocalizedString("Dark", comment: "App theme option")
		case .system : return NSLocalizedString("System", comment: "App theme option")
		}
	}
	
	func toInterfaceStyle() -> UIUserInterfaceStyle {
		switch self {
		case .light  : return .light
		case .dark   : return .dark
		case .system : return .unspecified
		}
	}
	
	func toColorScheme() -> ColorScheme? {
		switch self {
		case .light  : return ColorScheme.light
		case .dark   : return ColorScheme.dark
		case .system : return nil
		}
	}
}

enum PushPermissionQuery: String, Codable {
	case neverAskedUser
	case userDeclined
	case userAccepted
}

struct FcmTokenInfo: Equatable, Codable {
	let nodeID: String
	let fcmToken: String
}

struct ElectrumConfigPrefs: Codable {
	let host: String
	let port: UInt16
	let pinnedPubKey: String?
	
	private let version: Int // for potential future upgrades
	
	init(host: String, port: UInt16, pinnedPubKey: String?) {
		self.host = host
		self.port = port
		self.pinnedPubKey = pinnedPubKey
		self.version = 2
	} 
	
	var serverAddress: Lightning_kmpServerAddress {
		if let pinnedPubKey = pinnedPubKey {
			return Lightning_kmpServerAddress(
				host : host,
				port : Int32(port),
				tls  : Lightning_kmpTcpSocketTLS.PINNED_PUBLIC_KEY(pubKey: pinnedPubKey)
			)
		} else {
			return Lightning_kmpServerAddress(
				host : host,
				port : Int32(port),
				tls  : Lightning_kmpTcpSocketTLS.TRUSTED_CERTIFICATES()
			)
		}
	}

}

struct MaxFees: Codable {
	let feeBaseSat: Int64
	let feeProportionalMillionths: Int64
	
	static func fromTrampolineFees(_ fees: Lightning_kmpTrampolineFees) -> MaxFees {
		return MaxFees(
			feeBaseSat: fees.feeBase.sat,
			feeProportionalMillionths: fees.feeProportional
		)
	}
	
	func toKotlin() -> PhoenixShared.MaxFees {
		return PhoenixShared.MaxFees(
			feeBase: Bitcoin_kmpSatoshi(sat: self.feeBaseSat),
			feeProportionalMillionths: self.feeProportionalMillionths
		)
	}
}
