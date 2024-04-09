import Foundation
import Combine

extension Task {
	func autoCancellable() -> AnyCancellable {
		return AnyCancellable({
			self.cancel()
		})
	}
}
