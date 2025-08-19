import Foundation
import Combine

extension Task {
	func store(in set: inout Set<AnyCancellable>) {
		set.insert(AnyCancellable(cancel))
	}
}
