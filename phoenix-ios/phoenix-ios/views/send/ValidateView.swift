import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ValidateView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct PaymentNumbers {
	let baseMsat: Int64
	let tipMsat: Int64
	let minerFeeMsat: Int64
	let totalMsat: Int64
	let tipPercent: Double
	let minerFeePercent: Double
}

enum Problem: Error {
	case emptyInput
	case invalidInput
	case amountExceedsBalance
	case finalAmountExceedsBalance // including minerFee
	case amountOutOfRange
}

struct ValidateView: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@State var currency = Currency.bitcoin(.sat)
	@State var currencyList: [Currency] = [Currency.bitcoin(.sat)]
	
	@State var currencyPickerChoice: String = Currency.bitcoin(.sat).shortName
	@State var currencyConverterOpen = false
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var problem: Problem? = nil
	
	@State var preTipAmountMsat: Int64? = nil
	@State var postTipAmountMsat: Int64? = nil
	@State var priceSliderVisible: Bool = false
	
	@State var comment: String = ""
	@State var hasPromptedForComment = false
	
	@State var hasPickedSwapOutMode = false
	
	@State var didAppear = false
	
	let balancePublisher = Biz.business.balanceManager.balancePublisher()
	@State var balanceMsat: Int64 = 0
	
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	@State var chainContext: WalletContext.V0ChainContext? = nil
	
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// For the cicular buttons: [metadata, tip, comment]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: currencyConverterView(),
					isActive: $currencyConverterOpen
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
					.accessibilityHidden(true)
			}
			
			GeometryReader { geometry in
				ScrollView(.vertical) {
					content
						.frame(width: geometry.size.width)
						.frame(minHeight: geometry.size.height)
				}
				.onTapGesture {
					dismissKeyboardIfVisible()
				}
			}
			
			if mvi.model is Scan.Model_SwapOutFlow_Requesting {
				FetchActivityNotice(
					title: NSLocalizedString("Fetching Invoice", comment: "Progress title"),
					onCancel: { didCancelSwapOutRequest() }
				)
			}
			else if mvi.model is Scan.Model_LnurlPayFlow_LnurlPayFetch {
				FetchActivityNotice(
					title: NSLocalizedString("Fetching Invoice", comment: "Progress title"),
					onCancel: { didCancelLnurlPayFetch() }
				)
			}
			else if mvi.model is Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch {
				FetchActivityNotice(
					title: NSLocalizedString("Forwarding Invoice", comment: "Progress title"),
					onCancel: { didCancelLnurlWithdrawFetch() }
				)
			}
			
		}// </ZStack>
		.navigationTitle(
			mvi.model is Scan.Model_LnurlWithdrawFlow
				? NSLocalizedString("Confirm Withdraw", comment: "Navigation bar title")
				: NSLocalizedString("Confirm Payment", comment: "Navigation bar title")
		)
		.navigationBarTitleDisplayMode(.inline)
		.transition(
			.asymmetric(
				insertion: .identity,
				removal: .opacity
			)
		)
		.onAppear() {
			onAppear()
		}
		.navigationStackDestination( // For iOS 16+
			isPresented: $currencyConverterOpen,
			destination: currencyConverterView
		)
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: currencyPickerChoice) { _ in
			currencyPickerDidChange()
		}
		.onReceive(balancePublisher) {
			balanceDidChange($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextDidChange($0)
		}
	}
	
	@ViewBuilder
	var content: some View {
	
		let isDisconnected = !(connectionsMonitor.connections.global is Lightning_kmpConnection.ESTABLISHED)
		VStack {
			
			Spacer().frame(height: 20)
	
			if let host = paymentHost() {
				VStack(alignment: HorizontalAlignment.center, spacing: 10) {
					if mvi.model is Scan.Model_LnurlWithdrawFlow {
						Text("You are redeeming funds from")
					} else {
						Text("Payment requested by")
					}
					Text(host).bold()
				}
				.padding(.bottom)
				.padding(.bottom)
				.accessibilityElement(children: .combine)
			}
			
			if mvi.model is Scan.Model_LnurlWithdrawFlow {
				Text(verbatim: NSLocalizedString("amount to receive", comment: "SendView: lnurl-withdraw flow")
						.uppercased()
				)
				.padding(.bottom, 4)
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				TextField(verbatim: "123", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.fixedSize()
					.font(.title)
					.multilineTextAlignment(.trailing)
					.minimumScaleFactor(0.95) // SwiftUI bugs: truncating text in RTL
					.foregroundColor(isInvalidAmount() ? Color.appNegative : Color.primaryForeground)
					.accessibilityHint("amount in \(currency.shortName)")
			
				Picker(selection: $currencyPickerChoice, label: Text(currencyPickerChoice).frame(minWidth: 40)) {
					ForEach(currencyPickerOptions(), id: \.self) { option in
						Text(option).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.accessibilityLabel("") // see below
				.accessibilityHint("Currency picker")
				
				// For a Picker, iOS is setting the VoiceOver text twice:
				// > "sat sat, Button"
				//
				// If we change the accessibilityLabel to "foobar", then we get:
				// > "sat foobar, Button"
				//
				// So we have to set it to the empty string to avoid the double-word.

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
				.foregroundColor(problem != nil ? Color.appNegative : .secondary)
				.padding(.top, 4)
				.padding(.bottom)
			
			if showMetadataButton() || showRangeButton() || showTipButton() || showCommentButton() {
				HStack(alignment: VerticalAlignment.center, spacing: 20) {
					if showMetadataButton() {
						metadataButton()
					}
					if showRangeButton() {
						rangeButton()
					}
					if showTipButton() {
						tipButton()
					}
					if showCommentButton() {
						commentButton()
					}
				}
				.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
				.padding(.horizontal)
			}
			
			if let description = requestDescription() {
				Text(description)
					.padding()
					.padding(.bottom)
					.accessibilityHint("payment description")
			} else {
				Text("No description")
					.foregroundColor(.secondary)
					.padding()
					.padding(.bottom)
			}
			
			paymentButton(isDisconnected)
		
			if problem == nil && isDisconnected {
				
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
			
			PaymentSummaryView(
				problem: $problem,
				paymentNumbers: paymentNumbers()
			)
			.padding(.top)
			.padding(.top)
		} // </VStack>
	}
	
	@ViewBuilder
	func paymentButton(_ isDisconnected: Bool) -> some View {
		
		Button {
			if mvi.model is Scan.Model_SwapOutFlow_Init {
				prepareTransaction()
			} else {
				sendPayment()
			}
		} label: {
			HStack {
				if mvi.model is Scan.Model_SwapOutFlow_Init {
					Image(systemName: "hammer")
						.renderingMode(.template)
					Text("Prepare Transaction")
				}
				else if mvi.model is Scan.Model_LnurlWithdrawFlow {
					Image("ic_receive")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 22, height: 22)
					Text("Redeem")
				} else {
					Image("ic_send")
						.renderingMode(.template)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: 22, height: 22)
					Text("Pay")
				}
			}
			.font(.title2)
			.foregroundColor(Color.white)
			.padding(.top, 4)
			.padding(.bottom, 5)
			.padding([.leading, .trailing], 24)
		}
		.buttonStyle(ScaleButtonStyle(
			cornerRadius: 100,
			backgroundFill: Color.appAccent,
			disabledBackgroundFill: Color.gray
		))
		.disabled(problem != nil || isDisconnected)
		.accessibilityHint(paymentButtonHint())
	}
	
	@ViewBuilder
	func actionButton(
		text: String,
		image: Image,
		width: CGFloat = 20,
		height: CGFloat = 20,
		xOffset: CGFloat = 0,
		yOffset: CGFloat = 0,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
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
				
				Text(text.lowercased())
					.font(.caption)
					.foregroundColor(Color.secondary)
					.padding(.top, 2)
			} // </VStack>
		} // </Button>
		.frame(width: maxButtonWidth)
		.read(maxButtonWidthReader)
	}
	
	@ViewBuilder
	func metadataButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("info", comment: "button label - try to make it short"),
			image: Image(systemName: "info.circle"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			metadataButtonTapped()
		}
	}
	
	@ViewBuilder
	func rangeButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("range", comment: "button label - try to make it short"),
			image: Image(systemName: "target"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			rangeButtonTapped()
		}
	}
	
	@ViewBuilder
	func tipButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("tip", comment: "button label - try to make it short"),
			image: Image(systemName: "heart"),
			width: 18, height: 18,
			xOffset: 0, yOffset: 0
		) {
			tipButtonTapped()
		}
		.disabled(tipButtonDisabled())
	}
	
	@ViewBuilder
	func commentButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("comment", comment: "button label - try to make it short"),
			image: Image(systemName: "pencil.tip"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			commentButtonTapped()
		}
	}
	
	@ViewBuilder
	func currencyConverterView() -> some View {
		
		CurrencyConverterView(
			initialAmount: currentAmount(),
			didChange: currencyConverterAmountChanged,
			didClose: {}
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------

	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: currency,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false,
			userDidEdit: { userDidEditTextField() }
		)
	}
	
	func paymentHost() -> String? {
		
		if let lnurlPay = lnurlPay() {
			return lnurlPay.initialUrl.host
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.initialUrl.host
			
		} else {
			return nil
		}
	}
	
	func isInvalidAmount() -> Bool {
		
		if let problem = problem {
			switch problem {
				case .emptyInput: return true
				case .invalidInput: return true
				case .amountOutOfRange: return true
				case .amountExceedsBalance: return true
				case .finalAmountExceedsBalance: return false // problem isn't amount, it's the minerFee
			}
			
		} else {
			return false
		}
	}
	
	func requestDescription() -> String? {
		
		if mvi.model is Scan.Model_SwapOutFlow {
			return NSLocalizedString("On-Chain Payment", comment: "Generic description for L1 payment")
			
		} else if let paymentRequest = paymentRequest() {
			return paymentRequest.desc()
			
		} else if let lnurlPay = lnurlPay() {
			return lnurlPay.metadata.plainText
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.defaultDescription
			
		} else {
			return nil
		}
	}
	
	func showMetadataButton() -> Bool {
		
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
	
	func showRangeButton() -> Bool {
		
		// We've decided this button isn't particularly useful,
		// and the error messages explaining min or max are sufficient.
		return false
		
	/*
		// The "range" button is used for:
		// - lnurl-pay
		// - lnurl-withdraw
		//
		// when those requests have a range (as opposed to a specific required amount)
		
		if let lnurlPay = lnurlPay() {
			return lnurlPay.maxSendable.msat > lnurlPay.minSendable.msat
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.maxWithdrawable.msat > lnurlWithdraw.minWithdrawable.msat
			
		} else {
			return false
		}
	*/
	}
	
	func showTipButton() -> Bool {
		
		if mvi.model is Scan.Model_SwapOutFlow {
			return true
			
		} else if let _ = paymentRequest() {
			return true
			
		} else if let _ = lnurlPay() {
			return true
			
		} else { // e.g. lnurlWithdraw()
			return false
		}
	}
	
	func tipButtonDisabled() -> Bool {
		
		return parsedAmountMsat() == nil
	}
	
	func showCommentButton() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}

		let maxCommentLength = lnurlPay.maxCommentLength?.int64Value ?? 0
		return maxCommentLength > 0
	}
	
	func disconnectedText() -> String {
		
		if !(connectionsMonitor.connections.internet is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("waiting for internet", comment: "button text")
		}
		if !(connectionsMonitor.connections.peer is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("connecting to peer", comment: "button text")
		}
		if !(connectionsMonitor.connections.electrum is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("connecting to electrum", comment: "button text")
		}
		return ""
	}
	
	// --------------------------------------------------
	// MARK: Accessibility
	// --------------------------------------------------
	
	func paymentButtonHint() -> String {
		
		if mvi.model is Scan.Model_SwapOutFlow_Init {
			
			return NSLocalizedString("fetches the swap-out fee", comment: "VoiceOver hint")
			
		} else {
			
			guard let msat = parsedAmountMsat() else {
				return ""
			}
			
			let btcnAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
			
			if let exchangeRate = currencyPrefs.fiatExchangeRate() {
				let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				
				return NSLocalizedString("total amount: \(btcnAmt.string), ≈\(fiatAmt.string)",
					comment: "VoiceOver hint"
				)
			} else {
				return NSLocalizedString("total amount: ≈\(btcnAmt.string)",
					comment: "VoiceOver hint"
				)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	/// The amount we use when initializing the UI.
	/// This gets pulled based on the type of flow in use.
	///
	func initialAmount() -> Lightning_kmpMilliSatoshi? {
		
		if let paymentRequest = paymentRequest() {
			return paymentRequest.amount
			
		} else if let lnurlPay = lnurlPay() {
			return lnurlPay.minSendable
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.maxWithdrawable
			
		} else if let model = mvi.model as? Scan.Model_SwapOutFlow_Init {
			
			if let amount_sat = model.address.amount {
				return Lightning_kmpMilliSatoshi(sat: amount_sat)
			} else if let paymentRequest = model.address.paymentRequest {
				return paymentRequest.amount
			}
		}
		
		return nil
	}
	
	func paymentRequest() -> Lightning_kmpPaymentRequest? {
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			return model.paymentRequest
		} else {
			// Note: there's technically a `paymentRequest` within `Scan.Model_SwapOutFlow_Ready`.
			// But this method is designed to only pull from `Scan.Model_InvoiceFlow_InvoiceRequest`.
			return nil
		}
	}
	
	func lnurlPay() -> LnurlPay.Intent? {
		
		if let model = mvi.model as? Scan.Model_LnurlPayFlow {
			return model.paymentIntent
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayFetch {
			return model.paymentIntent
		} else {
			return nil
		}
	}
	
	func lnurlWithdraw() -> LnurlWithdraw? {
		
		if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			return model.lnurlWithdraw
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch {
			return model.lnurlWithdraw
		} else {
			return nil
		}
	}
	
	/// Returns true if:
	/// - this is a normal lightning invoice without a set amount
	/// - this is an on-chain payment (i.e. swap-out)
	///
	func isAmountlessInvoice() -> Bool {
		
		if mvi.model is Scan.Model_SwapOutFlow {
			return true
		} else if let paymentRequest = paymentRequest() {
			return paymentRequest.amount == nil
		} else {
			return false
		}
	}
	
	func priceRange() -> MsatRange? {
		
		if let paymentRequest = paymentRequest() {
			if let min = paymentRequest.amount {
				return MsatRange(
					min: min,
					max: min.times(m: 2.0)
				)
			}
			
		} else if let lnurlPay = lnurlPay() {
			return MsatRange(
				min: lnurlPay.minSendable,
				max: lnurlPay.maxSendable
			)
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return MsatRange(
				min: lnurlWithdraw.minWithdrawable,
				max: lnurlWithdraw.maxWithdrawable
			)
		}
		
		return nil
	}
	
	func swapOutRange() -> MsatRange? {
		
		guard let chainContext = chainContext, mvi.model is Scan.Model_SwapOutFlow else {
			return nil
		}

		return MsatRange(
			min: Utils.toMsat(sat: chainContext.swapOut.v1.minAmountSat),
			max: Utils.toMsat(sat: chainContext.swapOut.v1.maxAmountSat)
		)
	}
	
	func paymentNumbers() -> PaymentNumbers? {
		
		guard let preMinerFeeMsat = parsedAmountMsat() else {
			return nil
		}
		
		var preTipMsat: Int64? = nil
		if let paymentRequest = paymentRequest() {
			preTipMsat = paymentRequest.amount?.msat
		} else if let preTipAmountMsat = preTipAmountMsat {
			preTipMsat = preTipAmountMsat
		}
		let baseMsat = preTipMsat ?? preMinerFeeMsat
		
		let tipMsat = preMinerFeeMsat - baseMsat
		let tipPercent = Double(tipMsat) / Double(baseMsat)
		
		let minerFeeMsat: Int64
		if let model = mvi.model as? Scan.Model_SwapOutFlow_Ready {
			minerFeeMsat = Utils.toMsat(sat: model.fee)
		} else {
			minerFeeMsat = 0
		}
		
		if tipMsat <= 0 && minerFeeMsat <= 0 {
			return nil
		}
		
		let minerFeePercent = Double(minerFeeMsat) / Double(preMinerFeeMsat)
		let totalMsat = preMinerFeeMsat + minerFeeMsat
		
		return PaymentNumbers(
			baseMsat        : baseMsat,
			tipMsat         : tipMsat,
			minerFeeMsat    : minerFeeMsat,
			totalMsat       : totalMsat,
			tipPercent      : tipPercent,
			minerFeePercent : minerFeePercent
		)
	}
	
	func currencyPickerOptions() -> [String] {
		
		var options = [String]()
		for currency in currencyList {
			options.append(currency.shortName)
		}
		
		options.append(NSLocalizedString("other",
			comment: "Option in currency picker list. Sends user to Currency Converter")
		)
		
		return options
	}
	
	func parsedAmountMsat() -> Int64? {
		
		if let postTipAmountMsat {
			return postTipAmountMsat
		}
		
		guard let amt = try? parsedAmount.get(), amt > 0 else {
			return nil
		}
		
		var msat: Int64? = nil
		switch currency {
		case .bitcoin(let bitcoinUnit):
			msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
		case .fiat(let fiatCurrency):
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
			}
		}
		
		return msat
	}
	
	func currentAmount() -> CurrencyAmount? {
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			return CurrencyAmount(currency: currency, amount: amt)
		} else {
			return nil
		}
	}
	
	// --------------------------------------------------
	// MARK: View Transitions
	// --------------------------------------------------
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		if didAppear {
			return
		}
		didAppear = true
			
		currencyList = Currency.displayable(currencyPrefs: currencyPrefs)
		
		let bitcoinUnit = currencyPrefs.bitcoinUnit
		currency = Currency.bitcoin(bitcoinUnit)
		currencyPickerChoice = currency.shortName
		
		if let amount_msat = initialAmount() {
			
			let formattedAmt = Utils.formatBitcoin(
				msat: amount_msat,
				bitcoinUnit: bitcoinUnit,
				policy: .showMsatsIfNonZero
			)
			
			parsedAmount = Result.success(formattedAmt.amount) // do this first !
			amount = formattedAmt.digits
		} else {
			altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			problem = nil // display in gray at very beginning
		}
		
		if let model = mvi.model as? Scan.Model_SwapOutFlow_Init,
		   model.address.paymentRequest != nil,
		   !hasPickedSwapOutMode
		{
			log.debug("triggering popover w/PaymentLayerChoice")
			
			popoverState.display(dismissable: false) {
				PaymentLayerChoice(mvi: mvi)
			} onWillDisappear: {
				hasPickedSwapOutMode = true
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func modelDidChange(_ newModel: Scan.Model) {
		log.trace("modelDidChange()")
		
		// There are several transitions that require re-calculating the altAmout:
		//
		// * SwapOutFlow_Requesting -> SwapOutFlow_Ready => amount + minerFee >? balance
		// * SwapOutFlow_Ready -> SwapOutFlow_Init       => remove minerFee from calculations
		// * SwapOutFlow_Init -> InvoiceFlow_X           => range changed (e.g. minAmount)
		//
		if newModel is Scan.Model_SwapOutFlow || newModel is Scan.Model_InvoiceFlow {
			
			refreshAltAmount()
		}
		
		if let model = newModel as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			if let payError = model.error {
				
				popoverState.display(dismissable: true) {
					LnurlFlowErrorNotice(error: LnurlFlowError.pay(error: payError))
				}
			}
			
		} else if let model = newModel as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			if let withdrawError = model.error {
				
				popoverState.display(dismissable: true) {
					LnurlFlowErrorNotice(error: LnurlFlowError.withdraw(error: withdrawError))
				}
			}
		}
	}
	
	func chainContextDidChange(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextDidChange()")
		
		chainContext = context
	}
	
	func balanceDidChange(_ balance: Lightning_kmpMilliSatoshi?) {
		log.trace("balanceDidChange()")
		
		if let balance = balance {
			balanceMsat = balance.msat
		} else {
			balanceMsat = 0
		}
	}
	
	func currencyPickerDidChange() -> Void {
		log.trace("currencyPickerDidChange()")
		
		if let newCurrency = currencyList.first(where: { $0.shortName == currencyPickerChoice }) {
			if currency != newCurrency {
				currency = newCurrency
				
				// We might want to apply a different formatter
				let result = TextFieldCurrencyStyler.format(input: amount, currency: currency, hideMsats: false)
				parsedAmount = result.1
				amount = result.0
				
				// This seems to be needed, because `amountDidChange` isn't automatically called
				refreshAltAmount()
				if let model = mvi.model as? Scan.Model_SwapOutFlow_Ready {
					mvi.intent(Scan.Intent_SwapOutFlow_Invalidate(address: model.address))
				}
			}
			
		} else { // user selected "other"
			
			currencyConverterOpen = true
			currencyPickerChoice = currency.shortName // revert to last real currency
		}
		
		if !priceSliderVisible {
			preTipAmountMsat = nil
			postTipAmountMsat = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func dismissKeyboardIfVisible() -> Void {
		log.trace("dismissKeyboardIfVisible()")
		
		let keyWindow = UIApplication.shared.connectedScenes
			.filter({ $0.activationState == .foregroundActive })
			.map({ $0 as? UIWindowScene })
			.compactMap({ $0 })
			.first?.windows
			.filter({ $0.isKeyWindow }).first
		keyWindow?.endEditing(true)
	}
	
	func userDidEditTextField() {
		log.trace("userDidEditTextField()")
		
		// This is called if the user manually edits the TextField.
		// Which is distinct from `amountDidChange`, which may be triggered via code.
		
		preTipAmountMsat = nil
		postTipAmountMsat = nil
	}
	
	func amountDidChange() {
		log.trace("amountDidChange()")
		
		refreshAltAmount()
		if let model = mvi.model as? Scan.Model_SwapOutFlow_Ready {
			mvi.intent(Scan.Intent_SwapOutFlow_Invalidate(address: model.address))
		}
	}
	
	func refreshAltAmount() {
		log.trace("refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			
			switch error {
			case .emptyInput:
				problem = .emptyInput
				altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			case .invalidInput:
				problem = .invalidInput
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			problem = nil
			
			var msat: Int64? = nil
			if let postTipAmountMsat {
				msat = postTipAmountMsat
			} else {
				switch currency {
				case .bitcoin(let bitcoinUnit):
					msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					
				case .fiat(let fiatCurrency):
					if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
						msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					}
				}
			}
			
			if let msat = msat, !(mvi.model is Scan.Model_LnurlWithdrawFlow) {
				
				// There are 2 scenarios that we handle slightly differently:
				// - amount user selected (amount + tip) exceeds balance
				// - total amount (including server-selected miner fee) exceeds balance
				//
				// In one scenario, the user is at fault.
				// In the other, the user unexpectedly went over the limit.
				
				if msat > balanceMsat {
					problem = .amountExceedsBalance
					altAmount = NSLocalizedString("Amount exceeds your balance", comment: "error message")
					
				} else if let model = mvi.model as? Scan.Model_SwapOutFlow_Ready {
					let totalMsat = msat + Utils.toMsat(sat: model.fee)
					if totalMsat > balanceMsat {
						problem = .finalAmountExceedsBalance
						altAmount = NSLocalizedString("Total amount exceeds your balance", comment: "error message")
					}
				}
			}
			
			if problem == nil,
			   let msat = msat,
			   let range = priceRange() ?? swapOutRange()
			{
				let minMsat = range.min.msat
				let maxMsat = range.max.msat
				let isRange = maxMsat > minMsat
				
				var bitcoinUnit: BitcoinUnit
				if case .bitcoin(let unit) = currency {
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
					problem = .amountOutOfRange
					
					let exactBitcoin = Utils.formatBitcoin(msat: minMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = currency,
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
					problem = .amountOutOfRange
					
					let minBitcoin = Utils.formatBitcoin(msat: minMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = currency,
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
					problem = .amountOutOfRange
					
					let maxBitcoin = Utils.formatBitcoin(msat: maxMsat, bitcoinUnit: bitcoinUnit)
					
					if case .fiat(let fiatCurrency) = currency,
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
			
			if problem == nil {
				
				if let msat = msat {
					
					var altBitcoinUnit: FormattedAmount? = nil
					var altFiatCurrency: FormattedAmount? = nil
					
					let preferredBitcoinUnit = currencyPrefs.bitcoinUnit
					if currency != Currency.bitcoin(preferredBitcoinUnit) {
						altBitcoinUnit = Utils.formatBitcoin(msat: msat, bitcoinUnit: preferredBitcoinUnit)
					}
					
					let preferredFiatCurrency = currencyPrefs.fiatCurrency
					if currency != Currency.fiat(preferredFiatCurrency) {
						if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: preferredFiatCurrency) {
							altFiatCurrency = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
						}
					}
					
					if let altBitcoinUnit = altBitcoinUnit, let altFiatCurrency = altFiatCurrency {
						altAmount = "≈ \(altBitcoinUnit.string)  /  ≈ \(altFiatCurrency.string)"
						
					} else if let altBitcoinUnit = altBitcoinUnit {
						altAmount = "≈ \(altBitcoinUnit.string)"
						
					} else if let altFiatCurrency = altFiatCurrency {
						altAmount = "≈ \(altFiatCurrency.string)"
						
					} else {
						// We don't know the exchange rate
						altAmount = ""
					}
					
				} else {
					// We don't know the exchange rate
					altAmount = ""
				}
			}
			
		} // </switch parsedAmount>
	}
	
	func metadataButtonTapped() {
		log.trace("metadataButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
		
			MetadataSheet(lnurlPay: lnurlPay)
		}
	}
	
	func rangeButtonTapped() {
		log.trace("rangeButtonTapped()")
		
		var flow: FlowType? = nil
		if let range = priceRange() {
			
			if lnurlWithdraw() != nil {
				flow = FlowType.withdraw(range: range)
			} else {
				flow = FlowType.pay(range: range)
			}
			
		} else if isAmountlessInvoice() {
			
			if let parsedAmtMst = parsedAmountMsat() {
				flow = FlowType.pay(range: MsatRange(min: parsedAmtMst, max: parsedAmtMst + parsedAmtMst))
			}
		}
		
		if let flow {
			
			dismissKeyboardIfVisible()
			smartModalState.display(dismissable: true) {
				
				RangeSheet(flow: flow, valueChanged: amountChanged_rangeSheet)
			}
		}
	}
	
	func tipButtonTapped() {
		log.trace("tipButtonTapped()")
		
		guard var msat = preTipAmountMsat ?? parsedAmountMsat() else {
			return
		}
		
		var minMsat: Int64 = 0
		var maxMsat: Int64 = 0
		
		if let paymentRequest = paymentRequest(), let paymentRequestAmt = paymentRequest.amount {
			// This is a paymentRequest with a specific amount.
			// So it doesn't matter what the user typed in.
			// The min must be paymentRequest.amount.
			
			minMsat = paymentRequestAmt.msat
			maxMsat = paymentRequestAmt.msat * 2
			
		} else {
			// Could be:
			// - amountless paymentRequest
			// - lnurl-pay with range
			//
			// Either way, the user is typing in an amount, and then tapping the "tip" button.
			// So the base amount is what they typed in, and they are using the tip screen to do the math.
			
			minMsat = msat
			maxMsat = msat * 2
			preTipAmountMsat = msat
			
			if let range = priceRange() {
				if range.contains(msat: msat) {
					maxMsat = min(maxMsat, range.max.msat)
				} else {
					// User typed in an invalid amount.
					// So we're just going to show them the whole range.
					minMsat = range.min.msat
					maxMsat = range.max.msat
				}
			}
		}
		
		let isRange = maxMsat > minMsat
		if isRange {
			
			// A range of valid amounts are possible.
			// Show the PriceSliderSheet.
			
			let range = MsatRange(min: minMsat, max: maxMsat)
			
			if msat < minMsat {
				msat = minMsat
			} else if msat > maxMsat {
				msat = maxMsat
			}
			
			let flow: FlowType
			if lnurlWithdraw() != nil {
				flow = FlowType.withdraw(range: range)
			} else {
				flow = FlowType.pay(range: range)
			}
			
			priceSliderVisible = true
			dismissKeyboardIfVisible()
			smartModalState.display(dismissable: true) {
				
				PriceSliderSheet(
					flow: flow,
					msat: msat,
					valueChanged: amountChanged_priceSliderSheet
				)
				
			} onWillDisappear: {
				priceSliderVisible = false
			}
			
		} else if msat != minMsat {
			msat = minMsat
			
			// There is only one valid amount.
			// We set the amount directly via the button tap.
			
			amountChangedExternally(minMsat)
		}
	}
	
	func amountChanged_rangeSheet(_ msat: Int64) {
		log.trace("amountChanged_priceSliderSheet()")
		amountChangedExternally(msat)
		preTipAmountMsat = nil
		postTipAmountMsat = nil
	}
	
	func amountChanged_priceSliderSheet(_ msat: Int64) {
		log.trace("amountChanged_priceSliderSheet()")
		amountChangedExternally(msat)
	}
	
	func amountChangedExternally(_ msat: Int64) {
		log.trace("amountChangedExternally()")
		
		if case .fiat(let fiatCurrency) = currency,
			let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
		{
			// The calculations were done in msat, but the user is in fiat mode.
			// So let's perform the conversion back to fiat.
			// But let's also keep the msat precision for internal calculations.
			
			let amtDbl = Utils.convertToFiat(msat: msat, exchangeRate: exchangeRate)
			let amtFrmt = Utils.formatFiat(amount: amtDbl, fiatCurrency: currencyPrefs.fiatCurrency)
			
			parsedAmount = Result.success(amtDbl)
			amount = amtFrmt.digits
			postTipAmountMsat = msat
			
		} else {
			
			let preferredBitcoinUnit = currencyPrefs.bitcoinUnit
			currency = Currency.bitcoin(preferredBitcoinUnit)
			currencyPickerChoice = currency.shortName
			
			// The TextFieldCurrencyStyler doesn't seem to fire when we manually set the text value.
			// So we need to do it manually here, to ensure the `parsedAmount` is properly updated.
			
			let amtDbl = Utils.convertBitcoin(msat: msat, to: preferredBitcoinUnit)
			let amtFrmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: preferredBitcoinUnit, policy: .showMsatsIfNonZero)
			
			parsedAmount = Result.success(amtDbl)
			amount = amtFrmt.digits
			postTipAmountMsat = nil
		}
	}
	
	func commentButtonTapped() {
		log.trace("commentButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		let maxCommentLength = lnurlPay.maxCommentLength?.intValue ?? 140
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			
			CommentSheet(
				comment: $comment,
				maxCommentLength: maxCommentLength
			)
		}
	}
	
	func prepareTransaction() {
		log.trace("prepareTransaction()")
		
		guard let msat = parsedAmountMsat() else {
			return
		}
		
		let amountSat = Int64(Utils.convertBitcoin(msat: msat, to: .sat))
		
		if let model = mvi.model as? Scan.Model_SwapOutFlow_Init {
			mvi.intent(Scan.Intent_SwapOutFlow_Prepare(
				address: model.address,
				amount: Bitcoin_kmpSatoshi(sat: amountSat)
			))
		}
	}
	
	func sendPayment() {
		log.trace("sendPayment()")
		
		guard let msat = parsedAmountMsat() else {
			return
		}
		
		let saveTipPercentInPrefs = {
			if let nums = paymentNumbers(), nums.tipMsat > 0 {
				let tipPercent = Int(nums.tipPercent * 100.0)
				Prefs.shared.addRecentTipPercent(tipPercent)
			}
		}
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			
			saveTipPercentInPrefs()
			mvi.intent(Scan.Intent_InvoiceFlow_SendInvoicePayment(
				paymentRequest: model.paymentRequest,
				amount: Lightning_kmpMilliSatoshi(msat: msat),
				maxFees: Prefs.shared.maxFees?.toKotlin()
			))
			
		} else if let model = mvi.model as? Scan.Model_SwapOutFlow_Ready {
			
			saveTipPercentInPrefs()
			mvi.intent(Scan.Intent_SwapOutFlow_Send(
				amount: model.initialUserAmount.plus(other: model.fee),
				swapOutFee: model.fee,
				address: model.address,
				paymentRequest: model.paymentRequest,
				maxFees: Prefs.shared.maxFees?.toKotlin()
			))
			
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			
			if showCommentButton() && comment.count == 0 && !hasPromptedForComment {
				
				let maxCommentLength = model.paymentIntent.maxCommentLength?.intValue ?? 140
				
				dismissKeyboardIfVisible()
				smartModalState.display(dismissable: true) {
					
					CommentSheet(
						comment: $comment,
						maxCommentLength: maxCommentLength,
						sendButtonAction: { sendPayment() }
					)
				
				} onWillDisappear: {
					
					log.debug("smartModalState.onWillDisappear {}")
					hasPromptedForComment = true
				}
				
			} else {
				
				saveTipPercentInPrefs()
				mvi.intent(Scan.Intent_LnurlPayFlow_RequestInvoice(
					paymentIntent: model.paymentIntent,
					amount: Lightning_kmpMilliSatoshi(msat: msat),
					maxFees: Prefs.shared.maxFees?.toKotlin(),
					comment: comment
				))
			}
			
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			
			saveTipPercentInPrefs()
			mvi.intent(Scan.Intent_LnurlWithdrawFlow_SendLnurlWithdraw(
				lnurlWithdraw: model.lnurlWithdraw,
				amount: Lightning_kmpMilliSatoshi(msat: msat),
				description: nil
			))
		}
	}
	
	func didCancelSwapOutRequest() {
		log.trace("didCancelSwapOutRequest()")
		
		guard let model = mvi.model as? Scan.Model_SwapOutFlow_Requesting else {
			return
		}
		
		mvi.intent(Scan.Intent_SwapOutFlow_Invalidate(
			address: model.address
		))
	}
	
	func didCancelLnurlPayFetch() {
		log.trace("didCancelLnurlPayFetch()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		mvi.intent(Scan.Intent_LnurlPayFlow_CancelLnurlPayment(
			lnurlPay: lnurlPay
		))
	}
	
	func didCancelLnurlWithdrawFetch() {
		log.trace("didCancelLnurlWithdrawFetch()")
		
		guard let lnurlWithdraw = lnurlWithdraw() else {
			return
		}
		
		mvi.intent(Scan.Intent_LnurlWithdrawFlow_CancelLnurlWithdraw(
			lnurlWithdraw: lnurlWithdraw
		))
	}
	
	func currencyConverterAmountChanged(_ result: CurrencyAmount?) {
		log.trace("currencyConverterAmountChanged()")
		
		if let newAmt = result {

			let newCurrencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: newAmt.currency)

			if currencyList != newCurrencyList {
				currencyList = newCurrencyList
			}

			currency = newAmt.currency
			currencyPickerChoice = newAmt.currency.shortName

			let formattedAmt: FormattedAmount
			switch newAmt.currency {
			case .bitcoin(let bitcoinUnit):
				formattedAmt = Utils.formatBitcoin(
					amount: newAmt.amount,
					bitcoinUnit: bitcoinUnit,
					policy: .showMsatsIfNonZero
				)
			case .fiat(let fiatCurrency):
				formattedAmt = Utils.formatFiat(
					amount: newAmt.amount,
					fiatCurrency: fiatCurrency
				)
			}

			parsedAmount = Result.success(newAmt.amount)
			amount = formattedAmt.digits

		} else {

			parsedAmount = Result.failure(.emptyInput)
			amount = ""
		}
	}
	
	func showAppStatusPopover() {
		log.trace("showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}
