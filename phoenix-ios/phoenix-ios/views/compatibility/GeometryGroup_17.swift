/// Good description of `.geometryGroup()`
/// https://fatbobman.com/en/posts/mastring-geometrygroup/

import SwiftUI

struct GeometryGroupViewModifier: ViewModifier {
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 17, *) {
			content.geometryGroup()
		} else {
			content
		}
	}
}

extension View {
	
	func _geometryGroup() -> some View {
		modifier(GeometryGroupViewModifier())
	}
}
