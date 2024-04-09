import Foundation

extension Task where Success == Never, Failure == Never {
	
	static func sleep(seconds: TimeInterval) async throws {
		if #available(iOS 16.0, *) {
			try await Task.sleep(for: Duration.seconds(seconds))
		} else {
			let nanoseconds = UInt64(seconds * 1_000_000_000)
			try await Task.sleep(nanoseconds: nanoseconds)
		}
	}
	
	static func sleep(hours: Int) async throws {
		try await sleep(seconds: TimeInterval(hours) * 60 * 60)
	}
}
