import Foundation

func assertMainThread() {
	assert(Thread.isMainThread, "Improper thread: expected main thread; Thread-unsafe code ahead")
}
