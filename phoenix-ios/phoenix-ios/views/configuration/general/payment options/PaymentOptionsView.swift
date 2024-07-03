import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentOptionsView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum NavLinkTag: String {
	case BackgroundPaymentsSelector
}

struct PaymentOptionsView: View {
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			PaymentOptionsList(scrollViewProxy: scrollViewProxy)
		}
	}
}

fileprivate struct PaymentOptionsList: View {
	
	let scrollViewProxy: ScrollViewProxy
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var defaultPaymentDescription: String = Prefs.shared.defaultPaymentDescription ?? ""
	
	@State var invoiceExpirationDays: Int = Prefs.shared.invoiceExpirationDays
	let invoiceExpirationDaysOptions = [7, 30, 60]

	@State var allowOverpayment: Bool = Prefs.shared.allowOverpayment
	@State var randomPayerKey: Bool = Prefs.shared.randomPayerKey
	
	@State var notificationSettings = NotificationsManager.shared.settings.value
	
	@State var firstAppearance = true
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@Namespace var sectionID_incomingPayments
	@Namespace var sectionID_outgoingPayments
	@Namespace var sectionID_backgroundPayments
	
	@Environment(\.openURL) var openURL
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Payment Options", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_incomingPayments()
			section_outgoingPayments()
			section_backgroundPayments()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear {
			onAppear()
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onReceive(NotificationsManager.shared.settings) {
			notificationSettings = $0
		}
	}
	
	@ViewBuilder
	func section_incomingPayments() -> some View {
		
		Section {
			subsection_defaultPaymentDescription()
			subsection_incomingPaymentExpiry()
			
		} /* Section.*/header: {
			Text("Incoming payments")
			
		} // </Section>
		.id(sectionID_incomingPayments)
	}
	
	@ViewBuilder
	func section_outgoingPayments() -> some View {
		
		Section {
			subsection_enableOverpayments()
			subsection_randomPayerKey()
			
		} /* Section.*/header: {
			Text("Outgoing payments")
			
		} // </Section>
		.id(sectionID_outgoingPayments)
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
		.id(sectionID_backgroundPayments)
	}
	
	@ViewBuilder
	func subsection_defaultPaymentDescription() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			Text("Default payment description")
				.padding(.bottom, 8)
			
			HStack {
				TextField(
					NSLocalizedString("None", comment: "TextField placeholder"),
					text: $defaultPaymentDescription
				)
				.onChange(of: defaultPaymentDescription) { _ in
					defaultPaymentDescriptionChanged()
				}
				
				Button {
					defaultPaymentDescription = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(defaultPaymentDescription == "")
			}
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 4)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
		} // </VStack>
		.padding([.top, .bottom], 8)
	}
	
	@ViewBuilder
	func subsection_incomingPaymentExpiry() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			Text("Incoming payment expiry")
				.padding(.bottom, 8)
			
			Picker(
				selection: Binding(
					get: { invoiceExpirationDays },
					set: { invoiceExpirationDays = $0 }
				), label: Text("Invoice expiration")
			) {
				ForEach(invoiceExpirationDaysOptions, id: \.self) { days in
					Text("\(days) days").tag(days)
				}
			}
			.pickerStyle(SegmentedPickerStyle())
			.onChange(of: invoiceExpirationDays) { _ in
				invoiceExpirationDaysChanged()
			}
			
		} // </VStack>
		.padding([.top, .bottom], 8)
	}
	
	@ViewBuilder
	func subsection_enableOverpayments() -> some View {
		
		HStack(alignment: VerticalAlignment.centerTopLine) { // <- Custom VerticalAlignment
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				Text("Enable overpayment")
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
					
				Text(
					"""
					You'll be able to overpay Lightning invoices up to 2 times the amount requested. \
					Useful for manual tipping, or as a privacy measure.
					"""
				)
				.font(.callout)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.foregroundColor(Color.secondary)
					
			} // </VStack>
			
			Spacer()
			
			Toggle("", isOn: $allowOverpayment)
				.labelsHidden()
				.padding(.trailing, 2)
				.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
					d[VerticalAlignment.center]
				}
				.onChange(of: allowOverpayment) { newValue in
					Prefs.shared.allowOverpayment = newValue
				}
			
		} // </HStack>
	}
	
	@ViewBuilder
	func subsection_randomPayerKey() -> some View {
		
		HStack(alignment: VerticalAlignment.centerTopLine) { // <- Custom VerticalAlignment
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				Text("Random payer key")
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
					
				Text(
					"""
					Enable if you don't want Bolt12 recipients to know \
					that payments come from your wallet.
					"""
				)
				.font(.callout)
				.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
				.foregroundColor(Color.secondary)
					
			} // </VStack>
			
			Spacer()
			
			Toggle("", isOn: $randomPayerKey)
				.labelsHidden()
				.padding(.trailing, 2)
				.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
					d[VerticalAlignment.center]
				}
				.onChange(of: randomPayerKey) { newValue in
					Prefs.shared.randomPayerKey = newValue
				}
			
		} // </HStack>
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
			case .BackgroundPaymentsSelector : BackgroundPaymentsSelector()
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if firstAppearance {
			firstAppearance = false
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkChanged(deepLink)
				}
			}
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
				case .liquiditySettings  : break
				case .forceCloseChannels : break
				case .swapInWallet       : break
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: 1.5)
				
				// Interesting bug in SwiftUI:
				// If the navLinkTag you're targetting is scrolled off the screen,
				// the you won't be able to navigate to it.
				// My understanding is that List is lazy, and this somehow prevents triggering the navigation.
				// The workaround is to manually scroll to the item to ensure it's onscreen,
				// at which point we can activate the navLinkTag trigger.
				//
				scrollViewProxy.scrollTo(sectionID_backgroundPayments)
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
					self.navLinkTag = newNavLinkTag // Trigger/push the view
				}
			}
			
		} else {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 0.0)
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
			log.debug("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func defaultPaymentDescriptionChanged() {
		log.trace("defaultPaymentDescriptionChanged(): \(defaultPaymentDescription)")
		
		Prefs.shared.defaultPaymentDescription = self.defaultPaymentDescription
	}
	
	func invoiceExpirationDaysChanged() {
		log.trace("invoiceExpirationDaysChanged(): \(invoiceExpirationDays)")
		
		Prefs.shared.invoiceExpirationDays = self.invoiceExpirationDays
	}
	
	func openFaqButtonTapped() -> Void {
		log.trace("openFaqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
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
