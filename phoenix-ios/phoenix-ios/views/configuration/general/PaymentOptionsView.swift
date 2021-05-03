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
	
	@State var payToOpen_feePercent: Double = 0.0
	@State var payToOpen_minFeeSat: Int64 = 0
	
	@Environment(\.openURL) var openURL
	
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	var body: some View {
		
		Form {
			Section {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Default payment description")
						.padding(.bottom, 8)
					
					HStack {
						TextField("None", text: $defaultPaymentDescription)
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
				}
				.padding([.top, .bottom], 8)
			}
			
			Section {
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					Text("Fee for on-the-fly channel creation:")
						.padding(.bottom, 8)
					
					let feePercent = String(format:"%.2f", payToOpen_feePercent)
					let minFee = Utils.formatBitcoin(sat: payToOpen_minFeeSat, bitcoinUnit: .sat)
					
					Text("\(feePercent)% (\(minFee.string) minimum)")
					//	.font(.system(.callout, design: .monospaced))
					//	.fontWeight(.medium)
					//	.foregroundColor(.appAccent)
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
			}
		}
		.navigationBarTitle("Payment Options")
		.onAppear {
			UITableView.appearance().contentInset.top = -35
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
	}
	
	func defaultPaymentDescriptionChanged() -> Void {
		log.trace("defaultPaymentDescriptionChanged(): \(defaultPaymentDescription)")
		
		Prefs.shared.defaultPaymentDescription = self.defaultPaymentDescription
	}
	
	func openFaqButtonTapped() -> Void {
		log.trace("openFaqButtonTapped()")
		
		if let url = URL(string: "https://phoenix.acinq.co/faq") {
			openURL(url)
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		payToOpen_feePercent = context.payToOpen.v1.feePercent
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
