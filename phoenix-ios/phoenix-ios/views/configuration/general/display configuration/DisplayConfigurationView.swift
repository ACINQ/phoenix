import SwiftUI
import PhoenixShared

fileprivate let filename = "DisplayConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum NavLinkTag: String {
	case FiatCurrencySelector
	case BitcoinUnitSelector
	case RecentPaymentsSelector
}

struct DisplayConfigurationView: View {
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			DisplayConfigurationList(scrollViewProxy: scrollViewProxy)
		}
	}
}

fileprivate struct DisplayConfigurationList: View {
	
	let scrollViewProxy: ScrollViewProxy
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var fiatCurrency = GroupPrefs.shared.fiatCurrency
	@State var bitcoinUnit = GroupPrefs.shared.bitcoinUnit
	@State var theme = Prefs.shared.theme
	@State var showOriginalFiatAmount = Prefs.shared.showOriginalFiatAmount
	@State var recentPaymentsConfig = Prefs.shared.recentPaymentsConfig
	
	@Namespace var sectionID_currency
	@Namespace var sectionID_theme
	@Namespace var sectionID_paymentHistory
	@Namespace var sectionID_home
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Display options", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() ->  some View {
		List {
			section_currency()
			section_theme()
			section_paymentHistory()
			section_home()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(GroupPrefs.shared.fiatCurrencyPublisher) {
			fiatCurrency = $0
		}
		.onReceive(GroupPrefs.shared.bitcoinUnitPublisher) {
			bitcoinUnit = $0
		}
		.onReceive(Prefs.shared.themePublisher) {
			theme = $0
		}
		.onReceive(Prefs.shared.recentPaymentsConfigPublisher) {
			recentPaymentsConfig = $0
		}
	}
	
	@ViewBuilder
	func section_currency() -> some View {
		
		Section {
			navLink(.FiatCurrencySelector) {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Text("Fiat currency")
					Spacer()
					Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
						.foregroundColor(.secondary)
					Text(verbatim: "  \(fiatCurrency.flag)")
				}
			}
			
			navLink(.BitcoinUnitSelector) {
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
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.foregroundColor(.secondary)
				.padding(.top, 8)
				.padding(.bottom, 4)
			
		} // </Section>
		.id(sectionID_currency)
	}
	
	@ViewBuilder
	func section_theme() -> some View {
		
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
		.id(sectionID_theme)
	}
	
	@ViewBuilder
	func section_paymentHistory() -> some View {
		
		Section(header: Text("Payment History")) {
			
			Toggle(isOn: $showOriginalFiatAmount) {
				Text("Show original fiat amount")
			}
			.onChange(of: showOriginalFiatAmount) { newValue in
				Prefs.shared.showOriginalFiatAmount = newValue
			}
			
			Group {
				if showOriginalFiatAmount {
					Text(
						"The displayed fiat value will be the price you paid at the time of the payment."
					)
				} else {
					Text(
						"""
						The displayed fiat value will be the current price, \
						based on the current fiat/bitcoin exchange rate.
						"""
					)
				}
			}
			.font(.callout)
			.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
			.foregroundColor(Color.secondary)
			.padding(.top, 8)
			.padding(.bottom, 4)
			
		} // </Section>
		.id(sectionID_paymentHistory)
	}
	
	@ViewBuilder
	func section_home() -> some View {
		
		Section(header: Text("Home Screen")) {
			
			navLink(.RecentPaymentsSelector) {
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					switch recentPaymentsConfig {
					case .withinTime(let seconds):
						let config = RecentPaymentsConfig_WithinTime.closest(seconds: seconds).1
						Text(verbatim: config.configDisplay())
							.fixedSize(horizontal: false, vertical: true)
					case .mostRecent(let count):
						let config = RecentPaymentsConfig_MostRecent.closest(count: count).1
						Text(verbatim: config.configDisplay())
							.fixedSize(horizontal: false, vertical: true)
					case .inFlightOnly:
						Text("Show only in-flight payments")
							.fixedSize(horizontal: false, vertical: true)
					}
				}
			} // </navLink>
			
			Text("Use the payments screen to view your full payment history.")
				.font(.callout)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.foregroundColor(Color.secondary)
				.padding(.top, 8)
				.padding(.bottom, 4)
			
		} // </Section>
		.id(sectionID_home)
	}
	
	@ViewBuilder
	private func navLink<Content>(
		_ tag: NavLinkTag,
		label: () -> Content
	) -> some View where Content: View {
		
		NavigationLink(
			destination: navLinkView(tag),
			tag: tag,
			selection: $navLinkTag,
			label: label
		)
	}
	
	@ViewBuilder
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .FiatCurrencySelector   : FiatCurrencySelector(selectedFiatCurrency: fiatCurrency)
			case .BitcoinUnitSelector    : BitcoinUnitSelector(selectedBitcoinUnit: bitcoinUnit)
			case .RecentPaymentsSelector : RecentPaymentsSelector(recentPaymentsConfig: recentPaymentsConfig)
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
