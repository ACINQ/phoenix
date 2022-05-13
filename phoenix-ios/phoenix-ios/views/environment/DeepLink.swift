import Foundation

enum DeepLink: String, Equatable {
	case backup
	case electrum
}

class DeepLinkManager: ObservableObject {
	
	@Published var deepLink: DeepLink? = nil
	private var deepLinkIdx = 0
	
	func broadcast(_ value: DeepLink?) {
		self.deepLinkIdx += 1
		self.deepLink = value
		
		if value != nil {
			
			// The value should get unset when the UI reaches the final destination.
			// But if anything prevents that for any reason, this acts as a backup.
			
			let idx = self.deepLinkIdx
			DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
				if self.deepLinkIdx == idx {
					self.deepLink = nil
				}
			}
		}
	}
}
