import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "DisplayConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct DisplayConfigurationView: View {
	
	@State var fiatCurrency = Prefs.shared.fiatCurrency
	@State var bitcoinUnit = Prefs.shared.bitcoinUnit
	@State var theme = Prefs.shared.theme
	
	@State var sectionId = UUID()
	@State var firstAppearance = true
	
	@ViewBuilder
	var body: some View {
		List {
			Section {
				NavigationLink(
					destination: FiatCurrencySelector(selectedFiatCurrency: fiatCurrency)
				) {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text("Fiat currency")
						Spacer()
						Text(verbatim: fiatCurrency.shortName)
							.foregroundColor(Color.secondary)
						Text(verbatim: "  \(fiatCurrency.flag)")
					}
				}
				
				NavigationLink(
					destination: BitcoinUnitSelector(selectedBitcoinUnit: bitcoinUnit)
				) {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text("Bitcoin unit")
						Spacer()
						Text(verbatim: bitcoinUnit.shortName)
							.foregroundColor(Color.secondary) // +
						let imgSize = UIFont.preferredFont(forTextStyle: .body).pointSize
						Image("bitcoin")
							.resizable()
							.frame(width: imgSize, height: imgSize, alignment: .center)
							.padding(.leading, 6)
							.offset(x: 0, y: 2)
					}
				}
				
				Text("Throughout the app, you can tap on most amounts to toggle between bitcoin and fiat.")
					.font(.callout)
					.lineLimit(nil)          // SwiftUI bugs
					.minimumScaleFactor(0.5) // Truncating text
					.foregroundColor(Color.secondary)
					.padding(.top, 8)
					.padding(.bottom, 4)
			}
			.id(sectionId)
			
			Section(header: Text("Theme")) {
				Picker(
					selection: Binding(
						get: { theme },
						set: { Prefs.shared.theme = $0 }
					), label: Text("App theme")
				) {
					ForEach(Theme.allCases, id: \.self) { theme in
						Text(theme.localized()).tag(theme)
					}
				}
				.pickerStyle(SegmentedPickerStyle())
				
			} // </Section>
			
		} // </List>
		.listStyle(.insetGrouped)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Display options", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onAppear {
			onAppear()
		}
		.onReceive(Prefs.shared.fiatCurrencyPublisher) { newValue in
			fiatCurrency = newValue
		}
		.onReceive(Prefs.shared.bitcoinUnitPublisher) { newValue in
			bitcoinUnit = newValue
		}
		.onReceive(Prefs.shared.themePublisher) { newValue in
			theme = newValue
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		// SwiftUI BUG, and workaround.
		//
		// In iOS 14, the row remains selected after we return from the subview.
		// For example:
		// - Tap on "Fiat Currency"
		// - Make a selection or tap "<" to pop back
		// - Notice that the "Fiat Currency" row is still selected (e.g. has gray background)
		//
		// There are several workaround for this issue:
		// https://developer.apple.com/forums/thread/660468
		//
		// We are implementing the least risky solution.
		// Which requires us to change the `Section.id` property.
		
		if firstAppearance {
			firstAppearance = false
		} else {
			sectionId = UUID()
		}
	}
}

struct FiatCurrencySelector: View, ViewName {
	
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

						Text(fiatCurrency.shortName)
							.foregroundColor(Color.clear)
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
		.navigationBarTitle(
			NSLocalizedString("Fiat currency", comment: "Navigation bar title"),
			displayMode: .inline
		)
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
		.listStyle(PlainListStyle())
	}
	
	@ViewBuilder
	func row(_ fiatCurrency: FiatCurrency) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text(fiatCurrency.shortName)
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
		Prefs.shared.fiatCurrency = fiatCurrency
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func searchTextDidChange() {
		log.trace("[\(viewName)] searchTextDidChange(): \(searchText)")
		
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

struct BitcoinUnitSelector: View, ViewName {
	
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
			.listStyle(PlainListStyle())
			
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
		.navigationBarTitle(
			NSLocalizedString("Bitcoin unit", comment: "Navigation bar title"),
			displayMode: .inline
		)
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
		Prefs.shared.bitcoinUnit = bitcoinUnit
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
}

// MARK: -

class DisplayConfigurationView_Previews: PreviewProvider {

	static var previews: some View {
		
		DisplayConfigurationView()
			.preferredColorScheme(.light)
			.previewDevice("iPhone 11")
			.environmentObject(CurrencyPrefs.mockEUR())
		
		DisplayConfigurationView()
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 11")
			.environmentObject(CurrencyPrefs.mockEUR())
	}
}
