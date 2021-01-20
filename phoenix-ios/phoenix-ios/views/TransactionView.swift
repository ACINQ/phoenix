import SwiftUI
import PhoenixShared

struct PaymentView : View {

	let payment: PhoenixShared.Eclair_kmpWalletPayment
	let close: () -> Void
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	var body: some View {
		
		ZStack {
			VStack {
				HStack {
					Spacer()
					Button {
						close()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.padding(32)
				}
				Spacer()
			}

			VStack {
				switch (payment.status()) {
				case .success:
				//	Image(vector: "ic_payment_success_static") // looks pixelated
					Image(systemName: "checkmark.circle")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 100, height: 100)
						.foregroundColor(.appGreen)
					VStack {
						Text(payment.amountMsat() < 0 ? "SENT" : "RECEIVED")
							.font(Font.title2.bold())
							.padding(.bottom, 2)
						Text(payment.timestamp().formatDateMS())
							.font(.subheadline)
							.foregroundColor(.secondary)
					}
					.padding()
					
				case .pending:
					Image("ic_send")
						.renderingMode(.template)
						.resizable()
						.foregroundColor(Color(UIColor.systemGray))
						.frame(width: 100, height: 100)
					Text("PENDING")
						.font(Font.title2.bold())
						.padding()
					
				case .failure:
				//	Image(vector: "ic_cross") // looks pixelated
					Image(systemName: "xmark.circle")
						.renderingMode(.template)
						.resizable()
						.frame(width: 100, height: 100)
						.foregroundColor(.appRed)
					VStack {
						Text("FAILED")
							.font(Font.title2.bold())
							.padding(.bottom, 2)
                        
						Text("NO FUNDS HAVE BEEN SENT")
							.font(Font.title2.uppercaseSmallCaps())
							.padding(.bottom, 6)
						
						Text(payment.timestamp().formatDateMS())
							.font(Font.subheadline)
							.foregroundColor(.secondary)
						
					}
					.padding()
					
				default:
					EmptyView()
				}

				HStack(alignment: .bottom) {
					let amount = Utils.format(currencyPrefs, msat: payment.amountMsat(), hideMsats: false)
					
					Text(amount.digits)
						.font(.largeTitle)
						.onTapGesture { toggleCurrencyType() }
					Text(amount.type)
						.font(.title3)
						.foregroundColor(.gray)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
				}
				.padding()
				.background(
					VStack {
						Spacer()
						Line().stroke(Color.appHorizon, style: StrokeStyle(lineWidth: 4, lineCap: .round))
							.frame(height: 4)
					}
				)
				
				HStack(alignment: .top) {
					let desc = payment.desc() ?? NSLocalizedString("No description", comment: "placeholder text")
					
					Text("Desc")
						.foregroundColor(.secondary)
					Text(desc)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = desc
							}) {
								Text("Copy")
							}
						}
				}
				.padding(.top, 40)
				.padding([.leading, .trailing])
			}
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}

//class PaymentView_Previews : PreviewProvider {
	
//    static var previews: some View {
//        PaymentView(payment: PhoenixShared.Mock.outgoingPending(), close: {})
//			.preferredColorScheme(.dark)
//			.environmentObject(CurrencyPrefs.mockEUR())
//
//        PaymentView(payment: PhoenixShared.Mock.outgoingSuccessful(), close: {})
//			.preferredColorScheme(.dark)
//			.environmentObject(CurrencyPrefs.mockEUR())
//
//        PaymentView(payment: PhoenixShared.Mock.outgoingFailed(), close: {})
//			.preferredColorScheme(.dark)
//			.environmentObject(CurrencyPrefs.mockEUR())
//
//        PaymentView(payment: PhoenixShared.Mock.incomingPaymentReceived(), close: {})
//			.preferredColorScheme(.dark)
//			.environmentObject(CurrencyPrefs.mockEUR())
//	}
//
//	#if DEBUG
//	@objc class func injected() {
//		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
//	}
//	#endif
//}
