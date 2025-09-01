import SwiftUI

extension ColorScheme {
	
	var opposite: ColorScheme {
		switch self {
			case .light : return .dark
			default     : return .light
		}
	}
}
