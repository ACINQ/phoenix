import SwiftUI
import PhoenixShared

fileprivate let filename = "DetailsInfoGrid"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class DetailsInfoGridState: ObservableObject {
	@Published var truncated: [String: Bool] = [:]
}

protocol DetailsInfoGrid: InfoGridView {
	var detailsInfoGridState: DetailsInfoGridState { get }
	
	var paymentInfo: WalletPaymentInfo { get }
	var showOriginalFiatValue: Bool { get }
	var showFiatValueExplanation: Bool { get }
	var currencyPrefs: CurrencyPrefs { get }
	var dynamicTypeSize: DynamicTypeSize { get }
	
	func clockStateBinding() -> Binding<AnimatedClock.ClockState>
}

extension DetailsInfoGrid {
	
	var minKeyColumnWidth: CGFloat { return 50 }
	var maxKeyColumnWidth: CGFloat { return 140 }
	
	// --------------------------------------------------
	// MARK: Styling
	// --------------------------------------------------
	
	@ViewBuilder
	func header(_ title: LocalizedStringKey) -> some View {
		
		HStack {
			Spacer()
			Text(title)
				.textCase(.uppercase)
				.lineLimit(1)
				.minimumScaleFactor(0.5)
				.font(.headline)
				.foregroundColor(Color(UIColor.systemGray))
			Spacer()
		}
		.padding(.bottom, 12)
		.accessibilityAddTraits(.isHeader)
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func keyColumn(verbatim title: String) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	// --------------------------------------------------
	// MARK: Row Builders
	// --------------------------------------------------
	
	@ViewBuilder
	func detailsRow(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnText: LocalizedStringKey
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(valueColumnText)
		}
	}
	
	@ViewBuilder
	func detailsRow(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnVerbatim: String
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(verbatim: valueColumnVerbatim)
		}
	}
	
	@ViewBuilder
	func detailsRowCopyable(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		valueColumnText: String
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			Text(valueColumnText)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = valueColumnText
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func detailsRow<Content: View>(
		identifier: String,
		keyColumnTitle: LocalizedStringKey,
		@ViewBuilder valueBuilder: () -> Content
	) -> some View {
		
		DetailsRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn(keyColumnTitle)
			
		} valueColumn: {
			valueBuilder()
		}
	}
}
