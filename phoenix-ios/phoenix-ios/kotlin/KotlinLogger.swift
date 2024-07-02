import Foundation
import PhoenixShared
import Logging


class KotlinLogger {
	
	/// Singleton instance
	public static let shared = KotlinLogger()
	
	private var loggers: [String: Logger] = [:]
	private let lock = UnfairLock()
	
	private init() { // must use shared instance
		// Todo...
	}
	
	private func splitTag(_ tag: String) -> (String, String) {
		
		// The tag is something like:
		// - "fr.acinq.lightning.blockchain.electrum.ElectrumWatcher"
		// - "fr.acinq.phoenix.managers.CurrencyManager"
		//
		// We want to split this into something like:
		// - ("lightning-kmp", "ElectrumWatcher")
		// - ("phoenix-kmp", "CurrencyManager")
		//
		// So there's a clear: (module, filename)
		
		if let idx = tag.lastIndex(of: ".") {
			let idxPlusOne = tag.index(after: idx)
			if idxPlusOne != tag.endIndex {
				let subsystem = tag.prefix(upTo: idx)
				let filename = String(tag.suffix(from: idxPlusOne))
				
				if subsystem.hasPrefix("fr.acinq.lightning") {
					return ("lightning-kmp", filename)
				} else if subsystem.hasPrefix("fr.acinq.phoenix") {
					return ("phoenix-kmp", filename)
				} else {
					return (String(subsystem), filename)
				}
			}
		}
		
		return ("", tag)
	}
	
	private func getLogger(_ tag: String) -> Logger {
		lock.locked {
			if let cachedLogger = loggers[tag] {
				return cachedLogger
			}
			
			let (module, filename) = self.splitTag(tag)
			let newLogger = LoggerFactory.shared.logger(module: module, filename: filename, level: .trace)
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
		}
	}
}
