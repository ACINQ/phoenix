import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "ObservableConnectionsMonitor"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class ObservableConnectionsMonitor: ObservableObject {
	
	@Published var connections: Connections
	@Published var disconnectedAt: Date? = nil
	@Published var connectingAt: Date? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		let connectionsManager = Biz.business.connectionsManager
		let currentConnections = connectionsManager.currentValue
		
		connections = currentConnections
		connectionsChanged(currentConnections)
		
		connectionsManager.connectionsPublisher().sink {[weak self](newConnections: Connections) in
			self?.connectionsChanged(newConnections)
			
		}.store(in: &cancellables)
	}
	
	#if DEBUG // For debugging UI: Force connection state
	init(fakeConnections: Connections) {
		connections = fakeConnections
	}
	#endif
	
	private func connectionsChanged(_ newConnections: Connections) {
		connections = newConnections
		
		// Connection logic:
		// When the Internet connection is unreliable, we may cycle thru multiple connection attempts:
		// 1) disconnected
		// 2) connecting
		// 3) disconnected
		// 4) connecting
		// ...
		//
		// So we want to record the date of the first disconnection (1).
		// And the date of the first reconnection attempt (2).
		// Only in the event that we connect are the timestamps reset.
		// This allows our UI logic to properly count forward from these timestamps
		
		if newConnections.global.isEstablished() {
			// All connections are established
			if disconnectedAt != nil {
				disconnectedAt = nil
			}
			if connectingAt != nil {
				connectingAt = nil
			}
		} else {
			// One or more connections are disconnected
			if disconnectedAt == nil {
				disconnectedAt = Date()
			}
			if newConnections.oneOrMoreEstablishing() {
				if connectingAt == nil {
					connectingAt = Date()
				}
			}
		}
	}
}
