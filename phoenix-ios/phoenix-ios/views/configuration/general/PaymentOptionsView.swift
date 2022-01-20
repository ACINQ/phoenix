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
	
	@State var payToOpen_feePercent: Double = 0.0
	@State var payToOpen_minFeeSat: Int64 = 0
	
	@Environment(\.openURL) var openURL
	
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
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
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
	
	func openFaqButtonTapped() -> Void {
		log.trace("openFaqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		payToOpen_feePercent = context.payToOpen.v1.feePercent * 100 // 0.01 => 1%
		payToOpen_minFeeSat = context.payToOpen.v1.minFeeSat
	}
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
