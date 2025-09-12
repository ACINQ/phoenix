import Foundation

class AppState: ObservableObject {
	
	/// Singleton instance
	public static let shared = AppState()
	
	/// Indicates whether or not any required migration steps are still in flight.
	///
	@Published var migrationStepsCompleted: Bool
	
	/// Indicates whether the iOS keychain is available and ready.
	/// Failure to wait for this flag may result in the keychain returning item-not-found errors.
	///
	@Published var protectedDataAvailable: Bool
	
	/// Indicates whether the app is still loading/booting.
	/// When this is true, the LoadingView is still visible.
	///
	@Published var isLoading: Bool
	
	/// Indicates whether the app is locked or unlocked.
	/// This will be set to true if app-lock is disabled, or if the user has authenticated using biometrics.
	///
	@Published var isUnlocked: Bool
	
	
	private init() { // must use shared instance
		
		self.migrationStepsCompleted = false
		self.protectedDataAvailable = false
		self.isLoading = true
		self.isUnlocked = false
	}
}
