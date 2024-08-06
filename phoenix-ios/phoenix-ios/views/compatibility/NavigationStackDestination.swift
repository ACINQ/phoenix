import SwiftUI

struct NavigationStackDestinationIsPresented<V>: ViewModifier where V: View {
	
	let isPresented: Binding<Bool>
	let destination: () -> V
	
	init(isPresented: Binding<Bool>, destination: @escaping () -> V) {
		self.isPresented = isPresented
		self.destination = destination
	}
	
	@ViewBuilder
	func body(content: Content) -> some View {
		content
			.navigationDestination(isPresented: isPresented, destination: destination)
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
		content
			.navigationDestination(for: dataType, destination: destination)
	}
}

extension View {
	
	/// On iOS 16 only (not on 17 or later), will add the following:
	/// ```
	/// someView
	///   .navigationDestination(isPresented:destination:)
	/// ```
	///
	/// In iOS 16, Apple introduced `NavigationStack`, along with `NavigationLink(value:label:)`
	/// paired with `.navigationDestination(for:destination:)`.
	/// However, it's **completely broken** in iOS 16, and only works properly in iOS 17.
	/// So we still have to use the old navigation system in iOS 16.
	///
	func navigationStackDestination<V>(
		isPresented: Binding<Bool>,
		destination: @escaping () -> V
	) -> some View where V: View {
		modifier(_navigationStackDestinationIsPresented(isPresented: isPresented, destination: destination))
	}
	
	fileprivate func _navigationStackDestinationIsPresented<V>(
		isPresented: Binding<Bool>,
		destination: @escaping () -> V
	) -> some ViewModifier where V: View {
		if #unavailable(iOS 17) {
			return NavigationStackDestinationIsPresented(isPresented: isPresented, destination: destination)
		} else {
			return EmptyModifier()
		}
	}
	
	/// On iOS 17 and later, will add the following:
	/// ```
	/// someView
	///   .navigationDestination(for:destination:)
	/// ```
	///
	/// In iOS 16, Apple introduced `NavigationStack`, along with `NavigationLink(value:label:)`
	/// paired with `.navigationDestination(for:destination:)`.
	/// However, it's **completely broken** in iOS 16, and only works properly in iOS 17.
	/// So we still have to use the old navigation system in iOS 16.
	///
	func navigationStackDestination<D, C>(
		for dataType: D.Type,
		destination: @escaping (D) -> C
	) -> some View where D: Hashable, C: View {
		modifier(_navigationStackDestinationForType(for: dataType, destination: destination))
	}
	
	fileprivate func _navigationStackDestinationForType<D, C>(
		for dataType: D.Type,
		destination: @escaping (D) -> C
	) -> some ViewModifier where D: Hashable, C: View {
		if #available(iOS 17, *) {
			return NavigationStackDestinationForType(dataType: dataType, destination: destination)
		} else {
			return EmptyModifier()
		}
	}
}

