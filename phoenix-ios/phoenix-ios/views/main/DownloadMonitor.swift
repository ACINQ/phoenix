import SwiftUI
import Combine

fileprivate let filename = "DownloadMonitor"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif


class DownloadMonitor: ObservableObject {
	
	@Published var isDownloading: Bool = false
	@Published var oldestCompletedDownload: Date? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		if let syncManager = Biz.syncManager {
			let syncStatePublisher = syncManager.syncBackupManager.statePublisher
			
			syncStatePublisher.sink {[weak self](state: SyncBackupManager_State) in
				self?.update(state)
			}
			.store(in: &cancellables)
		}
	}
	
	private func update(_ state: SyncBackupManager_State) {
		log.trace("update()")
		
		if case .downloading(let details) = state {
			log.trace("isDownloading = true")
			isDownloading = true
			
			subscribe(details)
		} else {
			log.trace("isDownloading = false")
			isDownloading = false
		}
	}
	
	private func subscribe(_ details: SyncBackupManager_State_Downloading) {
		log.trace("subscribe()")
		
		details.$payments_oldestCompletedDownload.sink {[weak self](date: Date?) in
			log.trace("oldestCompletedDownload = \(date?.description ?? "nil")")
			self?.oldestCompletedDownload = date
		}
		.store(in: &cancellables)
	}
}
