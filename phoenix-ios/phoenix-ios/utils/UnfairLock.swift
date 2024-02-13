/**
 * Credit:
 * - http://www.russbishop.net/the-law
 * - https://swiftrocks.com/thread-safety-in-swift
 *
 * Note: Once you drop support for iOS 15, you can switch to:
 * https://developer.apple.com/documentation/os/osallocatedunfairlock
 */

import Foundation

final class UnfairLock {
	private var _lock: UnsafeMutablePointer<os_unfair_lock>

	init() {
		_lock = UnsafeMutablePointer<os_unfair_lock>.allocate(capacity: 1)
		_lock.initialize(to: os_unfair_lock())
	}

	deinit {
		_lock.deallocate()
	}
	
	func locked<ReturnValue>(_ f: () throws -> ReturnValue) rethrows -> ReturnValue {
		os_unfair_lock_lock(_lock)
		defer { os_unfair_lock_unlock(_lock) }
		return try f()
	}
}
