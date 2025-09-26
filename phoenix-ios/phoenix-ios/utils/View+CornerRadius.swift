import SwiftUI

extension View {
	
	/// Allows you to specify which corners to round
	///
	func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
		clipShape(RoundedCorner(radius: radius, corners: corners))
	}
}
