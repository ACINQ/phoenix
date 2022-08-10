/// Credit to this genius with the **wonderful** write-up:
/// https://finestructure.co/blog/2020/1/20/swiftui-equal-widths-view-constraints
///
/// How to use:
///
/// #1: Define the variables.
/// ```
/// enum MyTextWidth: Preference {}
/// let myTextWidthReader = GeometryPreferenceReader(
///     key: AppendValue<MyTextWidth>.self,
///     value: { [$0.size.width] }
/// )
/// @State var myTextWidth: CGFloat? = nil
/// ```
///
/// #2: Read the width/height, and assign the max value:
/// ```
/// VStack {
///   Text(foo.bar)
///       .read(myTextWidthReader)
///       .frame(width: myTextWidth, alignment: .leading)
///   // ...
/// }
/// .assignMaxPreference(for: myTextWidthReader.key, to: $myTextWidth)
/// ```


import SwiftUI

protocol Preference {}

struct GeometryPreferenceReader<K: PreferenceKey, V> where K.Value == V {
	let key: K.Type
	let value: (GeometryProxy) -> V
}

struct AppendValue<T: Preference>: PreferenceKey {
	typealias Value = [CGFloat]
	static var defaultValue: Value { [] }
	static func reduce(value: inout Value, nextValue: () -> Value) {
		value.append(contentsOf: nextValue())
	}
}

extension View {
	func assignMaxPreference<K: PreferenceKey>(
		for key: K.Type,
		to binding: Binding<CGFloat?>
	) -> some View where K.Value == [CGFloat] {

		return self.onPreferenceChange(key.self) { prefs in
			let maxPref = prefs.reduce(0, max)
			if maxPref > 0 {
				// only set value if > 0 to avoid pinning sizes to zero
				binding.wrappedValue = maxPref
			}
		}
	}

	func read<K: PreferenceKey, V>(_ reader: GeometryPreferenceReader<K, V>) -> some View {
		return self.modifier(GeometryPreferenceViewModifier(reader: reader))
	}
}

struct GeometryPreferenceViewModifier<K: PreferenceKey, V>: ViewModifier where K.Value == V {
	let reader: GeometryPreferenceReader<K, V>
	
	func body(content: Content) -> some View {
		content
			.background(GeometryReader { (proxy: GeometryProxy) in
				Color.clear.preference(
					key: reader.key,
					value: reader.value(proxy)
				)
			})
	}
}

// MARK: -

struct TaggedGeometryPreferenceReader<K: PreferenceKey, V> where K.Value == V {
	let key: K.Type
	let value: (GeometryProxy, String) -> V
}

struct TaggedValue: Equatable {
	let width: CGFloat
	let identifier: String
	
	init(_ width: CGFloat, _ identifier: String) {
		self.width = width
		self.identifier = identifier
	}
	
}

struct AppendTaggedValue<T: Preference>: PreferenceKey {
	typealias Value = [TaggedValue]
	static var defaultValue: Value { [] }
	static func reduce(value: inout Value, nextValue: () -> Value) {
		value.append(contentsOf: nextValue())
	}
}

extension View {
	
	func read<K: PreferenceKey, V>(_ reader: TaggedGeometryPreferenceReader<K, V>, identifier: String) -> some View {
		modifier(TaggedGeometryPreferenceViewModifier(reader: reader, identifier: identifier))
	}
}

struct TaggedGeometryPreferenceViewModifier<K: PreferenceKey, V>: ViewModifier where K.Value == V {
	let reader: TaggedGeometryPreferenceReader<K, V>
	let identifier: String
	
	func body(content: Content) -> some View {
		content
			.background(GeometryReader { (proxy: GeometryProxy) in
				Color.clear.preference(key: reader.key, value: reader.value(proxy, identifier))
			})
	}
}
