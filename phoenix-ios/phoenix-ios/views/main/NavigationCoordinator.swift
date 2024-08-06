import SwiftUI

class NavigationCoordinator: ObservableObject {
	@Published var path = NavigationPath()
	
	func popToRootView() {
		path.removeLast(path.count)
	}
}
