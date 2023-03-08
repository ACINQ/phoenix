import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BackgroundPaymentsSelector"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct BackgroundPaymentsSelector: View {
	
	@State var settings = NotificationsManager.shared.settings.value
	
	@State var includeAmount = !GroupPrefs.shared.discreetNotifications
	
	@State var didAppear = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Background payments", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			let config = BackgroundPaymentsConfig.fromSettings(settings)
			if config == .disabled {
				section_disabled()
			} else {
				section_enabled()
				section_notifications()
				section_privacy()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear() {
			onAppear()
		}
		.onReceive(NotificationsManager.shared.settings) {
			settings = $0
		}
		.onChange(of: includeAmount) { _ in
			includeAmountChanged()
		}
	}
	
	@ViewBuilder
	func section_enabled() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					Text("Background payments enabled")
						.font(.headline)
				} icon: {
					Image(systemName: "bolt.fill")
						.imageScale(.large)
						.font(.title)
						.foregroundColor(.yellow)
				}
				.padding(.bottom, 16)
				
				Label {
					Text("You can receive payments as long as your device is connected to the internet.")
				} icon: {
					invisibleImage()
				}
				.font(.callout)
				.padding(.bottom, 8)
				
				Label {
					Text("(even if Phoenix isn't running)").foregroundColor(.secondary)
				} icon: {
					invisibleImage()
				}
				.font(.callout)
				
			} // </VStack>
		} // </Section>
	}
	
	@ViewBuilder
	func section_notifications() -> some View {
		
		Section(header: Text("Notifications")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Button {
					customizeInIosSettings()
				} label: {
					Label {
						Text("Customize in iOS Settings")
					} icon: {
						Image(systemName: "bell.badge.fill")
							.imageScale(.large)
					}
					.font(.headline)
				}
				
			} // </VStack>
		} // </Section>
	}
	
	@ViewBuilder
	func section_privacy() -> some View {
		
		Section(header: Text("Privacy")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Toggle(isOn: $includeAmount) {
					Text("Include amount received")
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: onImage(),
					offImage: offImage()
				))
				
				Divider()
					.padding(.top, 16)
					.padding(.bottom, 32)
				
				if #available(iOS 15.0, *) {
					sampleNotificationContent()
						.background(
							.thickMaterial,
							in: RoundedRectangle(cornerRadius: 10, style: .continuous)
						)
				} else {
					sampleNotificationContent()
						.background(
							VisualEffectView(style: UIBlurEffect.Style.systemThickMaterial)
								.cornerRadius(10)
						)
				}
			}
		}
	}
	
	@ViewBuilder
	func sampleNotificationContent() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
			
			Image(uiImage: Bundle.main.icon ?? UIImage())
				.resizable()
				.frame(width: 50, height: 50)
				.cornerRadius(10)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 2) {
				Text("Received payment").bold()
				if includeAmount {
					Text(verbatim: exampleNotificationText())
						.lineLimit(3)
				}
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			
		} // </HStack>
		.padding(.all, 10)
	}
	
	@ViewBuilder
	func section_disabled() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					Text("You have disabled background payments.")
				} icon: {
					Image(systemName: "exclamationmark.triangle")
						.renderingMode(.template)
						.foregroundColor(Color.appNegative)
				}
				.font(.title3)
				.padding(.bottom, 30)
				
				Label {
					Text("You can only receive payments if Phoenix is running in the foreground.")
						.font(.callout)
				} icon: {
					Text("☹️").font(.headline)
				}
				.padding(.bottom)
				
				Label {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						Text(
							"""
							If you enable background payments, then you can receive payments \
							as long as your device is connected to the internet.
							"""
						)
						Text("(even if Phoenix isn't running)")
							.foregroundColor(.secondary)
					}
					.font(.callout)
				} icon: {
					Text("😃").font(.headline)
				}
				.padding(.bottom, 30)
				
				Label {
					VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
						Text("To enable background payments:")
						HStack(alignment: VerticalAlignment.top, spacing: 8) {
							Text("*").bold()
							Button {
								customizeInIosSettings()
							} label: {
								Text("Open iOS Settings for Phoenix")
							}
						}
						HStack(alignment: VerticalAlignment.top, spacing: 8) {
							Text("*").bold()
							Text("Enable notifications in the Notification Center")
						}
					}
				} icon: {
					Image(systemName: "info.circle")
						.renderingMode(.template)
						.foregroundColor(Color.appPositive)
				}
			}
		} // </Section>
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
		
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func exampleNotificationText() -> String {
		
		let sat: Int64 = 15_000
		let bitcoinAmt = Utils.formatBitcoin(sat: sat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		
		var fiatAmt: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			fiatAmt = Utils.formatFiat(sat: sat, exchangeRate: exchangeRate)
		}
		
		var amount = bitcoinAmt.string
		if let fiatAmt {
			amount += " (≈\(fiatAmt.string))"
		}
		
		return amount
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink, deepLink == .backgroundPayments {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func customizeInIosSettings() {
		log.trace("customizeInIosSettings()")
		
		if #available(iOS 16.0, *) {
			// We can jump directly to the notification settings
			if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
				if UIApplication.shared.canOpenURL(url) {
					UIApplication.shared.open(url)
				}
			}
		} else {
			// The best we can do is jump to the app settings
			if let bundleIdentifier = Bundle.main.bundleIdentifier,
			   let url = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
			{
				if UIApplication.shared.canOpenURL(url) {
					UIApplication.shared.open(url)
				}
			}
		}
	}
	
	func includeAmountChanged() {
		log.trace("includeAmountChanged()")
		
		log.debug("GroupPrefs.shared.discreetNotifications = \(!includeAmount)")
		GroupPrefs.shared.discreetNotifications = !includeAmount
	}
}
