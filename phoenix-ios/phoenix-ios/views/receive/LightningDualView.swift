import SwiftUI
import PhoenixShared

fileprivate let filename = "LightningDualView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LightningDualView: View {
	
	enum NavLinkTag: String, Codable {
		case CurrencyConverter
	}

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
	
	@State var bip353Address: String? = nil
	
	enum ActiveSheet {
		case sharingText(text: String)
		case sharingImage(image: UIImage)
	}
	@State var activeSheet: ActiveSheet? = nil
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var modificationAmount: CurrencyAmount? = nil
	
	let lastIncomingPaymentPublisher = Biz.business.paymentsManager.lastIncomingPaymentPublisher()
	
	// For the cicular buttons: [copy, share, edit]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
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
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
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
			case .sharingText(let text):
				
				let items: [Any] = [text]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			case .sharingImage(let image):
				
				let items: [Any] = [image]
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
			
			detailedInfo()
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
	func detailedInfo() -> some View {
		
		VStack(alignment: .center, spacing: 10) {
		
			if activeType == .bolt11_invoice {
				invoiceAmountView()
					.font(.footnote)
					.foregroundColor(.secondary)
			
				invoiceDescriptionView()
					.lineLimit(1)
					.font(.footnote)
					.foregroundColor(.secondary)
				
			} else {
				
				if let address = bip353Address {
					bip353AddressView(address)
						.lineLimit(2)
						.multilineTextAlignment(.center)
						.font(.footnote)
						.foregroundColor(.secondary)
					
				} else {
					
					offerAddressView()
						.lineLimit(1)
						.truncationMode(.middle)
						.font(.footnote)
						.foregroundColor(.secondary)
				}
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
	func invoiceDescriptionView() -> some View {
		
		if let m = mvi.model as? Receive.Model_Generated {
			if let desc = m.desc, desc.count > 0 {
				Text(desc)
			} else {
				Text("no description", comment: "placeholder: invoice is description-less")
			}
		} else {
			Text("...")
		}
	}
	
	@ViewBuilder
	func bip353AddressView(_ address: String) -> some View {
		
		let bAddress = "₿\(address)"
		
		Text("\(Image(systemName: "bitcoinsign.circle")) \(address)")
			.contextMenu {
				Button {
					copyTextToPasteboard(bAddress)
				} label: {
					Text("Copy")
				}
			}
	}
	
	@ViewBuilder
	func offerAddressView() -> some View {
		
		if let offerStr = qrCode.value?.original {
			Text(offerStr)
				.contextMenu {
					Button {
						copyTextToPasteboard(offerStr)
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
			text: NSLocalizedString("share", comment: "button label - try to make it short"),
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
				initialAmount: modificationAmount,
				didChange: currencyConverterDidChange,
				didClose: currencyConvertDidClose
			)
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
				qrCode.generate(value: QRCodeValue(
					original: offerString,
					rendered: offerString // is this supposed to be uppercase ?
				))
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
			qrCode.generate(value: QRCodeValue(
				original: m.request,
				rendered: m.request.uppercased()
			))
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
	
	func openCurrencyConverter() {
		log.trace("openCurrencyConverter()")
		
		navigateTo(.CurrencyConverter)
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
				openCurrencyConverter: openCurrencyConverter
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
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
					title: String(localized: "Lightning invoice", comment: "Type of text being copied"),
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
					title: String(localized: "Human-readable address", comment: "Type of text being copied"),
					subtitle: bAddress,
					callback: exportText(bAddress)
				))
			}
			if let offerText: String = qrCode.value?.original {
				sources.append(SourceInfo(
					type: .text,
					isDefault: false,
					title: String(localized: "Payment code", comment: "Type of text being copied"),
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
		
		// The edit button is only displayed for Bolt 11 invoices.
		
		if let model = mvi.model as? Receive.Model_Generated {
			
			smartModalState.display(dismissable: true) {
				
				ModifyInvoiceSheet(
					mvi: mvi,
					savedAmount: $modificationAmount,
					amount: model.amount,
					desc: model.desc ?? "",
					openCurrencyConverter: openCurrencyConverter
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
				qrCode.generate(value: QRCodeValue(
					original: offerStr,
					rendered: offerStr
				))
			} else {
				qrCode.clear()
			}
			
			if bip353Address == nil {
				bip353Address = AppSecurity.shared.getBip353Address()
			}
			
		case .bolt12_offer:
			// Switching to Bolt 11 invoice
			activeType = .bolt11_invoice
			
			if let m = mvi.model as? Receive.Model_Generated {
				// Issue #196: Use uppercase lettering for invoices and address QRs
				qrCode.generate(value: QRCodeValue(
					original: m.request,
					rendered: m.request.uppercased()
				))
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
}

