import SwiftUI

// Expands the frame such that the wider of the {width, height}
// becomes the size of the square.
//
// The view's preferred size is first measured before applying the frame modifier.
//
// Exmple:
// ```
// Image(systemName: "bolt.fill")
//   .imageScale(.large)
//   .font(.caption2)
//   .squareFrame()
// ```
//
struct SquareFrameWrapper<Content: View>: View {

	let alignment: Alignment
	let content: () -> Content
	
	enum SquareSize: Preference {}
	let squareSizeReader = GeometryPreferenceReader(
		key: AppendValue<SquareSize>.self,
		value: { [$0.size.width, $0.size.height] }
	)
	@State var squareSize: CGFloat? = nil
	
	@ViewBuilder
	var body: some View {
		
		content()
			.read(squareSizeReader)
			.frame(width: squareSize, height: squareSize, alignment: alignment)
			.assignMaxPreference(for: squareSizeReader.key, to: $squareSize)
	}
}

struct SquareFrameModifier: ViewModifier {
	let alignment: Alignment
	
	func body(content: Content) -> some View {
		SquareFrameWrapper(alignment: alignment) {
			content
		}
	}
}

extension View {
	func squareFrame(alignment: Alignment = .center) -> some View {
		ModifiedContent(
			content: self,
			modifier: SquareFrameModifier(alignment: alignment)
		)
	}
}
