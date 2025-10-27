import Foundation

struct AppIdentifier {
	
	enum AppId {
		case foreground
		case background
	}
	
	static var isForeground: Bool {
		return self.current == .foreground
	}
	
	static var isBackground: Bool {
		return self.current == .background
	}
}
