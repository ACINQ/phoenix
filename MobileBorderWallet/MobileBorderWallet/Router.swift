import SwiftUI

class Router: ObservableObject {

	@Published var navPath: NavigationPath = .init()
	
	func popToRoot() {
		navPath.removeLast(navPath.count)
	}
}
