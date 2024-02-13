import Foundation

fileprivate let filename = "XpcManager"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

typealias XpcListener = () -> Void

/**
 * What happens if multiple push notifications arrive ?
 *
 * iOS will launch the notification-service-extension upon receiving the first push notification.
 * Subsequent push notifications are queued by the OS. After the app extension finishes processing
 * the first notification (by invoking the `contentHandler`), then iOS will:
 *
 * - display the first push notification
 * - dealloc the `UNNotificationServiceExtension`
 * - Initialize a new `UNNotificationServiceExtension` instance
 * - And invoke it's `didReceive(_:)` function with the next item in the queue
 *
 * Note that it does **NOT** create a new app extension process.
 * It re-uses the existing process, and launches a new `UNNotificationServiceExtension` within it.
 *
 * This means that the following instances are recycled (continue existing in memory):
 * - PhoenixManager.shared
 * - XpcManager.shared
 */
class XpcManager {
	
	public static let shared = XpcManager()
	
	private var mainAppIsRunning: Bool = false
	
	private var queue = DispatchQueue(label: "XpcManager")
	private var listener: XpcListener? = nil
	
	private init() {
		log.trace("init()")
		
		CrossProcessCommunication.shared.start(actor: .notifySrvExt) {[weak self](msg: XpcMessage) in
			self?.didReceiveXpcMessage(msg)
		}
	}
	
	private func didReceiveXpcMessage(_ msg: XpcMessage) {
		log.trace("didReceiveXpcMessage()")
		assertMainThread()
		
		// Receiving a message means the main phoenix app is running.
		queue.async { [self] in
			mainAppIsRunning = (msg == .available)
			if mainAppIsRunning, let serviceListener = listener {
				DispatchQueue.main.async {
					serviceListener()
				}
			}
		}
	}
	
	public func register(mainAppIsRunning newListener: @escaping XpcListener) {
		log.trace("register(mainAppIsRunning:)")
		
		queue.async { [self] in
			if listener == nil {
				listener = newListener
				if mainAppIsRunning {
					DispatchQueue.main.async {
						newListener()
					}
				}
			}
		}
	}
	
	public func unregister() {
		log.trace("unregister()")
		
		queue.async { [self] in
			listener = nil
		}
	}
}
