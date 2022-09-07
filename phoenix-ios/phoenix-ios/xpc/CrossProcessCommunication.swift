import Foundation
import os.log
import notify

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "XPC"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class CrossProcessCommunication {
	
	public static let shared = CrossProcessCommunication()
	
	private let queue = DispatchQueue(label: "CrossProcessCommunication")
	private let channelPrefix = "co.acinq.phoenix"
	private let groupIdentifier = "group.co.acinq.phoenix"
	
	private let ping: Int32
	private let pong: Int32
	
	private var receivedMessage: (() -> Void)? = nil
	
	private var notifyToken: Int32 = NOTIFY_TOKEN_INVALID
	private var suspendCount: Int32 = 0
	
	/// Must use static `shared` instance
	private init() {
		
		ping = abs(ProcessInfo.processInfo.processIdentifier)
		pong = ping * -1
	}
	
	func start(receivedMessage: (() -> Void)?) {
		
		self.receivedMessage = receivedMessage
		queue.async {
			self.readChannelID()
		}
	}
	
	private func readChannelID() {
		log.trace("readChannelID()")
		
		let fm = FileManager.default
		guard let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier) else {
			fatalError("FileManager returned nil containerUrl !")
		}
		
		let fileURL = groupDir.appendingPathComponent("xpc.id", isDirectory: false)
		let fileCoordinator = NSFileCoordinator()
		
		var uuid = UUID()
		var error: NSError? = nil
		fileCoordinator.coordinate(readingItemAt: fileURL, writingItemAt: fileURL, error: &error) { readURL, writeURL in
			
			var fileContent: String? = nil
			if fm.fileExists(atPath: readURL.path) {
				do {
					fileContent = try String(contentsOf: readURL, encoding: .utf8)
				} catch {
					log.error("Error reading xpc.id: \(String(describing: error))")
				}
			}
			
			// E621E1F8-C36C-495A-93FC-0C247A3E6E5F
			// 123456789012345678901234567890123456
			//          ^         ^         ^
			
			var existingUUID: UUID? = nil
			if let fileContent = fileContent {
				
				let uuidString = String(fileContent.prefix(36))
				existingUUID = UUID(uuidString: uuidString)
			}
			
			if let existingUUID = existingUUID {
				uuid = existingUUID
			} else {
				
				let uuidData = uuid.uuidString.data(using: .utf8)
				do {
					try uuidData?.write(to: writeURL)
				} catch {
					log.error("Error writing xpc.id: \(String(describing: error))")
				}
			}
		}
		
		if let error = error {
			log.error("NSFileCoordinator: error: \(String(describing: error))")
		}
		
		let channelID = uuid.uuidString
		register(channelID: channelID)
	}
	
	private func register(channelID: String) {
		log.trace("register(channelID:)")
		
		guard !notify_is_valid_token(notifyToken) else {
			log.debug("ignoring: channel is already registered")
			return
		}
		
		let channel = "\(channelPrefix).\(channelID)"
		
		notify_register_dispatch(
			/* name      :*/ (channel as NSString).utf8String,
			/* out_token :*/ &notifyToken,
			/* queue     :*/ queue
		) {[weak self](token: Int32) in
			
			guard let self = self else {
				return
			}
			
			var state: UInt64 = 0
			notify_get_state(token, &state)
			
			// Convert from UInt64 to Int32 and extract proper sign (positive/negative)
			let signal = Int32(truncatingIfNeeded: Int64(bitPattern: state))
			
			if (signal == self.ping) || (signal == self.pong) {
				// ignoring signal from self
			} else if signal > 0 {
				log.debug("receivedPing(\(signal))")
				self.notifyReceivedMessage()
				self.sendSignal(self.pong, channel)
			} else {
				log.debug("receivedPong(\(signal))")
				self.notifyReceivedMessage()
			}
		}
		
		if notify_is_valid_token(notifyToken) {
			
			if suspendCount > 0 {
				
				for _ in 0 ..< suspendCount {
					log.debug("notify_suspend()")
					notify_suspend(notifyToken)
				}
				suspendCount = 0
				
			} else {
				sendSignal(self.ping, channel)
			}
		}
	}
	
	private func sendSignal(_ signal: Int32, _ channel: String) {
		log.trace("sendSignal(\(signal))")
		
		guard notify_is_valid_token(notifyToken) else {
			return
		}
		
		// Convert Int32 into UInt64 without losing sign
		let state = UInt64(UInt32(bitPattern: signal))
		
		notify_set_state(notifyToken, state)
		notify_post((channel as NSString).utf8String)
	}
	
	private func notifyReceivedMessage() {
		log.trace("notifyReceivedMessage()")
		
		if let receivedMessage = receivedMessage {
			DispatchQueue.main.async {
				receivedMessage()
			}
		}
	}
	
	public func suspend() {
		
		queue.async {
			
			if notify_is_valid_token(self.notifyToken) {
				log.debug("notify_suspend()")
				notify_suspend(self.notifyToken)
			} else {
				log.debug("suspendCount += 1")
				self.suspendCount += 1
			}
		}
	}
	
	public func resume() {
		
		queue.async {
			
			if notify_is_valid_token(self.notifyToken) {
				log.debug("notify_resume()")
				notify_resume(self.notifyToken)
			} else {
				log.debug("suspendCount -= 1")
				self.suspendCount -= 1
			}
		}
	}
}

