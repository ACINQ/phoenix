import Foundation
import PhoenixShared


extension KotlinBase {
	
	static let queue = DispatchQueue.init(label: "KotlinBase-AssociatedObject")
	
	func executeOnce<T>(storageKey key: UnsafeRawPointer, block: () -> T) -> T {
		KotlinBase.queue.sync {
			if let existingValue = objc_getAssociatedObject(self, key) as? T {
				return existingValue
			} else {
				let newValue = block()
				
				objc_setAssociatedObject(self, key, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
				return newValue
			}
		}
	}
}
