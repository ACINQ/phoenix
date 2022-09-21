import Foundation


class LockState: ObservableObject {
	
	/// Singleton instance
	public static let shared = LockState()
	
	/// Indicates whether the app is locked or unlocked.
	/// This will be set to true if app-lock is disabled, or if the user has authenticated using biometrics.
	///
	@Published var isUnlocked: Bool
	
	/// Indicates whether the iOS keychain is available and ready.
	/// Failure to wait for this flag may result in the keychain returning item-not-found errors.
	///
	@Published var protectedDataAvailable: Bool
	
	/// Indicates whether we've performed the first unlock attempt.
	/// Prior to this becoming true, we don't know if there's a wallet or not.
	///
	@Published var firstUnlockAttempted: Bool
	
	/// Indicates whether or not we found mnemonics in the keychain during first unlock attempt.
	/// This value will be set prior to firstUnlockAttempted.
	///
	@Published var firstUnlockFoundMnemonics: Bool
	
	/// Indicates whether or not any required migration steps are still in flight.
	/// 
	@Published var migrationStepsCompleted: Bool
	
	private init() { // must use shared instance
		
		self.isUnlocked = false
		self.protectedDataAvailable = false
		self.firstUnlockAttempted = false
		self.firstUnlockFoundMnemonics = false
		self.migrationStepsCompleted = false
	}
}
