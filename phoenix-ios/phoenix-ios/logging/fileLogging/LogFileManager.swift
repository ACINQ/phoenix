/**
 * Inspired by CocoaLumberjack's DDFileLogger,
 * which I wrote a long time ago in Objective-C.
 */

import Foundation
import Logging
import os.log

/// If the LogsFileManager itself encounters errors, we log them directly to OSLog.
/// So this is our internal logging mechanism.
///
fileprivate let ilog = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LogFileManager"
)

typealias LogFormatter = (
	_ /* level    */: Logging.Logger.Level,
	_ /* message  */: String,
	_ /* module   */: String,
	_ /* filename */: String
) -> String

/// The LogFileManager is the primary configuration & logic for a group of LogFileHandlers.
/// 
/// Normally a single LogFileManager instance is shared between ALL LogFileHandler instances.
/// This is because you want all logs from all parts of the app going to the same logFile.
///
/// However, there are times when you might want separate LogFileManager instances.
/// For example, your app might be split into the main iOS app, and a bundled app-extension.
/// In this case, you probably want separate logs for the app-extension.
///
/// Another example is if your app is split into modules, and you want separate logs for each module.
///
/// To accomplish this, make sure you either:
/// - use a different `logsDirectory` for each LogFileManager instance
/// - or alternatively, set a different `logFilePrefix` for each LogFileManager instance
///
class LogFileManager {
	
	/// ### Log File Rolling:
	///
	/// When your application is running, and messages are being streamed to the logFile,
	/// there comes a time when you want to close the logFile and create a new one.
	/// This is called "rolling" the log file, and there are several configuration options
	/// to control when that happens.
	///
	/// Both `maximumFileSize` and `rollingFrequency` are used to manage rolling during runtime.
	/// That is, whichever occurs first will cause the log file to be rolled.
	///
	/// For example:
	/// The `rollingFrequency` is set to 24 hours,
	/// but the log file surpasses the configured `maximumFileSize` after only 20 hours.
	/// The log file will be rolled at that 20 hour mark.
	/// A new log file will be created, and the 24 hour timer will be restarted.
	///
	struct RollingConfiguration {
		
		/// The approximate maximum size (in bytes) to allow log files to grow.
		/// If a log file is larger than this value after a log statement is appended,
		/// then the log file is rolled.
		///
		/// The default value is 1 MiB.
		/// You may optionally disable this option by setting it to zero.
		///
		let maximumFileSize: UInt64
		
		/// How often to roll the log file.
		/// The frequency is given as a `TimeInterval`, which is a Double that specifies the interval in seconds.
		/// Once the log file gets to be this old, it is rolled.
		///
		/// The default value is 24 hours.
		/// You may optionally disable this option by setting it to zero (or a negative value).
		///
		let rollingFrequency: TimeInterval
		
		/// When set, a new log file will be created each time the app is launched.
		///
		/// Note that if you set this value to true, you'll likely need to compensate by increasing
		/// the `cleanupConfig.maximumNumberOfLogFiles`, or you'll reduce the amount of log statements
		/// your app has access to.
		///
		/// The default value is false.
		///
		let rollOnAppLaunch: Bool
		
		/// If you completely disable all rolling:
		/// - maximumFileSize == 0
	 	/// - rollingFrequency == 0
		/// - rollOnAppLaunch = false
		///
		/// This is considered a configuration error, your configuration options will be ignored,
		/// and the system will fallback to using the default configuration (for system safety).
		///
		var isInvalid: Bool {
			return (maximumFileSize == 0) && (rollingFrequency <= 0) && (rollOnAppLaunch == false)
		}
		
		func sanitized() -> RollingConfiguration {
			return isInvalid ? RollingConfiguration.default : self
		}
		
		static var `default`: RollingConfiguration {
			RollingConfiguration(
				maximumFileSize: 1024 * 1024,   // 1 MiB
				rollingFrequency: 60 * 60 * 24, // 24 hours
				rollOnAppLaunch: false
			)
		}
	}
	
	/// #### Log file cleanup:
	///
	/// As log files are rolled, you end up with a number of "archived" log files.
	/// That is, older log files that are no longer being written to,
	/// and contain log statements going back further in time the older the log file.
	/// You likely don't want to keep these log files for forever, so there's a cleanup process,
	/// with several configuration options to control how that happens.
	///
	struct CleanupConfiguration {
		
		/// The maximum number of archived log files to keep on disk.
		///
		/// For example, if this property is set to 3, then the LogFileManager will only
		/// keep 3 archived log files (plus the current active log file) on disk.
		/// Once the active log file is rolled/archived, then the oldest of the
		/// existing 3 rolled/archived log files is deleted.
		///
		/// The default value is 5.
		/// You may optionally disable this option by setting it to zero (or a negative value).
		///
		let maximumNumberOfLogFiles: Int
		
		/// The maximum space that logs can take (in bytes).
		///
		/// After rolling a logfile, the total disk space is calculated by inspecting each archived logfile.
		/// If the total exceeds the logFilesDiskQuota, then the oldest archived logFile will be deleted,
		/// until the total is under the quota.
		///
		/// The default value is 20 MiB.
		/// You may optionally disable this option by setting it to zero.
		///
		var logFilesDiskQuota: UInt64
		
		/// If you completely disable all cleanup:
		/// - maximumNumberOfLogFiles == 0
		/// - logFilesDiskQuota == 0
		///
		/// This is considered a configuration error, your configuration options will be ignored,
		/// and the system will fallback to using the default configuration (for system safety).
		///
		var isInvalid: Bool {
			return (maximumNumberOfLogFiles <= 0) && (logFilesDiskQuota == 0)
		}
		
		func sanitized() -> CleanupConfiguration {
			return isInvalid ? CleanupConfiguration.default : self
		}
		
		static var `default`: CleanupConfiguration {
			CleanupConfiguration(
				maximumNumberOfLogFiles: 5,
				logFilesDiskQuota: 1024 * 1024 * 20 // 20 MiB
			)
		}
	}
	
	/// The directory where all the log files will be stored.
	///
	let logsDirectory: URL
	
	/// An optional prefix for every logFile name.
	///
	/// By default the logFile will be something like: "2024-03-11T20:04:11:452Z.log"
	/// If you set the prefix to "MyApp-" then the name will be: "MyApp-2024-03-11T20:04:11:452Z.log"
	///
	/// Important:
	/// If you **share** a logsDirectory (either between multiple apps, or between an app & app extension),
	/// then you should consider setting a logFilePrefix to ensure separate log files.
	///
	let logFilePrefix: String?
	
	/// The formatter defines how you want log messages to appear in the log file.
	///
	let formatter: LogFormatter
	
	/// Serial queues ensures that only one operation is in-flight at any given time.
	///
	private let loggingQueue = DispatchQueue(label: "LogFileManager.logging")
	private let cleanupQueue = DispatchQueue(label: "LogFileManager.cleanup")
	
	private var _rollingConfig: RollingConfiguration
	private var _cleanupConfig: CleanupConfiguration
	
	private var _currentLogFileInfo: LogFileInfo? = nil
	private var _currentLogFileHandle: FileHandle? = nil
	private var _currentLogFileVnode: DispatchSourceFileSystemObject? = nil
	
	private var _rollingTimer: DispatchSourceTimer? = nil
	
	private static let rollingLeewaySeconds: Int = 1
	
	/// Normally a single LogFileManager instance is shared between ALL LogFileHandler instances.
	/// This is because you want all logs from all parts of the app going to the same logFile.
	///
	/// However, there are times when you might want separate LogFileManager instance.
	/// For example, your app might be split into the main iOS app, and a bundled app-extension.
	/// In this case, you probably want separate logs for the app-extension.
	///
	/// Another example is if your app is split into modules, and you want separate logs for each module.
	///
	/// To accomplish this, make sure you either:
	/// - use a different `logsDirectory` for each LogFileManager instance
	/// - or alternatively, set a different `logFilePrefix` for each LogFileManager instance
	///
	init(
		logsDirectory: URL,
		logFilePrefix: String? = nil,
		rollingConfig: RollingConfiguration,
		cleanupConfig: CleanupConfiguration,
		formatter: @escaping LogFormatter
	) {
		self.logsDirectory = logsDirectory
		self.logFilePrefix = logFilePrefix
		self._rollingConfig = rollingConfig.sanitized()
		self._cleanupConfig = cleanupConfig.sanitized()
		self.formatter = formatter
		
		ilog.debug("logsDirectory: \(logsDirectory.path)")
	}
	
	// --------------------------------------------------
	// MARK: Public
	// --------------------------------------------------
	
	var rollingConfig: RollingConfiguration {
		get {
			loggingQueue.sync {
				self._rollingConfig
			}
		}
		set {
			loggingQueue.async {
				let oldConfig = self._rollingConfig
				let newConfig = newValue.sanitized()
				self._rollingConfig = newConfig
				if oldConfig.maximumFileSize != newConfig.maximumFileSize {
					self.maybeRollLogFileDueToSize()
				}
				if oldConfig.rollingFrequency != newConfig.rollingFrequency {
					self.maybeRollLogFileDueToAge()
				}
			}
		}
	}
	
	var cleanupConfig: CleanupConfiguration {
		get {
			cleanupQueue.sync {
				self._cleanupConfig
			}
		}
		set {
			cleanupQueue.async {
				let oldConfig = self._cleanupConfig
				let newConfig = newValue.sanitized()
				self._cleanupConfig = newConfig
				if oldConfig.maximumNumberOfLogFiles != newConfig.maximumNumberOfLogFiles ||
				   oldConfig.logFilesDiskQuota != newConfig.logFilesDiskQuota
				{
					self.deleteOldLogFiles()
				}
			}
		}
	}
	
	/// This method may optionally be called to prep the LogFileManager for use.
	/// Ideally, you would call this after initializing the instance,
	/// and after setting the desired configuration values.
	///
	/// Calling this function allows it to start some disk IO processes that
	/// are required before it can start writing log statements to disk, such as:
	///
	/// - checking the logsDirectory for existing log files
	/// - comparing timestamps of logFile entries
	/// - possibly creating a new logFile for use
	/// - opening the appropriate logFile for writing
	///
	func prepare() {
		
		loggingQueue.async {
			let _ = try? self.currentLogFileHandle()
		}
	}
	
	/// Returns an array of `LogFileInfo` objects,
	/// each representing an existing log file on disk,
	/// and containing information about the log file such as it's size & creation date.
	///
	/// The items in the array are sorted by creation date.
	/// The first item in the array will be the most recently created log file.
	///
	func sortedLogFiles() -> [LogFileInfo] {
		
		let keys = LogFileInfo.resourceValueKeys()
		let options: FileManager.DirectoryEnumerationOptions = [
			.skipsHiddenFiles,
			.skipsPackageDescendants,
			.skipsSubdirectoryDescendants
		]
		
		do {
			let allUrls = try FileManager.default.contentsOfDirectory(
				at: logsDirectory,
				includingPropertiesForKeys: Array(keys),
				options: options
			)
			
			return allUrls
				.filter { self.isLogFile($0) }
				.compactMap { LogFileInfo(url: $0) }
				.sorted { LogFileInfo.sortedByCreationDate(lhs: $0, rhs: $1) }
				.reversed()
			
		} catch {
			return []
		}
	}
	
	func log(
		level: Logging.Logger.Level,
		message: String,
		module: String,
		filename: String
	) {
		
		var string: String = formatter(level, message, module, filename)
		loggingQueue.sync {
			if !string.hasSuffix("\n") {
				string.append("\n")
			}
			if let data = string.data(using: .utf8) {
				self.logData(data)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Naming
	// --------------------------------------------------
	
	private func isLogFile(_ url: URL) -> Bool {
	
		let filename = url.lastPathComponent
		
		if let logFilePrefix {
			if !filename.hasPrefix(logFilePrefix) {
				return false
			}
		}
		
		return filename.hasSuffix(".log")
	}
	
	private func newLogFileBaseName() -> String {
		 
		let formattedDate = ISO8601DateFormatter().string(from: Date.now)
		
		if let logFilePrefix {
			return "\(logFilePrefix)\(formattedDate)"
		} else {
			return formattedDate
		}
	}
	
	// --------------------------------------------------
	// MARK: Internals
	// --------------------------------------------------
	
	/// This method is called on the `loggingQueue`
	///
	private func createNewLogFile() throws -> URL {
		
		// This method is called on the `loggingQueue`
		
		let baseFileName = newLogFileBaseName()
		
		var attempt = 1
		var errorCount = 0
		let MAX_ERROR_COUNT = 5
		
		while true {
			
			let fileName: String
			if attempt == 1 {
				fileName = "\(baseFileName).log"
			} else {
				fileName = "\(baseFileName) \(attempt).log"
			}
			
			let fileUrl: URL
			if #available(iOS 16.0, *) {
				fileUrl = logsDirectory.appending(path: fileName, directoryHint: .notDirectory)
			} else {
				fileUrl = logsDirectory.appendingPathComponent(fileName, isDirectory: false)
			}
			
			do {
				let options: Data.WritingOptions = .withoutOverwriting
				try Data().write(to: fileUrl, options: options)
				
				cleanupQueue.async {
					self.deleteOldLogFiles()
				}
				return fileUrl
				
			} catch {
				attempt += 1
				errorCount += 1
				
				if (errorCount >= MAX_ERROR_COUNT) {
					ilog.error("Cannot create log file: \(error)")
					throw error
				}
			}
		}
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func canReuse(_ logFile: LogFileInfo) -> Bool {
		
		let config = _rollingConfig
		if config.rollOnAppLaunch {
			return false
		}
		
		if let size = logFile.fileSize {
			if size >= config.maximumFileSize {
				return false
			}
		}
		
		if let age = logFile.age {
			if age >= config.rollingFrequency {
				return false
			}
		}
		
		return true
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func currentLogFileInfo() throws -> LogFileInfo {
		
		// This method is called on the `loggingQueue`
		
		if let currentLogFileInfo = _currentLogFileInfo {
			return currentLogFileInfo
		}
		
		// Possible situations:
		//
		// 1. Starting the app fresh
		//    Which means we want to check and see if we can continue writing to the old log file.
		//
		// 2. We just rolled the last log file
		//    If we're sharing the log files between multiple processes,
		//    then we want to check the disk, and see if there's a new logFile we should be using.
		//
		if let mostRecentLogFileInfo = sortedLogFiles().first {
			if canReuse(mostRecentLogFileInfo) {
				_currentLogFileInfo = mostRecentLogFileInfo
				return mostRecentLogFileInfo
			}
		}
		
		let newLogFileUrl = try createNewLogFile()
			
		let keys = LogFileInfo.resourceValueKeys()
		let resourceValues = try newLogFileUrl.resourceValues(forKeys: keys)
		
		let newLogFileInfo = LogFileInfo(url: newLogFileUrl, resourceValues: resourceValues)
		
		_currentLogFileInfo = newLogFileInfo
		return newLogFileInfo
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func currentLogFileHandle() throws -> FileHandle {
		
		// This method is called on the `loggingQueue`
		
		if let currentLogFileHandle = _currentLogFileHandle {
			return currentLogFileHandle
		}
		
		let logFileInfo = try currentLogFileInfo()
		
		let logFileHandle = try FileHandle(forWritingTo: logFileInfo.url)
		let _ = try logFileHandle.seekToEnd()
		
		_currentLogFileHandle = logFileHandle
		
		scheduleTimerToRollLogFileDueToAge()
		monitorCurrentLogFileForExternalChanges()
		
		return logFileHandle
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func logData(_ data: Data) {
		
		if data.isEmpty {
			return
		}
		
		guard let handle = try? currentLogFileHandle() else {
			return
		}
		
		let fd = handle.fileDescriptor
		while(flock(fd, LOCK_EX) != 0) {
		//	log.debug("Could not lock logfile, retrying in 1ms: \(errno): \(strerror(errno))")
			usleep(1000)
		}
		
		do {
			try handle.seekToEnd()
			try handle.write(contentsOf: data)
		} catch {
			ilog.error("Error writing to logFile: \(error)")
		}
		
		flock(fd, LOCK_UN)
		maybeRollLogFileDueToSize()
	}
	
	// --------------------------------------------------
	// MARK: Rolling
	// --------------------------------------------------
	
	/// This method is called on the `loggingQueue`
	///
	private func scheduleTimerToRollLogFileDueToAge() {
		
		if let timer = _rollingTimer {
			timer.cancel()
			_rollingTimer = nil
		}
		
		guard let logFileInfo = _currentLogFileInfo else {
			ilog.warning("scheduleTimerToRollLogFileDueToAge: currentLogFileInfo is nil; cannot proceed")
			return
		}
		
		let config = _rollingConfig
		if config.rollingFrequency <= 0.0 {
			return
		}
		
		var delay = config.rollingFrequency
		if let creationDate = logFileInfo.creationDate {
			let rollingDate = creationDate.addingTimeInterval(config.rollingFrequency)
			delay = rollingDate.timeIntervalSinceNow
			// ^^ could be negative, but that's not a problem (timer fires right away)
		}
		
		let timer = DispatchSource.makeTimerSource(flags: [], queue: loggingQueue)
		timer.setEventHandler { [weak self] in
			self?.maybeRollLogFileDueToAge()
		}
		
		timer.schedule(
			deadline: DispatchTime.now() + delay,
			repeating: .never,
			leeway: DispatchTimeInterval.seconds(LogFileManager.rollingLeewaySeconds)
		)
		
		_rollingTimer = timer
		timer.activate()
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func maybeRollLogFileDueToAge() {
		
		guard let logFileInfo = _currentLogFileInfo else {
			// No current log file, so nothing to roll right now.
			// This might happen if called before we've started logging (common),
			// or if called just after rolling a log file (but before the next log statement).
			return
		}
		
		let config = _rollingConfig
		if config.rollingFrequency <= 0.0 {
			return
		}
		
		let leeway = Double(LogFileManager.rollingLeewaySeconds)
		if let age = logFileInfo.age, (age + leeway) >= config.rollingFrequency {
			ilog.info("Rolling log file due to age (\(age))...")
			rollLogFileNow()
		} else {
			scheduleTimerToRollLogFileDueToAge()
		}
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func maybeRollLogFileDueToSize() {
		
		guard let logFileHandle = _currentLogFileHandle else {
			// No current log file, so nothing to roll right now.
			// This might happen if called before we've started logging (common),
			// or if called just after rolling a log file (but before the next log statement).
			return
		}
		
		let config = _rollingConfig
		if config.maximumFileSize == 0 {
			return
		}
		
		var fileSize: UInt64 = 0
		do {
			fileSize = try logFileHandle.offset()
		} catch {
			ilog.error("maybeRollLogFileDueToSize: Cannot get logFileHandle.offset: \(error)")
		}
		
		if fileSize >= config.maximumFileSize {
			ilog.info("Rolling log file due to size (\(fileSize))...")
			rollLogFileNow()
		}
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func monitorCurrentLogFileForExternalChanges() {
		
		if let logFileVnode = _currentLogFileVnode {
			logFileVnode.cancel()
			_currentLogFileVnode = nil
		}
		
		guard let logFileHandle = _currentLogFileHandle else {
			return
		}

		let logFileVnode = DispatchSource.makeFileSystemObjectSource(
			fileDescriptor: logFileHandle.fileDescriptor,
			eventMask: [.delete, .rename, .revoke],
			queue: loggingQueue
		)
		
		logFileVnode.setEventHandler { [weak self] in
			ilog.info("Rolling log file - current was deleted/renamed externally...")
			self?.rollLogFileNow()
		}

		_currentLogFileVnode = logFileVnode
		logFileVnode.activate()
	}
	
	/// This method is called on the `loggingQueue`
	///
	private func rollLogFileNow() {
		
		guard let logFileHandle = _currentLogFileHandle else {
			return
		}

		do {
			try logFileHandle.synchronize()
		} catch {
			ilog.error("Failed to synchronize file: \(error)")
		}
		
		do {
			try logFileHandle.close()
		} catch {
			ilog.error("Failed to close file: \(error)")
		}
		
		_currentLogFileHandle = nil
		_currentLogFileInfo = nil
		
		if let vnode = _currentLogFileVnode {
			vnode.cancel()
			_currentLogFileVnode = nil
		}

		if let timer = _rollingTimer {
			timer.cancel()
			_rollingTimer = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Cleanup
	// --------------------------------------------------
	
	/// This method is called on the `cleanupQueue`
	///
	private func deleteOldLogFiles() {
		
		let allLogFiles = self.sortedLogFiles()
		// ^ sorted by creation date, with index 0 being the current log file
		let candidateLogFiles = allLogFiles.isEmpty ? allLogFiles : Array(allLogFiles[1...])
		
		let config = _cleanupConfig
		var firstIndexToDelete: Int? = nil
		
		if config.logFilesDiskQuota > 0 {
			var used: UInt64 = 0
			for (index, logFile) in candidateLogFiles.enumerated() {
				used += UInt64(logFile.fileSize ?? 0)
				if (used > config.logFilesDiskQuota) {
					firstIndexToDelete = index
					break
				}
			}
		}
		
		if config.maximumNumberOfLogFiles > 0 {
			if let currentIndex = firstIndexToDelete {
				firstIndexToDelete = min(currentIndex, config.maximumNumberOfLogFiles)
			} else {
				firstIndexToDelete = config.maximumNumberOfLogFiles
			}
		}
		
		if let firstIndexToDelete, firstIndexToDelete < candidateLogFiles.count {
			// removing all log files starting with firstIndexToDelete
			for i in (firstIndexToDelete ..< candidateLogFiles.count) {
				let logFile = candidateLogFiles[i]
				do {
					ilog.info("Deleting log file: \(logFile.fileName)")
					try FileManager.default.removeItem(at: logFile.url)
				} catch {
					ilog.error("Error deleting log file: \(error)")
				}
			}
		}
	}
}
