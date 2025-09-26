import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "ServerMessageMonitor"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ServerMessage {
	let message: String
	let index: Int
}

class ServerMessageMonitor: ObservableObject {
	
	@Published private var readIndex: Int? = nil
	@Published private var walletNotice: WalletNotice? = nil
	
	@Published public var serverMessage: ServerMessage? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		
		Prefs.current.serverMessageReadIndexPublisher
			.sink {[weak self] (value: Int?) in
				self?.readIndexDidChange(value)
			}
			.store(in: &cancellables)
		
		Biz.business.appConfigurationManager.walletNoticePublisher()
			.sink {[weak self] (notice: WalletNotice) in
				self?.noticeDidChange(notice)
			}
			.store(in: &cancellables)
	}
	
	private func readIndexDidChange(_ readIndex: Int?) {
		log.trace("readIndexDidChange()")
		
		self.readIndex = readIndex
		updateServerMessage()
	}
	
	private func noticeDidChange(_ walletNotice: WalletNotice?) {
		log.trace("noticeDidChange()")
		
		self.walletNotice = walletNotice
		updateServerMessage()
	}
	
	private func updateServerMessage() {
		log.trace("updateServerMessage()")
		
		var newServerMessage: ServerMessage? = nil
		if let walletNotice {
			
			var isRead = false
			if let readIndex {
				isRead = readIndex >= Int(walletNotice.index)
			}
			
			if !isRead {
				newServerMessage = ServerMessage(
					message: walletNotice.message,
					index: Int(walletNotice.index)
				)
			}
		}
		
		self.serverMessage = newServerMessage
	}
}
