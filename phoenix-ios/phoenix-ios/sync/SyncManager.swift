import Foundation
import Network
import Combine
import CloudKit
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SyncManager"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

/// Common code utilized by both:
/// - SyncSeedManager
/// - SyncTxManager
///
class SyncManager {
	
	let syncSeedManager: SyncSeedManager
	let syncTxManager: SyncTxManager
	
	private let networkMonitor = NWPathMonitor()
	
	private var cancellables = Set<AnyCancellable>()
	
	init(chain: Chain, mnemonics: [String], cloudKey: Bitcoin_kmpByteVector32, encryptedNodeId: String) {
		
		syncSeedManager = SyncSeedManager(
			chain: chain,
			mnemonics: mnemonics,
			encryptedNodeId: encryptedNodeId
		)
		syncTxManager = SyncTxManager(
			cloudKey: cloudKey,
			encryptedNodeId: encryptedNodeId
		)
		
		startNetworkMonitor()
		startCloudStatusMonitor()
		checkForCloudCredentials()
	}
	
	private func startNetworkMonitor() {
		
		log.trace("startNetworkMonitor()")
		
		networkMonitor.pathUpdateHandler = {[weak self](path: NWPath) -> Void in
			
			let hasInternet: Bool
			switch path.status {
				case .satisfied:
					log.debug("nwpath.status.satisfied")
					hasInternet = true
					
				case .unsatisfied:
					log.debug("nwpath.status.unsatisfied")
					hasInternet = false
					
				case .requiresConnection:
					log.debug("nwpath.status.requiresConnection")
					hasInternet = true
					
				@unknown default:
					log.debug("nwpath.status.unknown")
					hasInternet = false
			}
			
			self?.syncSeedManager.networkStatusChanged(hasInternet: hasInternet)
			self?.syncTxManager.networkStatusChanged(hasInternet: hasInternet)
		}
		
		networkMonitor.start(queue: DispatchQueue.main)
	}
	
	private func startCloudStatusMonitor() {
		log.trace("startCloudStatusMonitor()")
		
		NotificationCenter.default
			.publisher(for: Notification.Name.CKAccountChanged)
			.sink {[weak self] _ in
			
				log.debug("CKAccountChanged")
				DispatchQueue.main.async {
					self?.checkForCloudCredentials()
				}
			
		}.store(in: &cancellables)
	}
	
	func checkForCloudCredentials() {
		log.trace("checkForCloudCredentials")
		
		CKContainer.default().accountStatus {[weak self](accountStatus: CKAccountStatus, error: Error?) in
			
			if let error = error {
				log.warning("Error fetching CKAccountStatus: \(String(describing: error))")
			}
			
			var hasCloudCredentials: Bool
			switch accountStatus {
			case .available:
				log.debug("CKAccountStatus.available")
				hasCloudCredentials = true
				
			case .noAccount:
				log.debug("CKAccountStatus.noAccount")
				hasCloudCredentials = false
				
			case .restricted:
				log.debug("CKAccountStatus.restricted")
				hasCloudCredentials = false
				
			case .couldNotDetermine:
				log.debug("CKAccountStatus.couldNotDetermine")
				hasCloudCredentials = false
				
			case .temporarilyUnavailable:
				log.debug("CKAccountStatus.temporarilyUnavailable")
				hasCloudCredentials = false
			
			@unknown default:
				log.debug("CKAccountStatus.unknown")
				hasCloudCredentials = false
			}
			
			self?.syncSeedManager.cloudCredentialsChanged(hasCloudCredentials: hasCloudCredentials)
			self?.syncTxManager.cloudCredentialsChanged(hasCloudCredentials: hasCloudCredentials)
		}
	}
}

protocol SyncManagerProtcol {
	
	func networkStatusChanged(hasInternet: Bool)
	func cloudCredentialsChanged(hasCloudCredentials: Bool)
}
