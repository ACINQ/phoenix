import Foundation
import PhoenixShared
import Combine

extension Eclair_kmpConnection {
	
	func localizedText() -> String {
		switch self {
		case .closed       : return NSLocalizedString("Offline", comment: "Connection state")
		case .establishing : return NSLocalizedString("Connecting...", comment: "Connection state")
		case .established  : return NSLocalizedString("Connected", comment: "Connection state")
		default            : return NSLocalizedString("Unknown", comment: "Connection state")
		}
	}
}

extension KotlinByteArray {
	
	static func fromSwiftData(_ data: Data) -> KotlinByteArray {
		
		let kba = KotlinByteArray(size: Int32(data.count))
		for (idx, byte) in data.enumerated() {
			kba.set(index: Int32(idx), value: Int8(bitPattern: byte))
		}
		return kba
	}
	
	func toSwiftData() -> Data {

		let size = self.size
		var data = Data(count: Int(size))
		for idx in 0 ..< size {
			let byte: Int8 = self.get(index: idx)
			data[Int(idx)] = UInt8(bitPattern: byte)
		}
		return data
	}
}

extension ConnectionsMonitor {
	
	var currentValue: Connections {
		return connections.value as! Connections
	}
	
//	var publisher: CurrentValueSubject<Connections, Never> {
//
//		let publisher = CurrentValueSubject<Connections, Never>(currentValue)
//
//		let swiftFlow = SwiftFlow<Connections>(origin: connections)
//		swiftFlow.watch {[weak publisher](connections: Connections?) in
//			publisher?.send(connections!)
//		}
//
//		return publisher
//	}
}

class ObservableConnectionsMonitor: ObservableObject {
	
	@Published var connections: Connections
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let monitor = AppDelegate.get().business.connectionsMonitor
		connections = monitor.currentValue
		
		let swiftFlow = SwiftFlow<Connections>(origin: monitor.connections)
		
		watcher = swiftFlow.watch {[weak self](newConnections: Connections?) in
			self?.connections = newConnections!
		}
	}
	
	deinit {
		watcher?.close()
	}
}

