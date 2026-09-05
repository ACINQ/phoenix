import SwiftUI
import PhoenixShared
import CoreNFC

fileprivate let filename = "LightningDualView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LightningDualView: View {

	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var inboundFeeState: InboundFeeState
	@ObservedObject var toast: Toast
	
	@Binding var didAppear: Bool
	
	let navigateTo: (ReceiveView.NavLinkTag) -> Void

	@StateObject var qrCode = QRCode()
	
	@State var offerStr: String? = nil
	@State var isFullScreenQrcode = false
	
	enum LightningType {
		case bolt11_invoice
		case bolt12_offer
	}
	@State var activeType: LightningType = .bolt11_invoice
	
	@State var bip353Address: String? = nil
	
	enum ActiveSheet {
		case sharingText(text: String)
		case sharingImage(image: UIImage)
	}
	@State var activeSheet: ActiveSheet? = nil
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var modificationAmount: CurrencyAmount? = nil
	@State var description: String = Prefs.current.defaultPaymentDescription ?? ""
	
	@State var amountMsat: Lightning_kmpMilliSatoshi? = nil
	@State var needsUpdateInvoiceOrOffer: Bool = true

	@State var modificationTitleType: ModifyInvoiceSheet.TitleType = .normal
	
	@State var hceEligible: Bool = false
	@State var hceActive: Bool = false

	@State var cardPending: Bool = false
	@State var cardErrorMessage: String? = nil
	
	enum CardState {
		case scanning
		case parsing
		case requesting
		case receiving
	}
	@State var cardState: CardState? = nil
	
	// For the cicular buttons: [copy, share, edit]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Receive", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ZStack {
			mainWrapper()
		}
		.onAppear {
			onAppear()
		}
		.onChange(of: mvi.model) {
			modelChanged($0)
		}
		.onChange(of: activeType) {
			activeTypeChanged($0)
		}
		.task {
			for await payment in Biz.business.paymentsManager.lastIncomingPaymentSequence() {
				lastIncomingPaymentChanged(payment)
			}
		}
		.onReceive(NotificationsManager.shared.permissions) {
			notificationPermissionsChanged($0)
		}
		.sheet(isPresented: Binding( // SwiftUI only allows for 1 ".sheet"
			get: { activeSheet != nil },
			set: { if !$0 { activeSheet = nil }}
		)) {
			switch activeSheet! {
			case .sharingText(let text):
				
				let items: [Any] = [text]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			case .sharingImage(let image):
				
				let items: [Any] = [image]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			} // </switch>
		}
		.task {
			// Don't show HCE (host card emulation) button unless their device is eligible to use it
			if #available(iOS 17.4, *) {
				if await CardSession.isEligible {
					hceEligible = true
				}
			}
		}
	}
	
	@ViewBuilder
	func mainWrapper() -> some View {
		
		if isFullScreenQrcode {
			fullScreenQrcode()
		} else {
			
			// SwiftUI BUG workaround:
			//
			// When the keyboard appears, the main view shouldn't move. At all.
			// It should perform ZERO keyboard avoidance.
			// Which means we need to use: `.ignoresSafeArea(.keyboard)`
			//
			// But, of course, this doesn't work properly because of a SwiftUI bug.
			// So the current recommended workaround is to wrap everything in a GeometryReader.
			//
			GeometryReader { geometry in
				ScrollView(.vertical) {
					main()
						.frame(width: geometry.size.width)
						.frame(minHeight: geometry.size.height)
				}
			}
			.ignoresSafeArea(.keyboard)
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		VStack {
			
			Text(title())
				.font(.title3)
				.foregroundColor(Color(UIColor.tertiaryLabel))
				.padding(.top)
			
			qrCodeWrapperView()
			
			if let warning = inboundFeeState.calculateInboundFeeWarning(invoiceAmount: invoiceAmount()) {
				inboundFeeInfo(warning)
					.padding(.top)
					.padding(.horizontal)
			}
			
			typePicker()
				.padding(.horizontal, 20)
				.padding(.vertical)
			
			actionButtons()
			
			detailedInfo()
				.padding(.horizontal, 20)
				.padding(.vertical)

			if activeType == .bolt12_offer, let address = bip353Address {
				bip353AddressView(address)
					.lineLimit(2)
					.multilineTextAlignment(.center)
					.font(.callout)
					.padding(.top)
			}
			
			if activeType == .bolt12_offer {
				howToUseButton()
					.padding(.top)
			}

			nfcActivity()
			
			if notificationPermissions == .disabled {
				backgroundPaymentsDisabledWarning()
					.padding(.top)
			}
			
			Spacer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func fullScreenQrcode() -> some View {
		
		ZStack {
			
			qrCodeView()
				.padding()
				.background(Color.white)
				.cornerRadius(20)
				.overlay(
					RoundedRectangle(cornerRadius: 20)
						.strokeBorder(
							ReceiveView.qrCodeBorderColor(colorScheme),
							lineWidth: 1
						)
				)
				.matchedGeometryEffect(id: "qrCodeView_outer", in: qrCodeAnimation_outer)
			
			VStack {
				HStack {
					Spacer()
					Button {
						withAnimation {
							isFullScreenQrcode = false
						}
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.padding()
					.accessibilityLabel("Close full screen")
					.accessibilitySortPriority(1)
				}
				Spacer()
			}
		}
	}
	
	@ViewBuilder
	func qrCodeWrapperView() -> some View {
		
		qrCodeView()
			.frame(width: 200, height: 200)
			.padding(.all, 20)
			.background(Color.white)
			.cornerRadius(20)
			.overlay(
				RoundedRectangle(cornerRadius: 20)
					.strokeBorder(
						ReceiveView.qrCodeBorderColor(colorScheme),
						lineWidth: 1
					)
			)
			.matchedGeometryEffect(id: "qrCodeView_outer", in: qrCodeAnimation_outer)
	}
	
	@ViewBuilder
	func qrCodeView() -> some View {
		
		if let qrCodeCgImage = qrCode.cgImage,
			let qrCodeImage = qrCode.image
		{
			qrCodeImage
				.resizable()
				.aspectRatio(contentMode: .fit)
				.contextMenu {
					Button {
						copyImageToPasteboard(qrCodeCgImage)
					} label: {
						Text("Copy")
					}
					Button {
						shareImageToSystem(qrCodeCgImage)
					} label: {
						Text("Share")
					}
					Button {
						showFullScreenQRCode()
					} label: {
						Text("Full Screen")
					}
				} // </contextMenu>
				.matchedGeometryEffect(id: "qrCodeView_inner", in: qrCodeAnimation_inner)
				.accessibilityElement()
				.accessibilityAddTraits(.isImage)
				.accessibilityLabel("QR code")
				.accessibilityHint("Lightning QR code")
				.accessibilityAction(named: "Copy Image") {
					copyImageToPasteboard(qrCodeCgImage)
				}
				.accessibilityAction(named: "Share Image") {
					shareImageToSystem(qrCodeCgImage)
				}
				.accessibilityAction(named: "Full Screen") {
					showFullScreenQRCode()
				}
			
		} else {
			VStack {
				// Remember: This view is on a white background. Even in dark mode.
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
					.padding(.bottom, 10)
			
				Text("Generating QRCode...")
					.foregroundColor(Color(UIColor.darkGray))
					.font(.caption)
			}
			.accessibilityElement(children: .combine)
		}
	}
	
	@ViewBuilder
	func typePicker() -> some View {
		
		Picker(
			selection: typePickerBinding(),
			label: Text("Type")
		) {
			Text("Single use").tag(LightningType.bolt11_invoice)
			Text("Reusable").tag(LightningType.bolt12_offer)
		}
		.pickerStyle(SegmentedPickerStyle())
	}
	
	@ViewBuilder
	func detailedInfo() -> some View {
		
		VStack(alignment: .center, spacing: 10) {
		
			invoiceAmountView()
				.font(.callout)
				.foregroundColor(.secondary)
			
			invoiceDescriptionView()
				.lineLimit(1)
				.font(.callout)
				.foregroundColor(.secondary)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func invoiceAmountView() -> some View {
		
		if let msat = amountMsat {
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				let btcAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
				Text(btcAmt.string)
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
					Text(verbatim: "  /  \(fiatAmt.digits) ")
					Text_CurrencyName(currency: fiatAmt.currency, fontTextStyle: .caption2)
				}
			}
			
		} else {
			Text("any amount")
		}
	}
	
	@ViewBuilder
	func invoiceDescriptionView() -> some View {
		
		let trimmedDesc = description.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		let finalDesc = trimmedDesc.isEmpty ? nil : trimmedDesc
		
		if let finalDesc {
			Text(finalDesc)
		} else {
			Text("no description", comment: "placeholder: invoice is description-less")
		}
	}
	
	@ViewBuilder
	func bip353AddressView(_ address: String) -> some View {
		
		let bAddress = "₿\(address)"
		
		Group {
			Text(Image(systemName: "bitcoinsign.circle")) + Text(verbatim: " \(address)")
		}
		.contextMenu {
			Button {
				copyTextToPasteboard(bAddress)
			} label: {
				Text("Copy")
			}
		}
	}
	
	@ViewBuilder
	func actionButtons() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 30) {
			copyButton()
			shareButton()
			editButton()
			if hceEligible {
				hceButton()
			}
			cardButton()
		}
		.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
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
						.frame(width: 40, height: 40)
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
				} // </ZStack>
				
				Text(text.lowercased())
					.font(.caption)
					.foregroundColor(Color.secondary)
					.padding(.top, 2)
				
			} // </VStack>
		} // </Button>
		.frame(width: maxButtonWidth)
		.read(maxButtonWidthReader)
		.accessibilityElement()
		.accessibilityLabel(text)
		.accessibilityAddTraits(.isButton)
	}
	
	@ViewBuilder
	func copyButton() -> some View {
		
		actionButton(
			text: String(localized: "copy", comment: "button label - try to make it short"),
			image: Image(systemName: "square.on.square"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			showCopyOptionsSheet()
		}
		.disabled(qrCode.value == nil)
		.accessibilityAction(named: "Copy options") {
			showCopyOptionsSheet()
		}
	}
	
	@ViewBuilder
	func shareButton() -> some View {
		
		actionButton(
			text: String(localized: "share", comment: "button label - try to make it short"),
			image: Image(systemName: "square.and.arrow.up"),
			width: 21, height: 21,
			xOffset: 0, yOffset: -1
		) {
			showShareOptionsSheet()
		}
		.disabled(qrCode.value == nil)
		.accessibilityAction(named: "Share options") {
			showShareOptionsSheet()
		}
	}
	
	@ViewBuilder
	func editButton() -> some View {
		
		actionButton(
			text: String(localized: "edit", comment: "button label - try to make it short"),
			image: Image(systemName: "square.and.pencil"),
			width: 19, height: 19,
			xOffset: 1, yOffset: -1
		) {
			didTapEditButton()
		}
		.disabled(!(mvi.model is Receive.Model_Generated))
	}
	
	@ViewBuilder
	func hceButton() -> some View {
		
		actionButton(
			text: String(localized: "nfc", comment: "button label - try to make it short"),
			image: Image(systemName: "dot.radiowaves.forward"),
			width: 21, height: 21,
			xOffset: 0, yOffset: 0
		) {
			didTapHceButton()
		}
		.disabled(hceActive)
	}

	@ViewBuilder
	func cardButton() -> some View {
		
		actionButton(
			text: String(localized: "card", comment: "button label - try to make it short"),
			image: Image(systemName: "creditcard"),
			width: 21, height: 21,
			xOffset: 0, yOffset: 0
		) {
			didTapCardButton()
		}
		.disabled(cardState != nil)
	}
	
	@ViewBuilder
	func howToUseButton() -> some View {
		
		Button {
			showBolt12Sheet()
		} label: {
			Label("How to use", systemImage: "info.circle")
		}
	}
	
	@ViewBuilder
	func nfcActivity() -> some View {
		
		if hceActive || (cardState != nil) {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				HorizontalActivity(color: .appAccent, diameter: 10, speed: 1.6)
					.frame(width: 240, height: 10)
					.padding(.horizontal)
					.padding(.bottom, 4)
				
				if let cardState {
					Group {
						switch cardState {
						case .scanning:
							Text("Reading card…")
						case .parsing:
							Text("Communicating with card's host…")
						case .requesting:
							Text("Requesting payment…")
						case .receiving:
							Text("Awaiting payment…")
						}
					}
					.multilineTextAlignment(.center)
				}
				
			} // </VStack>
			.padding(.top)
		}
	}
	
	@ViewBuilder
	func backgroundPaymentsDisabledWarning() -> some View {
		
		// The user has disabled "background payments"
		Button {
			navigationToBackgroundPayments()
		} label: {
			Label {
				Text("Background payments disabled")
			} icon: {
				Image(systemName: "exclamationmark.triangle")
					.renderingMode(.template)
			}
			.foregroundColor(.appNegative)
		}
		.accessibilityLabel("Warning: background payments disabled")
		.accessibilityHint("Tap for more info")
	}
	
	@ViewBuilder
	func inboundFeeInfo(_ warning: InboundFeeWarning) -> some View {
		
		Button {
			showInboundFeeWarning(warning)
		} label: {
			Label {
				switch warning.type {
				case .willFail:
					Text("Payment will fail")
				case .feeExpected:
					Text("On-chain fee expected")
				}
			} icon: {
				switch warning.type {
				case .willFail:
					Image(systemName: "exclamationmark.triangle").foregroundColor(.appNegative)
				case .feeExpected:
					Image(systemName: "info.circle").foregroundColor(.appAccent)
				}
			}
			.font(.headline)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func title() -> String {
		
		switch activeType {
		case .bolt11_invoice:
			return String(localized: "Lightning Bolt11", comment: "Secondary title")
		case .bolt12_offer:
			return String(localized: "Lightning Bolt12", comment: "Secondary title")
		}
	}
	
	func textType() -> String {
		
		switch activeType {
		case .bolt11_invoice:
			return String(localized: "(Lightning Bolt11)", comment: "Type of text being copied")
		case .bolt12_offer:
			return String(localized: "(Lightning Bolt12)", comment: "Type of text being copied")
		}
	}
	
	func typePickerBinding() -> Binding<LightningType> {
		
		return Binding<LightningType>(
			get: { activeType },
			set: { activeType = $0 }
		)
	}
	
	func invoiceAmount() -> Lightning_kmpMilliSatoshi? {
		
		if let model = mvi.model as? Receive.Model_Generated {
			return model.amount
		} else {
			return nil
		}
	}
	
	func invoiceDescription() -> String {
		
		if let m = mvi.model as? Receive.Model_Generated {
			if let desc = m.desc, desc.count > 0 {
				return desc
			} else {
				return NSLocalizedString("no description",
					comment: "placeholder: invoice is description-less"
				)
			}
		} else {
			return "..."
		}
	}

	// --------------------------------------------------
	// MARK: View Transitions
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// Careful: this function may be called multiple times
		guard !didAppear else {
			return
		}
		didAppear = true
		
		updateInvoiceOrOffer()
	}
	
	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------
	
	func updateInvoiceOrOffer() {
		log.trace("updateInvoiceOrOffer()")
		
		let trimmedDesc = description.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		let finalDesc = trimmedDesc.isEmpty ? nil : trimmedDesc
		
		if activeType == .bolt11_invoice {
			mvi.intent(Receive.IntentAsk(
				amount: amountMsat,
				desc: finalDesc,
				expirySeconds: Prefs.current.invoiceExpirationSeconds
			))
			
		} else {
			guard let nodeParams = Biz.business.nodeParamsManager.nodeParamsValue() else {
				log.warning("nodeParams is nil")
				return
			}
			
			// Requirement in lightning-kmp:
			// > an offer description must be provided if the amount isn't null
			//
			var fixedDesc = finalDesc
			if amountMsat != nil {
				fixedDesc = finalDesc ?? ""
			}
			
			let offerAndKey = Lightning_kmpOfferManagerCompanion.shared.deterministicOffer(
				chainHash: NodeParamsManager.companion.chain.chainHash,
				nodePrivateKey: nodeParams.nodePrivateKey,
				trampolineNodeId: NodeParamsManager.companion.trampolineNodeId,
				amount: amountMsat,
				description: fixedDesc,
				pathId: nil
			)
			offerStr = offerAndKey.offer.encode()
		}
	}
	
	func updateQRCode() {
		log.trace("updateQRCode()")
		
		switch activeType {
		case .bolt11_invoice:
			log.debug("activeType == .bolt11_invoice")
			
			if let m = mvi.model as? Receive.Model_Generated {
				// Issue #196: Use uppercase lettering for invoices and address QRs
				qrCode.generate(value: QRCodeValue(
					original: m.request,
					rendered: m.request.uppercased()
				))
			} else {
				qrCode.clear()
			}
			
		case .bolt12_offer:
			log.debug("activeType == .bolt12_offer")
			
			if let offerStr {
				qrCode.generate(value: QRCodeValue(
					original: offerStr,
					rendered: offerStr
				))
			} else {
				qrCode.clear()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func modelChanged(_ model: Receive.Model) {
		log.trace("modelChanged()")
		
		if activeType == .bolt11_invoice, model is Receive.Model_Generated {
			updateQRCode()
			
			if cardPending {
				cardPending = false
				startCardReader()
			}
		}
	}
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("lastIncomingPaymentChanged()")
		
		let state = lastIncomingPayment.state()
		guard state == WalletPaymentState.successOffChain else {
			log.debug("lastIncomingPaymentChanged(): state != .successOffChain")
			return
		}
		
		guard let lightningPayment = lastIncomingPayment as? Lightning_kmpLightningIncomingPayment else {
			log.debug("lastIncomingPaymentChanged(): not a Lightning_kmpLightningIncomingPayment")
			return
		}
		
		var didCompletePayment = false
		
		if let b11Payment = lightningPayment as? Lightning_kmpBolt11IncomingPayment {
					
			// While waiting for the payment to arrive,
			// the user might tap the "show reusable qr" button.
			// This would switch them over to Bolt12 mode.
			// But if a matching Bolt11 payment arrives during that moment,
			// we should still kick them back to the Home screen,
			// and show them the payment was received.
			
			if let model = mvi.model as? Receive.Model_Generated {
				if b11Payment.paymentHash.toHex() == model.paymentHash {
					didCompletePayment = true
				}
			}
			
		} else if let _ = lightningPayment as? Lightning_kmpBolt12IncomingPayment {
			
			if activeType == .bolt12_offer {
				didCompletePayment = true
			}
		}
		
		if didCompletePayment {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func notificationPermissionsChanged(_ newValue: NotificationPermissions) {
		log.trace("notificationPermissionsChanged()")
		
		notificationPermissions = newValue
	}
	
	func openCurrencyConverter() {
		log.trace("openCurrencyConverter()")
		
		navigateTo(
			.CurrencyConverter(
				initialAmount: modificationAmount,
				didChange: currencyConverterDidChange,
				didClose: currencyConvertDidClose
			)
		)
	}
	
	func modifyInvoiceSheetDidSave(_ msat: Lightning_kmpMilliSatoshi?, _ desc: String?) {
		log.trace("modifyInvoiceSheetDidSave()")
		
		amountMsat = msat
		updateInvoiceOrOffer()
		updateQRCode()
		
		// Do we update both the invoice AND offer right now ?
		//
		// Updating the invoice isn't "free" because we have to store the result in the database.
		// Thus when `activeType == .bolt12_offer` we don't want to update the invoice here.
		// So here's what we do:
		// - we set a flag: `needsUpdateInvoiceOrOffer`
		// - which means we've only updated either the invoice or the offer
		// - and if the activeType changes, we'll need to update the counterpart
		//
		needsUpdateInvoiceOrOffer = true
		
		if activeType == .bolt12_offer {
			if cardPending {
				cardPending = false
				startCardReader()
			}
		}
	}
	
	func modifyInvoiceSheetDidCancel() {
		log.trace(#function)
		
		if cardPending {
			cardPending = false
		}
	}
	
	func currencyConverterDidChange(_ amount: CurrencyAmount?) {
		log.trace("currencyConverterDidChange()")
		
		modificationAmount = amount
	}
	
	func currencyConvertDidClose() {
		log.trace("currencyConverterDidClose()")
		
		smartModalState.display(dismissable: true) {
			
			ModifyInvoiceSheet(
				titleType: modificationTitleType,
				savedAmount: $modificationAmount,
				description: $description,
				openCurrencyConverter: openCurrencyConverter,
				didSave: modifyInvoiceSheetDidSave,
				didCancel: modifyInvoiceSheetDidCancel
			)
		}
	}
	
	func activeTypeChanged(_ newType: LightningType) {
		log.trace("activeTypeChanged()")
		
		if needsUpdateInvoiceOrOffer {
			updateInvoiceOrOffer()
		}
		updateQRCode()
		
		if case .bolt12_offer = newType {
			if bip353Address == nil {
				bip353Address = Keychain.current.getBip353Address()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func showFullScreenQRCode() {
		log.trace("showFullScreenQRCode()")
		
		// We add a delay here to give the contextMenu time to finish it's own animation.
		// Otherwise the effect of the double-animations looks funky.
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
			withAnimation {
				isFullScreenQrcode = true
			}
		}
	}
	
	func showCopyOptionsSheet() {
		log.trace("showCopyOptionsSheet()")
		
		showCopyShareOptionsSheet(.copy)
	}
	
	func showShareOptionsSheet() {
		log.trace("showShareOptionsSheet()")
		
		showCopyShareOptionsSheet(.share)
	}
	
	func showCopyShareOptionsSheet(_ type: CopyShareOptionsSheet.ActionType) {
		log.trace("showCopyShareOptionsSheet(_)")
		
		let exportText = { (text: String) -> () -> Void in
			switch type {
				case .copy  : return { copyTextToPasteboard(text) }
				case .share : return { shareTextToSystem(text) }
			}
		}
		let exportImage = { (img: CGImage) -> () -> Void in
			switch type {
				case .copy  : return { copyImageToPasteboard(img) }
				case .share : return { shareImageToSystem(img) }
			}
		}
		
		var sources: [SourceInfo] = []
		switch activeType {
		case .bolt11_invoice:
			if let invoiceText: String = qrCode.value?.original {
				sources.append(SourceInfo(
					type: .text,
					isDefault: true,
					title: String(localized: "Lightning Bolt11", comment: "Type of text being copied"),
					subtitle: invoiceText,
					callback: exportText(invoiceText)
				))
			}
			if let invoiceImage: CGImage = qrCode.cgImage {
				sources.append(SourceInfo(
					type: .image,
					isDefault: false,
					title: String(localized: "QR code", comment: "Type of image being copied"),
					subtitle: nil,
					callback: exportImage(invoiceImage)
				))
			}
			
		case .bolt12_offer:
			if let address: String = bip353Address {
				let bAddress = "₿\(address)" // this will probably confuse users, but it's in the spec
				sources.append(SourceInfo(
					type: .text,
					isDefault: true,
					title: String(localized: "Lightning address", comment: "Type of text being copied"),
					subtitle: bAddress,
					callback: exportText(bAddress)
				))
			}
			if let offerText: String = qrCode.value?.original {
				sources.append(SourceInfo(
					type: .text,
					isDefault: false,
					title: String(localized: "Lightning Bolt12", comment: "Type of text being copied"),
					subtitle: offerText,
					callback: exportText(offerText)
				))
			}
			if let offerText: String = qrCode.value?.original {
				let uri = "bitcoin:?lno=\(offerText)"
				sources.append(SourceInfo(
					type: .text,
					isDefault: false,
					title: String(localized: "Full URI", comment: "Type of text being copied"),
					subtitle: uri,
					callback: exportText(uri)
				))
			}
			if let offerImage: CGImage = qrCode.cgImage {
				sources.append(SourceInfo(
					type: .image,
					isDefault: false,
					title: String(localized: "QR code", comment: "Type of image being copied"),
					subtitle: nil,
					callback: exportImage(offerImage)
				))
			}
		} // </switch>
		
		if !sources.isEmpty {
			smartModalState.display(dismissable: true) {
				CopyShareOptionsSheet(type: type, sources: sources)
			}
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("didTapEditButton()")
		
		modificationTitleType = .normal
		smartModalState.display(dismissable: true) {
			
			ModifyInvoiceSheet(
				titleType: modificationTitleType,
				savedAmount: $modificationAmount,
				description: $description,
				openCurrencyConverter: openCurrencyConverter,
				didSave: modifyInvoiceSheetDidSave,
				didCancel: modifyInvoiceSheetDidCancel
			)
		}
	}
	
	func didTapHceButton() {
		log.trace(#function)
		
		// We're going to build a BIP-321 URI
		
		var queryItems: [URLQueryItem] = []
		
		if activeType == .bolt11_invoice {
			guard let m = mvi.model as? Receive.Model_Generated else {
				log.warning("mvi.model !is Model_Generated")
				return
			}
			
			if let msat = m.amount {
				
				// BIP-321:
				//
				// > If an amount is provided, it MUST be specified in decimal BTC.
				// > All amounts MUST contain no commas and use a period (.) as the
				// > separating character to separate whole numbers and decimal fractions.
				// > I.e. amount=50.00 or amount=50 is treated as 50 BTC,
				// > and amount=50,000.00 is invalid.
				
				let formatter = NumberFormatter()
				formatter.usesGroupingSeparator = false
				formatter.decimalSeparator = "."
				formatter.minimumFractionDigits = 0
				formatter.maximumFractionDigits = 11
				
				let btc = Utils.convertBitcoin(msat: msat, to: .btc)
				if let btcStr = formatter.string(from: NSNumber(value: btc)) {
					queryItems.append(URLQueryItem(name: "amount", value: btcStr))
				}
			}
			
			queryItems.append(URLQueryItem(name: "lightning", value: m.request))
			
		} else {
			guard let offer = offerStr else {
				log.warning("offerStr is nil")
				return
			}
			
			queryItems.append(URLQueryItem(name: "lno", value: offer))
		}
		
		var comps = URLComponents()
		comps.scheme = "bitcoin"
		comps.queryItems = queryItems
		
		if let url = comps.url {
			log.debug("url: \(url.absoluteString)")
			
			if #available(iOS 17.4, *) {
				startHostCardEmulation(url)
			} else {
				handleHceWriterError(.hceNotAvailable)
			}
		}
	}

	func didTapCardButton() {
		log.trace(#function)
		
		if amountMsat == nil {
			// We need the user to enter an amount first.
			
			cardPending = true
			modificationTitleType = .cardPaymentNeedsAmount
			smartModalState.display(dismissable: true) {
				
				ModifyInvoiceSheet(
					titleType: modificationTitleType,
					savedAmount: $modificationAmount,
					description: $description,
					openCurrencyConverter: openCurrencyConverter,
					didSave: modifyInvoiceSheetDidSave,
					didCancel: modifyInvoiceSheetDidCancel
				)
			}
			
		} else {
			startCardReader()
		}
	}
	
	func navigationToBackgroundPayments() {
		log.trace("navigateToBackgroundPayments()")
		
		deepLinkManager.broadcast(DeepLink.backgroundPayments)
	}
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		deepLinkManager.broadcast(DeepLink.liquiditySettings)
	}
	
	func showInboundFeeWarning(_ warning: InboundFeeWarning) {
		
		smartModalState.display(dismissable: true) {
			InboundFeeSheet(warning: warning)
		}
	}
	
	func showBolt12Sheet() {
		log.trace("showBolt12Sheet()")
		
		smartModalState.display(dismissable: true) {
			Bolt12Sheet()
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func copyTextToPasteboard(_ text: String) {
		log.trace("copyTextToPasteboard(_)")
		
		UIPasteboard.general.string = text
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			style: .chrome
		)
	}
	
	func copyImageToPasteboard(_ cgImage: CGImage) {
		log.trace("copyImageToPasteboard(_)")
		
		let uiImg = UIImage(cgImage: cgImage)
		UIPasteboard.general.image = uiImg
		toast.pop(
			NSLocalizedString("Copied image to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
	
	func shareTextToSystem(_ text: String) {
		log.trace("shareTextToSystem(_)")
		
		activeSheet = ActiveSheet.sharingText(text: text)
	}
	
	func shareImageToSystem(_ cgImage: CGImage) {
		log.trace("shareImageToSystem(_)")
		
		let uiImg = UIImage(cgImage: cgImage)
		activeSheet = ActiveSheet.sharingImage(image: uiImg)
	}
	
	// --------------------------------------------------
	// MARK: Host Card Emulation
	// --------------------------------------------------
	
	@available(iOS 17.4, *)
	func startHostCardEmulation(_ url: URL) {
		log.trace(#function)
		
		let file = Ndef.ndefDataForUrl(url)
		
		hceActive = true
		Task { @MainActor in
			if let error = await HceWriter.shared.start(ndefFile: file) {
				handleHceWriterError(error)
			}
			hceActive = false
		}
	}
	
	func handleHceWriterError(_ error: HceWriterError) {
		log.trace(#function)
		
		let msg: String
		switch error {
		case .nfcNotAvailable:
			msg = String(localized: "NFC capabilities not available on this device")
			
		case .hceNotAvailable:
			msg = String(localized:
				"""
				Host card emulation not available.
				Requires iOS 17.4 or later.
				"""
			)
			
		case .hceNotEligible:
			msg = String(localized:
				"""
				Host card emulation not available.
				Limited to European Economic Area.
				"""
			)
			
		case .sessionError(let sessionError, _):
			let details: String
			switch sessionError {
			case .acquirePresentmentIntent:
				details = String(localized: "(wait a few seconds and then retry)")
			case .initializeSession:
				details = "(cannot initialize CardSession)"
			case .startEmulation:
				details = "(cannot start emulation session)"
			case .eventStream:
				details = "(event stream termination)"
			}
			
			msg = String(localized: "Host card emulation session error\n\(details)")
		}
		
		toast.pop(msg,
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 5.0,
			alignment: .bottom(),
			showCloseButton: true
		)
	}

	// --------------------------------------------------
	// MARK: Card Payment
	// --------------------------------------------------
	
	func startCardReader() {
		log.trace(#function)
		
		cardState = .scanning
		NfcReader.shared.readCard { result in
			
			cardState = nil
			switch result {
			case .failure(let failure):
				switch failure {
				case .readingNotAvailable:
					cardErrorMessage = String(localized: "NFC cababilities not available on this device")
				case .alreadyStarted:
					cardErrorMessage = String(localized: "NFC reader is already scanning")
				case .scanningTerminated(_):
					cardErrorMessage = String(localized: "Nothing scanned")
				case .errorReadingTag:
					cardErrorMessage = String(localized: "Error reading tag")
				}
				
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
					cardErrorMessage = nil
					handleScannedUri(scannedUri)
					
				} else {
					cardErrorMessage = String(localized: "No URI detected in NFC tag")
				}
			}
		}
	}
	
	func handleScannedUri(_ scannedUri: URL) {
		log.trace("handleScannedUri(\(scannedUri.absoluteString))")
		
		cardState = .parsing
		Task { @MainActor in
			do {
				let progressHandler = {(progress: SendManager.ParseProgress) -> Void in
					// nothing to do here currently
				}
				
				let result: SendManager.ParseResult = try await Biz.business.sendManager.parse(
					request: scannedUri.absoluteString,
					progress: progressHandler
				)
				
				cardState = nil
				handleParseResult(result)
				
			} catch {
				log.error("handleScannedUri: error: \(error)")
				
				cardState = nil
				cardErrorMessage = String(localized: "Could not communicate with card's wallet")
			}
			
		} // </Task>
	}
	
	func handleParseResult(_ result: SendManager.ParseResult) {
		log.trace("handleParseResult()")
		
		guard let expectedResult = result as? SendManager.ParseResult_Lnurl_Withdraw else {
			handleParseError(result)
			return
		}
		
		guard let model = mvi.model as? Receive.Model_Generated else {
			return
		}
		
		cardState = .requesting
		Task { @MainActor in
			do {
				
				let err: SendManager.LnurlWithdrawError? =
					try await Biz.business.sendManager.lnurlWithdraw_sendInvoice(
						lnurlWithdraw: expectedResult.lnurlWithdraw,
						invoice: model.invoice
					)
				
				if let remoteErr = err as? SendManager.LnurlWithdrawErrorRemoteError {
					cardState = nil
					handleRequestError(remoteErr)
				} else {
					cardState = .receiving
				}
				
			} catch {
				log.error("handleParseResult: error: \(error)")
				
				cardState = nil
				cardErrorMessage = String(localized: "Cound not communicate with card's wallet")
			}
		} // </Task>
	}
	
	func handleParseError(_ result: SendManager.ParseResult) {
		log.trace(#function)
		
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
			cardErrorMessage = msg
		}
	}
	
	func handleRequestError(_ result: SendManager.LnurlWithdrawErrorRemoteError) {
		log.trace(#function)
		
		let remoteFailure = result.err
		switch remoteFailure {
		
		case is LnurlError.RemoteFailure_CouldNotConnect:
			cardErrorMessage = String(
				localized: "Could not connect to card's host",
				comment: "Error message - processing bolt card payment"
			)
			
		case is LnurlError.RemoteFailure_Unreadable:
			cardErrorMessage = String(
				localized: "Unreadable response from card's host",
				comment: "Error message - processing bolt card payment"
			)
			
		case let rfDetailed as LnurlError.RemoteFailure_Detailed:
			cardErrorMessage = String(
				localized: "The card's host returned error message: \(rfDetailed.reason)",
				comment: "Error message - processing bolt card payment"
			)
			
		case let rfCode as LnurlError.RemoteFailure_Code:
			cardErrorMessage = String(
				localized: "The card's host returned error code: \(rfCode.code.value)",
				comment: "Error message - processing bolt card payment"
			)
			
		default:
			cardErrorMessage = String(
				localized: "Could not communicate with card's wallet",
				comment: "Error message - scanning lightning invoice"
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
