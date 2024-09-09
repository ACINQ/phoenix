import Foundation
import Logging

class LoggerFactory {
	
	static let shared = LoggerFactory()
	
	private let logFileManager: LogFileManager
	
	private init() { // must use shared instance
		
		let logsDir = try! LoggerFactory.logsDirectory()
		let formatter = LoggerFactory.formatter()
		
		logFileManager = LogFileManager(
			logsDirectory: logsDir,
			logFilePrefix: LoggerFactory.logFilePrefix,
			rollingConfig: LogFileManager.RollingConfiguration(
				maximumFileSize: 1024 * 1024 * 2,   // 2 MiB,
				rollingFrequency: 60 * 60 * 24,     // 24 hours,
				rollOnAppLaunch: false              // allow log files to span app launches
		 	),
			cleanupConfig: LogFileManager.CleanupConfiguration(
				maximumNumberOfLogFiles: 8,         // 8 archived log files (+ current log file)
				logFilesDiskQuota: 1024 * 1024 * 20 // 20 MiB
		 	),
			formatter: formatter
		)
		
		logFileManager.prepare()
	}
	
	class var friendlyProcessName_foreground: String {
		return "Phoenix"
	}
	
	class var friendlyProcessName_background: String {
		return "NotifySrvExt"
	}
	
	class var logFilePrefix: String {
		return "\(self.friendlyProcessName)-"
	}
	
	class func logsDirectory() throws -> URL {
		
		let sharedDir = FileManager.default.containerURL(
			forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix"
		)!
		
		let logsDir: URL = sharedDir.appending(path: "logs", directoryHint: .isDirectory)
		
		try FileManager.default.createDirectory(at: logsDir, withIntermediateDirectories: true)
		return logsDir
	}
	
	class func formatter() -> LogFormatter {
		
		let pi = ProcessInfo.processInfo
		let pid = pi.processIdentifier
		let df = ISO8601DateFormatter()
		
		let formatter = {(
			level: Logging.Logger.Level,
			message: String,
			module: String,
			filename: String
		) -> String in
			
			let ts = df.string(from: Date.now)
			let lvl = level.logFileAbbreviation
			let tid = Thread.currentThreadID
			
			return "[\(ts)] [\(pid):\(tid)] [\(module)/\(filename)] [\(lvl)] \(message)"
		}
		
		return formatter
	}
	
	func handler(module: String, filename: String) -> LogHandler {
		
		let osLogHandler = OSLogHandler(module: module, filename: filename)
		let fileHandler = LogFileHandler(module: module, filename: filename, logFileManager: logFileManager)
		
		return MultiplexLogHandler([osLogHandler, fileHandler])
	}
	
	func logger(module: String, filename: String, level: Logger.Level) -> Logger {
		
		// the Logger's label works similarly to a DispatchQueue label
		let label = "\(module)/\(filename)"
		
		var logger = Logger(label: label) { _ in
			handler(module: module, filename: filename)
		}
		logger.logLevel = level
		return logger
	}
	
	func logger(_ filename: String, _ level: Logger.Level) -> Logger {
		return logger(module: "swift", filename: filename, level: level)
	}
}

extension Logging.Logger.Level {
	var logFileAbbreviation: String {
		switch self {
			case .trace    : return "T"
			case .debug    : return "D"
			case .info     : return "I"
			case .notice   : return "N"
			case .warning  : return "W"
			case .error    : return "E"
			case .critical : return "C"
		}
	}
	
	static func fromLogFileAbbreviation(_ str: String) -> Logging.Logger.Level? {
		switch str {
			case "T" : return .trace
			case "D" : return .debug
			case "I" : return .info
			case "N" : return .notice
			case "W" : return .warning
			case "E" : return .error
			case "C" : return .critical
			default  : return nil
		}
	}
}

extension Thread {
	class var currentThreadID: String {
		var tid: __uint64_t = 0
		if (pthread_threadid_np(nil, &tid) == 0) {
			return "\(tid)"
		} else {
			return "?"
		}
	}
}
