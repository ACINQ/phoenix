import SwiftUI
import PhoenixShared

fileprivate let filename = "ValidateView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentNumbers {
	let baseMsat: Int64
	let tipMsat: Int64
	let lightningFeeMsat: Int64
	let minerFeeMsat: Int64
	let totalMsat: Int64
	let tipPercent: Double
	let lightningFeePercent: Double
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
	
	@State var spliceOutInProgress: Bool = false
	@State var spliceOutProblem: SpliceOutProblem? = nil
	
	@State var preTipAmountMsat: Int64? = nil
	@State var postTipAmountMsat: Int64? = nil
	@State var tipSliderSheetVisible: Bool = false
	
	@State var minerFeeInfo: MinerFeeInfo? = nil
	@State var satsPerByte: String = ""
	@State var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError> = Result.failure(.emptyInput)
	
	@State var allowOverpayment = Prefs.shared.allowOverpayment
	
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var comment: String = ""
	@State var hasPromptedForComment = false
	
	@State var hasShownChannelCapacityWarning = false
	@State var hasPickedSwapOutMode = false
	
	@State var didAppear = false
	
	let balancePublisher = Biz.business.balanceManager.balancePublisher()
	@State var balanceMsat: Int64 = 0
	
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	// For the cicular buttons: [metadata, tip, comment]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
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
					content()
						.frame(width: geometry.size.width)
						.frame(minHeight: geometry.size.height)
				}
				.onTapGesture {
					dismissKeyboardIfVisible()
				}
			}
			
			if mvi.model is Scan.Model_LnurlPayFlow_LnurlPayFetch {
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
		.navigationStackDestination(isPresented: $currencyConverterOpen) { // For iOS 16+
			currencyConverterView()
		}
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: currencyPickerChoice) { _ in
			currencyPickerDidChange()
		}
		.onReceive(Prefs.shared.allowOverpaymentPublisher) {
			allowOverpayment = $0
		}
		.onReceive(balancePublisher) {
			balanceDidChange($0)
		}
		.task {
			await fetchMempoolRecommendedFees()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
	
		VStack {
			header()
			amountField()
			
			Text(altAmount)
				.font(.caption)
				.foregroundColor(problem != nil ? Color.appNegative : .secondary)
				.padding(.top, 4)
				.padding(.bottom)
			
			optionalButtons()
			
			if mvi.model is Scan.Model_OnChainFlow {
				onChainDetails()
			} else {
				paymentDescription()
			}
			
			paymentButton()
			otherWarning()
			
			paymentSummary()
				.padding(.top)
				.padding(.top)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func header() -> some View {
		
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
			Text("amount to receive")
				.textCase(.uppercase)
				.padding(.bottom, 4)
		}
	}
	
	@ViewBuilder
	func amountField() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline) {
			TextField(verbatim: "123", text: currencyStyler().amountProxy)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.fixedSize()
				.font(.title)
				.disabled(isInvoiceWithAmount() && !allowOverpayment)
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
	}
	
	@ViewBuilder
	func optionalButtons() -> some View {
		
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
	func paymentDescription() -> some View {
		
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
	}
	
	@ViewBuilder
	func onChainDetails() -> some View {
		
		if let model = mvi.model as? Scan.Model_OnChainFlow {
			OnChainDetails(model: model)
				.padding(.horizontal, 60)
				.padding(.vertical)
				.padding(.bottom)
		}
	}
	
	@ViewBuilder
	func paymentButton() -> some View {
		
		let needsPrepare = (mvi.model is Scan.Model_OnChainFlow) && (minerFeeInfo == nil)
		
		Button {
			if needsPrepare {
				showMinerFeeSheet()
			} else {
				sendPayment()
			}
		} label: {
			HStack {
				if needsPrepare {
					Image(systemName: "hammer")
						.renderingMode(.template)
					Text("Prepare Transaction")
				} else if mvi.model is Scan.Model_LnurlWithdrawFlow {
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
		.disabled(problem != nil || isDisconnected || spliceOutInProgress)
		.accessibilityHint(paymentButtonHint())
	}
	
	@ViewBuilder
	func otherWarning() -> some View {
		
		if problem == nil {
			
			if isDisconnected {
				
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
				
			} else if let spliceOutProblem {
				
				Text(spliceOutProblem.localizedDescription())
					.foregroundColor(.appNegative)
			}
		}
	}
	
	@ViewBuilder
	func paymentSummary() -> some View {
		
		PaymentSummaryView(
			problem: $problem,
			paymentNumbers: paymentNumbers(),
			showMinerFeeSheet: showMinerFeeSheet
		)
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

	var isDisconnected: Bool {
		return !connectionsMonitor.connections.global.isEstablished()
	}
	
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
		
		if let paymentRequest = paymentRequest() {
			return paymentRequest.desc_()
			
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
		
		if mvi.model is Scan.Model_OnChainFlow {
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
		
		if !connectionsMonitor.connections.internet.isEstablished() {
			return NSLocalizedString("waiting for internet", comment: "button text")
		}
		if !connectionsMonitor.connections.peer.isEstablished() {
			return NSLocalizedString("connecting to peer", comment: "button text")
		}
		if !connectionsMonitor.connections.electrum.isEstablished() {
			return NSLocalizedString("connecting to electrum", comment: "button text")
		}
		return ""
	}
	
	// --------------------------------------------------
	// MARK: Accessibility
	// --------------------------------------------------
	
	func paymentButtonHint() -> String {
		
		if mvi.model is Scan.Model_OnChainFlow {
			
			return NSLocalizedString("continue to next step", comment: "VoiceOver hint")
			
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
	// MARK: Tasks
	// --------------------------------------------------
	
	func fetchMempoolRecommendedFees() async {
		
		for try await response in MempoolMonitor.shared.stream() {
			mempoolRecommendedResponse = response
			if Task.isCancelled {
				return
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
			
		} else if let model = mvi.model as? Scan.Model_OnChainFlow {
			
			if let amount_sat = model.uri.amount {
				return Lightning_kmpMilliSatoshi(sat: amount_sat)
			} else if let paymentRequest = model.uri.paymentRequest {
				return paymentRequest.amount
			}
		}
		
		return nil
	}
	
	func paymentRequest() -> Lightning_kmpBolt11Invoice? {
		
		if let model = mvi.model as? Scan.Model_Bolt11InvoiceFlow_InvoiceRequest {
			return model.invoice
		} else {
			// Note: there's technically a `paymentRequest` within `Scan.Model_SwapOutFlow_Ready`.
			// But this method is designed to only pull from `Scan.Model_Bolt11InvoiceFlow_InvoiceRequest`.
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
	/// - this is an on-chain payment (i.e. splice-out)
	///
	func isAmountlessInvoice() -> Bool {
		
		if mvi.model is Scan.Model_OnChainFlow {
			return true
		} else if let paymentRequest = paymentRequest() {
			return paymentRequest.amount == nil
		} else {
			return false
		}
	}
	
	/// Returns true if this is a Bolt 11 invoice with an (exact) amount.
	/// When this is the case, we may disable manual editing of the amount field.
	///
	func isInvoiceWithAmount() -> Bool {
		
		return paymentRequest()?.amount != nil
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
	
	func defaultTrampolineFees() -> Lightning_kmpTrampolineFees? {
		
		guard let peer = Biz.business.peerManager.peerStateValue() else {
			return nil
		}
		
		return peer.walletParams.trampolineFees.first
	}
	
	func paymentNumbers() -> PaymentNumbers? {
		
		guard let recipientAmountMsat = parsedAmountMsat() else {
			return nil
		}
		
		var preTipMsat: Int64? = nil
		if let paymentRequest = paymentRequest() {
			preTipMsat = paymentRequest.amount?.msat
		} else if let preTipAmountMsat = preTipAmountMsat {
			preTipMsat = preTipAmountMsat
		}
		let baseMsat = preTipMsat ?? recipientAmountMsat
		let tipMsat = recipientAmountMsat - baseMsat
		
		let lightningFeeMsat: Int64
		if mvi.model is Scan.Model_OnChainFlow {
			lightningFeeMsat = 0
		} else if let _ = lnurlWithdraw() {
			lightningFeeMsat = 0
		} else if let trampolineFees = defaultTrampolineFees() {
			let p1 = Utils.toMsat(sat: trampolineFees.feeBase)
			let f2 = Double(trampolineFees.feeProportional) / 1_000_000
			let p2 = Int64(Double(recipientAmountMsat) * f2)
			lightningFeeMsat = p1 + p2
		} else {
			lightningFeeMsat = 0
		}
		
		let minerFeeMsat: Int64
		if let minerFeeInfo {
			minerFeeMsat = Utils.toMsat(sat: minerFeeInfo.minerFee)
		} else {
			minerFeeMsat = 0
		}
		
		if tipMsat <= 0 && lightningFeeMsat <= 0 && minerFeeMsat <= 0 {
			return nil
		}
		
		let totalMsat = recipientAmountMsat + lightningFeeMsat + minerFeeMsat
		let tipPercent = Double(tipMsat) / Double(baseMsat)
		let lightningFeePercent = Double(lightningFeeMsat) / Double(recipientAmountMsat)
		let minerFeePercent = Double(minerFeeMsat) / Double(recipientAmountMsat)
		
		return PaymentNumbers(
			baseMsat            : baseMsat,
			tipMsat             : tipMsat,
			lightningFeeMsat    : lightningFeeMsat,
			minerFeeMsat        : minerFeeMsat,
			totalMsat           : totalMsat,
			tipPercent          : tipPercent,
			lightningFeePercent : lightningFeePercent,
			minerFeePercent     : minerFeePercent
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
		
		if let model = mvi.model as? Scan.Model_OnChainFlow,
		   model.uri.paymentRequest != nil,
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
		// * OnChainFlow -> InvoiceFlow_X                => range changed (e.g. minAmount)
		//
		if newModel is Scan.Model_Bolt11InvoiceFlow {
			
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
			}
			
		} else { // user selected "other"
			
			currencyConverterOpen = true
			currencyPickerChoice = currency.shortName // revert to last real currency
		}
		
		if !tipSliderSheetVisible {
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
				}
			}
			
			if problem == nil,
			   let msat = msat,
			   let range = priceRange()
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
		
		var range: MsatRange? = priceRange()
		
		if range == nil && isAmountlessInvoice() {
			if let parsedAmtMst = parsedAmountMsat() {
				range = MsatRange(min: parsedAmtMst, max: parsedAmtMst + parsedAmtMst)
			}
		}
		
		if let range {
			
			dismissKeyboardIfVisible()
			smartModalState.display(dismissable: true) {
				
				RangeSheet(range: range, valueChanged: amountChanged_rangeSheet)
			}
		}
	}
	
	func tipButtonTapped() {
		log.trace("tipButtonTapped()")
		
		guard var currentMsat = parsedAmountMsat() else {
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
			
			let baseMsat = preTipAmountMsat ?? currentMsat
			minMsat = baseMsat
			maxMsat = baseMsat * 2
			preTipAmountMsat = baseMsat
			
			// There are edge-cases with lnurl-pay here:
			// - user may have typed in an amount that's out-of-bounds of the accepted range
			// - our calculated maxMsat may be out-of-bounds of the accepted range
			//
			// From a UI perspective, it's more important that the TipSliderSheet is consistent.
			// So we prefer to handle the out-of-range problems via `refreshAltAmount`,
			// which will disable the pay button and display the proper error message.
		}
		
		// Show the TipSliderSheet.
		
		let range = MsatRange(min: minMsat, max: maxMsat)
		
		if currentMsat < minMsat {
			currentMsat = minMsat
		} else if currentMsat > maxMsat {
			currentMsat = maxMsat
		}
		
		tipSliderSheetVisible = true
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			
			TipSliderSheet(
				range: range,
				msat: currentMsat,
				valueChanged: amountChanged_priceSliderSheet
			)
			
		} onWillDisappear: {
			tipSliderSheetVisible = false
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
	
	func showMinerFeeSheet() {
		log.trace("showMinerFeeSheet()")
		
		guard
			let msat = parsedAmountMsat(),
			let model = mvi.model as? Scan.Model_OnChainFlow
		else {
			return
		}
		
		let sat = Utils.truncateToSat(msat: msat)
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			
			MinerFeeSheet(
				target: .spliceOut,
				amount: Bitcoin_kmpSatoshi(sat: sat),
				btcAddress: model.uri.address,
				minerFeeInfo: $minerFeeInfo,
				satsPerByte: $satsPerByte,
				parsedSatsPerByte: $parsedSatsPerByte,
				mempoolRecommendedResponse: $mempoolRecommendedResponse
			)
		}
		smartModalState.onNextDidDisappear {
			maybeShowCapacityImpactWarning()
		}
	}
	
	func maybeShowCapacityImpactWarning() {
		log.trace("maybeShowCapacityImpactWarning()")
		
		guard !Prefs.shared.doNotShowChannelImpactWarning else {
			log.debug("Prefs.shared.doNotShowChannelImpact = true")
			return
		}
		guard !hasShownChannelCapacityWarning else {
			log.debug("hasShownChannelCapacityWarning = true")
			return
		}
		
		smartModalState.display(dismissable: false) {
			ChannelSizeImpactWarning()
		} onWillDisappear: {
			hasShownChannelCapacityWarning = true
		}
	}
	
	func sendPayment() {
		log.trace("sendPayment()")
		
		guard
			let msat = parsedAmountMsat(),
			let trampolineFees = defaultTrampolineFees()
		else {
			return
		}
		
		let saveTipPercentInPrefs = {
			if let nums = paymentNumbers(), nums.tipMsat > 0 {
				let tipPercent = Int(nums.tipPercent * 100.0)
				Prefs.shared.addRecentTipPercent(tipPercent)
			}
		}
		
		if let model = mvi.model as? Scan.Model_Bolt11InvoiceFlow_InvoiceRequest {
			
			saveTipPercentInPrefs()
			mvi.intent(Scan.Intent_Bolt11InvoiceFlow_SendInvoicePayment(
				invoice: model.invoice,
				amount: Lightning_kmpMilliSatoshi(msat: msat),
				trampolineFees: trampolineFees
			))
			
		} else if let _ = mvi.model as? Scan.Model_OnChainFlow {
			
			guard
				let minerFeeInfo = minerFeeInfo,
				let peer = Biz.business.peerManager.peerStateValue(),
				spliceOutInProgress == false
			else {
				return
			}
			
			spliceOutInProgress = true
			spliceOutProblem = nil
			
			let amountSat = Bitcoin_kmpSatoshi(sat: Utils.truncateToSat(msat: msat))
			Task { @MainActor in
				do {
					let response = try await peer.spliceOut(
						amount: amountSat,
						scriptPubKey: minerFeeInfo.pubKeyScript,
						feerate: minerFeeInfo.feerate
					)
					
					self.spliceOutInProgress = false
					
					if let problem = SpliceOutProblem.fromResponse(response) {
						self.spliceOutProblem = problem
						
					} else {
						self.spliceOutProblem = nil
						self.presentationMode.wrappedValue.dismiss()
					}
					
				} catch {
					log.error("peer.spliceOut(): error: \(error)")
					
					self.spliceOutInProgress = false
					self.spliceOutProblem = .other
					
				}
			} // </Task>
			
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
				
				let updateMsat: Lightning_kmpMilliSatoshi
				if currency.type == .bitcoin {
					updateMsat = Lightning_kmpMilliSatoshi(msat: msat)
				} else {
					// Workaround for WalletOfSatoshi bug:
					//
					// It's common for a user to enter an amount in fiat,
					// and a proper conversion will often yield an amount with non-zero msat component.
					//
					// For example: 1.00 USD ==> 3,652.451 sat
					//                                 ^^^
					//                                 non-zero millisats
					//
					// However, when making a LN payment to a Lightning address,
					// the WoS service does not properly handle millisats and will generate an invoice
					// whose amount is rounded UP to the nearest whole satoshi.
					// In the example above, they would generate an invoice for 3,653 sat.
					// 
					// As such Phoenix correctly rejects the invoice, because the generated amount
					// does not match the expected amount.
					//
					// Even though this is a bug in WoS, Phoenix is regularly blamed for the problem.
					// So our workaround is to trim the msat component in this scenario.
					//
					// For more information:
					// https://github.com/ACINQ/phoenix/issues/388
					//
					let truncatedToSat = Lightning_kmpMilliSatoshi(msat: msat).truncateToSatoshi()
					updateMsat = Lightning_kmpMilliSatoshi(sat: truncatedToSat)
				}
				
				mvi.intent(Scan.Intent_LnurlPayFlow_RequestInvoice(
					paymentIntent: model.paymentIntent,
					amount: updateMsat,
					trampolineFees: trampolineFees,
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
