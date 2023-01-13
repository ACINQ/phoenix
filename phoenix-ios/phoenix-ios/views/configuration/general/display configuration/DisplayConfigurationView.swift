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
	
	@State var fiatCurrency = GroupPrefs.shared.fiatCurrency
	@State var bitcoinUnit = GroupPrefs.shared.bitcoinUnit
	@State var theme = Prefs.shared.theme
	
	@State var toggle_withinTime: Bool
	@State var slider_withinTime: Double
	@State var selected_withinTime: RecentPaymentsConfig_WithinTime
	
	@State var toggle_mostRecent: Bool
	@State var slider_mostRecent: Double = 0
	@State var selected_mostRecent: RecentPaymentsConfig_MostRecent
	
	@State var toggle_inFlight: Bool
	
	@State var sectionId = UUID()
	@State var firstAppearance = true
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init() {
		switch Prefs.shared.recentPaymentsConfig {
		case .withinTime(let seconds):
			_toggle_withinTime = State(initialValue: true)
			_toggle_mostRecent = State(initialValue: false)
			_toggle_inFlight = State(initialValue: false)
			
			do {
				let (idx, value) = RecentPaymentsConfig_WithinTime.closest(seconds: seconds)
				_slider_withinTime = State(initialValue: Double(idx))
				_selected_withinTime = State(initialValue: value)
			}
			do {
				let (idx, value) = RecentPaymentsConfig_MostRecent.defaultTuple()
				_slider_mostRecent = State(initialValue: Double(idx))
				_selected_mostRecent = State(initialValue: value)
			}
			
		case .mostRecent(let count):
			_toggle_withinTime = State(initialValue: false)
			_toggle_mostRecent = State(initialValue: true)
			_toggle_inFlight = State(initialValue: false)
			
			do {
				let (idx, value) = RecentPaymentsConfig_WithinTime.defaultTuple()
				_slider_withinTime = State(initialValue: Double(idx))
				_selected_withinTime = State(initialValue: value)
			}
			do {
				let (idx, value) = RecentPaymentsConfig_MostRecent.closest(count: count)
				_slider_mostRecent = State(initialValue: Double(idx))
				_selected_mostRecent = State(initialValue: value)
			}
			
		case .inFlightOnly:
			_toggle_withinTime = State(initialValue: false)
			_toggle_mostRecent = State(initialValue: false)
			_toggle_inFlight = State(initialValue: true)
			
			do {
				let (idx, value) = RecentPaymentsConfig_WithinTime.defaultTuple()
				_slider_withinTime = State(initialValue: Double(idx))
				_selected_withinTime = State(initialValue: value)
			}
			do {
				let (idx, value) = RecentPaymentsConfig_MostRecent.defaultTuple()
				_slider_mostRecent = State(initialValue: Double(idx))
				_selected_mostRecent = State(initialValue: value)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		List {
			section_currency()
			section_theme()
			section_home()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationTitle(NSLocalizedString("Display options", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
		.onAppear {
			onAppear()
		}
		.onChange(of: toggle_withinTime) {
			toggle_withinTime_changed($0)
		}
		.onChange(of: toggle_mostRecent) {
			toggle_mostRecent_changed($0)
		}
		.onChange(of: toggle_inFlight) {
			toggle_inFlight_changed($0)
		}
		.onChange(of: slider_withinTime) {
			slider_withinTime_changed($0)
		}
		.onChange(of: slider_mostRecent) {
			slider_mostRecent_changed($0)
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
	}
	
	@ViewBuilder
	func section_currency() -> some View {
		
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
	func section_home() -> some View {
		
		Section(header: Text("Home Screen")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Toggle(isOn: toggle_withinTime_binding()) {
					Text(selected_withinTime.configDisplay())
						.fixedSize(horizontal: false, vertical: true)
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: optionOnImage(),
					offImage: optionOffImage()
				))
				.padding(.bottom, 5)
				
				let options_withinTime = RecentPaymentsConfig_WithinTime.allCases
				let options_withinTime_lastIdx = Double(options_withinTime.count - 1)
				
				Label {
					Slider(value: $slider_withinTime, in: 0...options_withinTime_lastIdx, step: 1)
						.accentColor(toggle_withinTime ? .accentColor : .gray)
						.disabled(!toggle_withinTime)
				} icon: {
					invisibleImage()
				}
				.padding(.bottom, 15)
				
				Toggle(isOn: toggle_mostRecent_binding()) {
					Text(selected_mostRecent.configDisplay())
						.fixedSize(horizontal: false, vertical: true)
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: optionOnImage(),
					offImage: optionOffImage()
				))
				.padding(.bottom, 5)
				
				let options_mostRecent = RecentPaymentsConfig_MostRecent.allCases
				let options_mostRecent_lastIdx = Double(options_mostRecent.count - 1)
				
				Label {
					Slider(value: $slider_mostRecent, in: 0...options_mostRecent_lastIdx, step: 1)
						.accentColor(toggle_mostRecent ? .accentColor : .gray)
						.disabled(!toggle_mostRecent)
				} icon: {
					invisibleImage()
				}
				.padding(.bottom, 15)
				
				Toggle(isOn: toggle_inFlight_binding()) {
					Text("Show only in-flight payments")
						.fixedSize(horizontal: false, vertical: true)
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: optionOnImage(),
					offImage: optionOffImage()
				))
				.padding(.bottom, 10)
			
			} // </VStack>
			
			Label {
				Text("Use the payments screen to view your full payment history.")
					.font(.callout)
					.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
					.foregroundColor(Color.secondary)
			} icon: {
				invisibleImage()
			}
			.padding(.top, 8)
			.padding(.bottom, 4)
			
		} // </Section>
	}
	
	@ViewBuilder
	func optionOnImage() -> some View {
		Image(systemName: "record.circle")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func optionOffImage() -> some View {
		Image(systemName: "circle")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
	}
	
	// --------------------------------------------------
	// MARK: View helpers
	// --------------------------------------------------
	
	func toggle_withinTime_binding() -> Binding<Bool> {
		return Binding<Bool>(
			get: { toggle_withinTime },
			set: {
				if $0 {
					toggle_withinTime = true
					toggle_mostRecent = false
					toggle_inFlight = false
				}
			}
		)
	}
	
	func toggle_mostRecent_binding() -> Binding<Bool> {
		return Binding<Bool>(
			get: { toggle_mostRecent },
			set: {
				if $0 {
					toggle_withinTime = false
					toggle_mostRecent = true
					toggle_inFlight = false
				}
			}
		)
	}
	
	func toggle_inFlight_binding() -> Binding<Bool> {
		return Binding<Bool>(
			get: { toggle_inFlight },
			set: {
				if $0 {
					toggle_withinTime = false
					toggle_mostRecent = false
					toggle_inFlight = true
				}
			}
		)
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
			
		} else {
			sectionId = UUID()
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func toggle_withinTime_changed(_ newValue: Bool) {
		log.trace("toggle_withinTime_changed(\(newValue))")
		
		if newValue {
			Prefs.shared.recentPaymentsConfig = .withinTime(seconds: selected_withinTime.seconds)
		}
	}
	
	func toggle_mostRecent_changed(_ newValue: Bool) {
		log.trace("toggle_mostRecent_changed(\(newValue))")
		
		if newValue {
			Prefs.shared.recentPaymentsConfig = .mostRecent(count: selected_mostRecent.count)
		}
	}
	
	func toggle_inFlight_changed(_ newValue: Bool) {
		log.trace("toggle_inFlight_changed(\(newValue))")
		
		if newValue {
			Prefs.shared.recentPaymentsConfig = .inFlightOnly
		}
	}
	
	func slider_withinTime_changed(_ newValue: Double) {
		log.trace("slider_withinTime_changed(\(newValue))")
		
		let selectedIdx = Int(newValue)
		let selectedOption = RecentPaymentsConfig_WithinTime.allCases[selectedIdx]
		
		selected_withinTime = selectedOption
		if toggle_withinTime {
			Prefs.shared.recentPaymentsConfig = .withinTime(seconds: selectedOption.seconds)
		}
	}
	
	func slider_mostRecent_changed(_ newValue: Double) {
		log.trace("slider_mostRecent_changed(\(newValue))")
		
		let selectedIdx = Int(newValue)
		let selectedOption = RecentPaymentsConfig_MostRecent.allCases[selectedIdx]
		
		selected_mostRecent = selectedOption
		if toggle_mostRecent {
			Prefs.shared.recentPaymentsConfig = .mostRecent(count: selectedOption.count)
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
