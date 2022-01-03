import Foundation

enum DeepLink: Equatable {
	case backup
}

class DeepLinkManager: ObservableObject {
	
	@Published var deepLink: DeepLink? = nil
	private var deepLinkIdx = 0
	
	func broadcast(_ value: DeepLink?) {
		self.deepLinkIdx += 1
		let idx = self.deepLinkIdx
		
		self.deepLink = value
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
			if self.deepLinkIdx == idx {
				self.deepLink = nil
			}
		}
	}
}
