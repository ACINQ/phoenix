import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentOptionsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum NavLinkTag: String {
	case LiquidityPolicy
}

struct PaymentOptionsView: View {

	@State private var navLinkTag: NavLinkTag? = nil
	
	@State var defaultPaymentDescription: String = Prefs.shared.defaultPaymentDescription ?? ""
	
	@State var invoiceExpirationDays: Int = Prefs.shared.invoiceExpirationDays
	let invoiceExpirationDaysOptions = [7, 30, 60]

	@State var userDefinedMaxFees: MaxFees? = Prefs.shared.maxFees
	
	@State var payToOpen_feePercent: Double = 0.0
	@State var payToOpen_minFeeSat: Int64 = 0
	
	@State var firstAppearance = true
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	let maxFeesPublisher = Prefs.shared.maxFeesPublisher
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	
	@Environment(\.openURL) var openURL
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
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
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear {
			onAppear()
		}
		.onReceive(maxFeesPublisher) {
			maxFeesChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
	}
	
	@ViewBuilder
	func section_incomingPayments() -> some View {
		
		Section {
			subsection_defaultPaymentDescription()
			subsection_incomingPaymentExpiry()
			subsection_liquidityPolicy()
			
		} /* Section.*/header: {
			Text("Incoming payments")
			
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
	func subsection_liquidityPolicy() -> some View {
		
		navLink(.LiquidityPolicy) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
				Text("Miner fee policy")
				
				let numSats = liquidityPolicyMaxSats()
				Text("Automated (max \(numSats))")
					.font(.callout)
					.foregroundColor(.secondary)
			}
		}
		.padding([.top, .bottom], 8)
	}
	
	@ViewBuilder
	func section_outgoingPayments() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				Text("Maximum fee for outgoing Lightning payments")
					.padding(.bottom, 8)
				
				Button {
					showMaxFeeSheet()
				} label: {
					Text(maxFeesString())
				}
				
			} // </VStack>
			.padding([.top, .bottom], 8)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				Text("Phoenix will try to make the payment using the minimum fee possible.")
			}
			.font(.callout)
			.foregroundColor(Color.secondary)
			.padding([.top, .bottom], 8)
			
		} /* Section.*/header: {
			Text("Outgoing payments")
			
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
			case .LiquidityPolicy : LiquidityPolicyView()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func liquidityPolicyMaxSats() -> String {
		
		let sats = Prefs.shared.liquidityPolicy.effectiveMaxFeeSats
		return Utils.formatBitcoin(sat: sats, bitcoinUnit: .sat).string
	}
	
	func maxFeesString() -> String {
		
		let currentFees = userDefinedMaxFees ?? defaultMaxFees()
		
		let base = Utils.formatBitcoin(sat: currentFees.feeBaseSat, bitcoinUnit: .sat)
		let proportional = formatProportionalFee(currentFees.feeProportionalMillionths)
		
		return "\(base.string) + \(proportional)%"
	}
	
	func formatFeePercent() -> String {
		
		let formatter = NumberFormatter()
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: payToOpen_feePercent))!
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
				case .backgroundPayments : break
				case .liquiditySettings  : newNavLinkTag = NavLinkTag.LiquidityPolicy
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
	
	func showMaxFeeSheet() {
		log.trace("showMaxFeeSheet()")
		
		smartModalState.display(dismissable: false) {
			MaxFeeConfiguration()
		}
	}
	
	func openFaqButtonTapped() -> Void {
		log.trace("openFaqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func maxFeesChanged(_ newMaxFees: MaxFees?) {
		log.trace("maxFeesChanged()")
		
		userDefinedMaxFees = newMaxFees
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) {
		log.trace("chainContextChanged()")
		
		payToOpen_feePercent = context.payToOpen.v1.feePercent * 100 // 0.01 => 1%
		payToOpen_minFeeSat = context.payToOpen.v1.minFeeSat
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

func defaultMaxFees() -> MaxFees {
	
	let peer = Biz.business.getPeer()
	if let defaultMaxFees = peer?.walletParams.trampolineFees.last {
		return MaxFees.fromTrampolineFees(defaultMaxFees)
	} else {
		return MaxFees(feeBaseSat: 0, feeProportionalMillionths: 0)
	}
}

func formatProportionalFee(_ feeProportionalMillionths: Int64) -> String {
	
	let percent = Double(feeProportionalMillionths) / Double(1_000_000)
	
	let formatter = NumberFormatter()
	formatter.numberStyle = .percent
	formatter.percentSymbol = ""
	formatter.paddingCharacter = ""
	formatter.minimumFractionDigits = 2
	
	return formatter.string(from: NSNumber(value: percent))!
}
