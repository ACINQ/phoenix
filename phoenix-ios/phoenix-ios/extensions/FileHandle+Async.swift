import Foundation

extension FileHandle {
	
	func asyncWrite(
		data: Data,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: qos).async {
				do {
					try self.write(contentsOf: data)
					continuation.resume()
				} catch {
					continuation.resume(throwing: error)
				}
			}
		}
	}
	
	func asyncSyncAndClose(
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: qos).async {
				do {
					try self.synchronize()
					try self.close()
					continuation.resume()
				} catch {
					continuation.resume(throwing: error)
				}
			}
		}
	}
}
