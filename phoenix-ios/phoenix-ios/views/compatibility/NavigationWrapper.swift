import SwiftUI

struct NavigationWrapper<Content>: View where Content: View {
	@ViewBuilder var content: () -> Content
	
	var body: some View {
		if #available(iOS 16, *) {
			NavigationStack(root: content)
		} else {
			NavigationView(content: content)
				.navigationViewStyle(.stack)
		}
	}
}
