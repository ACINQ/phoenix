import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "MultiBusinessManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// Short-hand for `MultiBusinessManager.shared`
///
let MBiz = MultiBusinessManager.shared

/// Short-hand for `MBiz.current`
///
var Biz: BusinessManager {
	return MBiz.currentBiz
}

class MultiBusinessManager {
	
	/// Singleton instance
	public static let shared = MultiBusinessManager()
	
	public let currentBizPublisher: CurrentValueSubject<BusinessManager, Never>
	public var currentBiz: BusinessManager {
		return currentBizPublisher.value
	}
	
	public let bgBizListPublisher: CurrentValueSubject<[BusinessManager], Never>
	public var bgBizList: [BusinessManager] {
		return bgBizListPublisher.value
	}
	
	private var bgBizListTimers: [String: Int] = [:]
	
	private init() { // must use shared instance
		log.trace(#function)
		
		let biz = BusinessManager()
		currentBizPublisher = CurrentValueSubject(biz)
		bgBizListPublisher = CurrentValueSubject([])
	}
	
	func resetCurrent() {
		log.trace(#function)
		
		currentBiz.stop()
		let newBiz = BusinessManager()
		currentBizPublisher.send(newBiz)
	}
	
	func launchBackgroundBiz(_ push: PushNotification) {
		log.trace(#function)
		
		guard let nodeId = push.nodeId else {
			log.warning("launchBackgroundBiz(): invalid parameter: push.nodeId == nil")
			return
		}
		guard let chain = push.chain else {
			log.warning("launchBackgroundBiz(): invalid parameter: push.chain == nil")
			return
		}
		guard let nodeIdHash = push.nodeIdHash else {
			log.warning("launchBackgroundBiz(): invalid parameter: push.nodeIdHash == nil")
			return
		}
		
		// Step 1:
		// Check to see if the target is already loaded.
		
		if let walletInfo = currentBiz.walletInfo, walletInfo.nodeIdString == nodeId {
			log.debug("launchBackgroundBiz(): already loaded (current)")
			return
		}
		
		for biz in bgBizList {
			if let walletInfo = biz.walletInfo, walletInfo.nodeIdString == nodeId {
				log.debug("launchBackgroundBiz(): already loaded (background)")
				startOrResetTimerForBiz(biz)
				return
			}
		}
		
		// Step 2:
		// Lookup the key information for the target nodeId.
		
		guard let securityFile = SecurityFileManager.shared.currentSecurityFile() else {
			log.warning("launchBackgroundBiz(): SecurityFile.current(): nil found")
			return
		}
		guard case .v1(let v1) = securityFile else {
			log.warning("launchBackgroundBiz(): SecurityFile.current(): v0 found")
			return
		}
		
		let id = SecurityFile.V1.KeyComponents(chain: chain, nodeIdHash: nodeIdHash)
		guard let sealedBox = v1.getWallet(id)?.keychain else {
			log.warning("launchBackgroundBiz(): SecurityFile.current().getWallet(): nil found")
			return
		}
		
		let keychainResult = SharedSecurity.shared.readKeychainEntry(id.standardKeyId, sealedBox)
		guard case .success(let cleartextData) = keychainResult else {
			log.warning("launchBackgroundBiz(): readKeychainEntry(): failed")
			return
		}

		let decodeResult = SharedSecurity.shared.decodeRecoveryPhrase(cleartextData)
		guard case .success(let recoveryPhrase) = decodeResult else {
			log.warning("launchBackgroundBiz(): decodeRecoveryPhrase(): failed")
			return
		}
		guard recoveryPhrase.language != nil else {
			log.warning("launchBackgroundBiz(): recoveryPhrase.language == nil")
			return
		}
		
		// Step 3:
		// Start the new business instance.
		// This allows is to automatically connect and receive the incoming payment.
		
		let newBiz = BusinessManager()
		let newBgBizList = bgBizList + [newBiz]
		bgBizListPublisher.send(newBgBizList)
		
		newBiz.loadWallet(trigger: .walletUnlock, recoveryPhrase: recoveryPhrase)
		
		// Step 4:
		// Start a timer to shutdown the background business.
		
		startOrResetTimerForBiz(newBiz)
	}
	
	private func stopBackgroundBiz(_ biz: BusinessManager) {
		log.trace(#function)
		
		if bgBizList.contains(where: { $0 === biz }) {
			biz.stop()
			
			let newBgBizList = bgBizList.filter { $0 !== biz }
			bgBizListPublisher.send(newBgBizList)
		}
	}
	
	private func startOrResetTimerForBiz(_ biz: BusinessManager) {
		log.trace(#function)
		
		let bizId = ptrString(biz)
		
		let index: Int
		if let existingIndex = bgBizListTimers[bizId] {
			index = existingIndex + 1
		} else {
			index = 0
		}
		bgBizListTimers[bizId] = index
		
		Task { @MainActor in
			try await Task.sleep(seconds: 60)
			
			if let currentIndex = bgBizListTimers[bizId], currentIndex == index {
				self.stopBackgroundBiz(biz)
			}
		}
	}
	
	private func ptrString(_ biz: BusinessManager) -> String {
		return Unmanaged.passUnretained(biz).toOpaque().debugDescription
	}
}
