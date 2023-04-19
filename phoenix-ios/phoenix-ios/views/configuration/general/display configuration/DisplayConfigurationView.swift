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

fileprivate enum NavLinkTag: String {
	case FiatCurrencySelector
	case BitcoinUnitSelector
	case RecentPaymentsSelector
	case BackgroundPaymentsSelector
}

struct DisplayConfigurationView: View {
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var fiatCurrency = GroupPrefs.shared.fiatCurrency
	@State var bitcoinUnit = GroupPrefs.shared.bitcoinUnit
	@State var theme = Prefs.shared.theme
	@State var showOriginalFiatAmount = Prefs.shared.showOriginalFiatAmount
	@State var recentPaymentsConfig = Prefs.shared.recentPaymentsConfig
	@State var notificationSettings = NotificationsManager.shared.settings.value
	
	@State var sectionId = UUID()
	@State var firstAppearance = true
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
			section_backgroundPayments()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onAppear {
			onAppear()
		}
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
		.onReceive(NotificationsManager.shared.settings) {
			notificationSettings = $0
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
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
		}
		.id(sectionId)
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
	}
	
	@ViewBuilder
	func section_backgroundPayments() -> some View {
		
		Section(header: Text("Background Payments")) {
			
			let config = BackgroundPaymentsConfig.fromSettings(notificationSettings)
			let hideAmount = NSLocalizedString("(hide amount)", comment: "Background payments configuration")
			
			navLink(.BackgroundPaymentsSelector) {
				
				Group { // Compiler workaround: Type '()' cannot conform to 'View'
					switch config {
					case .receiveQuietly(let discreet):
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Text("Receive quietly")
							if discreet {
								Text(verbatim: hideAmount)
									.font(.subheadline)
									.foregroundColor(.secondary)
								}
							}
						
					case .fullVisibility(let discreet):
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Text("Visible")
							if discreet {
								Text(verbatim: hideAmount)
									.font(.subheadline)
									.foregroundColor(.secondary)
								}
							}
						
					case .customized(let discreet):
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Text("Customized")
							if discreet {
								Text(verbatim: hideAmount)
									.font(.subheadline)
									.foregroundColor(.secondary)
								}
							}
						
					case .disabled:
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("Disabled")
							Spacer()
							Image(systemName: "exclamationmark.triangle")
								.renderingMode(.template)
								.foregroundColor(Color.appWarn)
							}
						
					} // </switch>
				} // </Group>
			} // </navLink>
			
		} // </Section>
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
		case .FiatCurrencySelector       : FiatCurrencySelector(selectedFiatCurrency: fiatCurrency)
		case .BitcoinUnitSelector        : BitcoinUnitSelector(selectedBitcoinUnit: bitcoinUnit)
		case .RecentPaymentsSelector     : RecentPaymentsSelector(recentPaymentsConfig: recentPaymentsConfig)
		case .BackgroundPaymentsSelector : BackgroundPaymentsSelector()
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
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
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkChanged(deepLink)
				}
			}
			
		} else {
			sectionId = UUID()
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		// This is a hack, courtesy of bugs in Apple's NavigationLink:
		// https://developer.apple.com/forums/thread/677333
		//
		// Summary:
		// There's some quirky code in SwiftUI that is resetting our navLinkTag.
		// Several bizarre workarounds have been proposed.
		// I've tried every one of them, and none of them work (at least, without bad side-effects).
		//
		// The only clean solution I've found is to listen for SwiftUI's bad behaviour,
		// and forcibly undo it.
		
		if let value = value {
			
			// Navigate towards deep link (if needed)
			var newNavLinkTag: NavLinkTag? = nil
			switch value {
				case .paymentHistory     : break
				case .backup             : break
				case .drainWallet        : break
				case .electrum           : break
				case .backgroundPayments : newNavLinkTag = NavLinkTag.BackgroundPaymentsSelector
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: 5.0)
				
				self.navLinkTag = newNavLinkTag // Trigger/push the view
			}
			
		} else {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 1.0)
		}
	}
	
	// --------------------------------------------------
	// MARK: Workarounds
	// --------------------------------------------------
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				log.trace("swiftUiBugWorkaround = nil")
				self.swiftUiBugWorkaround = nil
			}
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
