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
	// @State var keyColumnWidths: [CGFloat] = []
	//
	// And then implement these methods to get/set the @State property.
	//
	func setKeyColumnWidths(_ value: [InfoGridRow_KeyColumn_Width]) -> Void
	func getKeyColumnWidths() -> [InfoGridRow_KeyColumn_Width]
	
	// Do NOT override this.
	// It has a default implementation which is correct.
	//
	func keyColumnWidth(identifier: String) -> CGFloat
	
	var minKeyColumnWidth: CGFloat { get }
	var maxKeyColumnWidth: CGFloat { get }
	//  ^^^^^^^^^^^^^^^^^
	// Possible solution if we don't know the max value, or don't want to hardcode it:
	// https://finestructure.co/blog/2020/1/20/swiftui-equal-widths-view-constraints
	// https://betterprogramming.pub/using-the-preferencekey-protocol-to-align-views-7f3ae32f60fc
	
	associatedtype Rows: View
	@ViewBuilder var infoGridRows: Self.Rows { get }
}


extension InfoGridView {
	
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
		
		let values = getKeyColumnWidths()
		
		// If we recently added a new row, then it's identifier won't be in the list.
		// In this case, we must not use the calculated value, since it might be too small.
		//
		if !values.contains(where: { $0.identifier == identifier }) {
			return maxKeyColumnWidth
		}
		
		let calculatedMaxWidth = values.reduce(0) { partialResult, value in
			max(partialResult, value.width)
		}
		return min(max(calculatedMaxWidth, minKeyColumnWidth), maxKeyColumnWidth)
	}
	
	var body: some View {
		
		infoGridRows.onPreferenceChange(InfoGridRow_KeyColumn_MeasuredWidth.self) {
			(values: [InfoGridRow_KeyColumn_Width]) in
			
			setKeyColumnWidths(values)
		}
	}
}


struct InfoGridRow<KeyColumn: View, ValueColumn: View>: View {
	
	let identifier: String
	let keyColumnWidth: CGFloat
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	private let horizontalSpacingBetweenColumns: CGFloat // = 8
	
	init(
		identifier: String,
		hSpacing: CGFloat,
		keyColumnWidth: CGFloat,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
		self.identifier = identifier
		self.horizontalSpacingBetweenColumns = hSpacing
		self.keyColumnWidth = keyColumnWidth
		self.keyColumn = keyColumnBuilder()
		self.valueColumn = valueColumnBuilder()
	}
	
	var body: some View {
		
		HStack(
			alignment : VerticalAlignment.firstTextBaseline,
			spacing   : horizontalSpacingBetweenColumns
		) {
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer(minLength: 0) // => HorizontalAlignment.trailing
				keyColumn.background(GeometryReader { proxy in
				
					Color.clear.preference(
						key: InfoGridRow_KeyColumn_MeasuredWidth.self,
						value: [InfoGridRow_KeyColumn_Width(identifier: identifier, width: proxy.size.width)]
					)
				})
			}
			.frame(width: keyColumnWidth)

			valueColumn
		}
	}
}

struct InfoGridRow_KeyColumn_Width: Equatable {
	let identifier: String
	let width: CGFloat
}

fileprivate struct InfoGridRow_KeyColumn_MeasuredWidth: PreferenceKey {
	typealias Value = [InfoGridRow_KeyColumn_Width]
	static let defaultValue: Value = []
	
	static func reduce(value: inout Value, nextValue: () -> Value) {
		value.append(contentsOf: nextValue())
	}
}

