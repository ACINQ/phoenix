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

struct TipNumbers {
	let baseMsat: Int64
	let tipMsat: Int64
	let totalMsat: Int64
	let percent: Double
}

struct TipStrings {
	let bitcoin_base: FormattedAmount
	let bitcoin_tip: FormattedAmount
	let bitcoin_total: FormattedAmount
	let fiat_base: FormattedAmount
	let fiat_tip: FormattedAmount
	let fiat_total: FormattedAmount
	let percent: String
	let isEmpty: Bool
	
	static func empty(_ currencyPrefs: CurrencyPrefs) -> TipStrings {
		let zeroBitcoin = Utils.formatBitcoin(msat: 0, bitcoinUnit: currencyPrefs.bitcoinUnit)
		let exchangeRate =  ExchangeRate.BitcoinPriceRate(
			fiatCurrency: currencyPrefs.fiatCurrency,
			price: 0.0,
			source: "",
			timestampMillis: 0
		)
		let zeroFiat = Utils.formatFiat(msat: 0, exchangeRate: exchangeRate)
		return TipStrings(
			bitcoin_base: zeroBitcoin,
			bitcoin_tip: zeroBitcoin,
			bitcoin_total: zeroBitcoin,
			fiat_base: zeroFiat,
			fiat_tip: zeroFiat,
			fiat_total: zeroFiat,
			percent: "0%",
			isEmpty: true
		)
	}
}

struct ValidateView: View {
	
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
	
	// For the cicular buttons: [metadata, tip, comment]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// For the tipSummary: the max of: [base, tip, total]
	enum MaxBitcoinWidth: Preference {}
	let maxBitcoinWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxBitcoinWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxBitcoinWidth: CGFloat? = nil
	
	// For the tipSummary: the max of: [base, tip, total]
	enum MaxFiatWidth: Preference {}
	let maxFiatWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxFiatWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxFiatWidth: CGFloat? = nil
	
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
			
			if mvi.model is Scan.Model_LnurlPayFlow_LnurlPayFetch {
				LnurlFetchNotice(
					title: NSLocalizedString("Fetching Invoice", comment: "Progress title"),
					onCancel: { didCancelLnurlPayFetch() }
				)
			} else if mvi.model is Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch {
				LnurlFetchNotice(
					title: NSLocalizedString("Forwarding Invoice", comment: "Progress title"),
					onCancel: { didCancelLnurlWithdrawFetch() }
				)
			}
			
		}// </ZStack>
		.navigationBarTitle(
			mvi.model is Scan.Model_LnurlWithdrawFlow
				? NSLocalizedString("Confirm Withdraw", comment: "Navigation bar title")
				: NSLocalizedString("Confirm Payment", comment: "Navigation bar title"),
			displayMode: .inline
		)
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
			
			if hasExtendedMetadata() || supportsPriceRange() || supportsComment() {
				HStack(alignment: VerticalAlignment.center, spacing: 20) {
					if hasExtendedMetadata() {
						metadataButton()
					}
					if supportsPriceRange() {
						priceTargetButton()
					}
					if supportsComment() {
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
					if mvi.model is Scan.Model_LnurlWithdrawFlow {
						Image("ic_receive")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.foregroundColor(Color.white)
							.frame(width: 22, height: 22)
						Text("Redeem")
							.font(.title2)
							.foregroundColor(Color.white)
					} else {
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
			
			tipSummary
				.padding(.top)
				.padding(.top)
		} // </VStack>
	}
	
	@ViewBuilder
	var tipSummary: some View {
		
		let tipInfo = tipStrings()
		
		// 1,000 sat       0.57 usd
		//    30 sat  +3%  0.01 usd
		// ---------       --------
		// 1,030 sat       0.58 usd
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
		
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text(verbatim: tipInfo.bitcoin_base.string)
					.read(maxBitcoinWidthReader)
				Text(verbatim: "+ \(tipInfo.bitcoin_tip.string)")
					.read(maxBitcoinWidthReader)
				Divider()
					.frame(width: tipInfo.isEmpty ? 0 : maxBitcoinWidth ?? 0, height: 1)
				Text(verbatim: tipInfo.bitcoin_total.string)
					.read(maxBitcoinWidthReader)
			}
			
			VStack(alignment: HorizontalAlignment.center, spacing: 8) {
				Text(verbatim: "")
				Text(verbatim: tipInfo.percent)
				Divider()
					.frame(width: 0, height: 1)
				Text(verbatim: "")
			}
			
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text(verbatim: tipInfo.fiat_base.string)
					.read(maxFiatWidthReader)
				Text(verbatim: "+ \(tipInfo.fiat_tip.string)")
					.read(maxFiatWidthReader)
				Divider()
					.frame(width: tipInfo.isEmpty ? 0 : maxBitcoinWidth ?? 0, height: 1)
				Text(verbatim: tipInfo.fiat_total.string)
					.read(maxFiatWidthReader)
			}
		}
		.assignMaxPreference(for: maxBitcoinWidthReader.key, to: $maxBitcoinWidth)
		.assignMaxPreference(for: maxFiatWidthReader.key, to: $maxFiatWidth)
		.font(.footnote)
		.foregroundColor(tipInfo.isEmpty ? Color.clear : Color.secondary)
	}
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
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
	func priceTargetButton() -> some View {
		
		actionButton(
			text: priceTargetButtonText(),
			image: Image(systemName: "target"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			priceTargetButtonTapped()
		}
	}
	
	func priceTargetButtonText() -> String {
		
		if let _ = lnurlWithdraw() {
			return NSLocalizedString("range", comment: "button label - try to make it short")
		} else {
			return NSLocalizedString("tip", comment: "button label - try to make it short")
		}
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
	
	func paymentRequest() -> Lightning_kmpPaymentRequest? {
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			return model.paymentRequest
		} else {
			return nil
		}
	}
	
	func lnurlPay() -> LNUrl.Pay? {
		
		if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			return model.lnurlPay
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayFetch {
			return model.lnurlPay
		} else {
			return nil
		}
	}
	
	func lnurlWithdraw() -> LNUrl.Withdraw? {
		
		if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			return model.lnurlWithdraw
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch {
			return model.lnurlWithdraw
		} else {
			return nil
		}
	}
	
	func paymentHost() -> String? {
		
		if let lnurlPay = lnurlPay() {
			return lnurlPay.lnurl.host
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.lnurl.host
			
		} else {
			return nil
		}
	}
	
	func requestDescription() -> String? {
		
		if let paymentRequest = paymentRequest() {
			return paymentRequest.desc()
			
		} else if let lnurlPay = lnurlPay() {
			return lnurlPay.metadata.plainText
			
		} else if let lnurlWithdraw = lnurlWithdraw() {
			return lnurlWithdraw.defaultDescription
			
		} else {
			return nil
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
		}
		else if let lnurlPay = lnurlPay() {
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
	
	func supportsPriceRange() -> Bool {
		
		if let tuple = priceRange() {
			return tuple.max.msat > tuple.min.msat
		} else {
			return false
		}
	}
	
	func supportsComment() -> Bool {
		
		guard let lnurlPay = lnurlPay() else {
			return false
		}

		let maxCommentLength = lnurlPay.maxCommentLength?.int64Value ?? 0
		return maxCommentLength > 0
	}
	
	func tipNumbers() -> TipNumbers? {
		
		guard let totalAmt = try? parsedAmount.get(), totalAmt > 0 else {
			return nil
		}
		
		var totalMsat: Int64? = nil
		switch unit {
		case .bitcoin(let bitcoinUnit):
			totalMsat = Utils.toMsat(from: totalAmt, bitcoinUnit: bitcoinUnit)
		case .fiat(let fiatCurrency):
			if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				totalMsat = Utils.toMsat(fromFiat: totalAmt, exchangeRate: exchangeRate)
			}
		}
		
		var baseMsat: Int64? = nil
		if let paymentRequest = paymentRequest() {
			baseMsat = paymentRequest.amount?.msat
		} else if let lnurlPay = lnurlPay() {
			baseMsat = lnurlPay.minSendable.msat
		}
		
		guard let totalMsat = totalMsat, let baseMsat = baseMsat, totalMsat > baseMsat else {
			return nil
		}
		
		let tipMsat = totalMsat - baseMsat
		let percent = Double(tipMsat) / Double(baseMsat)
		
		return TipNumbers(baseMsat: baseMsat, tipMsat: tipMsat, totalMsat: totalMsat, percent: percent)
	}
	
	func tipStrings() -> TipStrings {
		
		guard let nums = tipNumbers() else {
			return TipStrings.empty(currencyPrefs)
		}
		
		let bitcoin_base = Utils.formatBitcoin(msat: nums.baseMsat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		let bitcoin_tip = Utils.formatBitcoin(msat: nums.tipMsat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		let bitcoin_total = Utils.formatBitcoin(msat: nums.totalMsat, bitcoinUnit: currencyPrefs.bitcoinUnit)
		
		let fiat_base: FormattedAmount
		let fiat_tip: FormattedAmount
		let fiat_total: FormattedAmount
		if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: currencyPrefs.fiatCurrency) {
			
			fiat_base = Utils.formatFiat(msat: nums.baseMsat, exchangeRate: exchangeRate)
			fiat_tip = Utils.formatFiat(msat: nums.tipMsat, exchangeRate: exchangeRate)
			fiat_total = Utils.formatFiat(msat: nums.totalMsat, exchangeRate: exchangeRate)
		} else {
			fiat_base = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			fiat_tip = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			fiat_total = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		
		let percentStr = formatter.string(from: NSNumber(value: nums.percent)) ?? "?%"
		
		return TipStrings(
			bitcoin_base  : bitcoin_base,
			bitcoin_tip   : bitcoin_tip,
			bitcoin_total : bitcoin_total,
			fiat_base     : fiat_base,
			fiat_tip      : fiat_tip,
			fiat_total    : fiat_total,
			percent       : percentStr,
			isEmpty       : false
		)
	}
	
	func balanceMsat() -> Int64? {
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayRequest {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlPayFlow_LnurlPayFetch {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			return model.balanceMsat
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch {
			return model.balanceMsat
		} else {
			return nil
		}
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
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		let bitcoinUnit = currencyPrefs.bitcoinUnit
		unit = Currency.bitcoin(bitcoinUnit)
		
		var amount_msat: Lightning_kmpMilliSatoshi? = nil
		if let paymentRequest = paymentRequest() {
			amount_msat = paymentRequest.amount
		} else if let lnurlPay = lnurlPay() {
			amount_msat = lnurlPay.minSendable
		} else if let lnurlWithdraw = lnurlWithdraw() {
			amount_msat = lnurlWithdraw.maxWithdrawable
		}
		
		if let amount_msat = amount_msat {
			
			let formattedAmt = Utils.formatBitcoin(msat: amount_msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
			
			parsedAmount = Result.success(formattedAmt.amount) // do this first !
			amount = formattedAmt.digits
		} else {
			altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			isInvalidAmount = false // display in gray at very beginning
		}
	}
	
	func modelDidChange(_ newModel: Scan.Model) -> Void {
		log.trace("modelDidChange()")
		
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
	
	func amountDidChange() -> Void {
		log.trace("amountDidChange()")
		
		refreshAltAmount()
	}
	
	func unitDidChange() -> Void {
		log.trace("unitDidChange()")
		
		// We might want to apply a different formatter
		let result = TextFieldCurrencyStyler.format(input: amount, currency: unit, hideMsats: false)
		parsedAmount = result.1
		amount = result.0
		
		refreshAltAmount()
	}
	
	func refreshAltAmount() -> Void {
		log.trace("refreshAltAmount()")
		
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
				if msat > balanceMsat && !(mvi.model is Scan.Model_LnurlWithdrawFlow) {
					isInvalidAmount = true
					altAmount = NSLocalizedString("Amount exceeds your balance", comment: "error message")
					
				} else if let alt = alt {
					altAmount = "≈ \(alt.string)"
				}
			}
			
			if let paymentRequest = paymentRequest(),
			   let expiryTimestampSeconds = paymentRequest.expiryTimestampSeconds()?.doubleValue,
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
			   let range = priceRange()
			{
				let minMsat = range.min.msat
				let maxMsat = range.max.msat
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
		log.trace("metadataButtonTapped()")
		
		guard let lnurlPay = lnurlPay() else {
			return
		}
		
		dismissKeyboardIfVisible()
		shortSheetState.display(dismissable: true) {
		
			MetadataSheet(lnurlPay: lnurlPay)
		}
	}
	
	func priceTargetButtonTapped() {
		log.trace("priceTargetButtonTapped()")
		
		guard let range = priceRange() else {
			return
		}
		
		let minMsat = range.min.msat
		let maxMsat = range.max.msat
		
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
			
			var flowType: FlowType? = nil
			if paymentRequest() != nil || lnurlPay() != nil {
				flowType = FlowType.pay(range: range)
				
			} else if lnurlWithdraw() != nil {
				flowType = FlowType.withdraw(range: range)
			}
			
			if let flowType = flowType {
				
				dismissKeyboardIfVisible()
				shortSheetState.display(dismissable: true) {
					
					PriceSliderSheet(
						flowType: flowType,
						msat: msat,
						valueChanged: priceSliderChanged
					)
				}
			}
			
		} else if msat != minMsat {
			msat = minMsat
			
			// There is only one valid amount.
			// We set the amount directly via the button tap.
			
			priceSliderChanged(minMsat)
		}
	}
	
	func priceSliderChanged(_ msat: Int64) {
		log.trace("priceSliderChanged()")
		
		let preferredBitcoinUnit = currencyPrefs.bitcoinUnit
		unit = Currency.bitcoin(preferredBitcoinUnit)
		
		// The TextFieldCurrencyStyler doesn't seem to fire when we manually set the text value.
		// So we need to do it manually here, to ensure the `parsedAmount` is properly updated.
		
		let amt = Utils.formatBitcoin(msat: msat, bitcoinUnit: preferredBitcoinUnit)
		let result = TextFieldCurrencyStyler.format(input: amt.digits, currency: unit, hideMsats: false)
		
		parsedAmount = result.1
		amount = result.0
	}
	
	func commentButtonTapped() {
		log.trace("commentButtonTapped()")
		
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
		log.trace("sendPayment()")
		
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
		
		let saveTipPercentInPrefs = {
			if let tip = tipNumbers() {
				let percent = Int(tip.percent * 100.0)
				Prefs.shared.addRecentTipPercent(percent)
			}
		}
		
		if let model = mvi.model as? Scan.Model_InvoiceFlow_InvoiceRequest {
			
			if let msat = msat {
				saveTipPercentInPrefs()
				mvi.intent(Scan.Intent_InvoiceFlow_SendInvoicePayment(
					paymentRequest: model.paymentRequest,
					amount: Lightning_kmpMilliSatoshi(msat: msat),
					maxFees: Prefs.shared.maxFees?.toKotlin()
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
				
				saveTipPercentInPrefs()
				mvi.intent(Scan.Intent_LnurlPayFlow_SendLnurlPayment(
					lnurlPay: model.lnurlPay,
					amount: Lightning_kmpMilliSatoshi(msat: msat),
					maxFees: Prefs.shared.maxFees?.toKotlin(),
					comment: comment
				))
			}
			
		} else if let model = mvi.model as? Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest {
			
			if let msat = msat {
				
				saveTipPercentInPrefs()
				mvi.intent(Scan.Intent_LnurlWithdrawFlow_SendLnurlWithdraw(
					lnurlWithdraw: model.lnurlWithdraw,
					amount: Lightning_kmpMilliSatoshi(msat: msat),
					description: nil
				))
			}
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
	
	func showAppStatusPopover() {
		log.trace("showAppStatusPopover()")
		
		popoverState.display(dismissable: true) {
			AppStatusPopover()
		}
	}
}
