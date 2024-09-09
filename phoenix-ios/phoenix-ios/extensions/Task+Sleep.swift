import Foundation

extension Task where Success == Never, Failure == Never {
	
	static func sleep(seconds: TimeInterval) async throws {
		try await Task.sleep(for: Duration.seconds(seconds))
	}
	
	static func sleep(hours: Int) async throws {
		try await sleep(seconds: TimeInterval(hours) * 60 * 60)
	}
}
