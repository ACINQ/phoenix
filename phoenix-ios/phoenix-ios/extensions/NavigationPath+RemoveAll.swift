import SwiftUI

extension NavigationPath {
	
	mutating func removeAll() {
		if self.count > 0 {
			self.removeLast(self.count)
		}
	}
}
