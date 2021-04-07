import SwiftUI
import PhoenixShared



struct DisplayConfigurationView: View {
	
	@State var fiatCurrency = Prefs.shared.fiatCurrency
	@State var bitcoinUnit = Prefs.shared.bitcoinUnit
	@State var theme = Prefs.shared.theme
	
	var body: some View {
		Form {
			Section {
				Picker(
					selection: Binding(
						get: { fiatCurrency },
						set: { Prefs.shared.fiatCurrency = $0 }
					), label: Text("Fiat currency")
				) {
					ForEach(0 ..< FiatCurrency.default().values.count) {
						let fiatCurrency = FiatCurrency.default().values[$0]
						fiatCurrencyText(fiatCurrency).tag(fiatCurrency)
					}
				}
				
				Picker(
					selection: Binding(
						get: { bitcoinUnit },
						set: { Prefs.shared.bitcoinUnit = $0 }
					), label: Text("Bitcoin unit")
				) {
					ForEach(0 ..< BitcoinUnit.default().values.count) {
						let bitcoinUnit = BitcoinUnit.default().values[$0]
						bitcoinUnitText(bitcoinUnit).tag(bitcoinUnit)
					}
				}
				
				Text("Throughout the app, you can tap on most amounts to toggle between bitcoin and fiat.")
					.font(.callout)
					.foregroundColor(Color.secondary)
					.padding(.top, 8)
					.padding(.bottom, 4) // visible in dark mode
			}
			
			Section {
				Picker(
					selection: Binding(
						get: { theme },
						set: { Prefs.shared.theme = $0 }
					), label: Text("App theme")
				) {
					ForEach(0 ..< Theme.allCases.count) {
						let theme = Theme.allCases[$0]
						Text(theme.localized()).tag(theme)
					}
				}.pickerStyle(SegmentedPickerStyle())
			}
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.edgesIgnoringSafeArea(.bottom)
		.navigationBarTitle("Display options", displayMode: .inline)
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
	
	@ViewBuilder func fiatCurrencyText(_ fiatCurrency: FiatCurrency) -> some View {
		
		Text(fiatCurrency.shortName) +
		Text("  \(fiatCurrency.longName)")
			.font(.footnote)
			.foregroundColor(Color.secondary)
	}
	
	@ViewBuilder func bitcoinUnitText(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		// TODO: Define explanation of what a bitcoin unit is client side
		Text(bitcoinUnit.shortName) +
		Text("  \(bitcoinUnit.explanation)")
			.font(.footnote)
			.foregroundColor(Color.secondary)
	}
}

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
