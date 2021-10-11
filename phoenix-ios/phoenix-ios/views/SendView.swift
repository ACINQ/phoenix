import SwiftUI
import Combine
import AVFoundation
import PhoenixShared
import UIKit
import os.log

#if DEBUG && true
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
			$0.scan(firstModel: firstModel ?? Scan.Model_Ready())
		})
	}
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			content
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onReceive(AppDelegate.get().externalLightningUrlPublisher) { (url: URL) in
			didReceiveExternalLightningUrl(url)
		}
	}

	@ViewBuilder
	var content: some View {
		
		switch mvi.model {
		case _ as Scan.Model_Ready,
		     _ as Scan.Model_BadRequest,
		     _ as Scan.Model_InvoiceFlow_DangerousRequest,
		     _ as Scan.Model_LnurlServiceFetch:

			ScanView(
				mvi: mvi,
				paymentRequest: $paymentRequest,
				isWarningDisplayed: $isWarningDisplayed
			)

		case _ as Scan.Model_InvoiceFlow_InvoiceRequest,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayRequest,
		     _ as Scan.Model_LnurlPayFlow_LnUrlPayFetch:

			ValidateView(mvi: mvi)

		case _ as Scan.Model_InvoiceFlow_Sending,
		     _ as Scan.Model_LnurlPayFlow_Sending:

			SendingView(mvi: mvi)

		case _ as Scan.Model_LnurlAuthFlow_LoginRequest,
		     _ as Scan.Model_LnurlAuthFlow_LoggingIn,
		     _ as Scan.Model_LnurlAuthFlow_LoginResult:

			LoginView(mvi: mvi)

		default:
			fatalError("Unknown model \(mvi.model)")
		}
	}
	
	func modelDidChange(_ newModel: Scan.Model) -> Void {
		log.trace("modelDidChange()")
		
		if let newModel = newModel as? Scan.Model_BadRequest {
			showErrorToast(newModel)
		}
		else if let model = newModel as? Scan.Model_InvoiceFlow_DangerousRequest {
			paymentRequest = model.request
			isWarningDisplayed = true
		}
		else if let model = newModel as? Scan.Model_InvoiceFlow_InvoiceRequest {
			paymentRequest = model.request
		}
		else if newModel is Scan.Model_InvoiceFlow_Sending ||
		        newModel is Scan.Model_LnurlPayFlow_Sending
		{
			// Pop self from NavigationStack; Back to HomeView
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func showErrorToast(_ model: Scan.Model_BadRequest) -> Void {
		log.trace("showErrorToast()")
		
		let msg: String
		if let reason = model.reason as? Scan.BadRequestReasonChainMismatch {
			
			let requestChain = reason.requestChain?.name ?? "unknown"
			msg = NSLocalizedString(
				"The invoice is for \(requestChain), but you're on \(reason.myChain.name)",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else if model.reason is Scan.BadRequestReasonUnsupportedLnUrl {
			
			msg = NSLocalizedString(
				"Phoenix does not support this type of LNURL yet",
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
		
		mvi.intent(Scan.Intent_Parse(request: url.absoluteString))
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

	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			content
		}
		.frame(maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Scan a QR code", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(3) // [SendingView, ValidateView, LoginView, ScanView]
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
	
	@ViewBuilder
	var content: some View {
		
		ZStack {
			
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
			
			if mvi.model is Scan.Model_LnurlServiceFetch {
				LnurlFetchNotice(
					title: NSLocalizedString("Fetching Lightning URL", comment: "Progress title"),
					onCancel: { didCancelLnurlServiceFetch() }
				)
			}
		}
	}
	
	func didScanQRCode(_ request: String) {
		
		var isFetchingLnurl = false
		if let _ = mvi.model as? Scan.Model_LnurlServiceFetch {
			isFetchingLnurl = true
		}
		
		if !ignoreScanner && !isWarningDisplayed && !isFetchingLnurl {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
	}
	
	func didCancelLnurlServiceFetch() {
		log.trace("[\(viewName)] didCancelLnurlServiceFetch()")
		
		mvi.intent(Scan.Intent_CancelLnurlServiceFetch())
	}
	
	func pasteFromClipboard() {
		log.trace("[\(viewName)] pasteFromClipboard()")
		
		if let request = UIPasteboard.general.string {
			mvi.intent(Scan.Intent_Parse(request: request))
		}
	}
	
	func showWarning() {
		log.trace("[\(viewName)] showWarning()")
		
		guard let model = mvi.model as? Scan.Model_InvoiceFlow_DangerousRequest else {
			return
		}
		
		ignoreScanner = true
		popoverState.display(dismissable: false) {
			
			DangerousInvoiceAlert(
				model: model,
				intent: mvi.intent,
				isShowing: $isWarningDisplayed,
				ignoreScanner: $ignoreScanner
			)
		}
	}
}

struct LnurlFetchNotice: View, ViewName {
	
	let title: String
	let onCancel: () -> Void
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			Text(title)
			
			ZStack {
				Divider()
				HorizontalActivity(color: .appAccent, diameter: 10, speed: 1.6)
			}
			.frame(width: 125, height: 10)
			
			Button {
				didTapCancel()
			} label: {
				Text("Cancel")
			}
		}
		.padding()
		.background(Color(UIColor.systemBackground))
		.cornerRadius(16)
	}
	
	func didTapCancel() {
		log.trace("[\(viewName)] didTapCancel()")
		onCancel()
	}
}

struct DangerousInvoiceAlert : View, ViewName {

	let model: Scan.Model_InvoiceFlow_DangerousRequest
	let intent: (Scan.Intent) -> Void

	@Binding var isShowing: Bool
	@Binding var ignoreScanner: Bool
	
	@Environment(\.popoverState) var popoverState: PopoverState

	@ViewBuilder
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
		popoverState.close()
	}
	
	func didConfirm() -> Void {
		log.trace("[\(viewName)] didConfirm()")
		
		isShowing = false
		intent(Scan.Intent_InvoiceFlow_ConfirmDangerousRequest(
			request: model.request,
			paymentRequest: model.paymentRequest
		))
		popoverState.close()
	}
}

struct ValidateView: View, ViewName {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@State var unit = Currency.bitcoin(.sat)
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isExpiredInvoice: Bool = false
	
	@State var comment: String = ""
	@State var hasPromptedForComment = false
	
	@StateObject var connectionsManager = ObservableConnectionsManager()
	
	@Environment(\.colorScheme) var colorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
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
		
		if connectionsManager.connections.internet != Lightning_kmpConnection.established {
			return NSLocalizedString("waiting for internet", comment: "button text")
		}
		if connectionsManager.connections.peer != Lightning_kmpConnection.established {
			return NSLocalizedString("connecting to peer", comment: "button text")
		}
		if connectionsManager.connections.electrum != Lightning_kmpConnection.established {
			return NSLocalizedString("connecting to electrum", comment: "button text")
		}
		return ""
	}
	
	var body: some View {
		
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
			
			content
			
			if mvi.model is Scan.Model_LnurlPayFlow_LnUrlPayFetch {
				LnurlFetchNotice(
					title: NSLocalizedString("Fetching Invoice", comment: "Progress title"),
					onCancel: { didCancelLnurlPayFetch() }
				)
			}
			
		}// </ZStack>
		.navigationBarTitle(
			NSLocalizedString("Confirm Payment", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(1) // [SendingView, ValidateView, LoginView, ScanView]
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .opacity
			)
		)
		.onAppear() {
			onAppear()
		}
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: unit) { _  in
			unitDidChange()
		}
	}
	
	@ViewBuilder
	var content: some View {
	
		let isDisconnected = connectionsManager.connections.global != .established
		VStack {
	
			if let host = paymentRequestHost() {
				VStack(alignment: HorizontalAlignment.center, spacing: 10) {
					Text("Payment requested by")
					Text(host).bold()
				}
				.padding(.bottom)
				.padding(.bottom)
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				TextField(verbatim: "123", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.fixedSize()
					.font(.title)
					.multilineTextAlignment(.trailing)
					.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
			
				Picker(selection: $unit, label: Text(unit.abbrev).frame(minWidth: 40)) {
					let options = Currency.displayable(currencyPrefs: currencyPrefs)
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
				.padding(.bottom)
			
			if isLnurlPayRequest() {
				HStack(alignment: VerticalAlignment.center, spacing: 20) {
					if hasExtendedMetadata() {
						metadataButton()
					}
					priceTargetButton()
					if supportsComment() {
						commentButton()
					}
				}
				.padding(.horizontal)
			}
			
			if let description = requestDescription() {
				Text(description)
					.padding()
					.padding(.bottom)
			} else {
				Text("No description")
					.foregroundColor(.secondary)
					.padding()
					.padding(.bottom)
			}
			
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
					showAppStatusPopover()
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
	}
	
	@ViewBuilder
	func actionButton(
		image: Image,
		width: CGFloat = 20,
		height: CGFloat = 20,
		xOffset: CGFloat = 0,
		yOffset: CGFloat = 0,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			ZStack {
				Color.buttonFill
					.frame(width: 30, height: 30)
					.cornerRadius(50)
					.overlay(
						RoundedRectangle(cornerRadius: 50)
							.stroke(Color(UIColor.separator), lineWidth: 1)
					)
				
				image
					.renderingMode(.template)
					.resizable()
					.scaledToFit()
					.frame(width: width, height: height)
					.offset(x: xOffset, y: yOffset)
			}
		}
	}
	
	@ViewBuilder
	func metadataButton() -> some View {
		
		actionButton(
			image: Image(systemName: "info.circle"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			metadataButtonTapped()
		}
	}
	
	@ViewBuilder
	func priceTargetButton() -> some View {
		
		let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest
		let minMsat = model?.lnurlPay.minSendable.msat ?? 0
		let maxMsat = model?.lnurlPay.maxSendable.msat ?? 0
		let isRange = maxMsat > minMsat
		
		actionButton(
			image: Image(systemName: isRange ? "target" : "asterisk.circle"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			priceTargetButtonTapped()
		}
	}
	
	@ViewBuilder
	func commentButton() -> some View {
		
		actionButton(
			image: Image(systemName: "pencil.tip"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			commentButtonTapped()
		}
	}
	
	func lnurlPay() -> LNUrl.Pay? {
		
		if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			return model.lnurlPay
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnUrlPayFetch {
			return model.lnurlPay
		} else {
			return nil
		}
	}
	
	func paymentRequestHost() -> String? {
		
		if let lnurlPay = lnurlPay() {
			return lnurlPay.lnurl.host
		} else {
			return nil
		}
	}
	
	func requestDescription() -> String? {
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			return model.paymentRequest.desc()
		} else if let lnurlPay = lnurlPay() {
			return lnurlPay.metadata.plainText
		} else {
			return nil
		}
	}
	
	func isLnurlPayRequest() -> Bool {
		
		return mvi.model is Scan.Model_LnurlPayFlow_LnurlPayRequest ||
		       mvi.model is Scan.Model_LnurlPayFlow_LnUrlPayFetch
	}
	
	func hasExtendedMetadata() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}
		
		if lnurlPay.metadata.longDesc != nil {
			return true
		}
		if lnurlPay.metadata.imagePng != nil {
			return true
		}
		if lnurlPay.metadata.imageJpg != nil {
			return true
		}
		
		return false
	}
	
	func supportsComment() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}
		
		let maxCommentLength = lnurlPay.maxCommentLength?.int64Value ?? 0
		return maxCommentLength > 0
	}
	
	func supportsPriceRange() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}

		let min = lnurlPay.minSendable.msat
		let max = lnurlPay.maxSendable.msat
		return max > min
	}
	
	func balanceMsat() -> Int64? {
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnUrlPayFetch {
			return model.balanceMsat
		} else {
			return nil
		}
	}
	
	func onAppear() -> Void {
		log.trace("[\(viewName)] onAppear()")
		
		let bitcoinUnit = currencyPrefs.bitcoinUnit
		unit = Currency.bitcoin(bitcoinUnit)
		
		var amount_msat: Lightning_kmpMilliSatoshi? = nil
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			amount_msat = model.paymentRequest.amount
		} else if let lnurlPay = lnurlPay() {
			amount_msat = lnurlPay.minSendable
		}
		
		if let amount_msat = amount_msat {
			
			let targetAmt = Utils.convertBitcoin(msat: amount_msat, bitcoinUnit: bitcoinUnit)
			let formattedAmt = Utils.formatBitcoin(msat: amount_msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
			
			parsedAmount = Result.success(targetAmt) // do this first !
			amount = formattedAmt.digits
		} else {
			altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			isInvalidAmount = false // display in gray at very beginning
		}
	}
	
	func modelDidChange(_ newModel: Scan.Model) -> Void {
		log.trace("[\(viewName)] modelDidChange()")
		
		if let model = newModel as? Scan.Model_LnurlPayFlow_LnurlPayRequest,
		   let error = model.error
		{
			popoverState.display(dismissable: true) {
				LnurlPayErrorNotice(error: error)
			}
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
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					alt = Utils.formatFiat(msat: msat!, exchangeRate: exchangeRate)
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = ""
				}
			case .fiat(let fiatCurrency):
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					alt = Utils.formatBitcoin(msat: msat!, bitcoinUnit: currencyPrefs.bitcoinUnit)
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since Currency.displayable() already filters for this.
					altAmount = ""
				}
			}
			
			if let msat = msat {
				
				let balanceMsat = balanceMsat() ?? 0
				if msat > balanceMsat {
					isInvalidAmount = true
					altAmount = NSLocalizedString("Amount exceeds your balance", comment: "error message")
					
				} else if let alt = alt {
					altAmount = "≈ \(alt.string)"
				}
			}
			
			if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest,
			   let expiryTimestampSeconds = model.paymentRequest.expiryTimestampSeconds()?.doubleValue,
			   Date(timeIntervalSince1970: expiryTimestampSeconds) <= Date()
			{
				isExpiredInvoice = true
				if !isInvalidAmount {
					altAmount = NSLocalizedString("Invoice is expired", comment: "error message")
				}
			} else {
				isExpiredInvoice = false
			}
			
			if !isInvalidAmount,
			   let msat = msat,
			   let lnurlPay = lnurlPay()
			{
				let minMsat = lnurlPay.minSendable.msat
				let maxMsat = lnurlPay.maxSendable.msat
				
				let isRange = maxMsat > minMsat
				
				var bitcoinUnit: BitcoinUnit
				if case .bitcoin(let unit) = unit {
					bitcoinUnit = unit
				} else {
					bitcoinUnit = currencyPrefs.bitcoinUnit
				}
				
				// Since amounts are specified in bitcoin, there are challenges surrounding fiat conversion.
				// The min/max amounts in bitcoin may not properly round to fiat amounts.
				// Which could lead to weird UI issues such as:
				// - User types in 0.01 USD
				// - Max amount is 20 sats, which converts to less than 0.01 USD
				// - Error message says: Amount must be at most 0.01 USD
				//
				// So we should instead display error messages using exact BTC amounts.
				// And render fiat conversions as approximate.
				
				if !isRange && msat != minMsat { // amount must be exact
					isInvalidAmount = true
					
					let exactBitcoin = Utils.formatBitcoin(msat: minMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = unit,
						let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
					{
						let approxFiat = Utils.formatFiat(msat: minMsat, exchangeRate: exchangeRate)
						altAmount = NSLocalizedString(
							"Amount must be \(exactBitcoin.string) (≈ \(approxFiat.string))",
							comment: "error message"
						)
					} else {
						altAmount = NSLocalizedString(
							"Amount must be \(exactBitcoin.string)",
							comment: "error message"
						)
					}
					
				} else if msat < minMsat { // amount is too low
					isInvalidAmount = true
					
					let minBitcoin = Utils.formatBitcoin(msat: minMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = unit,
						let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
					{
						let approxFiat = Utils.formatFiat(msat: minMsat, exchangeRate: exchangeRate)
						altAmount = NSLocalizedString(
							"Amount must be at least \(minBitcoin.string) (≈ \(approxFiat.string))",
							comment: "error message"
						)
					} else {
						altAmount = NSLocalizedString(
							"Amount must be at least \(minBitcoin.string)",
							comment: "error message"
						)
					}
					
				} else if msat > maxMsat { // amount is too high
					isInvalidAmount = true
					
					let maxBitcoin = Utils.formatBitcoin(msat: maxMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = unit,
						let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
					{
						let approxFiat = Utils.formatFiat(msat: maxMsat, exchangeRate: exchangeRate)
						altAmount = NSLocalizedString(
							"Amount must be at most \(maxBitcoin.string) (≈ \(approxFiat.string))",
							comment: "error message"
						)
					} else {
						altAmount = NSLocalizedString(
							"Amount must be at most \(maxBitcoin.string)",
							comment: "error message"
						)
					}
				}
			}
			
		} // </switch parsedAmount>
	}
	
	func metadataButtonTapped() {
		log.trace("[\(viewName)] metadataButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		dismissKeyboardIfVisible()
		shortSheetState.display(dismissable: true) {
		
			MetadataSheet(lnurlPay: lnurlPay)
		}
	}
	
	func priceTargetButtonTapped() {
		log.trace("[\(viewName)] priceTargetButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		let minMsat = lnurlPay.minSendable.msat
		let maxMsat = lnurlPay.maxSendable.msat
		
		var msat = minMsat
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
				msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				
			case .fiat(let fiatCurrency):
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
				}
			}
		}
		
		let isRange = maxMsat > minMsat
		if isRange {
			
			// A range of valid amounts are possible.
			// Show the PriceSliderSheet.
			
			if msat < minMsat {
				msat = minMsat
			} else if msat > maxMsat {
				msat = maxMsat
			}
			
			dismissKeyboardIfVisible()
			shortSheetState.display(dismissable: true) {
				
				PriceSliderSheet(
					lnurlPay: lnurlPay,
					msat: msat,
					valueChanged: priceSliderChanged
				)
			}
			
		} else if msat != minMsat {
			msat = minMsat
			
			// There is only one valid amount.
			// We set the amount directly via the button tap.
			
			priceSliderChanged(minMsat)
		}
	}
	
	func priceSliderChanged(_ msat: Int64) {
		log.trace("[\(viewName)] priceSliderChanged()")
		
		let preferredBitcoinUnit = currencyPrefs.bitcoinUnit
		unit = Currency.bitcoin(preferredBitcoinUnit)
		
		// The TextFieldCurrencyStyler doesn't seem to fire when we manually set the text value.
		// So we need to do it manually here, to ensure the `parsedAmount` is properly updated.
		
		let amt = Utils.formatBitcoin(msat: msat, bitcoinUnit: preferredBitcoinUnit)
		let result = TextFieldCurrencyStyler.format(input: amt.digits, unit: unit, hideMsats: false)
		
		parsedAmount = result.1
		amount = result.0
	}
	
	func commentButtonTapped() {
		log.trace("[\(viewName)] commentButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		let maxCommentLength = lnurlPay.maxCommentLength?.intValue ?? 140
		
		dismissKeyboardIfVisible()
		shortSheetState.display(dismissable: true) {
			
			CommentSheet(
				comment: $comment,
				maxCommentLength: maxCommentLength
			)
		}
	}
	
	func sendPayment() {
		log.trace("[\(viewName)] sendPayment()")
		
		guard
			let amt = try? parsedAmount.get(),
			amt > 0
		else {
			isInvalidAmount = true
			return
		}
		
		var msat: Int64? = nil
		switch unit {
		case .bitcoin(let bitcoinUnit):
			msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
		case .fiat(let fiatCurrency):
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
			}
		}
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			
			if let msat = msat {
				mvi.intent(Scan.Intent_InvoiceFlow_SendInvoicePayment(
					paymentRequest: model.paymentRequest,
					amount: Lightning_kmpMilliSatoshi(msat: msat)
				))
			}
			
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			
			if supportsComment() && comment.count == 0 && !hasPromptedForComment {
				
				let maxCommentLength = model.lnurlPay.maxCommentLength?.intValue ?? 140
				
				shortSheetState.onNextWillDisappear {
					
					log.debug("shortSheetState.onNextWillDisappear {}")
					hasPromptedForComment = true
				}
				
				dismissKeyboardIfVisible()
				shortSheetState.display(dismissable: true) {
					
					CommentSheet(
						comment: $comment,
						maxCommentLength: maxCommentLength,
						sendButtonAction: { sendPayment() }
					)
				}
				
			} else if let msat = msat {
				
				mvi.intent(Scan.Intent_LnurlPayFlow_SendLnurlPayment(
					lnurlPay: model.lnurlPay,
					amount: Lightning_kmpMilliSatoshi(msat: msat),
					comment: comment
				))
			}
		}
	}
	
	func didCancelLnurlPayFetch() {
		log.trace("[\(viewName)] didCancelLnurlPayFetch()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		mvi.intent(Scan.Intent_LnurlPayFlow_CancelLnurlPayment(
			lnurlPay: lnurlPay
		))
	}
	
	func showAppStatusPopover() {
		log.trace("[\(viewName)] showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}

struct MetadataSheet: View, ViewName {
	
	let lnurlPay: LNUrl.Pay
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Metadata")
					.font(.title3)
				Spacer()
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			}
			.padding(.horizontal)
			.padding(.vertical, 8)
			.background(
				Color(UIColor.secondarySystemBackground)
					.cornerRadius(15, corners: [.topLeft, .topRight])
			)
			.padding(.bottom, 4)
			
			content
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.leading, spacing: 20) {
			
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					
					Text("Short Description")
						.font(Font.system(.body, design: .serif))
						.bold()
					
					Text(lnurlPay.metadata.plainText)
						.multilineTextAlignment(.leading)
						.lineLimit(nil)
						.padding(.leading)
				}
				
				if let longDesc = lnurlPay.metadata.longDesc {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Long Description")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						Text(longDesc)
							.multilineTextAlignment(.leading)
							.lineLimit(nil)
							.padding(.leading)
					}
				}
				
				if let imagePng = lnurlPay.metadata.imagePng {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Image")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						if let image = UIImage(data: imagePng.toSwiftData()) {
							Image(uiImage: image)
								.padding(.leading)
						} else {
							Text("Malformed PNG image data")
								.padding(.leading)
						}
					}
				}
				
				if let imageJpg = lnurlPay.metadata.imageJpg {
					VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
						
						Text("Image")
							.font(Font.system(.body, design: .serif))
							.bold()
						
						if let image = UIImage(data: imageJpg.toSwiftData()) {
							Image(uiImage: image)
								.padding(.leading)
						} else {
							Text("Malformed JPG image data")
								.padding(.leading)
						}
					}
				}
				
			} // </VStack>
			.padding(.horizontal)
			
		} // </ScrollView>
		.frame(maxHeight: 250)
		.padding(.vertical)
	}
	
	func closeButtonTapped() {
		log.trace("[\(viewName)] closeButtonTapped()")
		
		shortSheetState.close()
	}
}

struct PriceSliderSheet: View, ViewName {
	
	let lnurlPay: LNUrl.Pay
	let valueChanged: (Int64) -> Void
	
	init(lnurlPay: LNUrl.Pay, msat: Int64, valueChanged: @escaping (Int64) -> Void) {
		self.lnurlPay = lnurlPay
		self.amountSats = Utils.convertBitcoin(msat: msat, bitcoinUnit: .sat)
		self.valueChanged = valueChanged
	}
	
	// The Slider family works with BinaryFloatingPoint.
	// So we're going to switch to `sats: Double` for simplicity.
	
	@State var amountSats: Double
	
	var rangeSats: ClosedRange<Double> {
		let minSat: Double = Double(lnurlPay.minSendable.msat) / Utils.Millisatoshis_Per_Satoshi
		let maxSat: Double = Double(lnurlPay.maxSendable.msat) / Utils.Millisatoshis_Per_Satoshi
		
		return minSat...maxSat
	}
	
	enum MaxPercentWidth: Preference {}
	let maxPercentWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxPercentWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxPercentWidth: CGFloat? = nil
	
	enum MaxAmountWidth: Preference {}
	let maxAmountWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxAmountWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxAmountWidth: CGFloat? = nil
	
	enum ContentHeight: Preference {}
	let contentHeightReader = GeometryPreferenceReader(
		key: AppendValue<ContentHeight>.self,
		value: { [$0.size.height] }
	)
	@State var contentHeight: CGFloat? = nil
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Text(maxPercentAmount())
				.foregroundColor(.clear)
				.read(maxPercentWidthReader)
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text("Customize amount")
						.font(.title3)
					Spacer()
					Button {
						closeButtonTapped()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
				}
				.padding(.horizontal)
				.padding(.vertical, 8)
				.background(
					Color(UIColor.secondarySystemBackground)
						.cornerRadius(15, corners: [.topLeft, .topRight])
				)
				.padding(.bottom, 4)
				
				content
			}
		}
		.assignMaxPreference(for: maxPercentWidthReader.key, to: $maxPercentWidth)
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 20) {
			
			GeometryReader { proxy in
				
				// We have 3 columns:
				//
				// | bitcoin prices | vslider | fiat prices |
				//
				// We want:
				// - column 0 & 2 to be exactly the same width
				// - column 1 to be perfectly centered
				
				let vsliderWidth = CGFloat(50)
				let columnWidth = (proxy.size.width - vsliderWidth) / CGFloat(2)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					// Column 0: (left)
					// - Amounts in Bitcoin
					VStack(alignment: HorizontalAlignment.trailing, spacing: 40) {
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Max: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(maxBitcoinAmount().string)
								.foregroundColor(.secondary)
								.read(maxAmountWidthReader)
						}
						
						Text(bitcoinAmount().string)
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text("Min: ")
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(minBitcoinAmount().string)
								.foregroundColor(.secondary)
								.frame(width: maxAmountWidth, alignment: .trailing)
						}
						
					} // </VStack: column 0>
					.frame(width: columnWidth)
					.read(contentHeightReader)
					
					// Column 1: (center)
					// - Vertical slider
					VSlider(value: $amountSats, in: rangeSats) { value in
						log.debug("VSlider.onEditingChanged")
					}
					.frame(width: vsliderWidth, height: contentHeight, alignment: .center)
					
					// Column 2: (right)
					// - Amounts in fiat
					VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
						
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(maxFiatAmount().string)
								.foregroundColor(.secondary)
						}
						Spacer()
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(fiatAmount().string)
						}
						Spacer()
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text(verbatim: "≈ ")
								.font(.footnote)
								.foregroundColor(Color(UIColor.tertiaryLabel))
							Text(minFiatAmount().string)
								.foregroundColor(.secondary)
						}
					
					} // </VStack: column 2>
					.frame(width: columnWidth, height: contentHeight)
				
				} // </HStack>
				
			} // </GeometryReader>
			.frame(height: contentHeight)
			
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				
				Button {
					minusButtonTapped()
				} label: {
					Image(systemName: "minus.circle")
						.imageScale(.large)
				}
				Text(percentAmount())
					.frame(minWidth: maxPercentWidth, alignment: Alignment.center)
				Button {
					plusButtonTapped()
				} label: {
					Image(systemName: "plus.circle")
						.imageScale(.large)
				}
			
			} // </HStack>
			
		} // </VStack>
		.padding()
		.assignMaxPreference(for: maxAmountWidthReader.key, to: $maxAmountWidth)
		.assignMaxPreference(for: contentHeightReader.key, to: $contentHeight)
		.onChange(of: amountSats) {
			valueChanged(Utils.toMsat(from: $0, bitcoinUnit: .sat))
		}
	}
	
	func msat() -> Int64 {
		
		return Utils.toMsat(from: amountSats, bitcoinUnit: .sat)
	}
	
	func formatBitcoinAmount(msat: Int64) -> FormattedAmount {
		return Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
	}
	
	func maxBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: lnurlPay.maxSendable.msat)
	}
	
	func bitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: msat())
	}
	
	func minBitcoinAmount() -> FormattedAmount {
		return formatBitcoinAmount(msat: lnurlPay.minSendable.msat)
	}
	
	func formatFiatAmount(msat: Int64) -> FormattedAmount {
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: currencyPrefs.fiatCurrency) {
			return Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
		} else {
			return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
		}
	}
	
	func maxFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: lnurlPay.maxSendable.msat)
	}
	
	func fiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: msat())
	}
	
	func minFiatAmount() -> FormattedAmount {
		return formatFiatAmount(msat: lnurlPay.minSendable.msat)
	}
	
	func formatPercent(_ percent: Double) -> String {
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	func percentAmount() -> String {
		
		let minMsat = lnurlPay.minSendable.msat
		let curMsat = msat()
		
		let percent = Double(curMsat - minMsat) / Double(minMsat)
		
		return formatPercent(percent)
	}
	
	func maxPercentAmount() -> String {
		
		let minMsat = lnurlPay.minSendable.msat
		let maxMsat = lnurlPay.maxSendable.msat
		
		let percent = Double(maxMsat - minMsat) / Double(minMsat)
		
		return formatPercent(percent)
	}
	
	func willUserInterfaceChange(percent: Double) -> Bool {
		
		if formatPercent(percent) != percentAmount() {
			return true
		}
		
		let minMsat = lnurlPay.minSendable.msat
		let newMsat = Int64(Double(minMsat) * (1.0 + percent))
		
		if formatBitcoinAmount(msat: newMsat).digits != bitcoinAmount().digits {
			return true
		}
		
		if formatFiatAmount(msat: newMsat).digits != fiatAmount().digits {
			return true
		}
		
		return false
	}
	
	func minusButtonTapped() {
		log.trace("[\(viewName)] minusButtonTapped()")
		
		let minMsat = lnurlPay.minSendable.msat
		let curMsat = msat()
		
		let minPercent = 0.0
		let curPercent = Double(curMsat - minMsat) / Double(minMsat) * 100.0
		
		var floorPercent = curPercent.rounded(.down)
		
		// The previous percent may have been something like "8.7%".
		// And the new percent may be "8%".
		//
		// The question is, if we change the percent to "8%",
		// does this create any kind of change in the UI.
		//
		// If the answer is YES, then it's a valid change.
		// If the answer is NO, then we should drop another percentage point.
		
		if !willUserInterfaceChange(percent: (floorPercent / 100.0)) {
			floorPercent -= 1.0
		}
		if floorPercent < minPercent {
			floorPercent = minPercent
		}
		
		let newPercent = floorPercent / 100.0
		let newMsat = Int64(Double(minMsat) * (1.0 + newPercent))
		
		amountSats = Utils.convertBitcoin(msat: newMsat, bitcoinUnit: .sat)
	}
	
	func plusButtonTapped() {
		log.trace("[\(viewName)] plusButtonTapped()")
		
		let minMsat = lnurlPay.minSendable.msat
		let maxMsat = lnurlPay.maxSendable.msat
		let curMsat = msat()
		
		let maxPercent = Double(maxMsat - minMsat) / Double(minMsat) * 100.0
		let curPercent = Double(curMsat - minMsat) / Double(minMsat) * 100.0
		
		var ceilingPercent = curPercent.rounded(.up)
		
		// The previous percent may have been something like "8.7%".
		// And the new percent may be "9%".
		//
		// The question is, if we change the percent to "9%",
		// does this create any kind of change in the UI.
		//
		// If the answer is YES, then it's a valid change.
		// If the answer is NO, then we should add another percentage point.
		
		if !willUserInterfaceChange(percent: (ceilingPercent / 100.0)) {
			ceilingPercent += 1.0
		}
		if ceilingPercent > maxPercent {
			ceilingPercent = maxPercent
		}
		
		let newPercent = ceilingPercent / 100.0
		let newMsat = Int64(Double(minMsat) * (1.0 + newPercent))
		
		amountSats = Utils.convertBitcoin(msat: newMsat, bitcoinUnit: .sat)
	}
	
	func closeButtonTapped() {
		log.trace("[\(viewName)] closeButtonTapped()")
		
		shortSheetState.close()
	}
}

struct CommentSheet: View, ViewName {
	
	@State var text: String
	@Binding var comment: String
	
	let maxCommentLength: Int
	@State var remainingCount: Int
	
	let sendButtonAction: (() -> Void)?
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	init(comment: Binding<String>, maxCommentLength: Int, sendButtonAction: (() -> Void)? = nil) {
		self._comment = comment
		self.maxCommentLength = maxCommentLength
		self.sendButtonAction = sendButtonAction
		
		text = comment.wrappedValue
		remainingCount = maxCommentLength - comment.wrappedValue.count
	}
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Add optional comment")
					.font(.title3)
				Spacer()
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			}
			.padding(.horizontal)
			.padding(.vertical, 8)
			.background(
				Color(UIColor.secondarySystemBackground)
					.cornerRadius(15, corners: [.topLeft, .topRight])
			)
			.padding(.bottom, 4)
			
			content
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Your comment will be sent when you pay.")
				.font(.callout)
				.foregroundColor(.secondary)
				.padding(.bottom)
			
			TextEditor(text: $text)
				.frame(maxWidth: .infinity, maxHeight: 75)
				.padding(.all, 4)
				.background(
					RoundedRectangle(cornerRadius: 4)
						.stroke(Color(UIColor.separator), lineWidth: 1)
				)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("\(remainingCount) remaining")
					.foregroundColor(remainingCount >= 0 ? Color.primary : Color.appNegative)
				
				Spacer()
				
				Button {
					clearButtonTapped()
				} label: {
					Text("Clear")
				}
				.disabled(text.count == 0)
			}
			.padding(.top, 4)
			
			if sendButtonAction != nil {
				sendButton
					.padding(.top, 16)
			}
			
		} // </VStack>
		.padding()
		.onChange(of: text) {
			textDidChange($0)
		}
	}
	
	@ViewBuilder
	var sendButton: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				sendButtonTapped()
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
			Spacer()
			
		} // </HStack>
	}
	
	func textDidChange(_ newText: String) {
		log.trace("[\(viewName)] textDidChange()")
		
		if newText.count <= maxCommentLength {
			comment = newText
		} else {
			let endIdx = newText.index(newText.startIndex, offsetBy: maxCommentLength)
			let substr = newText[newText.startIndex ..< endIdx]
			comment = String(substr)
		}
		
		remainingCount = maxCommentLength - newText.count
	}
	
	func clearButtonTapped() {
		log.trace("[\(viewName)] clearButtonTapped()")
		
		text = ""
	}
	
	func closeButtonTapped() {
		log.trace("[\(viewName)] closeButtonTapped()")
		
		shortSheetState.close()
	}
	
	func sendButtonTapped() {
		log.trace("[\(viewName)] sendButtonTapped()")
		
		shortSheetState.close {
			sendButtonAction!()
		}
	}
}

struct LnurlPayErrorNotice: View, ViewName {
	
	let error: Scan.LNUrlPayError
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Image(systemName: "exclamationmark.triangle")
					.imageScale(.medium)
					.padding(.trailing, 6)
					.foregroundColor(Color.appNegative)
				
				Text(title())
					.font(.headline)
				
				Spacer()
				
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			}
			.padding(.horizontal)
			.padding(.vertical, 8)
			.background(
				Color(UIColor.secondarySystemBackground)
					.cornerRadius(15, corners: [.topLeft, .topRight])
			)
			.padding(.bottom, 4)
			
			content
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
		
			errorMessage
		}
		.padding()
	}
	
	@ViewBuilder
	var errorMessage: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			if let err = error as? Scan.LNUrlPay_Error_RemoteError {
				
				if let _ = err.err as? LNUrl.ErrorRemoteFailureCouldNotConnect {
					
					Text("Could not connect to host:")
					Text(err.err.origin)
						.font(.system(.subheadline, design: .monospaced))
				
				} else if let details = err.err as? LNUrl.ErrorRemoteFailureCode {
					
					Text("Host returned status code \(details.code.value):")
				 	Text(err.err.origin)
						.font(.system(.subheadline, design: .monospaced))
				 
				} else if let details = err.err as? LNUrl.ErrorRemoteFailureDetailed {
				
					Text("Host returned error response.")
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: \(details.reason)")
						.font(.system(.subheadline, design: .monospaced))
			 
				} else if let _ = err.err as? LNUrl.ErrorRemoteFailureUnreadable {
				
					Text("Host returned unreadable response:", comment: "error details")
				 	Text(err.err.origin)
						.font(.system(.subheadline, design: .monospaced))
					
				} else {
					genericErrorMessage
				}
				
			} else if let err = error as? Scan.LNUrlPay_Error_BadResponseError {
				
				if let details = err.err as? LNUrl.ErrorPayInvoiceMissingPr {
					
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: missing payment request")
						.font(.system(.subheadline, design: .monospaced))
					
				} else if let details = err.err as? LNUrl.ErrorPayInvoiceMalformedPr {
					
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: malformed payment request")
						.font(.system(.subheadline, design: .monospaced))
					
				} else if let details = err.err as? LNUrl.ErrorPayInvoiceInvalidHash {
					
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: invalid hash")
						.font(.system(.subheadline, design: .monospaced))
					
				} else if let details = err.err as? LNUrl.ErrorPayInvoiceInvalidAmount {
				 
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: invalid amount")
						.font(.system(.subheadline, design: .monospaced))
					
				} else {
					genericErrorMessage
				}
			 
			} else if let err = error as? Scan.LNUrlPay_Error_ChainMismatch {
				
				let lChain = err.myChain.name
				let rChain = err.requestChain?.name ?? "Unknown"
				
				Text("You are on bitcoin chain \(lChain), but the invoice is for \(rChain).")
				
			} else if let _ = error as? Scan.LNUrlPay_Error_AlreadyPaidInvoice {
				
				Text("You have already paid this invoice.")
				
		 	} else {
				genericErrorMessage
			}
		}
	}
	
	@ViewBuilder
	var genericErrorMessage: some View {
		
		Text("Please try again")
	}
	
	func title() -> String {
		
		if let err = error as? Scan.LNUrlPayErrorRemoteError {
			if err.err is LNUrl.ErrorRemoteFailureCouldNotConnect {
				return NSLocalizedString("Connection failure", comment: "Error title")
			} else {
				return NSLocalizedString("Invalid response", comment: "Error title")
			}
			
		} else if let _ = error as? Scan.LNUrlPayErrorBadResponseError {
			return NSLocalizedString("Invalid response", comment: "Error title")
			
		} else if let _ = error as? Scan.LNUrlPayErrorChainMismatch {
			return NSLocalizedString("Chain mismatch", comment: "Error title")
			
		} else if let _ = error as? Scan.LNUrlPayErrorAlreadyPaidInvoice {
			return NSLocalizedString("Already paid", comment: "Error title")
			
		} else {
			return NSLocalizedString("Unknown error", comment: "Error title")
		}
	}
	
	func closeButtonTapped() {
		log.trace("[\(viewName)] closeButtonTapped()")
		
		popoverState.close()
	}
}

struct SendingView: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>

	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
			VStack {
				Text("Sending Payment...")
					.font(.title)
					.padding()
			}
		}
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.navigationBarTitle(
			NSLocalizedString("Sending payment", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(0) // [SendingView, ValidateView, LoginView, ScanView]
	}
}

struct LoginView: View, ViewName {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	enum MaxImageHeight: Preference {}
	let maxImageHeightReader = GeometryPreferenceReader(
		key: AppendValue<MaxImageHeight>.self,
		value: { [$0.size.height] }
	)
	@State var maxImageHeight: CGFloat? = nil
	
	let buttonFont: Font = .title3
	let buttonImgScale: Image.Scale = .medium
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
			// I want the height of these 2 components to match exactly:
			// Button("<img> Login")
			// HStack("<img> Logged In")
			//
			// To accomplish this, I need the images to be same height.
			// But they're not - unless we measure them, and enforce matching heights.
			
			Image(systemName: "bolt")
				.imageScale(buttonImgScale)
				.font(buttonFont)
				.foregroundColor(.clear)
				.read(maxImageHeightReader)
			
			Image(systemName: "hand.thumbsup.fill")
				.imageScale(buttonImgScale)
				.font(buttonFont)
				.foregroundColor(.clear)
				.read(maxImageHeightReader)
			
			content
		}
		.assignMaxPreference(for: maxImageHeightReader.key, to: $maxImageHeight)
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.navigationBarTitle(
			NSLocalizedString("lnurl-auth", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.zIndex(2) // [SendingView, ValidateView, LoginView, ScanView]
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 30) {
			
			Spacer()
			
			Text("You can use your wallet to anonymously sign and authorize an action on:")
				.multilineTextAlignment(.center)
			
			Text(domain())
				.font(.headline)
				.multilineTextAlignment(.center)
			
			if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult, model.error == nil {
				
				HStack(alignment: VerticalAlignment.firstTextBaseline) {
					Image(systemName: "hand.thumbsup.fill")
						.renderingMode(.template)
						.imageScale(buttonImgScale)
						.frame(minHeight: maxImageHeight)
					Text(successTitle())
				}
				.font(buttonFont)
				.foregroundColor(Color.appPositive)
				.padding(.top, 4)
				.padding(.bottom, 5)
				.padding([.leading, .trailing], 24)
				
			} else {
				
				Button {
					loginButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline) {
						Image(systemName: "bolt")
							.renderingMode(.template)
							.imageScale(buttonImgScale)
							.frame(minHeight: maxImageHeight)
						Text(buttonTitle())
					}
					.font(buttonFont)
					.foregroundColor(Color.white)
					.padding(.top, 4)
					.padding(.bottom, 5)
					.padding([.leading, .trailing], 24)
				}
				.buttonStyle(ScaleButtonStyle(
					backgroundFill: Color.appAccent,
					disabledBackgroundFill: Color(UIColor.systemGray)
				))
				.disabled(mvi.model is Scan.Model_LnurlAuthFlow_LoggingIn)
			}
			
			ZStack {
				Divider()
				if mvi.model is Scan.Model_LnurlAuthFlow_LoggingIn {
					HorizontalActivity(color: .appAccent, diameter: 10, speed: 1.6)
				}
			}
			.frame(width: 100, height: 10)
			
			if let errorStr = errorText() {
				
				Text(errorStr)
					.font(.callout)
					.foregroundColor(.appNegative)
					.multilineTextAlignment(.center)
				
			} else {
				
				Text("No personal data will be shared with this service.")
					.font(.callout)
					.foregroundColor(.secondary)
					.multilineTextAlignment(.center)
			}
			
			Spacer()
			Spacer()
			
		} // </VStack>
		.padding(.horizontal, 20)
	}
	
	var auth: LNUrl.Auth? {
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginRequest {
			return model.auth
		} else if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoggingIn {
			return model.auth
		} else if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult {
			return model.auth
		} else {
			return nil
		}
	}
	
	func domain() -> String {
		
		return auth?.url.host ?? "?"
	}
	
	func buttonTitle() -> String {
		
		if let action = auth?.action() {
			switch action {
				case .register_ : return NSLocalizedString("Register", comment: "lnurl-auth: login button title")
				case .login     : return NSLocalizedString("Login",    comment: "lnurl-auth: login button title")
				case .link      : return NSLocalizedString("Link",     comment: "lnurl-auth: login button title")
				case .auth      : fallthrough
				default         : break
			}
		}
		return NSLocalizedString("Authenticate", comment: "lnurl-auth: login button title")
	}
	
	func successTitle() -> String {
		
		if let action = auth?.action() {
			switch action {
				case .register_ : return NSLocalizedString("Registered", comment: "lnurl-auth: success text")
				case .login     : return NSLocalizedString("Logged In",  comment: "lnurl-auth: success text")
				case .link      : return NSLocalizedString("Linked",     comment: "lnurl-auth: success text")
				case .auth      : fallthrough
				default         : break
			}
		}
		return NSLocalizedString("Authenticated", comment: "lnurl-auth: success text")
	}
	
	func errorText() -> String? {
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult, let error = model.error {
			
			if let error = error as? Scan.LoginErrorServerError {
				if let details = error.details as? LNUrl.ErrorRemoteFailureCode {
					let frmt = NSLocalizedString("Server returned HTTP status code %d", comment: "error details")
					return String(format: frmt, details.code.value)
				
				} else if let details = error.details as? LNUrl.ErrorRemoteFailureDetailed {
					let frmt = NSLocalizedString("Server returned error: %@", comment: "error details")
					return String(format: frmt, details.reason)
				
				} else {
					return NSLocalizedString("Server returned unreadable response", comment: "error details")
				}
				
			} else if error is Scan.LoginErrorNetworkError {
				return NSLocalizedString("Network error. Check your internet connection.", comment: "error details")
				
			} else {
				return NSLocalizedString("An unknown error occurred.", comment: "error details")
			}
		}
		
		return nil
	}
	
	func loginButtonTapped() {
		log.trace("[\(viewName)] loginButtonTapped()")
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginRequest {
			// There's usually a bit of delay between:
			// - the successful authentication (when Phoenix receives auth success response from server)
			// - the webpage updating to reflect the authentication success
			//
			// This is probably due to several factors:
			// Possibly due to client-side polling (webpage asking server for an auth result).
			// Or perhaps the server pushing the successful auth to the client via websockets.
			//
			// But whatever the case, it leads to a bit of confusion for the user.
			// The wallet says "success", but the website just sits there.
			// Meanwhile the user is left wondering if the wallet is wrong, or something else is broken.
			//
			// For this reason, we're smoothing the user experience with a bit of extra animation in the wallet.
			// Here's how it works:
			// - the user taps the button, and we immediately send the HTTP GET to the server for authentication
			// - the UI starts a pretty animation to show that it's authenticating
			// - if the server responds too quickly (with succcess), we inject a small delay
			// - during this small delay, the wallet UI continues the pretty animation
			// - this gives the website a bit of time to update
			//
			// The end result is that the website is usually updating (or updated) by the time
			// the wallet shows the "authenticated" screen.
			// This leads to less confusion, and a happier user.
			// Which hopefully leads to more lnurl-auth adoption.
			//
			mvi.intent(Scan.Intent_LnurlAuthFlow_Login(
				auth: model.auth,
				minSuccessDelaySeconds: 1.6
			))
		}
	}
}
