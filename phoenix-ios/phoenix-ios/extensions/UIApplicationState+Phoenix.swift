import UIKit


extension UIApplication.State: @retroactive CustomStringConvertible {
	
	public var description: String {
		switch self {
			case .inactive   : return "inactive"
			case .active     : return "active"
			case .background : return "background"
			default          : return "unknown"
		}
	}
}
