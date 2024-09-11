import SwiftUI
import PhoenixShared

fileprivate let filename = "CurrencySelector"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct CurrencySelector: View {
	
	@Binding var selectedCurrencies: [Currency]
	@Binding var replacingCurrency: Currency?
	
	let didSelectCurrency: (Currency) -> Void
	
	@State var visibleBitcoinUnits: [BitcoinUnit] = BitcoinUnit.companion.values
	@State var visibleFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
	
	@State var selectedCurrency: Currency? = nil
	@State var searchText: String = ""
	
	enum BitcoinTextWidth: Preference {}
	let bitcoinTextWidthReader = GeometryPreferenceReader(
		key: AppendValue<BitcoinTextWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var bitcoinTextWidth: CGFloat? = nil
	
	enum FiatTextWidth: Preference {}
	let fiatTextWidthReader = GeometryPreferenceReader(
		key: AppendValue<FiatTextWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var fiatTextWidth: CGFloat? = nil
	
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
			
			// We want to measure various items within the List.
			// But we need to measure ALL of them.
			// And we can't do that with a List because it's lazy.
			//
			// So we have to use this hack,
			// which force-renders all the Text items using a hidden VStack.
			//
			ScrollView {
				VStack {
					ForEach(BitcoinUnit.companion.values) { bitcoinUnit in
						
						Text(bitcoinUnit.shortName)
							.foregroundColor(Color.clear)
							.read(bitcoinTextWidthReader)
							.frame(width: bitcoinTextWidth, alignment: .leading) // required, or refreshes after display
					}
					
					ForEach(FiatCurrency.companion.values) { fiatCurrency in

						Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
							.foregroundColor(Color.clear)
							.read(fiatTextWidthReader)
							.frame(width: fiatTextWidth, alignment: .leading) // required, or refreshes after display
						
						Text(fiatCurrency.flag)
							.foregroundColor(Color.clear)
							.read(flagWidthReader)
							.frame(width: flagWidth, alignment: .leading) // required, or refreshes after display
					}
				}
				.assignMaxPreference(for: bitcoinTextWidthReader.key, to: $bitcoinTextWidth)
				.assignMaxPreference(for: fiatTextWidthReader.key, to: $fiatTextWidth)
				.assignMaxPreference(for: flagWidthReader.key, to: $flagWidth)
			}
			
			content
				.searchable(text: $searchText)
				.onChange(of: searchText) { _ in
					searchTextDidChange()
				}
		}
		.navigationTitle(NSLocalizedString("Fiat currency", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	var content: some View {
		
		List {
			
			Section(header: Text("Bitcoin")) {
				ForEach(visibleBitcoinUnits) { bitcoinUnit in
					
					Button {
						didSelect(bitcoinUnit)
					} label: {
						bitcoinRow(bitcoinUnit)
					}
					.buttonStyle(.plain)
					.disabled(isSelected(bitcoinUnit))
				}
			}
			
			Section(header: Text("Fiat")) {
				ForEach(visibleFiatCurrencies) { fiatCurrency in
					
					Button {
						didSelect(fiatCurrency)
					} label: {
						fiatRow(fiatCurrency)
					}
					.buttonStyle(.plain)
					.disabled(isSelected(fiatCurrency))
				}
			}
		}
		.listStyle(GroupedListStyle())
	}
	
	@ViewBuilder
	func bitcoinRow(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text(bitcoinUnit.shortName)
				.frame(width: bitcoinTextWidth, alignment: .leading)
			
			let imgSize = UIFont.preferredFont(forTextStyle: .body).pointSize
			Image("bitcoin")
				.resizable()
				.frame(width: imgSize, height: imgSize, alignment: .center)
				.padding(.leading, 6)
				.offset(x: 0, y: 2)
			
			Spacer()
			
			Text(verbatim: "  \(bitcoinUnit.explanation)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				.padding(.trailing, 4)
			
			Image(systemName: "checkmark")
				.foregroundColor(isSelected(bitcoinUnit) ? .appAccent : .clear)
		}
		.contentShape(Rectangle()) // allow spacer area to be tapped
	}
	
	@ViewBuilder
	func fiatRow(_ fiatCurrency: FiatCurrency) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
				.frame(width: fiatTextWidth, alignment: .leading)
				
			Text(fiatCurrency.flag)
				.frame(width: flagWidth, alignment: .center)
				.padding(.leading, 3)
			
			Text(verbatim: "  \(fiatCurrency.longName)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				
			Spacer()
			
			Image(systemName: "checkmark")
				.foregroundColor(isSelected(fiatCurrency) ? Color.appAccent : .clear)
		}
		.contentShape(Rectangle()) // allow spacer area to be tapped
	}
	
	func isSelected(_ bitcoinUnit: BitcoinUnit) -> Bool {
		return isSelected(Currency.bitcoin(bitcoinUnit))
	}
	
	func isSelected(_ fiatCurrency: FiatCurrency) -> Bool {
		return isSelected(Currency.fiat(fiatCurrency))
	}
	
	func isSelected(_ currency: Currency) -> Bool {
		
		if currency == selectedCurrency {
			return true
		} else if currency == replacingCurrency {
			return false
		} else {
			return selectedCurrencies.contains(currency)
		}
	}
	
	func didSelect(_ bitcoinUnit: BitcoinUnit) {
		didSelect(Currency.bitcoin(bitcoinUnit))
	}
	
	func didSelect(_ fiatCurrency: FiatCurrency) {
		didSelect(Currency.fiat(fiatCurrency))
	}
	
	func didSelect(_ currency: Currency) {
		log.trace("didSelect(\(currency))")
		
		selectedCurrency = currency
		didSelectCurrency(currency)
		popView()
	}
	
	func searchTextDidChange() {
		log.trace("searchTextDidChange(): \(searchText)")
		
		let allBitcoinUnits: [BitcoinUnit] = BitcoinUnit.companion.values
		let allFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
		
		let components = searchText
			.components(separatedBy: .whitespacesAndNewlines)
			.filter { !$0.isEmpty }
		
		if components.isEmpty {
			
			visibleBitcoinUnits = allBitcoinUnits
			visibleFiatCurrencies = allFiatCurrencies
			
		} else {
			
			visibleBitcoinUnits = allBitcoinUnits.filter { bitcoinUnit in
				components.allSatisfy { component in
					bitcoinUnit.shortName.localizedCaseInsensitiveContains(component)
				}
			}
			
			visibleFiatCurrencies = allFiatCurrencies.filter { fiatCurrency in
				components.allSatisfy { component in
					fiatCurrency.shortName.localizedCaseInsensitiveContains(component) ||
					fiatCurrency.longName.localizedCaseInsensitiveContains(component)
				}
			}
		}
	}
	
	func popView() {
		log.trace("popView()")
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
}
