import Foundation
import PhoenixShared
import Combine
import CryptoKit

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

extension Bitcoin_kmpByteVector32 {
	
	static func random() -> Bitcoin_kmpByteVector32 {
		
		let key = SymmetricKey(size: .bits256) // 256 / 8 = 32
		
		let data = key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		
		return Bitcoin_kmpByteVector32(bytes: KotlinByteArray.fromSwiftData(data))
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
	
	#if DEBUG // For debugging UI: Force connection state
	init(fakeConnections: Connections) {
		self.connections = fakeConnections
	}
	#endif
	
	deinit {
		watcher?.close()
	}
}

class ObservableLastIncomingPayment: ObservableObject {
	
	@Published var value: Eclair_kmpWalletPayment? = nil
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let incomingPaymentFlow = AppDelegate.get().business.incomingPaymentFlow()
		value = incomingPaymentFlow.value as? Eclair_kmpWalletPayment
		
		let swiftFlow = SwiftFlow<Eclair_kmpWalletPayment>(origin: incomingPaymentFlow)
		swiftFlow.watch {[weak self](payment: Eclair_kmpWalletPayment?) in
			self?.value = payment
		}
	}
	
	deinit {
		watcher?.close()
	}
}

