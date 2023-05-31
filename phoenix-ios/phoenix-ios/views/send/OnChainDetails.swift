import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "OnChainDetails"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct OnChainDetails: View {
	
	let model: Scan.Model_OnChainFlow
	
	@ViewBuilder
	var body: some View {
		
		if #available(iOS 16.0, *) {
			details_ios16()
		} else {
			details_ios15()
		}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func details_ios16() -> some View {
		
		Grid(horizontalSpacing: 8, verticalSpacing: 12) {
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				Text("Desc")
					.textCase(.uppercase)
					.font(.subheadline)
					.foregroundColor(.secondary)
					.gridColumnAlignment(.trailing)
				
				Text("On-Chain Payment")
					.font(.subheadline)
					.gridColumnAlignment(.leading)
			}
			GridRow(alignment: VerticalAlignment.firstTextBaseline) {
				Text("Send To")
					.textCase(.uppercase)
					.font(.subheadline)
					.foregroundColor(.secondary)
				
				let btcAddr = model.uri.address
				Text(btcAddr)
					.font(.subheadline)
					.contextMenu {
						Button {
							UIPasteboard.general.string = btcAddr
						} label: {
							Text("Copy")
						}
					}
			}
		}
	}
	
	@ViewBuilder
	func details_ios15() -> some View {
		
		OnChainDetails_Grid(model: model)
	}
}

fileprivate struct OnChainDetails_Grid: InfoGridView {
	
	let model: Scan.Model_OnChainFlow
	
	// <InfoGridView Protocol>
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	@State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) {
		keyColumnSizes = value
	}
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size] {
		return keyColumnSizes
	}
	
	@State var rowSizes: [InfoGridRow_Size] = []
	func setRowSizes(_ sizes: [InfoGridRow_Size]) {
		rowSizes = sizes
	}
	func getRowSizes() -> [InfoGridRow_Size] {
		return rowSizes
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			description()
			sendTo()
		}
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.uppercase)
			.font(.subheadline)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func description() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Desc")
		} valueColumn: {
			
			Text("On-Chain Payment")
				.font(.subheadline)
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func sendTo() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			keyColumn("Send To")
		} valueColumn: {
			
			let btcAddr = model.uri.address
			Text(btcAddr)
				.font(.subheadline)
				.contextMenu {
					Button {
						UIPasteboard.general.string = btcAddr
					} label: {
						Text("Copy")
					}
				}
			
		} // </InfoGridRow>
	}
}
