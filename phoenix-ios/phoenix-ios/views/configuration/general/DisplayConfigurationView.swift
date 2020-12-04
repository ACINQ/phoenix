import SwiftUI
import PhoenixShared

struct DisplayConfigurationView: View {

	var body: some View {
		MVIView({ $0.displayConfiguration() }) { model, postIntent in
			Form {
				Section {
					Picker(
						selection: Binding(
							get: { Prefs.shared.fiatCurrency },
							set: {
								Prefs.shared.fiatCurrency = $0
							}
						), label: Text("Fiat currency")
					) {
						ForEach(0 ..< FiatCurrency.default().values.count) {
							let fiatCurrency = FiatCurrency.default().values[$0]
							fiatCurrencyText(fiatCurrency).tag(fiatCurrency)
						}
					}
					
					Picker(
						selection: Binding(
							get: { Prefs.shared.bitcoinUnit },
							set: {
								Prefs.shared.bitcoinUnit = $0
							}
						), label: Text("Bitcoin unit")
					) {
						ForEach(0 ..< BitcoinUnit.default().values.count) {
							let bitcoinUnit = BitcoinUnit.default().values[$0]
							bitcoinUnitText(bitcoinUnit).tag(bitcoinUnit)
						}
					}
                }

				Section {
					Picker(
						selection: Binding(
							get: { model.appTheme },
							set: {
								postIntent(DisplayConfiguration.IntentUpdateAppTheme(appTheme: $0))
							}
						), label: Text("application theme")
					) {
						ForEach(0..<AppTheme.default().values.count) {
							let theme = AppTheme.default().values[$0]
							Text(theme.label).tag(theme)
						}
					}.pickerStyle(SegmentedPickerStyle())
				}
			}
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.edgesIgnoringSafeArea(.bottom)
			.navigationBarTitle("Display options", displayMode: .inline)

            Spacer()
        }
    }
	
	@ViewBuilder func fiatCurrencyText(_ fiatCurrency: FiatCurrency) -> some View {
		
		Text(fiatCurrency.shortLabel) +
		Text(" (\(fiatCurrency.longLabel))")
			.font(.footnote)
			.foregroundColor(Color.secondary)
	}
	
	@ViewBuilder func bitcoinUnitText(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		if bitcoinUnit.explanation.count == 0 {
			Text(bitcoinUnit.label)
		} else {
			Text(bitcoinUnit.label) +
			Text(" (\(bitcoinUnit.explanation))")
				.font(.footnote)
				.foregroundColor(Color.secondary)
		}
	}
}

class DisplayConfigurationView_Previews: PreviewProvider {

	static let mockModel = DisplayConfiguration.Model(
		fiatCurrency: .usd,
		bitcoinUnit: .satoshi,
		appTheme: .system
	)

	static var previews: some View {
		mockView(DisplayConfigurationView())
			.previewDevice("iPhone 11")
	}

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
