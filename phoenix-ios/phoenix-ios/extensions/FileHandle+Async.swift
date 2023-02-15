import Foundation

extension FileHandle {
	
	func asyncWrite(
		data: any DataProtocol,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		return try await withCheckedThrowingContinuation { continuation in
			DispatchQueue.global(qos: qos).async {
				do {
					try self.write(contentsOf: data)
					continuation.resume(with: .success)
				} catch {
					continuation.resume(with: .failure(error))
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
					continuation.resume(with: .success)
				} catch {
					continuation.resume(with: .failure(error))
				}
			}
		}
	}
}
