import SwiftUI
import AVFoundation
import PhoenixShared
import UIKit
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SendView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct SendView: MVIView {
	
	@StateObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var paymentRequest: String? = nil
	@State var isWarningDisplayed: Bool = false
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(firstModel: Scan.Model? = nil) {
		
		self._mvi = StateObject.init(wrappedValue: MVIState.init {
			$0.scan(firstModel: firstModel ?? Scan.ModelReady())
		})
	}
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			main
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model, perform: { newModel in

			if let newModel = newModel as? Scan.ModelBadRequest {
				showErrorToast(newModel)
			}
			else if let model = newModel as? Scan.ModelDangerousRequest {
				paymentRequest = model.request
				isWarningDisplayed = true
			}
			else if let model = newModel as? Scan.ModelValidate {
				paymentRequest = model.request
			}
			else if newModel is Scan.ModelSending {
				// Pop self from NavigationStack; Back to HomeView
				presentationMode.wrappedValue.dismiss()
			}
		})
		.onReceive(AppDelegate.get().externalLightningUrlPublisher, perform: { (url: URL) in
			didReceiveExternalLightningUrl(url)
		})
	}

	@ViewBuilder
	var main: some View {
		
		switch mvi.model {
		case _ as Scan.ModelReady,
		     _ as Scan.ModelBadRequest,
		     _ as Scan.ModelDangerousRequest:

			ScanView(
				mvi: mvi,
				paymentRequest: $paymentRequest,
				isWarningDisplayed: $isWarningDisplayed
			)

		case let model as Scan.ModelValidate:
			ValidateView(model: model, postIntent: mvi.intent)

		case let m as Scan.ModelSending:
			SendingView(model: m)

		default:
			fatalError("Unknown model \(mvi.model)")
		}
	}
	
	func showErrorToast(_ model: Scan.ModelBadRequest) -> Void {
		log.trace("showErrorToast()")
		
		let msg: String
		if let reason = model.reason as? Scan.BadRequestReasonChainMismatch {
			
			let requestChain = reason.requestChain?.name ?? "unknown"
			msg = NSLocalizedString(
				"The invoice is for \(requestChain), but you're on \(reason.myChain.name)",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else if model.reason is Scan.BadRequestReasonIsLnUrl {
			
			msg = NSLocalizedString(
				"Phoenix does not support the LNURL protocol yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		} else if model.reason is Scan.BadRequestReasonIsBitcoinAddress {
			
			msg = NSLocalizedString(
				"""
				You scanned a bitcoin address. Phoenix currently only supports sending Lightning payments. \
				You can use a third-party service to make the offchain->onchain swap.
				""",
				comment: "Error message - scanning lightning invoice"
			)
			
		} else if model.reason is Scan.BadRequestReasonAlreadyPaidInvoice {
			
			msg = NSLocalizedString(
				"You've already paid this invoice. Paying it again could result in stolen funds.",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else {
		
			msg = NSLocalizedString(
				"This doesn't appear to be a Lightning invoice",
				comment: "Error message - scanning lightning invoice"
			)
		}
		toast.pop(
			Text(msg).multilineTextAlignment(.center).anyView,
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 30.0,
			location: .middle,
			showCloseButton: true
		)
	}
	
	func didReceiveExternalLightningUrl(_ url: URL) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
		
		mvi.intent(Scan.IntentParse(request: url.absoluteString))
	}
}

struct ScanView: View, ViewName {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@Binding var paymentRequest: String?
	@Binding var isWarningDisplayed: Bool
	
	@State var ignoreScanner: Bool = false // subtle timing bug described below
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	// Subtle timing bug:
	//
	// Steps to reproduce:
	// - scan payment without amount (and without trampoline support)
	// - warning popup is displayed
	// - keep QRcode within camera screen while tapping Confirm button
	//
	// What happens:
	// - the validate screen is not displayed as it should be
	//
	// Why:
	// - the warning popup is displayed
	// - user taps "confirm"
	// - we send IntentConfirmEmptyAmount to library
	// - QrCodeScannerView fires
	// - we send IntentParse to library
	// - library sends us ModelValidate
	// - library sends us ModelRequestWithoutAmount

	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			VStack {
				
				QrCodeScannerView {(request: String) in
					didScanQRCode(request)
				}
				
				Button {
					pasteFromClipboard()
				} label: {
					Image(systemName: "arrow.right.doc.on.clipboard")
					Text("Paste from clipboard")
						.font(.title2)
				}
				.disabled(!UIPasteboard.general.hasStrings)
				.padding([.top, .bottom])
			}
		}
		.frame(maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Scan a QR code", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(2) // [SendingView, ValidateView, ScanView]
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .move(edge: .bottom)
			)
		)
		.onChange(of: isWarningDisplayed) { newValue in
			if newValue {
				showWarning()
			}
		}
	}
	
	func didScanQRCode(_ request: String) -> Void {
		
		if !isWarningDisplayed && !ignoreScanner {
			mvi.intent(Scan.IntentParse(request: request))
		}
	}
	
	func pasteFromClipboard() -> Void {
		log.trace("[\(viewName)] pasteFromClipboard()")
		
		if let request = UIPasteboard.general.string {
			mvi.intent(Scan.IntentParse(request: request))
		}
	}
	
	func showWarning() -> Void {
		log.trace("[\(viewName)] showWarning()")
		
		guard
			let paymentRequest = paymentRequest,
			let model = mvi.model as? Scan.ModelDangerousRequest
		else {
			return
		}
		
		ignoreScanner = true
		popoverState.display.send(PopoverItem(
			
			DangerousInvoiceAlert(
				model: model,
				postIntent: mvi.intent,
				paymentRequest: paymentRequest,
				isShowing: $isWarningDisplayed,
				ignoreScanner: $ignoreScanner
			).anyView,
			dismissable: false
		))
	}
}

struct DangerousInvoiceAlert : View, ViewName {

	let model: Scan.ModelDangerousRequest
	let postIntent: (Scan.Intent) -> Void
	let paymentRequest: String

	@Binding var isShowing: Bool
	@Binding var ignoreScanner: Bool
	
	@Environment(\.popoverState) var popoverState: PopoverState

	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {

			Text("Warning")
				.font(.title2)
				.padding(.bottom)
			
			if model.reason is Scan.DangerousRequestReasonIsAmountlessInvoice {
				content_amountlessInvoice
			} else if model.reason is Scan.DangerousRequestReasonIsOwnInvoice {
				content_ownInvoice
			} else {
				content_unknown
			}

			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Spacer()
				
				Button("Cancel") {
					didCancel()
				}
				.font(.title3)
				.padding(.trailing)
					
				Button("Continue") {
					didConfirm()
				}
				.font(.title3)
				.disabled(isUnknownType())
			}
			.padding(.top, 30)
			
		} // </VStack>
		.padding()
	}
	
	@ViewBuilder
	var content_amountlessInvoice: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text(styled: NSLocalizedString(
				"""
				The invoice doesn't include an amount. This can be dangerous: \
				malicious nodes may be able to steal your payment. To be safe, \
				**ask the payee to specify an amount**  in the payment request.
				""",
				comment: "SendView"
			))
			.padding(.bottom)

			Text("Are you sure you want to pay this invoice?")
		}
	}
	
	@ViewBuilder
	var content_ownInvoice: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("The invoice is for you. You are about to pay yourself.")
		}
	}
	
	@ViewBuilder
	var content_unknown: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Something is amiss with this invoice...")
		}
	}
	
	func isUnknownType() -> Bool {
		
		if model.reason is Scan.DangerousRequestReasonIsAmountlessInvoice {
			return false
		} else if model.reason is Scan.DangerousRequestReasonIsOwnInvoice {
			return false
		} else {
			return true
		}
	}
	
	func didCancel() -> Void {
		log.trace("[\(viewName)] didCancel()")
		
		isShowing = false
		ignoreScanner = false
		popoverState.close.send()
	}
	
	func didConfirm() -> Void {
		log.trace("[\(viewName)] didConfirm()")
		
		isShowing = false
		postIntent(Scan.IntentConfirmDangerousRequest(request: paymentRequest))
		popoverState.close.send()
	}
}

struct ValidateView: View, ViewName {
	
	let model: Scan.ModelValidate
	let postIntent: (Scan.Intent) -> Void

	@State var number: Double = 0.0
	
	@State var unit: CurrencyUnit = CurrencyUnit(bitcoinUnit: BitcoinUnit.sat)
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isExpiredInvoice: Bool = false
	
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@Environment(\.colorScheme) var colorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			unit: $unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}
	
	func disconnectedText() -> String {
		
		if connectionsMonitor.connections.internet != Lightning_kmpConnection.established {
			return NSLocalizedString("waiting for internet", comment: "button text")
		}
		if connectionsMonitor.connections.peer != Lightning_kmpConnection.established {
			return NSLocalizedString("connecting to peer", comment: "button text")
		}
		if connectionsMonitor.connections.electrum != Lightning_kmpConnection.established {
			return NSLocalizedString("connecting to electrum", comment: "button text")
		}
		return ""
	}
	
	var body: some View {
		
		let isDisconnected = connectionsMonitor.connections.global != .established
		ZStack {
		
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
					.onTapGesture {
						dismissKeyboardIfVisible()
					}
			} else {
				Color.clear
					.ignoresSafeArea(.all, edges: .all)
					.contentShape(Rectangle())
					.onTapGesture {
						dismissKeyboardIfVisible()
					}
			}
			
			VStack {
		
				HStack(alignment: .firstTextBaseline) {
					TextField(verbatim: "123", text: currencyStyler().amountProxy)
						.keyboardType(.decimalPad)
						.disableAutocorrection(true)
						.fixedSize()
						.font(.title)
						.multilineTextAlignment(.trailing)
						.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
				
					Picker(selection: $unit, label: Text(unit.abbrev).frame(minWidth: 40)) {
						let options = CurrencyUnit.displayable(currencyPrefs: currencyPrefs)
						ForEach(0 ..< options.count) {
							let option = options[$0]
							Text(option.abbrev).tag(option)
						}
					}
					.pickerStyle(MenuPickerStyle())
	
				} // </HStack>
				.padding([.leading, .trailing])
				.background(
					VStack {
						Spacer()
						Line().stroke(Color.appAccent, style: StrokeStyle(lineWidth: 2, dash: [3]))
							.frame(height: 1)
					}
				)
				
				Text(altAmount)
					.font(.caption)
					.foregroundColor((isInvalidAmount || isExpiredInvoice) ? Color.appNegative : .secondary)
					.padding(.top, 4)
				
				Text(model.requestDescription ?? "")
					.padding()
					.padding([.top, .bottom])
				
				Button {
					sendPayment()
				} label: {
					HStack {
						Image("ic_send")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.foregroundColor(Color.white)
							.frame(width: 22, height: 22)
						Text("Pay")
							.font(.title2)
							.foregroundColor(Color.white)
					}
					.padding(.top, 4)
					.padding(.bottom, 5)
					.padding([.leading, .trailing], 24)
				}
				.buttonStyle(ScaleButtonStyle(
					backgroundFill: Color.appAccent,
					disabledBackgroundFill: Color.gray
				))
				.disabled(isInvalidAmount || isExpiredInvoice || isDisconnected)
			
				if !isInvalidAmount && !isExpiredInvoice && isDisconnected {
					
					Button {
						showConnectionsPopover()
					} label: {
						HStack {
							ProgressView()
								.progressViewStyle(CircularProgressViewStyle())
								.padding(.trailing, 1)
							Text(disconnectedText())
						}
					}
					.padding(.top, 4)
				}
				
			} // </VStack>
			
		}// </ZStack>
		.navigationBarTitle(
			NSLocalizedString("Confirm Payment", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(1) // [SendingView, ValidateView, ScanView]
		.transition(.asymmetric(insertion: .identity, removal: .opacity))
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: unit) { _  in
			unitDidChange()
		}
	}
	
	func onAppear() -> Void {
		log.trace("[\(viewName)] onAppear()")
		
		let bitcoinUnit = currencyPrefs.bitcoinUnit
		unit = CurrencyUnit(bitcoinUnit: bitcoinUnit)
		
		if let msat_kotlin = model.amountMsat {
			let msat = Int64(truncating: msat_kotlin)
			
			let targetAmt = Utils.convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
			let formattedAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
			
			parsedAmount = Result.success(targetAmt) // do this first !
			amount = formattedAmt.digits
		}
	}
	
	func dismissKeyboardIfVisible() -> Void {
		log.trace("[\(viewName)] dismissKeyboardIfVisible()")
		
		let keyWindow = UIApplication.shared.connectedScenes
			.filter({ $0.activationState == .foregroundActive })
			.map({ $0 as? UIWindowScene })
			.compactMap({ $0 })
			.first?.windows
			.filter({ $0.isKeyWindow }).first
		keyWindow?.endEditing(true)
	}
	
	func amountDidChange() -> Void {
		log.trace("[\(viewName)] amountDidChange()")
		
		refreshAltAmount()
	}
	
	func unitDidChange() -> Void {
		log.trace("[\(viewName)] unitDidChange()")
		
		// We might want to apply a different formatter
		let result = TextFieldCurrencyStyler.format(input: amount, unit: unit, hideMsats: false)
		parsedAmount = result.1
		amount = result.0
		
		refreshAltAmount()
	}
	
	func refreshAltAmount() -> Void {
		log.trace("[\(viewName)] refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			isInvalidAmount = true
			
			switch error {
			case .emptyInput:
				altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			case .invalidInput:
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			isInvalidAmount = false
			
			var msat: Int64? = nil
			var alt: FormattedAmount? = nil
			
			if let bitcoinUnit = unit.bitcoinUnit {
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					
					msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					alt = Utils.formatFiat(msat: msat!, exchangeRate: exchangeRate)
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = ""
				}
				
			} else if let fiatCurrency = unit.fiatCurrency {
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					alt = Utils.formatBitcoin(msat: msat!, bitcoinUnit: currencyPrefs.bitcoinUnit)
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since CurrencyUnit.displayable() already filters for this.
					altAmount = ""
				}
			}
			
			if let msat = msat, let alt = alt {
				if msat > model.balanceMsat {
					isInvalidAmount = true
					altAmount = NSLocalizedString("Amount exceeds your balance", comment: "error message")
					
				} else {
					altAmount = "≈ \(alt.string)"
				}
			}
			
			if let expiryTimestamp = model.expiryTimestamp?.doubleValue,
			   Date(timeIntervalSince1970: expiryTimestamp) <= Date()
			{
				isExpiredInvoice = true
				if !isInvalidAmount {
					altAmount = NSLocalizedString("Invoice is expired", comment: "error message")
				}
			} else {
				isExpiredInvoice = false
			}
			
		} // </switch parsedAmount>
	}
	
	func sendPayment() -> Void {
		log.trace("[\(viewName)] sendPayment()")
		
		guard
			let amt = try? parsedAmount.get(),
			amt > 0
		else {
			return
		}
		
		if let bitcoinUnit = unit.bitcoinUnit {

            let msat = Lightning_kmpMilliSatoshi(msat: Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit))
			postIntent(Scan.IntentSend(request: model.request, amount: msat))
			
		} else if let fiatCurrency = unit.fiatCurrency,
		          let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
		{
            let msat = Lightning_kmpMilliSatoshi(msat: Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate))
			postIntent(Scan.IntentSend(request: model.request, amount: msat))
		}
	}
	
	func showConnectionsPopover() -> Void {
		log.trace("[\(viewName)] showConnectionsPopover()")
		
		popoverState.display.send(PopoverItem(
			
			ConnectionsPopover().anyView,
			dismissable: true
		))
	}
}

struct SendingView: View {
	let model: Scan.ModelSending

	var body: some View {
		
		ZStack {
		
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
			}
			
			VStack {
				Text("Sending Payment...")
					.font(.title)
					.padding()
			}
		}
		.frame(maxHeight: .infinity)
		.background(Color.primaryBackground)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.navigationBarTitle(
			NSLocalizedString("Sending payment", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(0) // [SendingView, ValidateView, ScanView]
	}
}

// MARK:-

class SendView_Previews: PreviewProvider {
	
	static let request = "lntb15u1p0hxs84pp5662ywy9px43632le69s5am03m6h8uddgln9cx9l8v524v90ylmesdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5xr4khzu3xter2z7dldnl3eqggut200vzth6cj8ppmqvx29hzm30q0as63ks9zddk3l5vf46lmkersynge3fy9nywwn8z8ttfdpak5ka9dvcnfrq95e6s06jacnsdryq8l8mrjkrfyd3vxgyv4axljvplmwsqae7yl9"

	static var previews: some View {
		
		NavigationView {
			SendView().mock(Scan.ModelValidate(
				request: request,
				amountMsat: 1_500,
				expiryTimestamp: nil,
				requestDescription: "1 Blockaccino",
				balanceMsat: 300_000_000
			))
		}
		.modifier(GlobalEnvironment())
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			SendingView(model: Scan.ModelSending())
		}
		.modifier(GlobalEnvironment())
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")

		NavigationView {
			SendingView(model: Scan.ModelSending())
		}
		.modifier(GlobalEnvironment())
		.preferredColorScheme(.dark)
		.previewDevice("iPhone 8")
	}
}
