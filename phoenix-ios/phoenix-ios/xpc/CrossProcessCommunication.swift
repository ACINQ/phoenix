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

enum XpcActor {
	case mainApp
	case notifySrvExt
}

enum XpcMessage {
	case available
	case unavailable
}

fileprivate let msgPing_mainApp: Int32 = 1
fileprivate let msgPong_mainApp: Int32 = 2
fileprivate let msgUnavailable_mainApp: Int32 = 3

fileprivate let msgPing_notifySrvExt: Int32 = 4
fileprivate let msgPong_notifySrvExt: Int32 = 5
fileprivate let msgUnavailable_notifySrvExt: Int32 = 6


class CrossProcessCommunication {
	
	public static let shared = CrossProcessCommunication()
	
	private let queue = DispatchQueue(label: "CrossProcessCommunication")
	private let channelPrefix = "co.acinq.phoenix"
	private let groupIdentifier = "group.co.acinq.phoenix"
	
	private var actor: XpcActor? = nil
	private var receivedMessage: ((XpcMessage) -> Void)? = nil
	private var channel: String? = nil
	
	private var notifyToken: Int32 = NOTIFY_TOKEN_INVALID
	private var suspendCount: Int32 = 0
	
	/// Must use static `shared` instance
	private init() {}
	
	public func start(actor: XpcActor, receivedMessage: @escaping (XpcMessage) -> Void) {
		log.trace("start()")
		
		queue.async {
			self.actor = actor
			self.receivedMessage = receivedMessage
		}
		DispatchQueue.global(qos: .utility).async {
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
		
		// `NSFileCoordinator.coordinate()`:
		// This method executes **synchronously**,
		// blocking the current thread until the reader/write block finishes executing.
		//
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
		queue.async {
			self.register(channelID)
		}
	}
	
	private func register(_ channelID: String) {
		log.trace("register()")
		
		guard !notify_is_valid_token(notifyToken) else {
			log.debug("ignoring: channel is already registered")
			return
		}
		
		let channel = "\(channelPrefix).\(channelID)"
		self.channel = channel
		
		notify_register_dispatch(
			/* name      :*/ (channel as NSString).utf8String,
			/* out_token :*/ &notifyToken,
			/* queue     :*/ queue
		) {[weak self](token: Int32) in
			
			guard let self = self else {
				return
			}
			let actor = self.actor
			
			var state: UInt64 = 0
			notify_get_state(token, &state)
			
			// Convert from UInt64 to Int32 and extract proper sign (positive/negative)
			let signal = Int32(truncatingIfNeeded: Int64(bitPattern: state))
			
			if actor == .mainApp {
				
				if signal == msgPing_mainApp ||
				   signal == msgPong_mainApp ||
				   signal == msgUnavailable_mainApp {
					
					log.debug("ignorning own message")
					
				} else if signal == msgPing_notifySrvExt {
					
					log.debug("received message: ping (from notifySrvExt)")
					self.notifyReceivedMessage(.available)
					self.sendMessage(msgPong_mainApp)
					
				} else if signal == msgPong_notifySrvExt {
					
					log.debug("received message: pong (from notifySrvExt)")
					self.notifyReceivedMessage(.available)
					
				} else if signal == msgUnavailable_notifySrvExt {
					
					log.debug("received message: unavailable (from notifySrvExt)")
					self.notifyReceivedMessage(.unavailable)
				}
				
			} else if actor == .notifySrvExt {
				
				if signal == msgPing_notifySrvExt ||
				   signal == msgPong_notifySrvExt ||
				   signal == msgUnavailable_notifySrvExt {
					
					log.debug("ignorning own message")
					
				} else if signal == msgPing_mainApp {
					
					log.debug("received message: ping (from mainApp)")
					self.notifyReceivedMessage(.available)
					self.sendMessage(msgPong_notifySrvExt)
					
				} else if signal == msgPong_mainApp {
					
					log.debug("received message: pong (from mainApp)")
					self.notifyReceivedMessage(.available)
					
				} else if signal == msgUnavailable_mainApp {
					
					log.debug("received message: unavailable (from mainApp)")
					self.notifyReceivedMessage(.unavailable)
				}
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
				
				if actor == .mainApp {
					sendMessage(msgPing_mainApp)
				} else {
					sendMessage(msgPing_notifySrvExt)
				}
			}
		}
	}
	
	private func sendMessage(_ msg: Int32) {
		
		switch msg {
			case msgPing_mainApp             : log.trace("sendMessage(ping_mainApp)")
			case msgPong_mainApp             : log.trace("sendMessage(pong_mainApp)")
			case msgUnavailable_mainApp      : log.trace("sendMessage(unavailable_mainApp)")
			case msgPing_notifySrvExt        : log.trace("sendMessage(ping_notifySrvExt)")
			case msgPong_notifySrvExt        : log.trace("sendMessage(pong_notifySrvExt)")
			case msgUnavailable_notifySrvExt : log.trace("sendMessage(unavailable_notifySrvExt)")
			default                          : log.trace("sendMessage(unknown)")
		}
		
		guard notify_is_valid_token(notifyToken), let channel = channel else {
			return
		}
		
		// Convert Int32 into UInt64 without losing sign
		let state = UInt64(UInt32(bitPattern: msg))
		
		notify_set_state(notifyToken, state)
		notify_post((channel as NSString).utf8String)
	}
	
	private func notifyReceivedMessage(_ msg: XpcMessage) {
		log.trace("notifyReceivedMessage()")
		
		if let receivedMessage {
			DispatchQueue.main.async {
				receivedMessage(msg)
			}
		}
	}
	
	public func suspend() {
		
		queue.async {
			
			if notify_is_valid_token(self.notifyToken) {
				switch self.actor {
					case .mainApp      : self.sendMessage(msgUnavailable_mainApp)
					case .notifySrvExt : self.sendMessage(msgUnavailable_notifySrvExt)
					default            : break
				}
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

