import SwiftUI

/// In iOS 16, `NavigationLink(destination:tag:selection:label:) is deprecated`.
///
/// We are told to instead use `NavigationLink(value:label:)` along
/// with `.navigationDestination(for:destination:)`.
/// However, it's **completely broken** in iOS 16, and only works properly in iOS 17.
/// So we still have to use the old navigation system in iOS 16.
///
/// Except we don't want those annoying deprecation warnings everywhere.
/// So we have this hack to silence them.
///
struct NavigationLink_16<V: Hashable, Destination: View, Label: View>: View {
	
	let destination: Destination
	let tag: V
	let selection: Binding<V?>
	let label: () -> Label
	
	@ViewBuilder
	var body: some View {
		AnyView((self as (any NavigationLink_16_ShutUpCompiler)).shutUpCompiler())
	}
	
	@ViewBuilder
	@available(iOS, deprecated: 16.0)
	func shutUpCompiler() -> some View {
		NavigationLink(
			destination: destination,
			tag: tag,
			selection: selection,
			label: label
		)
	}
}

private protocol NavigationLink_16_ShutUpCompiler {
	associatedtype V: View
	func shutUpCompiler() -> V
}
extension NavigationLink_16: NavigationLink_16_ShutUpCompiler {}
