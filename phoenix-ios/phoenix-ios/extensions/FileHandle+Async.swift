import Foundation

extension FileHandle {
	
	func asyncWrite(
		data: any DataProtocol,
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		try self.write(contentsOf: data)
	}
	
	func asyncSyncAndClose(
		qos: DispatchQoS.QoSClass = .userInitiated
	) async throws {
		
		try self.synchronize()
		try self.close()
	}
}
