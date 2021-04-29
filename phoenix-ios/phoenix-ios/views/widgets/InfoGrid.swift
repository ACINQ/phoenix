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
// In SwiftUI, it's not that simple. But it's not that bad either.
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
	// @State var calculatedKeyColumnWidth: CGFloat? = nil
	//
	// And then implement these methods to get/set the @State property.
	//
	func setCalculatedKeyColumnWidth(_ value: CGFloat?) -> Void
	func getCalculatedKeyColumnWidth() -> CGFloat?
	
	// Do NOT override this.
	// It has a default implementation which is correct.
	//
	var keyColumnWidth: CGFloat { get }
	
	var minKeyColumnWidth: CGFloat { get }
	var maxKeyColumnWidth: CGFloat { get }
	
	associatedtype Rows: View
	@ViewBuilder var infoGridRows: Self.Rows { get }
}


extension InfoGridView {
	
	var keyColumnWidth: CGFloat {
		get {
			// Normally what happens is:
			// - During the first rendering pass,
			//   all of the InfoGrid_Column0 items receive a proposed size.width=some_BIG_number
			// - Each calculates it's desired width
			// - That information is passed back up the view hierarchy, and stored in widthColumn0
			// - The second rendering pass then uses widthColumn0 to lay out everything,
			//   and it looks beautiful
			//
			// But what sometimes happens is:
			// - During the first rendering pass,
			//   all of the InfoGrid_Column0 items receive a proposed size.width=some_SMALL_number
			// - Each calculates it's desired width, which ends up being too small
			// - That information is passed back up the view hierarchy, and stored in widthColumn0
			// - The second rendering pass then uses widthColumn0 to lay out everything,
			//   and it looks horrible
			//
			// So during the first rendering pass, we need to ensure
			// that proposed size.width is big enough to achieve our goals.
			
			return getCalculatedKeyColumnWidth() ?? maxKeyColumnWidth
		}
	}
	
	var body: some View {
		
		infoGridRows.onPreferenceChange(InfoGrid_KeyColumn_MeasuredWidth.self) {
			
			if let width = $0 {
				setCalculatedKeyColumnWidth(min(max(width, minKeyColumnWidth), maxKeyColumnWidth))
			} else {
				setCalculatedKeyColumnWidth(nil)
			}
		}
	}
}


struct InfoGridRow<KeyColumn: View, ValueColumn: View>: View {
	
	let keyColumnWidth: CGFloat
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	private let horizontalSpacingBetweenColumns: CGFloat // = 8
	
	init(
		hSpacing: CGFloat,
		keyColumnWidth: CGFloat,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
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
						key: InfoGrid_KeyColumn_MeasuredWidth.self,
						value: proxy.size.width
					)
				})
			}
			.frame(width: keyColumnWidth)

			valueColumn
		}
	}
}


fileprivate struct InfoGrid_KeyColumn_MeasuredWidth: PreferenceKey {
	static let defaultValue: CGFloat? = nil
	
	static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
		
		// This function is called with the measured width of each individual column0 item.
		// We want to determine the maximum measured width here.
		if let prv = value {
			if let nxt = nextValue() {
				value = prv >= nxt ? prv : nxt
			} else {
				value = prv
			}
		} else {
			value = nextValue()
		}
	}
}

