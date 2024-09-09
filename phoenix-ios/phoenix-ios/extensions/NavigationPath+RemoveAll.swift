import SwiftUI

extension NavigationPath {
	
	mutating func removeAll() {
		self.removeLast(self.count)
	}
}
