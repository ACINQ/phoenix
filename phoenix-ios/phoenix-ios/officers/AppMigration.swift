import Foundation
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AppMigration"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class AppMigration {
	
	/// Singleton instance
	public static let shared = AppMigration()
	
	private let completionPublisher = CurrentValueSubject<Int, Never>(1)
	private var cancellables = Set<AnyCancellable>()
	
	public func performMigrationChecks() -> Void {
		
		let key = "lastVersionCheck"
		let previousBuild = UserDefaults.standard.string(forKey: key) ?? "17"
		let currentBuild = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
		
		completionPublisher.sink { value in
			if value == 0 { // migration complete !
				if previousBuild.isVersion(lessThan: currentBuild) {
					UserDefaults.standard.set(currentBuild, forKey: key)
				}
				LockState.shared.migrationStepsCompleted = true
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
			GroupPrefs.shared.performMigration("40", completionPublisher)
			AppSecurity.shared.performMigration("40", completionPublisher)
		}
		
		// v1.5.2 (build 41)
		// - hot-fix for `!protectedDataAvailable`
		//
		if previousBuild.isVersion(lessThan: "41") {
			AppSecurity.shared.performMigration("41", completionPublisher)
		}
		
		// v1.6.0 (build 44)
		// - recentPaymentsSeconds -> recentPayments (enum)
		//
		if previousBuild.isVersion(lessThan: "44") {
			Prefs.shared.performMigration("44", completionPublisher)
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
