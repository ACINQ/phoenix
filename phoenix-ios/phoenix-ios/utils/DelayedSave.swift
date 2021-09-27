import UIKit
import Combine

class DelayedSave {
	
	private var cancellables = Set<AnyCancellable>()
	private var timer: Timer? = nil
	private var needsSave: Bool = false
	private var saveAction: (() -> Void)? = nil
	
	init() {
		
		let nc = NotificationCenter.default
		nc.publisher(for: UIApplication.willResignActiveNotification).sink {[weak self] _ in
			self?.saveIfNeeded()
		}.store(in: &cancellables)
	}
	
	deinit {
		timer?.invalidate()
	}
	
	func save(withDelay delay: TimeInterval, action: @escaping () -> Void) {
		
		assert(Thread.isMainThread, "This function is restricted to the main-thread")
		
		needsSave = true
		saveAction = action
		
		timer?.invalidate()
		timer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false, block: {[weak self] _ in
			self?.saveIfNeeded()
		})
	}
	
	func saveIfNeeded() {
		if needsSave {
			needsSave = false
			
			timer?.invalidate()
			timer = nil
			
			saveAction?()
			saveAction = nil
		}
	}
}
