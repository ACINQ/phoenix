import SwiftUI

fileprivate let filename = "RecentPaymentsSelector"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct RecentPaymentsSelector: View {
	
	@State var recentPaymentsConfig: RecentPaymentsConfig
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Home screen", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_withinTime()
			section_mostRecent()
			section_only()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_withinTime() -> some View {
		
		Section {
			
			Text("Show payments within the last:")
				.font(.headline)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.padding(.top, 4)
				.padding(.bottom, 8)
			
			let options = RecentPaymentsConfig_WithinTime.allCases
			let currentSeconds = currentConfig_seconds()
			ForEach(options) { option in
				Button {
					didSelect(option)
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text(option.configDisplayPicker())
							.foregroundColor(.primary)
						Spacer()
						
						let isSelected = option.seconds == currentSeconds
						Image(systemName: "checkmark")
							.foregroundColor(isSelected ? .appAccent : .clear)
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func section_mostRecent() -> some View {
		
		Section {
			
			Text("Show most recent:")
				.font(.headline)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.padding(.top, 4)
				.padding(.bottom, 8)
			
			let options = RecentPaymentsConfig_MostRecent.allCases
			let currentCount = currentConfig_count()
			ForEach(options) { option in
				Button {
					didSelect(option)
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text(option.configDisplayPicker())
							.foregroundColor(.primary)
						Spacer()
						
						let isSelected = option.count == currentCount
						Image(systemName: "checkmark")
							.foregroundColor(isSelected ? .appAccent : .clear)
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func section_only() -> some View {
		
		Section {
			
			Text("Show only:")
				.font(.headline)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.padding(.top, 4)
				.padding(.bottom, 8)
			
			Button {
				didSelectInFlightOnly()
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Text("in-flight payments")
						.foregroundColor(.primary)
					Spacer()
					
					let isSelected = recentPaymentsConfig == .inFlightOnly
					Image(systemName: "checkmark")
						.foregroundColor(isSelected ? .appAccent : .clear)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func currentConfig_seconds() -> Int? {
		
		switch recentPaymentsConfig {
		case .withinTime(let seconds):
			return seconds
		default:
			return nil
		}
	}
	
	func currentConfig_count() -> Int? {
		
		switch recentPaymentsConfig {
		case .mostRecent(let count):
			return count
		default:
			return nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didSelect(_ option: RecentPaymentsConfig_WithinTime) {
		log.trace("didSelect(withinTime:)")
		didSelect(RecentPaymentsConfig.withinTime(seconds: option.seconds))
	}
	
	func didSelect(_ option: RecentPaymentsConfig_MostRecent) {
		log.trace("didSelect(mostRecent:)")
		didSelect(RecentPaymentsConfig.mostRecent(count: option.count))
	}
	
	func didSelectInFlightOnly() {
		log.trace("didSelectInFlightOnly")
		didSelect(RecentPaymentsConfig.inFlightOnly)
	}
	
	func didSelect(_ newValue: RecentPaymentsConfig) {
		
		self.recentPaymentsConfig = newValue
		Prefs.current.recentPaymentsConfig = newValue
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
}
