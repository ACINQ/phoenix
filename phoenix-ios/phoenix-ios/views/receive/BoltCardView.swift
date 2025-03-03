import SwiftUI
import PhoenixShared
import CoreNFC

fileprivate let filename = "BoltCardView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif


struct BoltCardView: View {
	
	@ObservedObject var inboundFeeState: InboundFeeState
	@ObservedObject var toast: Toast
	
	let navigateTo: (ReceiveView.NavLinkTag) -> Void
	
	@State var currency = Currency.bitcoin(.sat)
	@State var currencyList: [Currency] = [Currency.bitcoin(.sat)]
	@State var currencyPickerChoice: String = Currency.bitcoin(.sat).shortName
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	@State var altAmount: String = ""
	
	@State var description: String = ""
	
	@State var isScanning: Bool = false
	@State var nfcUnavailable: Bool = false
	@State var readErrorMessage: String = ""
	
	@State var isParsing: Bool = false
	@State var parseIndex: Int = 0
	@State var parseProgress: SendManager.ParseProgress? = nil
	
	@State var isReceiving: Bool = false
	@State var bolt11Invoice: Lightning_kmpBolt11Invoice? = nil
	
	@State var didAppear = false
	
	enum Field: Hashable {
		case amountTextField
		case descriptionTextField
	}
	@FocusState var focusedField: Field?
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Receive", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			contentWrapper()
		}
	}
	
	@ViewBuilder
	func contentWrapper() -> some View {
		
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack {
			
			Text("Bolt Card")
				.font(.title3)
				.foregroundColor(Color(UIColor.tertiaryLabel))
				.padding(.top)
			
			boltCardView()
				.padding(.bottom, 20)
			
			amountField()
				.padding(.bottom, 6)
			
			Text(altAmount)
				.font(.callout)
				.foregroundColor(altAmountColor())
				.padding(.bottom, 30)
			
			descriptionTextField()
				.padding(.bottom, 30)
			
			readCardButton()
			
			if let warning = inboundFeeState.calculateInboundFeeWarning(invoiceAmount: msatAmount()) {
				inboundFeeInfo(warning)
					.padding(.top)
					.padding(.horizontal)
			}
			
			Spacer()
			
		} // </VStack>
		.onAppear {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: currencyPickerChoice) { _ in
			currencyPickerDidChange()
		}
		.onReceive(Biz.business.paymentsManager.lastIncomingPaymentPublisher()) {
			lastIncomingPaymentChanged($0)
		}
	}
	
	func boltCardView() -> some View {
		
		ZStack(alignment: Alignment.top) {
			
			Image(systemName: "creditcard")
				.resizable()
				.font(.body.weight(.ultraLight))
				.scaledToFit()
				.frame(width: 280, alignment: .top)
				.foregroundColor(.primary)
			
			Image("boltcard")
				.resizable()
				.scaledToFit()
				.aspectRatio(contentMode: .fit)
				.frame(width: 42, height: 42, alignment: .topLeading)
				.padding(.trailing, 157)
				.padding(.top, 122)
			
			if isParsing || isReceiving {
				HorizontalActivity(color: Color(UIColor.systemBackground), diameter: 10, speed: 1.6)
					.frame(width: 240, height: 10)
					.padding(.top, 45)
			}
			
		//	if isParsing, parseProgress is SendManager.ParseProgress_LnurlServiceFetch {
				
				Text("Fetching Lightning URL")
					.lineLimit(1)
					.truncationMode(.tail)
					.padding(.top, 75)
		//	}
		}
		.frame(width: 280)
	}
	
	@ViewBuilder
	func amountField() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline) {
			TextField(verbatim: "123", text: currencyStyler().amountProxy)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.fixedSize()
				.font(.title)
				.multilineTextAlignment(.trailing)
				.foregroundColor(isInvalidAmount() ? Color.appNegative : Color.primaryForeground)
				.accessibilityHint("amount in \(currency.shortName)")
				.focused($focusedField, equals: .amountTextField)
				.disabled(isScanning || isParsing || isReceiving)
		
			Picker(
				selection: $currencyPickerChoice,
				label: Text(currencyPickerChoice).bold().frame(minWidth: 40)
			) {
				ForEach(currencyPickerOptions(), id: \.self) { option in
					Text(option).tag(option)
				}
			}
			.pickerStyle(MenuPickerStyle())
			.disabled(isScanning || isParsing || isReceiving)
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
				Line()
					.stroke(Color.appAccent, style: StrokeStyle(lineWidth: 2, dash: [3]))
					.frame(height: 1)
			}
		)
	}
	
	@ViewBuilder
	func descriptionTextField() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Image(systemName: "multiply.circle.fill")
				.foregroundStyle(Color.clear)
			
			Spacer()
			
			TextField(
				String(localized: "Description (optional)", comment: "TextField placeholder; translate: optional"),
				text: $description,
				axis: .vertical
			)
			.multilineTextAlignment(.center)
			.lineLimit(4)
			.frame(maxWidth: 320)
			.focused($focusedField, equals: .descriptionTextField)
			.disabled(isScanning || isParsing || isReceiving)
			
			Spacer()
			
			// Clear button
			Button {
				description = ""
			} label: {
				Image(systemName: "multiply.circle.fill")
					.foregroundColor(Color(UIColor.tertiaryLabel))
			}
			.isHidden(focusedField != .descriptionTextField || description.isEmpty)
		}
		.padding(.horizontal)
	}
	
	@ViewBuilder
	func readCardButton() -> some View {

		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			
			Button {
				startNfcReader()
			} label: {
				Text("Read Card")
					.font(.title3)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.disabled(readButtonDisabled())
			
			Text(readErrorMessage)
				.multilineTextAlignment(.center)
				.foregroundStyle(Color.appNegative)
		}
	}
	
	@ViewBuilder
	func inboundFeeInfo(_ warning: InboundFeeWarning) -> some View {
		
		Button {
			showInboundFeeWarning(warning)
		} label: {
			switch warning.type {
			case .willFail:
				Label {
					Text("Payment will fail")
				} icon: {
					Image(systemName: "exclamationmark.triangle").foregroundColor(.appNegative)
				}
				
			case .feeExpected:
				Label {
					Text("On-chain fee expected")
				} icon: {
					Image(systemName: "info.circle").foregroundColor(.appAccent)
				}
				
			} // </switch>
		}
		.font(.headline)
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
	
	func msatAmount() -> Lightning_kmpMilliSatoshi? {
		
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
		
		if let msat {
			return Lightning_kmpMilliSatoshi(msat: msat)
		} else {
			return nil
		}
	}
	
	func currencyAmount() -> CurrencyAmount? {
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			return CurrencyAmount(currency: currency, amount: amt)
		} else {
			return nil
		}
	}
	
	func isInvalidAmount() -> Bool {
		
		switch parsedAmount {
		case .success(let amt):
			return amt <= 0
			
		case .failure(let reason):
			switch reason {
			case .emptyInput:
				return false
			case .invalidInput:
				return true
			}
		}
	}
	
	func altAmountColor() -> Color {
		
		switch parsedAmount {
		case .success(_):
			return Color.secondary
			
		case .failure(let reason):
			switch reason {
			case .emptyInput:
				return Color.secondary
			case .invalidInput:
				return Color.appNegative
			}
		}
	}
	
	func readButtonDisabled() -> Bool {
		
//		if isScanning || isReceiving || nfcUnavailable {
//			return true
//		}
//		if parsedAmount.isError {
//			return true
//		}
		
		return false
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			// First time displaying this View
			
			currency = Currency.bitcoin(currencyPrefs.bitcoinUnit)
			currencyList = Currency.displayable(currencyPrefs: currencyPrefs)
			currencyPickerChoice = currency.shortName
			
			altAmount = NSLocalizedString("Enter an amount", comment: "error message")
			
			if !NFCReaderSession.readingAvailable {
				nfcUnavailable = true
				readErrorMessage = String(localized: "NFC capabilities not available on this device.")
			}
			
		} else {
			
			// We are returning to this View
		}
	}
	
	func userDidEditTextField() {
		log.trace("userDidEditTextField()")
		
		// This is called if the user manually edits the TextField.
		// Which is distinct from `amountDidChange`, which may be triggered via code.
		
		// Nothing to do here currently
	}
	
	func amountDidChange() {
		log.trace("amountDidChange()")
		
		refreshAltAmount()
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
			navigateTo(
				.CurrencyConverter(
					initialAmount: currencyAmount(),
					didChange: currencyConverterAmountChanged,
					didClose: nil
				)
			)
		}
	}
	
	func currencyConverterAmountChanged(_ result: CurrencyAmount?) {
		log.trace("currencyConverterAmountChanged()")
		
		if let newAmt = result {

			let newCurrencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: [newAmt.currency])

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
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("lastIncomingPaymentChanged()")
		
		guard let invoice = bolt11Invoice else {
			return
		}
		
//		let state = lastIncomingPayment.state()
//		if state == WalletPaymentState.successOffChain {
//			if lastIncomingPayment.paymentHash.toHex() == invoice.paymentHash.toHex() {
//				presentationMode.wrappedValue.dismiss()
//			}
//		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------

	func refreshAltAmount() {
		log.trace("refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			
			switch error {
			case .emptyInput:
				altAmount = String(localized: "Enter an amount", comment: "error message")
			case .invalidInput:
				altAmount = String(localized: "Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			
			var msat: Int64? = nil
			switch currency {
			case .bitcoin(let bitcoinUnit):
				msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				
			case .fiat(let fiatCurrency):
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
				}
			}
			
			if let msat {
				
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
			
		} // </switch parsedAmount>
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func fakeItTillYouMakeIt() {
		log.trace("fakeItTillYouMakeIt()")
		
		isScanning = true
		DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
			self.isScanning = false
		}
	}
	
	func startNfcReader() {
		log.trace("startNfcReader()")
		
		isScanning = true
		NfcReader.shared.readCard { result in
			
			isScanning = false
			switch result {
			case .success(let result):
				log.debug("NFCNDEFMessage: \(result)")
				
				var scannedUri: URL? = nil
				
				result.records.forEach { payload in
					if let uri = payload.wellKnownTypeURIPayload() {
						log.debug("found uri = \(uri)")
						if scannedUri == nil {
							scannedUri = uri
						}
						
					} else if let text = payload.wellKnownTypeTextPayload().0 {
						log.debug("found text = \(text)")
						
					} else {
						log.debug("found tag with unknown type")
					}
				}
				
				if let scannedUri {
					readErrorMessage = ""
					handleScannedUri(scannedUri)
					
				} else {
					readErrorMessage = String(localized: "No URI detected in NFC tag")
				}
				
			case .failure(let failure):
				switch failure {
				case .readingNotAvailable:
					readErrorMessage = String(localized: "NFC cababilities not available on this device")
				case .alreadyStarted:
					readErrorMessage = String(localized: "NFC reader is already scanning")
				case .scanningTerminated(_):
					readErrorMessage = String(localized: "Nothing scanned")
				case .errorReadingTag:
					readErrorMessage = String(localized: "Error reading tag")
				}
			}
		}
	}
	
	func showInboundFeeWarning(_ warning: InboundFeeWarning) {
		
		smartModalState.display(dismissable: true) {
			InboundFeeSheet(warning: warning)
		}
	}
	
	func dismissKeyboardIfVisible() -> Void {
		log.trace("dismissKeyboardIfVisible()")
			
		focusedField = nil
	}
	
	// --------------------------------------------------
	// MARK: Payment Logic
	// --------------------------------------------------
	
	func handleScannedUri(_ scannedUri: URL) {
		log.trace("handleScannedUri(\(scannedUri.absoluteString))")
		
		isParsing = true
		parseIndex += 1
		let index = parseIndex
		
		Task { @MainActor in
			do {
				let progressHandler = {(progress: SendManager.ParseProgress) -> Void in
					if index == parseIndex {
						self.parseProgress = progress
					} else {
						log.warning("handleScannedUri: progressHandler: ignoring: cancelled")
					}
				}
				
				let result: SendManager.ParseResult = try await Biz.business.sendManager.parse(
					request: scannedUri.absoluteString,
					progress: progressHandler
				)
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
					handleParseResult(result)
				} else {
					log.warning("handleScannedUri: result: ignoring: cancelled")
				}
				
			} catch {
				log.error("handleScannedUri: error: \(error)")
				
				if index == parseIndex {
					isParsing = false
					parseProgress = nil
				}
			}
			
		} // </Task>
	}
	
	func handleParseResult(_ result: SendManager.ParseResult) {
		log.trace("handleParseResult()")
		
		guard let expectedResult = result as? SendManager.ParseResult_Lnurl_Withdraw else {
			showErrorMessage(result)
			return
		}
		
		guard let msat = msatAmount() else {
			return
		}
		
		isReceiving = true
		
		Task { @MainActor in
			do {
				
				bolt11Invoice = try await Biz.business.sendManager.lnurlWithdraw_createInvoice(
					lnurlWithdraw: expectedResult.lnurlWithdraw,
					amount: msat,
					description: description
				)
				
				let err: SendManager.LnurlWithdrawError? =
					try await Biz.business.sendManager.lnurlWithdraw_sendInvoice(
						lnurlWithdraw: expectedResult.lnurlWithdraw,
						invoice: bolt11Invoice!
					)
				
			//	if let remoteErr = err as? SendManager.LnurlWithdrawErrorRemoteError {
			//
			//		// Todo: map this to BadRequestReason_ServiceError, and call showErrorMessage()
			//	}
			
			} catch {
				log.error("handleParseResult: error: \(error)")
				
				isReceiving = false
			}
		} // </Task>
	}
	
	func showErrorMessage(_ result: SendManager.ParseResult) {
		log.trace("showErrorMessage()")
		
		var msg = String(localized: "Does not appear to be a bolt card.")
		var websiteLink: URL? = nil
		
		if let badRequest = result as? SendManager.ParseResult_BadRequest {
			
			if let serviceError = badRequest.reason as? SendManager.BadRequestReason_ServiceError {
				
				let remoteFailure: LnurlError.RemoteFailure = serviceError.error
				let origin = remoteFailure.origin
				
				if remoteFailure is LnurlError.RemoteFailure_IsWebsite {
					websiteLink = URL(string: serviceError.url.description())
					msg = String(
						localized: "Unreadable response from service: \(origin)",
						comment: "Error message - scanning lightning invoice"
					)
				}
			}
		}
		
		if let websiteLink {
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					didCopyLink: didCopyLink,
					didOpenLink: nil
				)
			}
			
		} else {
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 10.0,
				alignment: .middle,
				showCloseButton: true
			)
		}
	}
	
	func didCopyLink() {
		log.trace("didCopyLink()")
		
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
}
