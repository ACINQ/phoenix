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
		let previousBuild = UserDefaults.standard.string(forKey: key) ?? "3"
		let currentBuild = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
		
		completionPublisher.sink { value in
			if value == 0 { // migration complete !
				if previousBuild.isVersion(lessThan: currentBuild) {
					UserDefaults.standard.set(currentBuild, forKey: key)
				}
				LockState.shared.migrationStepsCompleted = true
			}
		}.store(in: &cancellables)

		// v0.7.3 (build 4)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		// - we decided to delete old channels database (due to low number of test users)
		//
		if previousBuild.isVersion(lessThan: "4") {
			migrateChannelsDbFiles()
		}

		// v0.7.4 (build 5)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		//
		if previousBuild.isVersion(lessThan: "5") {
			migrateChannelsDbFiles()
		}

		// v0.7.6 (build 7)
		// - adding support for both soft & hard biometrics
		// - previously only supported hard biometics
		//
		if previousBuild.isVersion(lessThan: "7") {
			AppSecurity.shared.performMigration(previousBuild, completionPublisher)
		}
		
		// v0.8.0 (build 8)
		// - app db structure has changed
		// - channels/payments db have changed but files are renamed, no need to delete
		//
		if previousBuild.isVersion(lessThan: "8") {
			removeAppDbFile()
		}
		
		// v1.5.1 (build 40)
		// - notification service extension added
		// - database files moved to shared group
		// - several UserDefault values moved to shared group
		// - security.json file moved to shared group
		// - keychain item moved to shared group
		//
		if previousBuild.isVersion(lessThan: "40") {
			
			migrateDbFilesToGroup()
			GroupPrefs.shared.performMigration(previousBuild, completionPublisher)
			AppSecurity.shared.performMigration(previousBuild, completionPublisher)
		}
		
		// v1.5.2 (build 41)
		// - hot-fix for `!protectedDataAvailable`
		//
		if previousBuild.isVersion(lessThan: "41") {
			
			AppSecurity.shared.performMigration(previousBuild, completionPublisher)
		}
		
		completionPublisher.value -= 1
	}
	
	private func migrateChannelsDbFiles() -> Void {
		
		let fm = FileManager.default
		
		let appSupportDirs = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)
		guard let appSupportDir = appSupportDirs.first else {
			return
		}
		
		let databasesDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		
		let db1 = databasesDir.appendingPathComponent("channels.sqlite", isDirectory: false)
		let db2 = databasesDir.appendingPathComponent("channels.sqlite-shm", isDirectory: false)
		let db3 = databasesDir.appendingPathComponent("channels.sqlite-wal", isDirectory: false)
		
		if !fm.fileExists(atPath: db1.path) &&
			!fm.fileExists(atPath: db2.path) &&
			!fm.fileExists(atPath: db3.path)
		{
			// Database files don't exist. So there's nothing to migrate.
			return
		}
		
		let placeholder = "{version}"
		
		let template1 = "channels.\(placeholder).sqlite"
		let template2 = "channels.\(placeholder).sqlite-shm"
		let template3 = "channels.\(placeholder).sqlite-wal"
		
		var done = false
		var version = 0
		
		while !done {
			
			let f1 = template1.replacingOccurrences(of: placeholder, with: String(version))
			let f2 = template2.replacingOccurrences(of: placeholder, with: String(version))
			let f3 = template3.replacingOccurrences(of: placeholder, with: String(version))
			
			let dst1 = databasesDir.appendingPathComponent(f1, isDirectory: false)
			let dst2 = databasesDir.appendingPathComponent(f2, isDirectory: false)
			let dst3 = databasesDir.appendingPathComponent(f3, isDirectory: false)
			
			if fm.fileExists(atPath: dst1.path) ||
				fm.fileExists(atPath: dst2.path) ||
				fm.fileExists(atPath: dst2.path)
			{
				version += 1
			} else {
				
				try? fm.moveItem(at: db1, to: dst1)
				try? fm.moveItem(at: db2, to: dst2)
				try? fm.moveItem(at: db3, to: dst3)
				
				done = true
			}
		}
		
		// As a safety precaution (to prevent a crash), always delete the original filePath.
		
		try? fm.removeItem(at: db1)
		try? fm.removeItem(at: db2)
		try? fm.removeItem(at: db3)
		
		// Note:
		// - We just migrated the user's channels database.
		// - Which means their existing channels are going to get force closed by the server.
	}
	
	private func removeAppDbFile() {
		let fm = FileManager.default
		
		let appSupportDirs = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)
		guard let appSupportDir = appSupportDirs.first else {
			return
		}
		
		let databasesDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		let db = databasesDir.appendingPathComponent("app.sqlite", isDirectory: false)
		if !fm.fileExists(atPath: db.path) {
			return
		} else {
			try? fm.removeItem(at: db)
		}
	}
	
	/// Moves all the database files to ...
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
