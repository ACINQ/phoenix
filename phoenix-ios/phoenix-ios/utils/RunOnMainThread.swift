import Foundation

func runOnMainThread(_ block: @escaping () -> Void) {
	if Thread.isMainThread {
		block()
	} else {
		DispatchQueue.main.async { block() }
	}
}
