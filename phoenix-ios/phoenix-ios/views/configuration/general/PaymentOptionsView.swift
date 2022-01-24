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
	
	@State var payToOpen_feePercent: Double = 0.0
	@State var payToOpen_minFeeSat: Int64 = 0
	
	@Environment(\.openURL) var openURL
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	let maxFeesPublisher = Prefs.shared.maxFeesPublisher
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	var body: some View {
		
		List {
			
			Section {
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
							.stroke(Color(UIColor.separator), lineWidth: 1)
					)
				} // </VStack>
				.padding([.top, .bottom], 8)
			} // </Section>
			
			Section {
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Incoming payment expiry")
						.padding(.bottom, 8)
					
					Picker(
						selection: Binding(
							get: { invoiceExpirationDays },
							set: { invoiceExpirationDays = $0 }
						), label: Text("Invoice expiration")
					) {
						ForEach(0 ..< invoiceExpirationDaysOptions.count) {
							let days = invoiceExpirationDaysOptions[$0]
							Text("\(days) days").tag(days)
						}
					}
					.pickerStyle(SegmentedPickerStyle())
					.onChange(of: invoiceExpirationDays) { _ in
						invoiceExpirationDaysChanged()
					}
					
				} // </VStack>
				.padding([.top, .bottom], 8)
				
			} // </Section>

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
				
			} // </Section>
			
			Section {
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Fee for on-the-fly channel creation:")
						.padding(.bottom, 8)
					
					let feePercent = formatFeePercent()
					let minFee = Utils.formatBitcoin(sat: payToOpen_minFeeSat, bitcoinUnit: .sat)
					
					// This doesn't get translated properly. SwiftUI localization bug ?
				//	Text("\(feePercent)% (\(minFee.string) minimum)")
					
					Text(String(format: NSLocalizedString(
						"%@%% (%@ minimum)",
						comment: "Minimum fee information. E.g.: 1% (3,000 sats minimum)"),
						feePercent, minFee.string
					))
				}
				.padding([.top, .bottom], 8)
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("This fee applies when you receive a payment over Lightning and a new channel needs to be created.")
						.padding(.bottom, 12)
					
					Text("This fee is dynamic, and may change depending on the conditions of the bitcoin network.")
						.padding(.bottom, 12)
					
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text("For more information, see the FAQ")
							.padding(.trailing, 6)
						Button {
							openFaqButtonTapped()
						} label: {
							Image(systemName: "link.circle.fill")
								.imageScale(.medium)
						}
					}
				}
				.font(.callout)
				.foregroundColor(Color.secondary)
				.padding([.top, .bottom], 8)
			
			} // </Section>
		} // </List>
		.listStyle(.insetGrouped)
		.navigationBarTitle(NSLocalizedString("Payment Options", comment: "Navigation Bar Title"))
		.onReceive(maxFeesPublisher) {
			maxFeesChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
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
		
		shortSheetState.display(dismissable: false) {
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
}

struct MaxFeeConfiguration: View, ViewName {
	
	@State var baseFee: String = ""
	@State var parsedBaseFee: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	@State var isInvalidBaseFee: Bool = false
	
	@State var proportionalFee: String = ""
	@State var parsedProportionalFee: Result<NSNumber, TextFieldNumberParserError> = Result.failure(.emptyInput)
	@State var isInvalidProportionalFee: Bool = false
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Base fee (satoshis)")
				.padding(.leading, 8)
				.padding(.bottom, 6)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					defaultBaseFee(),
					text: currencyStyler().amountProxy
				)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.foregroundColor(isInvalidBaseFee ? Color.appNegative : Color.primaryForeground)
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					clearBaseFee()
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(baseFee == "")
				.padding(.trailing, 8)
			}
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color(UIColor.separator), lineWidth: 1)
			)
			.padding(.bottom)
			
			Text("Proportional fee (%)")
				.padding(.leading, 8)
				.padding(.bottom, 6)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					defaultProportionalFee(),
					text: percentParser().amountProxy
				)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.foregroundColor(isInvalidProportionalFee ? Color.appNegative : Color.primaryForeground)
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					clearProportionalFee()
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(proportionalFee == "")
				.padding(.trailing, 8)
			}
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color(UIColor.separator), lineWidth: 1)
			)
			.padding(.bottom)
			
			HStack {
				Spacer()
				
				Button("Cancel") {
					didTapCancelButton()
				}
				.padding(.trailing)
				
				Button("Save") {
					didTapSaveButton()
				}
				.disabled(isInvalidBaseFee || isInvalidProportionalFee)
			}
			.font(.title2)
		}
		.padding()
		.onAppear {
			onAppear()
		}
		.onChange(of: baseFee) { _ in
			baseFeeDidChange()
		}
		.onChange(of: proportionalFee) { _ in
			proportionalFeeDidChange()
		}
	}
	
	func defaultBaseFee() -> String {
		
		let fees = defaultMaxFees()
		let formatted = Utils.formatBitcoin(sat: fees.feeBaseSat, bitcoinUnit: .sat)
		return formatted.digits
	}
	
	func defaultProportionalFee() -> String {
		
		let fees = defaultMaxFees()
		return formatProportionalFee(fees.feeProportionalMillionths)
	}
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: Currency.bitcoin(.sat),
			amount: $baseFee,
			parsedAmount: $parsedBaseFee,
			hideMsats: false
		)
	}
	
	func percentParser() -> TextFieldNumberParser {
		return TextFieldNumberParser(
			formatter: NumberFormatter(),
			amount: $proportionalFee,
			parsedAmount: $parsedProportionalFee
		)
	}
	
	func onAppear() {
		log.trace("[\(viewName)] onAppear()")
		
		if let maxFees = Prefs.shared.maxFees {
			
			let feeBaseFormatted = Utils.formatBitcoin(sat: maxFees.feeBaseSat, bitcoinUnit: .sat)
			parsedBaseFee = Result.success(Double(maxFees.feeBaseSat))
			baseFee = feeBaseFormatted.digits
			
			let percent = Double(maxFees.feeProportionalMillionths) / Double(1_000_000)
			parsedProportionalFee = Result.success(NSNumber(value: percent))
			proportionalFee = formatProportionalFee(maxFees.feeProportionalMillionths)
		}
	}
	
	func baseFeeDidChange() {
		log.trace("[\(viewName)] baseFeeDidChange()")
		
		switch parsedBaseFee {
		case .failure:
			if baseFee.isEmpty {
				isInvalidBaseFee = false // user choosing default value
			} else {
				isInvalidBaseFee = true
			}
		case .success:
			isInvalidBaseFee = false
		}
	}
	
	func proportionalFeeDidChange() {
		log.trace("[\(viewName)] proportionalFeeDidChange()")
		
		switch parsedProportionalFee {
		case .failure:
			if proportionalFee.isEmpty {
				isInvalidProportionalFee = false // user choosing default value
			} else {
				isInvalidProportionalFee = true
			}
		case .success:
			isInvalidProportionalFee = false
		}
	}
	
	func clearBaseFee() {
		log.trace("[\(viewName)] clearBaseFee()")
		
		parsedBaseFee = .failure(.emptyInput)
		baseFee = ""
	}
	
	func clearProportionalFee() {
		log.trace("[\(viewName)] clearProportionalFee()")
		
		parsedProportionalFee = .failure(.emptyInput)
		proportionalFee = ""
	}
	
	func didTapCancelButton() {
		log.trace("[\(viewName)] didTapCancelButton()")
		
		shortSheetState.close()
	}
	
	func didTapSaveButton() {
		log.trace("[\(viewName)] didTapSaveButton()")
		
		// Reminder: Our state variables:
		//
		// parsedBaseFee: Result<Double, TextFieldCurrencyStylerError>
		// parsedProportionalFee: Result<NSNumber, TextFieldNumberParserError>
		
		var shouldSave = false
		
		var newBaseFee_sat: Int64? = nil
		var newProportionalFee_millionths: Int64? = nil
		
		if let newBaseFee_double = try? parsedBaseFee.get(), newBaseFee_double > 0 {
			
			newBaseFee_sat = Int64(newBaseFee_double)
			shouldSave = true
		}
		if let newProportionalFee_number = try? parsedProportionalFee.get() {
			let newProportionalFee_double = newProportionalFee_number.doubleValue
			if newProportionalFee_double > 0 {
				
				// Example:
				// newProportionalFee_double: 0.5 <- is a a percent, i.e. 0.5%
				// toNumber: 0.5 / 100 = 0.005
				// toMillionths: 0.005 * 1,000,000 = 5,000
				//
				// Or, to simplify:
				// toMillionths: 0.5 * 10,000 = 5,000
				
				newProportionalFee_millionths = Int64(newProportionalFee_double * Double(10_000))
				shouldSave = true
			}
		}
		
		if shouldSave {
			let defaults = defaultMaxFees()
			Prefs.shared.maxFees = MaxFees(
				feeBaseSat: newBaseFee_sat ?? defaults.feeBaseSat,
				feeProportionalMillionths: newProportionalFee_millionths ?? defaults.feeProportionalMillionths
			)
		} else {
			Prefs.shared.maxFees = nil
		}
		
		shortSheetState.close()
	}
}

fileprivate func defaultMaxFees() -> MaxFees {
	
	let peer = AppDelegate.get().business.getPeer()
	if let defaultMaxFees = peer?.walletParams.trampolineFees.last {
		return MaxFees.fromTrampolineFees(defaultMaxFees)
	} else {
		return MaxFees(feeBaseSat: 0, feeProportionalMillionths: 0)
	}
}

fileprivate func formatProportionalFee(_ feeProportionalMillionths: Int64) -> String {
	
	let percent = Double(feeProportionalMillionths) / Double(1_000_000)
	
	let formatter = NumberFormatter()
	formatter.numberStyle = .percent
	formatter.percentSymbol = ""
	formatter.paddingCharacter = ""
	formatter.minimumFractionDigits = 2
	
	return formatter.string(from: NSNumber(value: percent))!
}

class PaymentOptionsView_Previews: PreviewProvider {
	
	static var previews: some View {
		
		NavigationView {
			PaymentOptionsView()
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
	}
}
