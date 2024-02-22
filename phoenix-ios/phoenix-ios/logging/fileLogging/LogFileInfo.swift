import Foundation

/// A simple wrapper around a logFile item.
///
/// The resourceValues (such as `fileSize`) are snapshots taken from the
/// filesystem (and do not automatically update).
///
struct LogFileInfo {
	
	let url: URL
	let resourceValues: URLResourceValues
	
	init(url: URL, resourceValues: URLResourceValues) {
		self.url = url
		self.resourceValues = resourceValues
	}
	
	init?(url: URL) {
		do {
			self.url = url
			self.resourceValues = try url.resourceValues(forKeys: LogFileInfo.resourceValueKeys())
		} catch {
			return nil
		}
	}
	
	var fileName: String {
		get { url.lastPathComponent }
	}
	
	var fileSize: Int? {
		get { resourceValues.fileSize }
	}
	
	var creationDate: Date? {
		get { resourceValues.creationDate }
	}
	
	var age: TimeInterval? {
		get {
			if let date = self.creationDate {
				return date.timeIntervalSinceNow * -1.0
			} else {
				return nil
			}
		}
	}
	
	static func resourceValueKeys() -> Set<URLResourceKey> {
		return Set<URLResourceKey>([.fileSizeKey, .creationDateKey])
	}
	
	/// Returns true if `lhs.creationDate < rhs.creationDate`, false otherwise.
	///
	static func sortedByCreationDate(lhs: LogFileInfo, rhs: LogFileInfo) -> Bool {
		
		let lcd = lhs.creationDate
		let rcd = rhs.creationDate
		
		if let lcd, let rcd {
			return lcd < rcd
		} else {
			// if any creationDate is nil, we assume distantPast:
			// * lcd(non-nil) < nil => false
			// * nil < rcd(non-nil) => true
			// * nil < nil => false (they're equal)
			return rcd != nil
		}
	}
}
