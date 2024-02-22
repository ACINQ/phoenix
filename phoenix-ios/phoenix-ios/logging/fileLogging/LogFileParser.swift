import Foundation
import Logging
import AsyncAlgorithms

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

struct LogFileEntryTimestampWrapper {
	let urlIndex: Int
	let entry: LogFileEntryTimestamp
}


class LogFileParser {
	
	/// Parses multiple log files simulataneously, streaming entries from earliest to latest.
	///
	/// This function is designed to minimize memory pressure, allowing dozens of log files to
	/// be streamed, while only reading minimal information into memory during the streaming process.
	///
	/// Since an AsyncChannel is being used, backpressure is properly implemented.
	/// Meaning data from the underlying files will only be read as you request more items from this channel.
	///
	static func asyncLightParseChannel(
		_ urls: [URL]
	) -> AsyncThrowingChannel<LogFileEntryTimestampWrapper, Error> {
		
		let channel = AsyncThrowingChannel<LogFileEntryTimestampWrapper, Error>()
		
		Task.detached {
			
			var iterators: [AsyncThrowingChannel<LogFileEntryTimestamp, Error>.Iterator] = []
			for url in urls {
				let channel = asyncLightParseChannel(url)
				let iterator = channel.makeAsyncIterator()
				
				iterators.append(iterator)
			}
			
			var entries: [Optional<LogFileEntryTimestamp>] = []
			
			for i in 0..<iterators.count {
				var iterator = iterators[i]
				let entry = try? await iterator.next() // func `next()` is a mutating operation
				
				entries.append(entry)
				iterators[i] = iterator
			}
			
			let nextEntry = {() -> (Int, LogFileEntryTimestamp)? in
				
				var minIndex: Int? = nil
				var minEntry: LogFileEntryTimestamp? = nil
				
				for (index, entry) in entries.enumerated() {
					if let entry {
						if let currentMinEntry = minEntry {
							if entry.timestamp < currentMinEntry.timestamp {
								minIndex = index
								minEntry = entry
							}
						} else {
							minIndex = index
							minEntry = entry
						}
					}
				}
				
				if let index = minIndex, let entry = minEntry {
					return (index, entry)
				} else {
					return nil
				}
			}
			
			while let tuple = nextEntry() {
				
				let (index, entry) = tuple
				let entryWrapper = LogFileEntryTimestampWrapper(urlIndex: index, entry: entry)
				
				await channel.send(entryWrapper)
				
				var iterator = iterators[index]
				let nextEntry = try? await iterator.next()
				
				entries[index] = nextEntry
				iterators[index] = iterator
			}
			
			channel.finish()
			
		} // </Task>
		
		return channel
	}
	
	/// Returns an AsyncThrowingChannel that can be used to read the log entries iteratively.
	///
	/// Since an AsyncChannel is being used, backpressure is properly implemented.
	/// Meaning data from the underlying file will only be read as you request more items from this channel.
	///
	/// An error may be emitted on the channel if there are IO errors when opening/reading the file.
	/// Errors when attempting to parse particular log entries are ignored (but log messages are generated).
	///
	static func asyncFullParseChannel(
		_ url: URL
	) -> AsyncThrowingChannel<LogFileEntry, Error> {
		
		let channel = AsyncThrowingChannel<LogFileEntry, Error>()
		Task.detached {
			do {
				let df = ISO8601DateFormatter()
				let rawEntriesChannel = asyncRawParseChannel(url)
				
				for try await rawEntry in rawEntriesChannel {
					if let entry = parseEntry(rawEntry, df) {
						await channel.send(entry)
					}
				}
				
				channel.finish()
			} catch {
				channel.fail(error)
			}
		}
		
		return channel
	}
	
	/// Returns an AsyncThrowingChannel that can be used to read the log entries iteratively.
	///
	/// Since an AsyncChannel is being used, backpressure is properly implemented.
	/// Meaning data from the underlying file will only be read as you request more items from this channel.
	///
	/// An error may be emitted on the channel if there are IO errors when opening/reading the file.
	/// Errors when attempting to parse particular log entries are ignored (but log messages are generated).
	///
	static func asyncLightParseChannel(
		_ url: URL
	) -> AsyncThrowingChannel<LogFileEntryTimestamp, Error> {
		
		let channel = AsyncThrowingChannel<LogFileEntryTimestamp, Error>()
		Task.detached {
			do {
				let df = ISO8601DateFormatter()
				let rawEntriesChannel = asyncRawParseChannel(url)
				
				for try await rawEntry in rawEntriesChannel {
					if let entry = parseLightEntry(rawEntry, df) {
						await channel.send(entry)
					}
				}
				
				channel.finish()
			} catch {
				channel.fail(error)
			}
		}
		
		return channel
	}
	
	/// Returns an AsyncThrowingChannel that can be used to read the log entries iteratively.
	///
	/// Since an AsyncChannel is being used, backpressure is properly implemented.
	/// Meaning data from the underlying file will only be read as you request more items from this channel.
	///
	/// An error may be emitted on the channel if there are IO errors when opening/reading the file.
	///
	static func asyncRawParseChannel(
		_ url: URL
	) -> AsyncThrowingChannel<String, Error> {
		
		let channel = AsyncThrowingChannel<String, Error>()
		Task.detached {
			do {
				// Does URL.lines properly support backpressure ?
				//
				// This question is important in our implementation,
				// because we want to open all log files simultaneously,
				// and combine them on the fly (sorted by timestamp).
				//
				// If URL.lines does NOT support backpressure,
				// then it will immediately read the entire file into memory,
				// and our "combine strategy" falls apart.
				//
				// However, after testing, I'm happy to report that it
				// does indeed support backpressure (even though it's not
				// explained in the documentation).
				
				var buffer: String = ""
				for try await line in url.lines {
					if line.hasPrefix("[") {
						if !buffer.isEmpty {
							await channel.send(buffer)
						}
						buffer = line
					} else if !buffer.isEmpty {
						buffer += line
					}
				}
				
				if !buffer.isEmpty {
					await channel.send(buffer)
				}
				
				channel.finish()
			} catch {
				channel.fail(error)
			}
		} // </Task>
		
		return channel
	}
	
	static func parseEntry(
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
	
	static func parseLightEntry(
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

