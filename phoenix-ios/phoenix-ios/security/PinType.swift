import Swift

enum PinType: CustomStringConvertible {
	case lockPin
	case spendingPin
	
	var description: String {
		switch self {
			case .lockPin     : return "lockPin"
			case .spendingPin : return "spendingPin"
		}
	}
	
	var keyPin: KeychainKey {
		switch self {
			case .lockPin     : return KeychainKey.lockPin
			case .spendingPin : return KeychainKey.spendingPin
		}
	}
	
	var keyInvalidPin: KeychainKey {
		switch self {
			case .lockPin     : return KeychainKey.invalidLockPin
			case .spendingPin : return KeychainKey.invalidSpendingPin
		}
	}
}
