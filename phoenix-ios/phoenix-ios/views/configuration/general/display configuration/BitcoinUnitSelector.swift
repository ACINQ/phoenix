import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BitcoinUnitSelector"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct BitcoinUnitSelector: View {
	
	@State var selectedBitcoinUnit: BitcoinUnit
	
	@Environment(\.colorScheme) var colorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			List {
				ForEach(BitcoinUnit.companion.values) { bitcoinUnit in
					Button {
						didSelect(bitcoinUnit)
					} label: {
						row(bitcoinUnit)
					}
				}
			}
			.listStyle(.plain)
			.listBackgroundColor(.primaryBackground)
			
			footer()
				.padding(.horizontal, 10)
				.padding(.vertical, 20)
				.frame(maxWidth: .infinity)
				.background(
					Color(
						colorScheme == ColorScheme.light
						? UIColor.systemGroupedBackground
						: UIColor.secondarySystemGroupedBackground
					)
					.edgesIgnoringSafeArea(.bottom) // background color should extend to bottom of screen
				)
		}
		.navigationTitle(NSLocalizedString("Bitcoin unit", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func row(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text(bitcoinUnit.shortName)
			
			Spacer()
			
			Text(verbatim: "  \(bitcoinUnit.explanation)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				.padding(.trailing, 4)
			
			let isSelected = bitcoinUnit == selectedBitcoinUnit
			Image(systemName: "checkmark")
				.foregroundColor(isSelected ? .appAccent : .clear)
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			Text(
				"""
				A bitcoin can be divided into smaller units. The smallest unit is called a \
				Satoshi, and there are 100 million satoshis in a single bitcoin.
				"""
			)
			Text(
				"""
				Satoshis are often preferred because it's easier to work with whole numbers rather than fractions.
				"""
			)
		}
		.font(.callout)
	}
	
	func didSelect(_ bitcoinUnit: BitcoinUnit) {
		log.trace("didSelect(bitcoinUnit = \(bitcoinUnit.shortName)")
		
		selectedBitcoinUnit = bitcoinUnit
		GroupPrefs.shared.bitcoinUnit = bitcoinUnit
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
}
