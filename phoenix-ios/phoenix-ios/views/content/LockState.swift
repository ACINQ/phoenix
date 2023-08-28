import Foundation

enum WalletExistence: CustomStringConvertible {
	
	/// We don't know if a wallet exists or not.
	/// We're still waiting for initialization to complete.
	case unknown
	
	case exists
	case doesNotExist
	
	var description: String {
		switch self {
			case .unknown      : return "unknown"
			case .exists       : return "exists"
			case .doesNotExist : return "doesNotExist"
		}
	}
}

class LockState: ObservableObject {
	
	/// Singleton instance
	public static let shared = LockState()
	
	/// Indicates whether or not any required migration steps are still in flight.
	///
	@Published var migrationStepsCompleted: Bool
	
	/// Indicates whether the iOS keychain is available and ready.
	/// Failure to wait for this flag may result in the keychain returning item-not-found errors.
	///
	@Published var protectedDataAvailable: Bool
	
	/// Indicates whether or not a wallet exists.
	/// Note that this is unknown until after we've checked the keychain.
	///
	@Published var walletExistence: WalletExistence
	
	/// Indicates whether the app is locked or unlocked.
	/// This will be set to true if app-lock is disabled, or if the user has authenticated using biometrics.
	///
	@Published var isUnlocked: Bool
	
	
	private init() { // must use shared instance
		
		self.migrationStepsCompleted = false
		self.protectedDataAvailable = false
		self.walletExistence = .unknown
		self.isUnlocked = false
	}
}
