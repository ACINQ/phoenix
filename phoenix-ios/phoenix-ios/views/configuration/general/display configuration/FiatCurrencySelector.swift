import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "FiatCurrencySelector"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct FiatCurrencySelector: View {
	
	@State var selectedFiatCurrency: FiatCurrency
	
	@State var visibleFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
	@State var searchText: String = ""
	
	enum TextWidth: Preference {}
	let textWidthReader = GeometryPreferenceReader(
		key: AppendValue<TextWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var textWidth: CGFloat? = nil
	
	enum FlagWidth: Preference {}
	let flagWidthReader = GeometryPreferenceReader(
		key: AppendValue<FlagWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var flagWidth: CGFloat? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			// We want to vertically align the text:
			//
			// AUD (flag) Australian Dollar
			// BRL (flag) Brazilian Real
			//            ^ The fiatCurrency.longName should be
			//              vertically aligned on leading edge.
			//
			// To accomplish this we need to measure the shortName & flag.
			// But we need to measure ALL of them.
			// And we can't do that with a List because it's lazy.
			//
			// So we have to use this hack,
			// which force-renders all the Text items using a hidden VStack.
			//
			ScrollView {
				VStack {
					ForEach(FiatCurrency.companion.values) { fiatCurrency in
						
						Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
							.foregroundColor(.clear)
							.read(textWidthReader)
							.frame(width: textWidth, alignment: .leading)
						
						Text(fiatCurrency.flag)
							.foregroundColor(Color.clear)
							.read(flagWidthReader)
							.frame(width: flagWidth, alignment: .leading)
					}
				}
				.assignMaxPreference(for: textWidthReader.key, to: $textWidth)
				.assignMaxPreference(for: flagWidthReader.key, to: $flagWidth)
			}
			
			if #available(iOS 15.0, *) {
				content
					.searchable(text: $searchText)
					.onChange(of: searchText) { _ in
						searchTextDidChange()
					}
			} else {
				content
			}
		}
		.navigationTitle(NSLocalizedString("Fiat currency", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	var content: some View {
		
		List {
			ForEach(visibleFiatCurrencies) { fiatCurrency in
				
				Button {
					didSelect(fiatCurrency)
				} label: {
					row(fiatCurrency)
				}
			}
		}
		.listStyle(.plain)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func row(_ fiatCurrency: FiatCurrency) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
				.frame(width: textWidth, alignment: .leading)
				
			Text(fiatCurrency.flag)
				.frame(width: flagWidth, alignment: .center)
				.padding(.leading, 3)
			
			Text(verbatim: "  \(fiatCurrency.longName)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				
			Spacer()
			
			if (fiatCurrency == selectedFiatCurrency) {
				Image(systemName: "checkmark")
					.foregroundColor(Color.appAccent)
			}
		}
	}
	
	func didSelect(_ fiatCurrency: FiatCurrency) {
		log.trace("didSelect(fiatCurrency = \(fiatCurrency.shortName)")
		
		selectedFiatCurrency = fiatCurrency
		GroupPrefs.shared.fiatCurrency = fiatCurrency
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func searchTextDidChange() {
		log.trace("searchTextDidChange(): \(searchText)")
		
		let allFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
		
		let components = searchText
			.components(separatedBy: .whitespacesAndNewlines)
			.filter { !$0.isEmpty }
		
		if components.isEmpty {
			visibleFiatCurrencies = allFiatCurrencies
			
		} else {
			visibleFiatCurrencies = allFiatCurrencies.filter { fiatCurrency in
				components.allSatisfy { component in
					fiatCurrency.shortName.localizedCaseInsensitiveContains(component) ||
					fiatCurrency.longName.localizedCaseInsensitiveContains(component)
				}
			}
		}
	}
}
