import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
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
			.id(sectionId)
			
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
				}
				.pickerStyle(SegmentedPickerStyle())
			}
		}
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
	
	@ViewBuilder
	func fiatCurrencyText(_ fiatCurrency: FiatCurrency) -> some View {
		
		Text(fiatCurrency.shortName) +
		Text(verbatim: "  \(fiatCurrency.longName)")
			.font(.footnote)
			.foregroundColor(Color.secondary)
	}
	
	@ViewBuilder
	func bitcoinUnitText(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		// TODO: Define explanation of what a bitcoin unit is client side
		Text(bitcoinUnit.shortName) +
		Text(verbatim: "  \(bitcoinUnit.explanation)")
			.font(.footnote)
			.foregroundColor(Color.secondary)
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		// SwiftUI BUG, and workaround.
		//
		// In iOS 14, the Picker remains selected after we return from the subview.
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
