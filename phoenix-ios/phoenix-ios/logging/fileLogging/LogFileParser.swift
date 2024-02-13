import Foundation
import Logging

fileprivate let filename = "LogFileParser"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// A fully parsed log entry with all the data.
///
struct LogFileEntry {
	let raw: String // unparsed entry from log file
	let timestamp: Date
	let processID: Int32
	let threadID: UInt64
	let module: String
	let filename: String
	let level: Logging.Logger.Level
	let message: String
}

/// A lightly parsed log entry (for when you only need the timestamp).
///
struct LogFileEntryTimestamp {
	let raw: String // unparsed entry from LogFile
	let timestamp: Date
}


class LogFileParser {
	
	/// An error may be thrown if there are IO errors when opening/reading the file.
	/// Errors when attempting to parse particular log entries are ignored (but log messages are generated).
	///
	static func fullParse(_ url: URL) async throws -> [LogFileEntry] {
		
		let rawEntries = try await parseRawEntries(url)
		let df = ISO8601DateFormatter()
		
		var entries: [LogFileEntry] = []
		for rawEntry in rawEntries {
			if let entry = parseEntry(rawEntry, df) {
				entries.append(entry)
			}
		}
		
		return entries
	}
	
	/// An error may be thrown if there are IO errors when opening/reading the file.
	/// Errors when attempting to parse particular log entries are ignored (but log messages are generated).
	///
	static func lightParse(_ url: URL) async throws -> [LogFileEntryTimestamp] {
		
		let rawEntries = try await parseRawEntries(url)
		let df = ISO8601DateFormatter()
		
		var entries: [LogFileEntryTimestamp] = []
		for rawEntry in rawEntries {
			if let entry = parseLightEntry(rawEntry, df) {
				entries.append(entry)
			}
		}
		
		return entries
	}
	
	private static func parseRawEntries(_ url: URL) async throws -> [String] {
		
		var rawEntries: [String] = []
		
		var buffer: String = ""
		for try await line in url.lines {
			if line.hasPrefix("[") {
				if !buffer.isEmpty {
					rawEntries.append(buffer)
				}
				buffer = line
			} else if !buffer.isEmpty {
				buffer += line
			}
		}
		
		if !buffer.isEmpty {
			rawEntries.append(buffer)
		}
		
		return rawEntries
	}
	
	private static func parseEntry(
		_ string: String,
		_ df: ISO8601DateFormatter
	) -> LogFileEntry? {
		
		var offset: String.Index = string.startIndex
		guard let timestamp = parseTimestamp(string, df, &offset) else {
			return nil
		}
		guard let (processID, threadID) = parseProcessThread(string, &offset) else {
			return nil
		}
		guard let (module, filename) = parseModuleFilename(string, &offset) else {
			return nil
		}
		guard let level = parseLevel(string, &offset) else {
			return nil
		}
		
		var msg = string.suffix(from: offset)
		if msg.hasPrefix(" ") {
			msg = msg.suffix(from: msg.index(after: offset))
		}
		
		return LogFileEntry(
			raw: string,
			timestamp: timestamp,
			processID: processID,
			threadID: threadID,
			module: module,
			filename: filename,
			level: level,
			message: String(msg)
		)
	}
	
	private static func parseLightEntry(
		_ string: String,
		_ df: ISO8601DateFormatter
	) -> LogFileEntryTimestamp? {
		
		var offset: String.Index = string.startIndex
		guard let timestamp = parseTimestamp(string, df, &offset) else {
			return nil
		}
		
		return LogFileEntryTimestamp(
			raw: string,
			timestamp: timestamp
		)
	}
	
	private static func parseTimestamp(
		_ fullString: String,
		_ df: ISO8601DateFormatter,
		_ index: inout String.Index
	) -> Date? {
		
		let label = "timestamp"
		guard let substring = parseEnclosure(fullString, label, &index) else {
			return nil
		}
		
		let result = df.date(from: String(substring))
		if result == nil {
			log.debug("parseItem: \(label): cannot parse timestamp: \(substring)")
		}
		
		return result
	}
	
	private static func parseProcessThread(
		_ fullString: String,
		_ index: inout String.Index
	) -> (Int32, UInt64)? {
		
		let label = "process/thread"
		guard let substring = parseEnclosure(fullString, label, &index) else {
			return nil
		}
		
		let components = substring.components(separatedBy: ":")
		guard components.count == 2 else {
			log.debug("parseItem: \(label): components.count != 2")
			return nil
		}
		
		guard let processID = Int32(components[0]) else {
			log.debug("parseItem: \(label): cannot prase processID: \(components[0])")
			return nil
		}
		
		guard let threadID = UInt64(components[1]) else {
			log.debug("parseItem: \(label): cannot parse threadID: \(components[1])")
			return nil
		}
		
		return (processID, threadID)
	}
	
	private static func parseModuleFilename(
		_ fullString: String,
		_ index: inout String.Index
	) -> (String, String)? {
		
		let label = "module/filename"
		guard let substring = parseEnclosure(fullString, label, &index) else {
			return nil
		}
		
		let components = substring.components(separatedBy: "/")
		if components.count == 2 {
			return (components[0], components[1])
		} else if components.count > 2 {
			// Either the module or filename has a "/" character.
			// This isn't supposed to happen, but if it does, we'll assume it's part of the module.
			let module = components.dropLast().joined(separator: "/")
			let filename = components.last!
			return (module, filename)
		} else {
			log.debug("parseItem: \(label): cannot parse components: \(substring)")
			return nil
		}
	}
	
	private static func parseLevel(
		_ fullString: String,
		_ index: inout String.Index
	) -> Logging.Logger.Level? {
		
		let label = "level"
		guard let substring = parseEnclosure(fullString, label, &index) else {
			return nil
		}
		
		let result = Logging.Logger.Level.fromLogFileAbbreviation(String(substring))
		if result == nil {
			log.debug("parseItem: \(label): cannot parse level: \(substring)")
		}
		
		return result
	}
	
	private static func parseEnclosure(
		_ fullString: String,
		_ label: String,
		_ index: inout String.Index
	) -> String.SubSequence? {
		
		let substring0 = fullString[index...]
		guard let preIndex = substring0.firstIndex(of: "[") else {
			log.debug("parseItem: \(label): missing enclosure: '['")
			return nil
		}
		
		let startIndex = substring0.index(after: preIndex)
		let substring1 = substring0[startIndex...]
		guard let endIndex = substring1.firstIndex(of: "]") else {
			log.debug("parseItem: \(label): missing enclosure: ']'")
			return nil
		}
		
		index = fullString.index(after: endIndex)
		return substring1[..<endIndex]
	}
}

