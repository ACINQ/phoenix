import SwiftUI


struct NavigationStackDestination<V>: ViewModifier where V: View {
	
	let isPresented: Binding<Bool>
	let destination: () -> V
	
	init(isPresented: Binding<Bool>, destination: @escaping () -> V) {
		self.isPresented = isPresented
		self.destination = destination
	}
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.navigationDestination(isPresented: isPresented, destination: destination)
		} else {
			content
		}
	}
}

struct NavigationStackDestinationForType<D, C>: ViewModifier where D: Hashable, C: View {
	
	let dataType: D.Type
	let destination: (D) -> C
	
	init(dataType: D.Type, destination: @escaping (D) -> C) {
		self.dataType = dataType
		self.destination = destination
	}
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 16.0, *) {
			content
				.navigationDestination(for: dataType, destination: destination)
		} else {
			content
		}
	}
}

extension View {
	
	/// Backwards compatibility for:
	/// ```
	/// someView
	///   .navigationDestination(isPresented:destination:)
	/// ```
	///
	/// Since the above can only be called on iOS 16, you can instead use:
	/// ```
	/// someView
	///   .navigationStackDestination(isPresented:destination:)
	/// ```
	/// which will take effect on iOS 16+, and be ignored on earlier versions.
	///
	func navigationStackDestination<V>(
		isPresented: Binding<Bool>,
		destination: @escaping () -> V
	) -> some View where V: View {
		ModifiedContent(
			content: self,
			modifier: NavigationStackDestination(isPresented: isPresented, destination: destination)
		)
	}
	
	/// Backwards compatibility for:
	/// ```
	/// someView
	///   .navigationDestination(for:destination:)
	/// ```
	///
	/// Since the above can only be called on iOS 16, you can instead use:
	/// ```
	/// someView
	///   .navigationStackDestination(for:destination:)
	/// ```
	/// which will take effect on iOS 16+, and be ignored on earlier versions.
	///
	func navigationStackDestination<D, C>(
		for dataType: D.Type,
		destination: @escaping (D) -> C)
	-> some View where D: Hashable, C: View {
		ModifiedContent(
			content: self,
			modifier: NavigationStackDestinationForType(dataType: dataType, destination: destination)
		)
	}
}
