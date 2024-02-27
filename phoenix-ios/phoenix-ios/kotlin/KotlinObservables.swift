import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "KotlinObservables"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class ObservableConnectionsMonitor: ObservableObject {
	
	@Published var connections: Connections
	@Published var disconnectedAt: Date? = nil
	@Published var connectingAt: Date? = nil
	
	private var cancellables = Array<AnyCancellable>()
	
	init() {
		let connectionsManager = Biz.business.connectionsManager
		let currentConnections = connectionsManager.currentValue
		
		connections = currentConnections
		connectionsChanged(currentConnections)
		
		cancellables.append(
			Task { @MainActor [weak self] in
				for await newConnections in connectionsManager.connectionsSequence() {
					self?.connectionsChanged(newConnections)
				}
			}.autoCancellable()
		)
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

// MARK: -

class CustomElectrumServerObserver: ObservableObject {
	
	enum Problem {
		case badCertificate
		//
		// reserved for other problems such as:
		// - cannot connect to server
		// - cannot resolve DNS address
	}
	
	@Published var problem: Problem? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	private var lastElectrumClose: Lightning_kmpConnection.CLOSED? = nil
	private var electrumConfig: ElectrumConfigPrefs?
	
	init() {
		let connectionsManager = Biz.business.connectionsManager
		
		cancellables.insert(
			Task { @MainActor [weak self] in
				for await newConnections in connectionsManager.connectionsSequence() {
					self?.connectionsChanged(newConnections)
				}
			}.autoCancellable()
		)
		
		electrumConfig = GroupPrefs.shared.electrumConfig
		GroupPrefs.shared.electrumConfigPublisher.sink {[weak self](config: ElectrumConfigPrefs?) in
			self?.configChanged(config)
			
		}.store(in: &cancellables)
		
		connectionsChanged(connectionsManager.currentValue)
	}
	
	private func connectionsChanged(_ connections: Connections) {
		let electrumConnection = connections.electrum
		
		if let close = electrumConnection as? Lightning_kmpConnection.CLOSED {
			lastElectrumClose = close
			checkForProblems()
			
		} else if electrumConnection is Lightning_kmpConnection.ESTABLISHED {
			lastElectrumClose = nil
			checkForProblems()
			
		} else if electrumConnection is Lightning_kmpConnection.ESTABLISHING {
			// waiting for either success or failure...
		}
	}
	
	private func configChanged(_ config: ElectrumConfigPrefs?) {
		
		electrumConfig = config
		checkForProblems()
	}
	
	private func checkForProblems() {
		
		// Lots of wrapping & unwrapping here:
		//
		// The original error comes from PhoenixCrypto (written in Swift).
		// The original error is of type `NWError` - a Swift enum, which cannot be sent to Kotlin.
		// So `NWError` is converted into type `NativeSocketError` (NSObject).
		// Kotlin wants to convert this into the cross-platform generic type `TcpSocket.IOException`.
		// So the `NativeSocketError` is converted into `NativeSocketException`,
		// which can be put inside `TcpSocket.IOException`.
		
		guard
			let _ = electrumConfig,
			let closed = lastElectrumClose,
			let closedReason: Lightning_kmpTcpSocketIOException = closed.reason,
			let nativeSocketException = closedReason.cause as? Lightning_kmpNativeSocketException
		else {
			// - Not using a custom electrum server
			// - Electrum connection isn't closed
			// - Electrum connection isn't closed abnormally
			// - Cannot extract exception info
			problem = nil
			return
		}
		
		log.debug("electrumConnection.closed.reason = \(closedReason)")
		
		if let tlsError = nativeSocketException.asTLS() {
			
			if tlsError.status == errSSLBadCert {
				problem = .badCertificate
			} else {
				problem = nil
			}
			
		} else {
			problem = nil
		}
	}
}
