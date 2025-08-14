import SwiftUI
import PhoenixShared

fileprivate let filename = "ValidateView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ValidateView: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case CurrencyConverter
		case PaymentRequestedView(invoice: Lightning_kmpBolt11Invoice)
		
		var description: String {
			switch self {
			case .CurrencyConverter       : return "CurrencyConverter"
			case .PaymentRequestedView(_) : return "PaymentRequestedView"
			}
		}
	}
	
	let popTo: (PopToDestination) -> Void // For iOS 16

	@State var flow: SendManager.ParseResult
	
	@State var isLnurlFetch: Bool = false
	
	@State var currency = Currency.bitcoin(.sat)
	@State var currencyList: [Currency] = [Currency.bitcoin(.sat)]
	
	@State var currencyPickerChoice: String = Currency.bitcoin(.sat).shortName
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	
	enum Problem: Error {
		case emptyInput
		case invalidInput
		case amountExceedsBalance
		case finalAmountExceedsBalance // including minerFee
		case amountOutOfRange
	}
	@State var problem: Problem? = nil
	
	@State var paymentInProgress: Bool = false
	@State var payOfferProblem: PayOfferProblem? = nil
	@State var spliceOutProblem: ChannelFundingProblem? = nil
	
	@State var preTipAmountMsat: Int64? = nil
	@State var postTipAmountMsat: Int64? = nil
	@State var tipSliderSheetVisible: Bool = false
	
	@State var minerFeeInfo: MinerFeeInfo? = nil
	@State var satsPerByte: String = ""
	@State var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError> = Result.failure(.emptyInput)
	
	@State var allowOverpayment = Prefs.current.allowOverpayment
	
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var comment: String = ""
	@State var hasPromptedForComment = false
	
	@State var hasShownChannelCapacityWarning = false
	@State var hasPickedSwapOutMode = false
	
	@State var contact: ContactInfo? = nil
	
	@State var didAppear = false
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	
	@State var payIndex: Int = 0
	
	let balancePublisher = Biz.business.balanceManager.balancePublisher()
	@State var balanceMsat: Int64 = 0
	
	// For the cicular buttons: [metadata, tip, comment]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
	@StateObject var toast = Toast()
	@StateObject var connectionsMonitor = ObservableConnectionsMonitor()
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(flow: SendManager.ParseResult, popTo: @escaping (PopToDestination) -> Void) {
		self._flow = State(initialValue: flow)
		self.popTo = popTo
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(
				isLnurlWithdrawFlow
					? NSLocalizedString("Confirm Withdraw", comment: "Navigation bar title")
					: NSLocalizedString("Confirm Payment", comment: "Navigation bar title")
			)
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			if Biz.showTestnetBackground {
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
			
			if parseProgress != nil {
				FetchActivityNotice(
					title: fetchActivityTitle_parsing(),
					onCancel: { cancelParseRequest() }
				)
			}
			
			if isLnurlFetch {
				FetchActivityNotice(
					title: fetchActivityTitle_lnurlFetch(),
					onCancel: { cancelLnurlFetch() }
				)
			}
			
			toast.view()
			
		}// </ZStack>
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: currencyPickerChoice) { _ in
			currencyPickerDidChange()
		}
		.onReceive(Prefs.current.allowOverpaymentPublisher) {
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
				.padding(.bottom, 4)
			
			Text(altAmount)
				.font(.caption)
				.foregroundColor(problem != nil ? Color.appNegative : .secondary)
				.padding(.bottom)
			
			optionalButtons()
			
			paymentDetails()
				.padding(.vertical)
			
			paymentButton()
			otherWarning()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func header() -> some View {
		
		Spacer().frame(height: 20)

		if let host = paymentHost() {
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				if isLnurlWithdrawFlow {
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
		
		if isLnurlWithdrawFlow {
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
		
		let metadataVisible = metadataButtonVisible()
		let tipVisible = tipButtonVisible()
		let commentVisible = commentButtonVisible()
		
		if metadataVisible || tipVisible || commentVisible {
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				if metadataVisible {
					metadataButton()
				}
				if tipVisible {
					tipButton()
				}
				if commentVisible {
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
			showCommentSheet()
		}
	}
	
	@ViewBuilder
	func paymentDetails() -> some View {
		
		PaymentDetails(parent: self)
			.padding(.horizontal, 10)
	}
	
	@ViewBuilder
	func paymentButton() -> some View {
		
		let needsPrepare = isOnChainFlow && (minerFeeInfo == nil)
		let fetchingInvoice = isBolt12OfferFlow && paymentInProgress
		
		Button {
			if needsPrepare {
				showMinerFeeSheet()
			} else {
				maybeSendPayment()
			}
		} label: {
			HStack {
				if needsPrepare {
					Image(systemName: "hammer")
						.renderingMode(.template)
					Text("Prepare Transaction")
				} else if fetchingInvoice {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle())
						.padding(.trailing, 2)
					Text("Fetching invoice...")
				} else if isLnurlWithdrawFlow {
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
		.disabled(problem != nil || isDisconnected || paymentInProgress)
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
				
			} else if let payOfferProblem {
				
				Text(payOfferProblem.localizedDescription())
					.multilineTextAlignment(.center)
					.foregroundColor(.appNegative)
					.padding(.horizontal)
				
			} else if let spliceOutProblem {
				
				Text(spliceOutProblem.localizedDescription())
					.multilineTextAlignment(.center)
					.foregroundColor(.appNegative)
					.padding(.horizontal)
			}
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .CurrencyConverter:
			CurrencyConverterView(
				initialAmount: currentAmount(),
				didChange: currencyConverterAmountChanged,
				didClose: nil
			)
			
		case .PaymentRequestedView(let invoice):
			if let withdrawFlow = flow as? SendManager.ParseResult_Lnurl_Withdraw {
				PaymentRequestedView(flow: withdrawFlow, invoice: invoice, popTo: self.popToWrapper)
			} else {
				EmptyView()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	var isDisconnected: Bool {
		return !connectionsMonitor.connections.global.isEstablished()
	}
	
	var isBolt11InvoiceFlow: Bool {
		return flow is SendManager.ParseResult_Bolt11Invoice
	}
	
	var isBolt12OfferFlow: Bool {
		return flow is SendManager.ParseResult_Bolt12Offer
	}
	
	var isLnurlPayFlow: Bool {
		return flow is SendManager.ParseResult_Lnurl_Pay
	}
	
	var isLnurlWithdrawFlow: Bool {
		return flow is SendManager.ParseResult_Lnurl_Withdraw
	}
	
	var isLnurlAuthFlow: Bool {
		return flow is SendManager.ParseResult_Lnurl_Auth
	}
	
	var isOnChainFlow: Bool {
		// This is a bit confusing, because a Uri might contain (for example) both L1 & L2 instructions.
		// But when this is the case, we prompt the user to choose which layer.
		// And if they choose L2, then we call `parse` again, and update our `flow` variable.
		//
		return flow is SendManager.ParseResult_Uri
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
	
	func fetchActivityTitle_parsing() -> String {
		
		if parseProgress is SendManager.ParseProgress_LnurlServiceFetch {
			return String(localized: "Fetching Lightning URL", comment: "Progress title")
		} else {
			return String(localized: "Resolving lightning address", comment: "Progress title")
		}
	}
	
	func fetchActivityTitle_lnurlFetch() -> String {
		
		if isLnurlPayFlow {
			return String(localized: "Fetching Invoice", comment: "Progress title")
		} else {
			return String(localized: "Forwarding Invoice", comment: "Progress title")
		}
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
	
	func metadataButtonVisible() -> Bool {
		
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
	
	func tipButtonVisible() -> Bool {
		
		if isOnChainFlow {
			return true
			
		} else if let _ = bolt11Invoice() {
			return true
			
		} else if let _ = bolt12Offer() {
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
	
	func commentButtonVisible() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}

		let maxCommentLength = lnurlPay.maxCommentLength?.int64Value ?? 0
		return maxCommentLength > 0
	}
	
	func disconnectedText() -> String {
		
		if !connectionsMonitor.connections.internet.isEstablished() {
			return String(localized: "waiting for internet", comment: "button text")
		}
		if !connectionsMonitor.connections.peer.isEstablished() {
			return String(localized: "connecting to peer", comment: "button text")
		}
		if !connectionsMonitor.connections.electrum.isEstablished() {
			return String(localized: "connecting to electrum", comment: "button text")
		}
		return ""
	}
	
	// --------------------------------------------------
	// MARK: Accessibility
	// --------------------------------------------------
	
	func paymentButtonHint() -> String {
		
		if isOnChainFlow {
			
			return String(localized: "continue to next step", comment: "VoiceOver hint")
			
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
		
		if let invoice = bolt11Invoice() {
			return invoice.amount
			
		} else if let offer = bolt12Offer() {
			return offer.amount
			
		} else if let lnurlPay = lnurlPay() {
			return lnurlPay.minSendable
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.maxWithdrawable
			
		} else if let model = flow as? SendManager.ParseResult_Uri {
			
			if let amount_sat = model.uri.amount {
				return Lightning_kmpMilliSatoshi(sat: amount_sat)
			} else if let paymentRequest = model.uri.paymentRequest {
				return paymentRequest.amount
			}
		}
		
		return nil
	}
	
	func lightningAddress() -> String? {
		
		var result: String? = nil
		if let model = flow as? SendManager.ParseResult_Bolt12Offer {
			result = model.lightningAddress
		} else if let model = flow as? SendManager.ParseResult_Lnurl_Pay {
			result = model.lightningAddress
		}
		
		return result?.trimmingCharacters(in: .whitespacesAndNewlines)
	}
	
	func bolt11Invoice() -> Lightning_kmpBolt11Invoice? {
		
		if let model = flow as? SendManager.ParseResult_Bolt11Invoice {
			return model.invoice
		} else {
			return nil
		}
	}
	
	func bolt12Offer() -> Lightning_kmpOfferTypesOffer? {
		
		if let model = flow as? SendManager.ParseResult_Bolt12Offer {
			return model.offer
		} else {
			return nil
		}
	}
	
	func lnurlPay() -> LnurlPay.Intent? {
		
		if let model = flow as? SendManager.ParseResult_Lnurl_Pay {
			return model.paymentIntent
		} else {
			return nil
		}
	}
	
	func lnurlWithdraw() -> LnurlWithdraw? {
		
		if let model = flow as? SendManager.ParseResult_Lnurl_Withdraw {
			return model.lnurlWithdraw
		} else {
			return nil
		}
	}
	
	/// Returns true if:
	/// - this is an on-chain payment
	/// - this is a bolt 11 invoice without a set amount
	/// - this is a bolt 12 offer without a set amount
	///
	func isAmountlessInvoice() -> Bool {
		
		if isOnChainFlow {
			return true
		} else if let invoice = bolt11Invoice() {
			return invoice.amount == nil
		} else if let offer = bolt12Offer() {
			return offer.amount == nil
		} else {
			return false
		}
	}
	
	/// Returns true if this is a Bolt 11 invoice with an (exact) amount.
	/// When this is the case, we may disable manual editing of the amount field.
	///
	func isInvoiceWithAmount() -> Bool {
		
		return bolt11Invoice()?.amount != nil
	}
	
	func priceRange() -> MsatRange? {
		
		if let invoice = bolt11Invoice() {
			if let min = invoice.amount {
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
	
	func paymentNumbers() -> PaymentSummary? {
		
		guard let recipientAmountMsat = parsedAmountMsat() else {
			return nil
		}
		
		var preTipMsat: Int64? = nil
		if let invoice = bolt11Invoice() {
			preTipMsat = invoice.amount?.msat
		} else if let preTipAmountMsat = preTipAmountMsat {
			preTipMsat = preTipAmountMsat
		}
		let baseMsat = preTipMsat ?? recipientAmountMsat
		let tipMsat = recipientAmountMsat - baseMsat
		
		let lightningFeeMsat: Int64
		if isOnChainFlow {
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
		
		return PaymentSummary(
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
	
	func paymentStrings() -> PaymentSummaryStrings? {
		
		return PaymentSummaryStrings.create(
			from: paymentNumbers(),
			currencyPrefs: currencyPrefs,
			problem: problem
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
		
		if !didAppear {
			didAppear = true
			
			// First time displaying this ValidateView
			
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
			
			if let model = flow as? SendManager.ParseResult_Uri,
				model.uri.paymentRequest != nil,
				!hasPickedSwapOutMode
			{
				log.debug("triggering popover w/PaymentLayerChoice")
		
				popoverState.display(dismissable: false) {
					PaymentLayerChoice(
						didChooseL1: self.paymentLayerChoice_didChooseL1,
						didChooseL2: self.paymentLayerChoice_didChooseL2
					)
				} onWillDisappear: {
					hasPickedSwapOutMode = true
				}
			}
			
		} else {
			
			// We are returning to this ValidateView from a child view (e.g. PaymentRequestedView).
			
			if let destination = popToDestination {
				log.debug("popToDestination: \(destination)")
				
				popToDestination = nil
				presentationMode.wrappedValue.dismiss()
			}
		}
		
		if let contactsDb = Biz.business.databaseManager.contactsDbValue() {
			if let address = lightningAddress() {
				contact = contactsDb.contactForLightningAddress(address: address)
			}
			if contact == nil, let offer = bolt12Offer() {
				contact = contactsDb.contactForOffer(offer: offer)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
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
			
			currencyPickerChoice = currency.shortName // revert to last real currency
			navigateTo(.CurrencyConverter)
		}
		
		if !tipSliderSheetVisible {
			preTipAmountMsat = nil
			postTipAmountMsat = nil
		}
	}
	
	func paymentLayerChoice_didChooseL1() {
		log.trace("paymentLayerChoice_didChooseL1()")
		
		// Nothing to do here except close the popover.
		// We're already setup to process the payment on L1.
		
		popoverState.close()
	}
	
	func paymentLayerChoice_didChooseL2() {
		log.trace("paymentLayerChoice_didChooseL2()")
		
		guard
			let model = flow as? SendManager.ParseResult_Uri,
			let paymentRequest = model.uri.paymentRequest
		else {
			return
		}
		
		parseUserInput(paymentRequest.write())
		popoverState.close()
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.description))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popToWrapper(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
			popTo(destination)
		}
	}
	
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
			
			if let msat = msat, !isLnurlWithdrawFlow {
				
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
	
	func tipButtonTapped() {
		log.trace("tipButtonTapped()")
		
		guard var currentMsat = parsedAmountMsat() else {
			return
		}
		
		var minMsat: Int64 = 0
		var maxMsat: Int64 = 0
		
		if let invoice = bolt11Invoice(), let invoiceAmt = invoice.amount {
			// This is an invoice with a specific amount.
			// So it doesn't matter what the user typed in.
			// The min must be invoice.amount.
			
			minMsat = invoiceAmt.msat
			maxMsat = invoiceAmt.msat * 2
			
		} else {
			// Could be:
			// - amountless invoice
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
	
	func showCommentSheet() {
		log.trace("showCommentSheet()")
		
		var maxCommentLength: Int? = nil
		if let _ = bolt12Offer() {
			maxCommentLength = 64
			
		} else if let lnurlPay = lnurlPay() {
			maxCommentLength = lnurlPay.maxCommentLength?.intValue ?? 140
		}
		
		guard let maxCommentLength else {
			log.warning("showCommentSheet(): ignored: unknown state")
			return
		}
		
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
			let model = flow as? SendManager.ParseResult_Uri
		else {
			return
		}
		
		let btcAddr = model.uri.address
		let amount = Bitcoin_kmpSatoshi(sat: Utils.truncateToSat(msat: msat))
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			
			MinerFeeSheet(
				target: .spliceOut(btcAddress: btcAddr, amount: amount),
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
		
		guard !Prefs.current.doNotShowChannelImpactWarning else {
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
	
	func cancelParseRequest() {
		log.trace("cancelParseRequest()")
		
		isParsing = false
		parseIndex += 1
		parseProgress = nil
	}
	
	func cancelLnurlFetch() {
		log.trace("cancelLnurlFetch()")
		
		payIndex += 1
		
		paymentInProgress = false
		isLnurlFetch = false
	}
	
	func popToRootView() {
		log.trace("popToRootView()")
		
		if #available(iOS 17, *) {
			navCoordinator.path.removeAll()
		} else { // iOS 16
			popTo(.RootView(followedBy: nil))
			presentationMode.wrappedValue.dismiss()
		}
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

			let formattedAmt = Utils.format(currencyAmount: newAmt, policy: .showMsatsIfNonZero)
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
	
	func didCopyLink() {
		log.trace("didCopyLink()")
		
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
	
	// --------------------------------------------------
	// MARK: Contacts
	// --------------------------------------------------
	
	func manageExistingContact() {
		log.trace("manageExistingContact()")
		
		guard let contact else {
			log.info("manageExistingContact(): ignoring: no existing contact")
			return
		}
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: false) {
			ManageContact(
				location: .smartModal,
				popTo: nil,
				info: nil,
				contact: contact,
				contactUpdated: contactUpdated
			)
		}
	}
	
	func addContact() {
		log.trace("addContact()")
		
		guard contact == nil else {
			log.info("addContact(): ignoring: contact already exists")
			return
		}
		
		let address = lightningAddress()
		
		var offer: Lightning_kmpOfferTypesOffer? = nil
		if address == nil {
			offer = bolt12Offer()
		}
		
		guard (address != nil) || (offer != nil) else {
			log.info("addContact(): ignoring: missing address/offer")
			return
		}
		
		let info = AddToContactsInfo(offer: offer, address: address)
		
		let count: Int = Biz.business.databaseManager.contactsDbValue()?.contactsListCount() ?? 0
		if count == 0 {
			// User doesn't have any contacts.
			// No choice but to create a new contact.
			addContact_createNew(info)
			
		} else {
			
			dismissKeyboardIfVisible()
			smartModalState.display(dismissable: true) {
				AddContactOptionsSheet(
					createNewContact: { addContact_createNew(info) },
					addToExistingContact: { addContact_selectExisting(info) }
				)
			}
		}
	}
	
	private func addContact_createNew(
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_createNew()")
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: false) {
			ManageContact(
				location: .smartModal,
				popTo: nil,
				info: info,
				contact: nil,
				contactUpdated: contactUpdated
			)
		}
	}
	
	private func addContact_selectExisting(
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_selectExisting()")
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			ContactsListSheet(didSelectContact: { existingContact in
				log.debug("didSelectContact")
				smartModalState.onNextDidDisappear {
					addContact_addToExisting(existingContact, info)
				}
			})
		}
	}
	
	private func addContact_addToExisting(
		_ existingContact: ContactInfo,
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_addToExisting()")
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: false) {
			ManageContact(
				location: .smartModal,
				popTo: nil,
				info: info,
				contact: existingContact,
				contactUpdated: contactUpdated
			)
		}
	}
	
	func contactUpdated(_ updatedContact: ContactInfo?) {
		log.trace("contactUpdated()")
		
		contact = updatedContact
	}
	
	// --------------------------------------------------
	// MARK: Sending
	// --------------------------------------------------
	
	func maybeSendPayment() {
		log.trace("maybeSendPayment()")
		
		dismissKeyboardIfVisible()
		
		let enabledSecurity = Keychain.current.enabledSecurity
		if enabledSecurity.contains(.spendingPin) {
			
			smartModalState.display(dismissable: false) {
				AuthenticateWithPinSheet(type: .spendingPin) { result in
					if result == .Authenticated {
						sendPayment()
					}
				}
			}
			
		} else {
			sendPayment()
		}
	}
	
	func sendPayment() {
		log.trace("sendPayment()")
		
		guard let msat = parsedAmountMsat() else {
			log.debug("ignored: msat == nil")
			return
		}
		
		if let model = flow as? SendManager.ParseResult_Bolt11Invoice {
			sendPayment_bolt11Invoice(model, msat)
			
		} else if let model = flow as? SendManager.ParseResult_Bolt12Offer {
			sendPayment_bolt12Offer(model, msat)
			
		} else if let model = flow as? SendManager.ParseResult_Uri {
			sendPayment_onChain(model, msat)
			
		} else if let model = flow as? SendManager.ParseResult_Lnurl_Pay {
			sendPayment_lnurlPay(model, msat)
			
		} else if let model = flow as? SendManager.ParseResult_Lnurl_Withdraw {
			sendPayment_lnurlWithdraw(model, msat)
		}
	}
	
	func sendPayment_bolt11Invoice(
		_ model: SendManager.ParseResult_Bolt11Invoice,
		_ msat: Int64
	) {
		log.trace("sendPayment_bolt11Invoice")
		
		guard !paymentInProgress else {
			log.warning("ignore: payment already in progress")
			return
		}
		guard let trampolineFees = defaultTrampolineFees() else {
			log.warning("ignore: trapolineFees == nil")
			return
		}
		
		paymentInProgress = true
		saveTipPercentInPrefs()
		Task { @MainActor in
			do {
				try await Biz.business.sendManager.payBolt11Invoice(
					amountToSend: Lightning_kmpMilliSatoshi(msat: msat),
					trampolineFees: trampolineFees,
					invoice: model.invoice,
					metadata: nil
				)
				popToRootView()
				
			} catch {
				log.error("payBolt11Invoice(): error: \(error)")
				paymentInProgress = false
			}
		} // </Task>
	}
	
	func sendPayment_bolt12Offer(
		_ model: SendManager.ParseResult_Bolt12Offer,
		_ msat: Int64
	) {
		log.trace("sendPayment_bolt12Offer()")
		
		guard !paymentInProgress else {
			log.warning("ignore: payment already in progress")
			return
		}
		
		paymentInProgress = true
		payOfferProblem = nil
		let payerNote = comment.isEmpty ? nil : comment
		
		saveTipPercentInPrefs()
		Task { @MainActor in
			do {
				let paymentId = Lightning_kmpUUID.companion.randomUUID()
				Biz.beginLongLivedTask(id: paymentId.description())
				
				let payerKey: Bitcoin_kmpPrivateKey
				if contact?.useOfferKey ?? false {
					let offerData = try await Biz.business.nodeParamsManager.defaultOffer()
					payerKey = offerData.payerKey
				} else {
					payerKey = Lightning_randomKey()
				}
				
				let response: Lightning_kmpOfferNotPaid? =
					try await Biz.business.sendManager.payBolt12Offer(
						paymentId: paymentId,
						amount: Lightning_kmpMilliSatoshi(msat: msat),
						offer: model.offer,
						lightningAddress: model.lightningAddress,
						payerKey: payerKey,
						payerNote: payerNote,
						fetchInvoiceTimeoutInSeconds: 30
					)
				
				paymentInProgress = false
				
				if let problem = PayOfferProblem.fromResponse(response) {
					payOfferProblem = problem
					Biz.endLongLivedTask(id: paymentId.description())
					
				} else {
					payOfferProblem = nil
					popToRootView()
				}
			
			} catch {
				log.error("peer.payOffer(): error: \(error)")
				
				paymentInProgress = false
				payOfferProblem = .other
			}
		} // </Task>
	}
	
	func sendPayment_onChain(
		_ model: SendManager.ParseResult_Uri,
		_ msat: Int64
	) {
		log.trace("sendPayment_onChain()")
		
		guard !paymentInProgress else {
			log.warning("ignore: payment already in progress")
			return
		}
		guard let peer = Biz.business.peerManager.peerStateValue() else {
			log.warning("ignore: peer == nil")
			return
		}
		guard
			let minerFeeInfo,
			let scriptPubKey = minerFeeInfo.pubKeyScript
		else {
			log.warning("ignore: minerFeeInfo info missing")
			return
		}
		
		paymentInProgress = true
		spliceOutProblem = nil
		
		let amountSat = Bitcoin_kmpSatoshi(sat: Utils.truncateToSat(msat: msat))
		Task { @MainActor in
			do {
				let response = try await peer.spliceOut(
					amount: amountSat,
					scriptPubKey: scriptPubKey,
					feerate: minerFeeInfo.feerate
				)
				
				self.paymentInProgress = false
				
				if let problem = ChannelFundingProblem.fromResponse(response) {
					spliceOutProblem = problem
					
				} else {
					spliceOutProblem = nil
					popToRootView()
				}
				
			} catch {
				log.error("peer.spliceOut(): error: \(error)")
				
				paymentInProgress = false
				spliceOutProblem = .other
			}
		} // </Task>
	}
	
	func sendPayment_lnurlPay(
		_ model: SendManager.ParseResult_Lnurl_Pay,
		_ msat: Int64
	) {
		log.trace("sendPayment_lnurlPay()")
		
		guard !paymentInProgress else {
			log.warning("ignore: payment already in progress")
			return
		}
		guard let trampolineFees = defaultTrampolineFees() else {
			log.warning("ignore: trapolineFees == nil")
			return
		}
		
		if commentButtonVisible() && comment.count == 0 && !hasPromptedForComment {
			
			let maxCommentLength = model.paymentIntent.maxCommentLength?.intValue ?? 140
			
			dismissKeyboardIfVisible()
			smartModalState.display(dismissable: true) {
				
				CommentSheet(
					comment: $comment,
					maxCommentLength: maxCommentLength,
					sendButtonAction: { maybeSendPayment() }
				)
			
			} onWillDisappear: {
				
				log.debug("smartModalState.onWillDisappear {}")
				hasPromptedForComment = true
			}
			
		} else {
			
			saveTipPercentInPrefs()
			
			let updatedMsat: Lightning_kmpMilliSatoshi
			if currency.type == .bitcoin {
				updatedMsat = Lightning_kmpMilliSatoshi(msat: msat)
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
				updatedMsat = Lightning_kmpMilliSatoshi(sat: truncatedToSat)
			}
			
			paymentInProgress = true
			isLnurlFetch = true
			let index = payIndex
			let commentSnapshot = comment
			
			Task { @MainActor in
				do {
					let result1: Bitcoin_kmpEither<SendManager.LnurlPayError, LnurlPay.Invoice> =
						try await Biz.business.sendManager.lnurlPay_requestInvoice(
							pay: model,
							amount: updatedMsat,
							comment: commentSnapshot
						)
					
					guard index == payIndex else {
						log.info("sendPayment_lnurlPay: ignoring fetch: cancelled")
						return
					}
					isLnurlFetch = false
					
					if result1.isLeft {
						let payError: SendManager.LnurlPayError = result1.left!
						
						popoverState.display(dismissable: true) {
							LnurlFlowErrorNotice(error: LnurlFlowError.pay(error: payError))
						}
						
					} else {
						let invoice: LnurlPay.Invoice = result1.right!
						
						try await Biz.business.sendManager.lnurlPay_payInvoice(
							pay: model,
							amount: updatedMsat,
							comment: commentSnapshot,
							invoice: invoice,
							trampolineFees: trampolineFees
						)
						
						popToRootView()
					}
					
				} catch {
					log.error("sendPayment_lnurlPay: error: \(error)")
					paymentInProgress = false
					isLnurlFetch = false
				}
			} // </Task>
		}
	}
	
	func sendPayment_lnurlWithdraw(
		_ model: SendManager.ParseResult_Lnurl_Withdraw,
		_ msat: Int64
	) {
		log.trace("sendPayment_lnurlWithdraw()")
		
		paymentInProgress = true
		isLnurlFetch = true
		let index = payIndex
		
		saveTipPercentInPrefs()
		Task { @MainActor in
			do {
				let invoice: Lightning_kmpBolt11Invoice =
					try await Biz.business.sendManager.lnurlWithdraw_createInvoice(
						lnurlWithdraw: model.lnurlWithdraw,
						amount: Lightning_kmpMilliSatoshi(msat: msat),
						description: nil
					)
				
				guard index == payIndex else {
					log.info("sendPayment_lnurlWithdraw: ignoring fetch: cancelled")
					return
				}
				isLnurlFetch = false
				
				let withdrawError: SendManager.LnurlWithdrawError? =
					try await Biz.business.sendManager.lnurlWithdraw_sendInvoice(
						lnurlWithdraw: model.lnurlWithdraw,
						invoice: invoice
					)
				
				if let withdrawError {
					popoverState.display(dismissable: true) {
						LnurlFlowErrorNotice(error: LnurlFlowError.withdraw(error: withdrawError))
					}
				} else {
					navigateTo(.PaymentRequestedView(invoice: invoice))
				}
				
			} catch {
				log.error("sendPayment_lnurlPay: error: \(error)")
				paymentInProgress = false
				isLnurlFetch = false
			}
		} // </Task>
	}
	
	func saveTipPercentInPrefs() {
		log.trace("saveTipPercentInPrefs()")
		
		if let nums = paymentNumbers(), nums.tipMsat > 0 {
			let tipPercent = Int(nums.tipPercent * 100.0)
			Prefs.current.addRecentTipPercent(tipPercent)
		}
	}
	
	// --------------------------------------------------
	// MARK: Parsing
	// --------------------------------------------------
	
	func parseUserInput(_ input: String) {
		log.trace("parseUserInput()")
		
		guard !isParsing else {
			log.warning("parseUserInput: ignoring: isParsing == true")
			return
		}
		
		isParsing = true
		parseIndex += 1
		let index = parseIndex
		
		Task { @MainActor in
			do {
				let progressHandler = {(progress: SendManager.ParseProgress) -> Void in
					if index == parseIndex {
						self.parseProgress = progress
					} else {
						log.warning("parseUserInput: progressHandler: ignoring: cancelled")
					}
				}
				
				let result: SendManager.ParseResult = try await Biz.business.sendManager.parse(
					request: input,
					progress: progressHandler
				)
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
					
					if let badRequest = result as? SendManager.ParseResult_BadRequest {
						showErrorMessage(badRequest)
					} else {
						flow = result
					}
					
				} else {
					log.warning("parseUserInput: result: ignoring: cancelled")
				}
				
			} catch {
				log.error("parseUserInput: error: \(error)")
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
				}
			}
		} // </Task>
	}
	
	func showErrorMessage(_ result: SendManager.ParseResult_BadRequest) {
		log.trace("showErrorMessage()")
		
		let either = ParseResultHelper.processBadRequest(result)
		switch either {
		case .Left(let msg):
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 30.0,
				alignment: .middle,
				showCloseButton: true
			)
			
		case .Right(let websiteLink):
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					didCopyLink: didCopyLink,
					didOpenLink: nil
				)
			}
		}
	}
}

