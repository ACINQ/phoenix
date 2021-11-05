import Foundation
import PhoenixShared


class ObservableConnectionsManager: ObservableObject {
	
	@Published var connections: Connections
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let manager = AppDelegate.get().business.connectionsManager
		connections = manager.currentValue
		
		let swiftFlow = SwiftFlow<Connections>(origin: manager.connections)
		
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
		let _watcher = watcher
		DispatchQueue.main.async {
			_watcher?.close()
		}
	}
}
