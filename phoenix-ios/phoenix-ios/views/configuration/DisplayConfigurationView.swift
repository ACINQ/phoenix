import SwiftUI
import PhoenixShared

struct DisplayConfigurationView: View {

    var body: some View {
        MVIView({ $0.displayConfiguration() }) { model, postIntent in
            Form {
                Section {
                    Picker(
                            selection: Binding(
                                    get: { model.fiatCurrency },
                                    set: { postIntent(DisplayConfiguration.IntentUpdateFiatCurrency(fiatCurrency: $0)) }
                            ), label: Text("Fiat currency")) {
                        ForEach(0..<FiatCurrency.default().values.count) {
                            let fiatCurrency = FiatCurrency.default().values[$0]
                            Text(fiatCurrency.label).tag(fiatCurrency)
                        }
                    }
                }

                Section {
                    Picker(
                            selection: Binding(
                                    get: { model.bitcoinUnit },
                                    set: { postIntent(DisplayConfiguration.IntentUpdateBitcoinUnit(bitcoinUnit: $0)) }
                            ), label: Text("Bitcoin unit")) {
                        ForEach(0..<BitcoinUnit.default().values.count) {
                            let unit = BitcoinUnit.default().values[$0]
                            Text(unit.label).tag(unit)
                        }
                    }
                }

                Section {
                    Picker(
                            selection: Binding(
                                    get: { model.appTheme },
                                    set: { postIntent(DisplayConfiguration.IntentUpdateAppTheme(appTheme: $0)) }
                            ), label: Text("application theme")) {
                        ForEach(0..<AppTheme.default().values.count) {
                            let theme = AppTheme.default().values[$0]
                            Text(theme.label).tag(theme)
                        }
                    }.pickerStyle(SegmentedPickerStyle())
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
        mockView(DisplayConfigurationView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
