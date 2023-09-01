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


struct PaymentOptionsView: View {
	
	@State var defaultPaymentDescription: String = Prefs.shared.defaultPaymentDescription ?? ""
	
	@State var invoiceExpirationDays: Int = Prefs.shared.invoiceExpirationDays
	let invoiceExpirationDaysOptions = [7, 30, 60]

	@State var userDefinedMaxFees: MaxFees? = Prefs.shared.maxFees
	
	let maxFeesPublisher = Prefs.shared.maxFeesPublisher
	
	@Environment(\.openURL) var openURL
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
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(maxFeesPublisher) {
			maxFeesChanged($0)
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
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func maxFeesString() -> String {
		
		let currentFees = userDefinedMaxFees ?? defaultMaxFees()
		
		let base = Utils.formatBitcoin(sat: currentFees.feeBaseSat, bitcoinUnit: .sat)
		let proportional = formatProportionalFee(currentFees.feeProportionalMillionths)
		
		return "\(base.string) + \(proportional)%"
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
}

func defaultMaxFees() -> MaxFees {
	
	let peer = Biz.business.peerManager.peerStateValue()
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
