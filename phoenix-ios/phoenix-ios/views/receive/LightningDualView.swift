import SwiftUI
import PhoenixShared

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

	@StateObject var qrCode = QRCode()
	
	@State var offerStr: String? = nil
	@State var isFullScreenQrcode = false
	
	enum LightningType {
		case bolt11_invoice
		case bolt12_offer
	}
	@State var activeType: LightningType = .bolt11_invoice
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	@State var activeSheet: ReceiveViewSheet? = nil
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var modificationAmount: CurrencyAmount? = nil
	@State var currencyConverterOpen = false
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	let lastIncomingPaymentPublisher = Biz.business.paymentsManager.lastIncomingPaymentPublisher()
	
	// For the cicular buttons: [copy, share, edit]
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
		
		content()
			.navigationTitle(NSLocalizedString("Receive", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
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
			
			mainWrapper()
		}
		.onAppear {
			onAppear()
		}
		.navigationStackDestination(isPresented: $currencyConverterOpen) { // For iOS 16+
			currencyConverterView()
		}
		.onChange(of: mvi.model) {
			modelChanged($0)
		}
		.onReceive(lastIncomingPaymentPublisher) {
			lastIncomingPaymentChanged($0)
		}
		.onReceive(NotificationsManager.shared.permissions) {
			notificationPermissionsChanged($0)
		}
		.sheet(isPresented: Binding( // SwiftUI only allows for 1 ".sheet"
			get: { activeSheet != nil },
			set: { if !$0 { activeSheet = nil }}
		)) {
			switch activeSheet! {
			case .sharingUrl(let sharingUrl):
				
				let items: [Any] = [sharingUrl]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			case .sharingImg(let sharingImg):
				
				let items: [Any] = [sharingImg]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			} // </switch>
		}
		.task {
			await generateQrCode()
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
			
			qrCodeInfo()
				.padding(.horizontal, 20)
				.padding(.vertical)
			
			actionButtons()
				.padding(.bottom)
			
			switchTypeButton()
			
			if activeType == .bolt12_offer {
				howToUseButton()
					.padding(.top)
			}
			
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
		
		if let qrCodeImage = qrCode.image {
			qrCodeImage
				.resizable()
				.aspectRatio(contentMode: .fit)
				.contextMenu {
					Button {
						copyImageToPasteboard()
					} label: {
						Text("Copy")
					}
					Button {
						shareImageToSystem()
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
					copyImageToPasteboard()
				}
				.accessibilityAction(named: "Share Image") {
					shareImageToSystem()
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
	func qrCodeInfo() -> some View {
		
		VStack(alignment: .center, spacing: 10) {
		
			if activeType == .bolt11_invoice {
				invoiceAmountView()
					.font(.footnote)
					.foregroundColor(.secondary)
			
				Text(invoiceDescription())
					.lineLimit(1)
					.font(.footnote)
					.foregroundColor(.secondary)
				
			} else {
				addressView()
					.font(.footnote)
					.foregroundColor(.secondary)
			}
		}
	}
	
	@ViewBuilder
	func invoiceAmountView() -> some View {
		
		if let m = mvi.model as? Receive.Model_Generated {
			if let msat = m.amount?.msat {
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
		} else {
			Text("...")
		}
	}
	
	@ViewBuilder
	func addressView() -> some View {
		
		if let offerStr = qrCode.value {
			Text(offerStr)
				.lineLimit(2)
				.multilineTextAlignment(.center)
				.truncationMode(.middle)
				.contextMenu {
					Button {
						didTapCopyButton()
					} label: {
						Text("Copy")
					}
				}
		} else {
			Text(verbatim: "…")
		}
	}
	
	@ViewBuilder
	func actionButtons() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 30) {
			copyButton()
			shareButton()
			if activeType == .bolt11_invoice {
				editButton()
			}
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
			text: NSLocalizedString("copy", comment: "button label - try to make it short"),
			image: Image(systemName: "square.on.square"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0
		) {
			// using simultaneousGesture's below
		}
		.disabled(!(mvi.model is Receive.Model_Generated))
		.simultaneousGesture(LongPressGesture().onEnded { _ in
			didLongPressCopyButton()
		})
		.simultaneousGesture(TapGesture().onEnded {
			didTapCopyButton()
		})
		.accessibilityAction(named: "Copy Text (lightning invoice)") {
			copyTextToPasteboard()
		}
		.accessibilityAction(named: "Copy Image (QR code)") {
			copyImageToPasteboard()
		}
	}
	
	@ViewBuilder
	func shareButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("share", comment: "button label - try to make it short"),
			image: Image(systemName: "square.and.arrow.up"),
			width: 21, height: 21,
			xOffset: 0, yOffset: -1
		) {
			// using simultaneousGesture's below
		}
		.disabled(!(mvi.model is Receive.Model_Generated))
		.simultaneousGesture(LongPressGesture().onEnded { _ in
			didLongPressShareButton()
		})
		.simultaneousGesture(TapGesture().onEnded {
			didTapShareButton()
		})
		.accessibilityAction(named: "Share Text (lightning invoice)") {
			shareTextToSystem()
		}
		.accessibilityAction(named: "Share Image (QR code)") {
			shareImageToSystem()
		}
	}
	
	@ViewBuilder
	func editButton() -> some View {
		
		actionButton(
			text: NSLocalizedString("edit", comment: "button label - try to make it short"),
			image: Image(systemName: "square.and.pencil"),
			width: 19, height: 19,
			xOffset: 1, yOffset: -1
		) {
			didTapEditButton()
		}
		.disabled(!(mvi.model is Receive.Model_Generated))
	}
	
	@ViewBuilder
	func switchTypeButton() -> some View {
		
		switch activeType {
		case .bolt11_invoice:
			ZStack(alignment: .topLeading) {
				Button {
					toggleActiveType()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
						Image(systemName: "qrcode")
						Text("Show reusable QR")
					}
				}
				.buttonStyle(.bordered)
				.buttonBorderShape(.capsule)
				
				Text("NEW")
					.font(.footnote)
					.padding(.vertical, 2.5)
					.padding(.horizontal, 7.5)
					.foregroundColor(.white)
					.background(Capsule().fill(Color.appAccent))
					.offset(x: -15, y: -20)
					.rotationEffect(.degrees(-45))
			}
			
		case .bolt12_offer:
			Button {
				toggleActiveType()
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 2) {
					Image(systemName: "qrcode")
					Text("Show one-time invoice")
				}
			}
			.buttonStyle(.bordered)
			.buttonBorderShape(.capsule)
		}
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
	
	@ViewBuilder
	func currencyConverterView() -> some View {
		
		CurrencyConverterView(
			initialAmount: modificationAmount,
			didChange: currencyConverterDidChange,
			didClose: currencyConvertDidClose
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func title() -> String {
		
		switch activeType {
		case .bolt11_invoice:
			return String(localized: "Lightning Invoice", comment: "Secondary title")
		case .bolt12_offer:
			return String(localized: "Lightning Offer", comment: "Secondary title")
		}
	}
	
	func textType() -> String {
		
		switch activeType {
		case .bolt11_invoice:
			return String(localized: "(Lightning invoice)", comment: "Type of text being copied")
		case .bolt12_offer:
			return String(localized: "(Lightning offer)", comment: "Type of text being copied")
		}
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
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		// Careful: this function may be called multiple times
		guard !didAppear else {
			return
		}
		didAppear = true
			
		let defaultDesc = Prefs.shared.defaultPaymentDescription
		mvi.intent(Receive.IntentAsk(
			amount: nil,
			desc: defaultDesc,
			expirySeconds: Prefs.shared.invoiceExpirationSeconds
		))
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func generateQrCode() async {
		
		do {
			let offerData = try await Biz.business.nodeParamsManager.defaultOffer()
			let offerString = offerData.defaultOffer.encode()
			
			offerStr = offerString
			if activeType == .bolt12_offer {
				qrCode.generate(value: offerString)
			}
			
		} catch {
			log.error("nodeParamsManager.defaultOffer(): error: \(error)")
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func modelChanged(_ model: Receive.Model) {
		log.trace("modelChanged()")
		
		if activeType == .bolt11_invoice, let m = model as? Receive.Model_Generated {
			log.debug("updating qr code...")
			
			// Issue #196: Use uppercase lettering for invoices and address QRs
			qrCode.generate(value: m.request.uppercased())
		}
	}
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("lastIncomingPaymentChanged()")
		
		guard let model = mvi.model as? Receive.Model_Generated else {
			return
		}
		
		let state = lastIncomingPayment.state()
		if state == WalletPaymentState.successOnChain || state == WalletPaymentState.successOffChain {
			if lastIncomingPayment.paymentHash.toHex() == model.paymentHash {
				presentationMode.wrappedValue.dismiss()
			}
		}
	}
	
	func notificationPermissionsChanged(_ newValue: NotificationPermissions) {
		log.trace("notificationPermissionsChanged()")
		
		notificationPermissions = newValue
	}
	
	func currencyConverterDidChange(_ amount: CurrencyAmount?) {
		log.trace("currencyConverterDidChange()")
		
		modificationAmount = amount
	}
	
	func currencyConvertDidClose() {
		log.trace("currencyConverterDidClose()")
		
		var amount: Lightning_kmpMilliSatoshi? = nil
		var desc: String? = nil
		if let model = mvi.model as? Receive.Model_Generated {
			amount = model.amount
			desc = model.desc
		}
		
		smartModalState.display(dismissable: true) {
			
			ModifyInvoiceSheet(
				mvi: mvi,
				savedAmount: $modificationAmount,
				amount: amount,
				desc: desc ?? "",
				currencyConverterOpen: $currencyConverterOpen
			)
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
	
	func didTapCopyButton() {
		log.trace("didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() {
		log.trace("didLongPressCopyButton()")
		
		smartModalState.display(dismissable: true) {
			
			CopyOptionsSheet(
				textType: textType(),
				copyText: { copyTextToPasteboard() },
				copyImage: { copyImageToPasteboard() }
			)
		}
	}
	
	func didTapShareButton() -> Void {
		log.trace("didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() -> Void {
		log.trace("didLongPressShareButton()")
		
		smartModalState.display(dismissable: true) {
			
			ShareOptionsSheet(
				textType: textType(),
				shareText: { shareTextToSystem() },
				shareImage: { shareImageToSystem() }
			)
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("didTapEditButton()")
		
		// The edit button is only displayed for Bolt 11 invoices.
		
		if let model = mvi.model as? Receive.Model_Generated {
			
			smartModalState.display(dismissable: true) {
				
				ModifyInvoiceSheet(
					mvi: mvi,
					savedAmount: $modificationAmount,
					amount: model.amount,
					desc: model.desc ?? "",
					currencyConverterOpen: $currencyConverterOpen
				)
			}
		}
	}
	
	func toggleActiveType() {
		log.trace("toggleActiveType()")
		
		switch activeType {
		case .bolt11_invoice:
			// Switching to Bolt 12 offer
			activeType = .bolt12_offer
			
			if let offerStr {
				qrCode.generate(value: offerStr)
			} else {
				qrCode.clear()
			}
			
		case .bolt12_offer:
			// Switching to Bolt 11 invoice
			activeType = .bolt11_invoice
			
			if let m = mvi.model as? Receive.Model_Generated {
				// Issue #196: Use uppercase lettering for invoices and address QRs
				qrCode.generate(value: m.request.uppercased())
			} else {
				qrCode.clear()
			}
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
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let qrCodeValue = qrCode.value {
			UIPasteboard.general.string = qrCodeValue
			toast.pop(
				NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
				colorScheme: colorScheme.opposite,
				style: .chrome
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("copyImageToPasteboard()")
		
		if let qrCodeCgImage = qrCode.cgImage {
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			UIPasteboard.general.image = uiImg
			toast.pop(
				NSLocalizedString("Copied QR code image to pasteboard!", comment: "Toast message"),
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func shareTextToSystem() -> Void {
		log.trace("shareTextToSystem()")
		
		if let qrCodeValue = qrCode.value {
			withAnimation {
				let url = "lightning:\(qrCodeValue)"
				activeSheet = ReceiveViewSheet.sharingUrl(url: url)
			}
		}
	}
	
	func shareImageToSystem() -> Void {
		log.trace("shareImageToSystem()")
		
		if let qrCodeCgImage = qrCode.cgImage {
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			activeSheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
}

