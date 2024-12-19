import Foundation
import Logging
import os.log

struct OSLogHandler: LogHandler {
	
	private let logger: os.Logger
	
	init(module: String, filename: String) {
		logger = Logger(subsystem: module, category: filename)
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
		source: String,
		file: String,
		function: String,
		line: UInt
	){
		if let pm = prettyMetadata {
			switch level {
				case .trace    : logger.trace("{\(pm)} \(message.description)")
				case .debug    : logger.debug("{\(pm)} \(message.description)")
				case .info     : logger.info("{\(pm)} \(message.description)")
				case .notice   : logger.notice("{\(pm)} \(message.description)")
				case .warning  : logger.warning("{\(pm)} \(message.description)")
				case .error    : logger.error("{\(pm)} \(message.description)")
				case .critical : logger.critical("{\(pm)} \(message.description)")
			}
			
		} else {
			switch level {
				case .trace    : logger.trace("\(message.description)")
				case .debug    : logger.debug("\(message.description)")
				case .info     : logger.info("\(message.description)")
				case .notice   : logger.notice("\(message.description)")
				case .warning  : logger.warning("\(message.description)")
				case .error    : logger.error("\(message.description)")
				case .critical : logger.critical("\(message.description)")
			}
		}
	}
}
