import Foundation
import Logging

struct LogFileHandler: LogHandler {
	
	let module: String
	let filename: String
	let logFileManager: LogFileManager

	init(module: String, filename: String, logFileManager: LogFileManager) {
		self.module = module
		self.filename = filename
		self.logFileManager = logFileManager
	}
	
	public var logLevel: Logging.Logger.Level = .trace
	
	public var metadata = Logger.Metadata() {
		didSet {
			self.prettyMetadata = self.prettify(self.metadata)
		}
	}
	
	public subscript(metadataKey metadataKey: String) -> Logging.Logger.Metadata.Value? {
		get { self.metadata[metadataKey] }
		set { self.metadata[metadataKey] = newValue }
	}
	
	private var prettyMetadata: String?
	private func prettify(_ metadata: Logging.Logger.Metadata) -> String? {
		guard !metadata.isEmpty else {
			return nil
		}
		return metadata.map { "\($0)=\($1)" }.joined(separator: ", ")
	}
	
	public func log(
		level: Logging.Logger.Level,
		message: Logging.Logger.Message,
		metadata: Logging.Logger.Metadata?,
		file: String,
		function: String,
		line: UInt
	){
		let msg: String
		if let pm = prettyMetadata {
			msg = "{\(pm)} \(message.description)"
		} else {
			msg = message.description
		}
		
		logFileManager.log(
			level: level,
			message: msg,
			module: module,
			filename: filename
		)
	}
}
