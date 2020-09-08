import SwiftUI
import PhoenixShared

struct DisplayConfigurationView: MVIView {
    typealias Model = DisplayConfiguration.Model
    typealias Intent = DisplayConfiguration.Intent

    @State var selectedFiatCurrency: FiatCurrency = .usd
    @State var selectedBitcoinUnit: BitcoinUnit = .satoshi
    @State var selectedAppTheme: AppTheme = .system

    var body: some View {
        mvi(
                background: true,
                onModel: { model in
                    selectedFiatCurrency = model.fiatCurrency
                    selectedBitcoinUnit = model.bitcoinUnit
                    selectedAppTheme = model.appTheme
                }
        ) { model, intent in
            Form {
                Section {
                    Picker(selection: $selectedFiatCurrency, label: Text("Fiat currency")) {
                        ForEach(0..<FiatCurrency.default().values.count) {
                            let fiatCurrency = FiatCurrency.default().values[$0]
                            Text(fiatCurrency.label).tag(fiatCurrency)
                        }
                    }
                            .onChange(of: selectedFiatCurrency) { selected in
                                intent(DisplayConfiguration.IntentUpdateFiatCurrency(fiatCurrency: selected))
                            }
                }

                Section {
                    Picker(selection: $selectedBitcoinUnit, label: Text("Bitcoin unit")) {
                        ForEach(0..<BitcoinUnit.default().values.count) {
                            let unit = BitcoinUnit.default().values[$0]
                            Text(unit.label).tag(unit)
                        }
                    }
                            .onChange(of: selectedBitcoinUnit) { selected in
                                intent(DisplayConfiguration.IntentUpdateBitcoinUnit(bitcoinUnit: selected))
                            }
                }

                Section {
                    Picker(selection: $selectedAppTheme, label: Text("application theme")) {
                        ForEach(0..<AppTheme.default().values.count) {
                            let theme = AppTheme.default().values[$0]
                            Text(theme.label).tag(theme)
                        }
                    }.pickerStyle(SegmentedPickerStyle())
                            .onChange(of: selectedAppTheme) { selected in
                                intent(DisplayConfiguration.IntentUpdateAppTheme(appTheme: selected))
                            }
                }
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Display options", displayMode: .inline)

            Spacer()
        }
    }
}

class DisplayConfigurationView_Previews: PreviewProvider {
    static let mockModel = DisplayConfiguration.Model(fiatCurrency: .usd, bitcoinUnit: .satoshi, appTheme: .system)

    static var previews: some View {
        mockView(DisplayConfigurationView()) {
            $0.displayConfigurationModel = mockModel
        }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
