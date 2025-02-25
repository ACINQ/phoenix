import SwiftUI

struct DetailsRowWrapper<KeyColumn: View, ValueColumn: View>: View {
	
	let identifier: String
	let keyColumnWidth: CGFloat
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	init(
		identifier: String,
		keyColumnWidth: CGFloat,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
		self.identifier = identifier
		self.keyColumnWidth = keyColumnWidth
		self.keyColumn = keyColumnBuilder()
		self.valueColumn = valueColumnBuilder()
	}
	
	@ViewBuilder
	var body: some View {
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: 8,
			keyColumnWidth: keyColumnWidth,
			keyColumnAlignment: .trailing
		) {
			keyColumn
		} valueColumn: {
			valueColumn.font(.callout)
		}
	}
}
