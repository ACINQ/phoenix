import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "CustomElectrumServerObserver"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class CustomElectrumServerObserver: ObservableObject {
	
	enum Problem {
		case requiresOnionAddress
		case badCertificate
		//
		// reserved for other problems such as:
		// - cannot connect to server
		// - cannot resolve DNS address
	}
	
	@Published var problem: Problem? = nil
	
	private var isTorEnabled: Bool = Biz.business.appConfigurationManager.isTorEnabledValue
	private var electrumConfig: ElectrumConfig = Biz.business.appConfigurationManager.electrumConfigValue
	
	private var lastElectrumClose: Lightning_kmpConnection.CLOSED? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		Biz.business.connectionsManager.connectionsPublisher()
			.sink {[weak self](newConnections: Connections) in
				self?.connectionsChanged(newConnections)
			}.store(in: &cancellables)
		
		Biz.business.appConfigurationManager.isTorEnabledPublisher()
			.sink {[weak self](newValue: Bool) in
				self?.isTorEnabledChanged(newValue)
			}.store(in: &cancellables)
		
		
		Biz.business.appConfigurationManager.electrumConfigPublisher()
			.sink {[weak self](newValue: ElectrumConfig) in
				self?.electrumConfigChanged(newValue)
			}.store(in: &cancellables)
		
		connectionsChanged(Biz.business.connectionsManager.currentValue)
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
	
	private func isTorEnabledChanged(_ newValue: Bool) {
		log.trace("isTorEnabledChanged()")
		
		isTorEnabled = newValue
		checkForProblems()
	}
	
	private func electrumConfigChanged(_ newValue: ElectrumConfig) {
		log.trace("electrumConfigChanged()")
		
		electrumConfig = newValue
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
		
		guard let customConfig = electrumConfig as? ElectrumConfig.Custom else {
			// - Not using a custom electrum server
			problem = nil
			return
		}
		
		if isTorEnabled && !customConfig.server.isOnion && customConfig.requireOnionIfTorEnabled {
			problem = .requiresOnionAddress
			return
		}
		
		if let closed = lastElectrumClose,
		   let closedReason: Lightning_kmpTcpSocketIOException = closed.reason,
		   let nativeSocketException = closedReason.cause as? Lightning_kmpNativeSocketException,
			let tlsError = nativeSocketException.asTLS()
		{
			log.debug("electrumConnection.closed.reason = \(closedReason)")
			
			if tlsError.status == errSSLBadCert {
				problem = .badCertificate
				return
			}
		}
		
		problem = nil
	}
}
