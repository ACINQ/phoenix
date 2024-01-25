import Foundation
import PhoenixShared
import os.log

class KotlinLogger {
	
	/// Singleton instance
	public static let shared = KotlinLogger()
	
	private var loggers: [String: Logger] = [:]
	private let lock = UnfairLock()
	
	private init() { // must use shared instance
		// Todo...
	}
	
	private func splitTag(_ tag: String) -> (String, String) {
		
		// The tag is something like: "PhoenixShared.PhoenixBusiness.BalanceManager"
		// We want to split that into ("PhoenixShared.PhoenixBusiness", "BalanceManager")
		
		if let idx = tag.lastIndex(of: ".") {
			let idxPlusOne = tag.index(after: idx)
			if idxPlusOne != tag.endIndex {
				let subsystem = String(tag.prefix(upTo: idx))
				let category = String(tag.suffix(from: idxPlusOne))
				return (subsystem, category)
			}
		}
		
		return ("", tag)
	}
	
	private func getLogger(_ tag: String) -> Logger {
		lock.locked {
			if let cachedLogger = loggers[tag] {
				return cachedLogger
			}
			
			let (subsystem, category) = splitTag(tag)
			let newLogger = Logger(subsystem: subsystem, category: category)
			loggers[tag] = newLogger
			
			return newLogger
		}
	}
	
	public func logger(_ severity: Kermit_coreSeverity, _ msg: String, _ tag: String) -> Void {
		
		let logger = getLogger(tag)
		switch severity {
			case Kermit_coreSeverity.verbose : logger.trace("\(msg)")
			case Kermit_coreSeverity.debug   : logger.debug("\(msg)")
			case Kermit_coreSeverity.info    : logger.info("\(msg)")
			case Kermit_coreSeverity.warn    : logger.warning("\(msg)")
			case Kermit_coreSeverity.error   : logger.error("\(msg)")
			case Kermit_coreSeverity.assert  : logger.error("\(msg)")
			default                          : logger.notice("\(msg)")
		}
	}
}
