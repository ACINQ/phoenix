import SwiftUI

// Architecture Design:
//
// We want to display a list of key/value pairs:
//
//     Desc: Pizza reimbursement
//     Fees: 2 sat
//  Elapsed: 2.4 seconds
//
// Requirements:
// 1. the elements must be vertically aligned
//   - all keys have same trailing edge
//   - all values have same leading edge
// 2. the list (as a whole) must be horizontally centered
//
//       1,042 sat
//       ---------
// Desc: Party
//
//      ^ Wrong! List not horizontally centered!
//
//       1,042 sat
//       ---------
//      Desc: Party
//
//      ^ Correct!
//
// Ultimately, we need to:
// - assign all keys the same width
// - ensure the assigned width is the minimum possible width
//
// This was super easy with UIKit.
// We could simply add constraints such that all keys are equal width.
//
// In SwiftUI, it's not that simple.
//
// - we use a GeometryReader to measure the width of each KeyColumn
// - we use InfoGrid_KeyColumn_MeasuredWidth to communicate the width
//   up the hierarchy to the InfoGrid.
// - InfoGrid_KeyColumn_MeasuredWidth.reduce is used to find the max width
// - InfoGridRow assigns the proper width to each KeyColumn frame
//
// Note that this occurs in 2 passes.
// - In the first pass, InfoGrid.keyColumnWidthState is nil
// - It then lays out all the elements, and they get measured
// - The width is passed up the hierarchy via InfoGrid_KeyColumn_MeasuredWidth preference
// - This triggers InfoGrid.onPreferenceChange(InfoGrid_KeyColumn_MeasuredWidth.self)
// - Which triggers a second layout pass


protocol InfoGridView: View {
	
	// Add this to your implementation:
	// @State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	//
	// And then implement these methods to get/set the @State property.
	//
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) -> Void
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size]
	
	var minKeyColumnWidth: CGFloat { get }
	var maxKeyColumnWidth: CGFloat { get }
	
	// Add this to your implementation:
	// @State var rowSizes: [InfoGridRow_Size] = []
	//
	// And then implement these methods to get/set the @State property.
	//
	func setRowSizes(_ value: [InfoGridRow_Size]) -> Void
	func getRowSizes() -> [InfoGridRow_Size]
	
	// Do not override these.
	// They have a default implementation which is correct.
	//
	func rowSize(identifier: String) -> CGSize?
	func keyColumnSize(identifier: String) -> CGSize?
	func keyColumnWidth(identifier: String) -> CGFloat
	
	associatedtype Rows: View
	@ViewBuilder var infoGridRows: Self.Rows { get }
}


extension InfoGridView {
	
	func rowSize(identifier: String) -> CGSize? {
		
		return getRowSizes().first { $0.identifier == identifier }?.size
	}
	
	func keyColumnSize(identifier: String) -> CGSize? {
		
		return getKeyColumnSizes().first { $0.identifier == identifier }?.size
	}
	
	func keyColumnWidth(identifier: String) -> CGFloat {
		// Normally what happens is:
		// - During the first rendering pass,
		//   all of the InfoGridRow_KeyColumn items receive a proposed size.width=some_BIG_number
		// - Each calculates it's desired width
		// - That information is passed back up the view hierarchy, and stored in @State
		// - The second rendering pass then uses keyColumnWidth() during the layout process,
		//   and it looks beautiful
		//
		// But what sometimes happens is:
		// - During the first rendering pass,
		//   all of the InfoGridRow_KeyColumn items receive a proposed size.width=some_SMALL_number
		// - Each calculates it's desired width, which ends up being too small
		// - That information is passed back up the view hierarchy, and stored in @State
		// - The second rendering pass then uses keyColumnWidth() during the layout process,
		//   and it looks horrible
		//
		// So during the first rendering pass, we need to ensure
		// that the proposed size.width is big enough to achieve our goals.
		
		let values = getKeyColumnSizes()
		
		// If we recently added a new row, then it's identifier won't be in the list.
		// In this case, we must not use the calculated value, since it might be too small.
		//
		if !values.contains(where: { $0.identifier == identifier }) {
			return maxKeyColumnWidth
		}
		
		let calculatedMaxWidth = values.reduce(0) { partialResult, value in
			max(partialResult, value.size.width)
		}
		return min(max(calculatedMaxWidth, minKeyColumnWidth), maxKeyColumnWidth)
	}
	
	@ViewBuilder
	var body: some View {
		
		infoGridRows
			.onPreferenceChange(InfoGridRow_KeyColumn_MeasuredSize.self) {(sizes: [InfoGridRow_KeyColumn_Size]) in
				setKeyColumnSizes(sizes)
			}
			.onPreferenceChange(InfoGridRow_MeasuredSize.self) {(sizes: [InfoGridRow_Size]) in
				setRowSizes(sizes)
			}
	}
}


struct InfoGridRow<KeyColumn: View, ValueColumn: View>: View {
	
	let identifier: String
	let vAlignment: VerticalAlignment
	let hSpacing: CGFloat
	let keyColumnWidth: CGFloat
	let keyColumnAlignment: Alignment
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	init(
		identifier: String,
		vAlignment: VerticalAlignment,
		hSpacing: CGFloat,
		keyColumnWidth: CGFloat,
		keyColumnAlignment: Alignment,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
		self.identifier = identifier
		self.vAlignment = vAlignment
		self.hSpacing = hSpacing
		self.keyColumnWidth = keyColumnWidth
		self.keyColumnAlignment = keyColumnAlignment
		self.keyColumn = keyColumnBuilder()
		self.valueColumn = valueColumnBuilder()
	}
	
	var body: some View {
		
		HStack(alignment: vAlignment, spacing: hSpacing) {
			
			keyColumn
				.background(GeometryReader { proxy in
					Color.clear.preference(
						key: InfoGridRow_KeyColumn_MeasuredSize.self,
						value: [InfoGridRow_KeyColumn_Size(identifier: identifier, size: proxy.size)]
					)
				})
				.frame(width: keyColumnWidth, alignment: keyColumnAlignment)

			valueColumn
		}
		.accessibilityElement(children: .combine)
		.background(GeometryReader { proxy in
			Color.clear.preference(
				key: InfoGridRow_MeasuredSize.self,
				value: [InfoGridRow_Size(identifier: identifier, size: proxy.size)]
			)
		})
	}
}

struct InfoGridRow_KeyColumn_Size: Equatable {
	let identifier: String
	let size: CGSize
}

fileprivate struct InfoGridRow_KeyColumn_MeasuredSize: PreferenceKey {
	typealias Value = [InfoGridRow_KeyColumn_Size]
	static let defaultValue: Value = []
	
	static func reduce(value: inout Value, nextValue: () -> Value) {
		value.append(contentsOf: nextValue())
	}
}

struct InfoGridRow_Size: Equatable {
	let identifier: String
	let size: CGSize
}

fileprivate struct InfoGridRow_MeasuredSize: PreferenceKey {
	typealias Value = [InfoGridRow_Size]
	static let defaultValue: Value = []
	
	static func reduce(value: inout Value, nextValue: () -> Value) {
		value.append(contentsOf: nextValue())
	}
}
