import Foundation
import Combine


fileprivate let filename = "AppMigration"
#if DEBUG && true
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class AppMigration {
	
	/// Singleton instance
	public static let shared = AppMigration()
	
	public private(set) var previousBuildNumber: String = ""
	public private(set) var currentBuildNumber: String = ""
	public private(set) var didUpdate: Bool = false
	
	private let completionPublisher = CurrentValueSubject<Int, Never>(1)
	private var cancellables = Set<AnyCancellable>()
	
	public func performMigrationChecks() -> Void {
		
		let key = "lastVersionCheck"
		let previousBuild = UserDefaults.standard.string(forKey: key) ?? "17"
		let currentBuild = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
		
		self.previousBuildNumber = previousBuild
		self.currentBuildNumber = currentBuild
		self.didUpdate = currentBuild.isVersion(greaterThan: previousBuild)
		
		completionPublisher.sink { value in
			if value == 0 { // migration complete !
				if previousBuild.isVersion(lessThan: currentBuild) {
					UserDefaults.standard.set(currentBuild, forKey: key)
				}
				AppState.shared.migrationStepsCompleted = true
			}
		}.store(in: &cancellables)

		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		// v1.5.1 (build 40)
		// - notification service extension added
		// - database files moved to shared group
		// - several UserDefault values moved to shared group
		// - security.json file moved to shared group
		// - keychain item moved to shared group
		//
		if previousBuild.isVersion(lessThan: "40") {
			migrateDbFilesToGroup()
			GroupPrefs.performMigration("40", completionPublisher)
			Keychain.performMigration("40", completionPublisher)
		}
		
		// v1.5.2 (build 41)
		// - hot-fix for `!protectedDataAvailable`
		//
		if previousBuild.isVersion(lessThan: "41") {
			Keychain.performMigration("41", completionPublisher)
		}
		
		// v1.6.0 (build 44)
		// - recentPaymentsSeconds -> recentPayments (enum)
		//
		if previousBuild.isVersion(lessThan: "44") {
			Prefs.performMigration("44", completionPublisher)
		}
		
		// v2.0.6 (build 65)
		// - UserDefault value (liquidityPolicy) moved to shared group
		if currentBuild.isVersion(greaterThanOrEqualTo: "65") && previousBuild.isVersion(lessThan: "65") {
			GroupPrefs.performMigration("65", completionPublisher)
		}
		
		// v2.7.0 (build 96)
		// - Prefs & GroupPrefs moved to wallet-specific keys
		if currentBuild.isVersion(greaterThanOrEqualTo: "96") && previousBuild.isVersion(lessThan: "96") {
		#if DEBUG
			log.debug("--------------------------------------------------")
			log.debug("# PREFS: PHASE 0:")
			Prefs.printAllKeyValues()
			log.debug("# GROUP_PREFS: PHASE 0:")
			GroupPrefs.printAllKeyValues()
			log.debug("# KEYCHAIN(nil): PHASE 0:")
			Keychain.printKeysAndValues(nil)
			log.debug("--------------------------------------------------")
		#endif
			Prefs.performMigration("96", completionPublisher)
			GroupPrefs.performMigration("96", completionPublisher)
			Keychain.performMigration("96", completionPublisher)
		#if DEBUG
			log.debug("--------------------------------------------------")
			log.debug("# PREFS: PHASE 1:")
			Prefs.printAllKeyValues()
			log.debug("# GROUP_PREFS: PHASE 1:")
			GroupPrefs.printAllKeyValues()
			log.debug("# KEYCHAIN(default): PHASE 1:")
			Keychain.printKeysAndValues(KEYCHAIN_DEFAULT_ID)
			log.debug("--------------------------------------------------")
		#endif
		}
		
		completionPublisher.value -= 1
	}
	
	private func migrateDbFilesToGroup() -> Void {
		
		let fm = FileManager.default
		
		guard
			let appSupportDir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first,
			let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix")
		else {
			return
		}
		
		let oldDbDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		let newDbDir = groupDir.appendingPathComponent("databases", isDirectory: true)
		
		do {
			try fm.createDirectory(at: newDbDir, withIntermediateDirectories: true)
		} catch {
			log.error("Error creating directory: \(String(describing: error))")
		}
		
		let contents = try? fm.contentsOfDirectory(
			at: oldDbDir,
			includingPropertiesForKeys: nil,
			options: [.skipsHiddenFiles, .skipsSubdirectoryDescendants, .skipsPackageDescendants]
		)
		for srcUrl in (contents ?? []) {
		
			if srcUrl.path.contains(".sqlite") {
				
				let filename = srcUrl.lastPathComponent
				let dstUrl = newDbDir.appendingPathComponent(filename, isDirectory: false)
				
				log.debug("mv: \(srcUrl.path) -> \(dstUrl.path)")
				
				do {
					try fm.moveItem(at: srcUrl, to: dstUrl)
					
				} catch {
					log.error("Error moving file: \(String(describing: error))")
				}
			}
		}
	}
}
