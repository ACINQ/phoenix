import SwiftUI
import PhoenixShared

fileprivate let filename = "DisplayConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DisplayConfigurationView: View {
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			DisplayConfigurationList(scrollViewProxy: scrollViewProxy)
		}
	}
}

fileprivate struct DisplayConfigurationList: View {
	
	enum NavLinkTag: String {
		case FiatCurrencySelector
		case BitcoinUnitSelector
		case RecentPaymentsSelector
	}
	
	let scrollViewProxy: ScrollViewProxy
	
	@State var fiatCurrency = GroupPrefs.current.fiatCurrency
	@State var bitcoinUnit = GroupPrefs.current.bitcoinUnit
	@State var theme = Prefs.global.theme
	@State var showOriginalFiatAmount = Prefs.current.showOriginalFiatAmount
	@State var recentPaymentsConfig = Prefs.current.recentPaymentsConfig
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
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
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
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
		.onReceive(GroupPrefs.current.fiatCurrencyPublisher) {
			fiatCurrency = $0
		}
		.onReceive(GroupPrefs.current.bitcoinUnitPublisher) {
			bitcoinUnit = $0
		}
		.onReceive(Prefs.global.themePublisher) {
			theme = $0
		}
		.onReceive(Prefs.current.recentPaymentsConfigPublisher) {
			recentPaymentsConfig = $0
		}
	}
	
	@ViewBuilder
	func section_currency() -> some View {
		
		Section {
			navLink_plain(.FiatCurrencySelector) {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Text("Fiat currency")
					Spacer()
					Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
						.foregroundColor(.secondary)
					Text(verbatim: "  \(fiatCurrency.flag)")
				}
			}
			
			navLink_plain(.BitcoinUnitSelector) {
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
					set: { Prefs.global.theme = $0 }
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
				Prefs.current.showOriginalFiatAmount = newValue
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
			
			navLink_plain(.RecentPaymentsSelector) {
				
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
	func navLink_plain<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
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
