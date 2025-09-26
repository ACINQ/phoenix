import SwiftUI

extension View {
	
	/// Helper to create `AnyView` from view
	var anyView: AnyView {
		AnyView(self)
	}
}
