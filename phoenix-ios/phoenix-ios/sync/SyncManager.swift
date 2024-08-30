import Foundation
import Network
import Combine
import CloudKit
import PhoenixShared

fileprivate let filename = "SyncManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// Common code utilized by both:
/// - SyncSeedManager
/// - SyncBackupManager
///
class SyncManager {
	
	let syncSeedManager: SyncSeedManager
	let syncBackupManager: SyncBackupManager
	
	private let networkMonitor = NWPathMonitor()
	
	private var cancellables = Set<AnyCancellable>()
	
	init(
		chain: Bitcoin_kmpChain,
		recoveryPhrase: RecoveryPhrase,
		cloudKey: Bitcoin_kmpByteVector32,
		encryptedNodeId: String
	) {
		
		syncSeedManager = SyncSeedManager(
			chain: chain,
			recoveryPhrase: recoveryPhrase,
			encryptedNodeId: encryptedNodeId
		)
		syncBackupManager = SyncBackupManager(
			cloudKey: cloudKey,
			encryptedNodeId: encryptedNodeId
		)
		
		syncSeedManager.parent = self
		syncBackupManager.parent = self
		
		startNetworkMonitor()
		startCloudStatusMonitor()
		checkForCloudCredentials()
	}
	
	private func startNetworkMonitor() {
		
		log.trace("startNetworkMonitor()")
		
		networkMonitor.pathUpdateHandler = {[weak self](path: NWPath) -> Void in
			
			guard let self = self else {
				return
			}
			
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
			
			self.syncSeedManager.networkStatusChanged(hasInternet: hasInternet)
			self.syncBackupManager.networkStatusChanged(hasInternet: hasInternet)
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
	
	/// May also be called by `SyncBackupManager` or `SyncSeedManager` if they encounter
	/// errors related to iCloud credential problems.
	///
	func checkForCloudCredentials() {
		log.trace("checkForCloudCredentials()")
		
		CKContainer.default().accountStatus {[weak self](accountStatus: CKAccountStatus, error: Error?) in
			
			guard let self = self else {
				return
			}
			
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
			
			self.syncSeedManager.cloudCredentialsChanged(hasCloudCredentials: hasCloudCredentials)
			self.syncBackupManager.cloudCredentialsChanged(hasCloudCredentials: hasCloudCredentials)
		}
	}
	
	func shutdown() {
		log.trace("shutdown()")
		
		networkMonitor.cancel()
		cancellables.removeAll()
		
		syncSeedManager.shutdown()
		syncBackupManager.shutdown()
	}
}

protocol SyncManagerProtcol {
	
	func networkStatusChanged(hasInternet: Bool)
	func cloudCredentialsChanged(hasCloudCredentials: Bool)
}
