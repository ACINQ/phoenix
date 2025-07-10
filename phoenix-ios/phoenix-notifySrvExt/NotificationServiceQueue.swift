import Foundation
import UserNotifications

class NotificationServiceQueue {
	
	struct Item {
		let identifier: String
		let content: UNNotificationContent
	}
	
	public static let shared = NotificationServiceQueue()
	
	private var queue: [Item] = []
	
	private init() {} // Must use shared instance
	
	// --------------------------------------------------
	// MARK: Public Functions
	// --------------------------------------------------
	
	public func enqueue(identifier: String, content: UNNotificationContent) {
		queue.append(Item(identifier: identifier, content: content))
	}
	
	public func dequeue() -> Item? {
		if queue.isEmpty {
			return nil
		} else {
			return queue.removeFirst()
		}
	}
}
