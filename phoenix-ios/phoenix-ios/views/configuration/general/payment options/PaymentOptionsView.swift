import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentOptionsView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentOptionsView: View {
	
	@State var defaultPaymentDescription: String = Prefs.shared.defaultPaymentDescription ?? ""
	
	@State var invoiceExpirationDays: Int = Prefs.shared.invoiceExpirationDays
	let invoiceExpirationDaysOptions = [7, 30, 60]

	@State var allowOverpayment: Bool = Prefs.shared.allowOverpayment
	
	@State var notificationSettings = NotificationsManager.shared.settings.value
	
	@State var firstAppearance = true
	
	enum NavLinkTag: String, Codable {
		case BackgroundPaymentsSelector
	}
	
	@Environment(\.openURL) var openURL
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
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
			.navigationDestination(for: NavLinkTag.self) { tag in
				switch tag {
					case .BackgroundPaymentsSelector : BackgroundPaymentsSelector()
				}
			}
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
	}
	
	@ViewBuilder
	func section_outgoingPayments() -> some View {
		
		Section {
			subsection_enableOverpayments()
			
		} /* Section.*/header: {
			Text("Outgoing payments")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_backgroundPayments() -> some View {
		
		Section(header: Text("Background Payments")) {
			
			let config = BackgroundPaymentsConfig.fromSettings(notificationSettings)
			let hideAmount = NSLocalizedString("(hide amount)", comment: "Background payments configuration")
			
			NavigationLink(value: NavLinkTag.BackgroundPaymentsSelector) {
				
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
			} // </NavigationLink>
			
		} // </Section>
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
				navCoordinator.path.append(newNavLinkTag)
			}
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
}
