import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveLightningView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ReceiveLightningView: View {
	
	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var toast: Toast
	
	@Binding var didAppear: Bool
	
	@StateObject var qrCode = QRCode()

	@State var isFullScreenQrcode = false
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	@State var sheet: ReceiveViewSheet? = nil
	
	@State var swapIn_enabled = true
	@State var payToOpen_enabled = true
	@State var payToOpen_minFundingSat: Int64 = 0
	
	@State var channelsCount = 0
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var modificationAmount: CurrencyAmount? = nil
	@State var currencyConverterOpen = false
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let lastIncomingPaymentPublisher = Biz.business.paymentsManager.lastIncomingPaymentPublisher()
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	
	// Saving custom publisher in @State since otherwise it fires on every render
	@State var channelsPublisher = Biz.business.peerManager.peerStatePublisher().flatMap { $0.channelsPublisher() }
	
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
			
			content()
		}
		.onAppear {
			onAppear()
		}
		.navigationStackDestination( // For iOS 16+
			isPresented: $currencyConverterOpen,
			destination: currencyConverterView
		)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onReceive(lastIncomingPaymentPublisher) {
			lastIncomingPaymentChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
		.onReceive(channelsPublisher) {
			channelsChanged($0)
		}
		.onReceive(NotificationsManager.shared.permissions) {
			notificationPermissionsChanged($0)
		}
		.sheet(isPresented: Binding( // SwiftUI only allows for 1 ".sheet"
			get: { sheet != nil },
			set: { if !$0 { sheet = nil }}
		)) {
			switch sheet! {
			case .sharingUrl(let sharingUrl):
				
				let items: [Any] = [sharingUrl]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			case .sharingImg(let sharingImg):
				
				let items: [Any] = [sharingImg]
				ActivityView(activityItems: items, applicationActivities: nil)
			
			} // </switch>
		}
		.navigationTitle(NSLocalizedString("Receive", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		if isFullScreenQrcode {
			fullScreenQrcode()
		} else {
			mainWrapper()
		}
	}
	
	@ViewBuilder
	func mainWrapper() -> some View {
		
		// SwiftUI BUG workaround:
		//
		// When the keyboard appears, the main view shouldn't move. At all.
		// It should perform ZERO keyboard avoidance.
		// Which means we need to use: `.ignoresSafeArea(.keyboard)`
		//
		// But, of course, this doesn't work properly because of a SwiftUI bug.
		// So the current recommended workaround is to wrap everything in a GeometryReader.
		//
		GeometryReader { _ in
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer(minLength: 0)
				if verticalSizeClass == UserInterfaceSizeClass.compact {
					mainLandscape()
				} else {
					mainPortrait()
				}
				Spacer(minLength: 0)
			} // </HStack>
		} // </GeometryReader>
		.ignoresSafeArea(.keyboard)
	}
	
	@ViewBuilder
	func mainPortrait() -> some View {
		
		VStack {
			qrCodeView()
				.frame(width: 200, height: 200)
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
				.padding(.top)
			
			if !payToOpen_enabled {
				payToOpenDisabledWarning()
					.padding(.top, 8)
			} else if channelsCount == 0 {
				payToOpenMinimumWarning()
					.padding(.top, 8)
			}
			
			VStack(alignment: .center) {
			
				invoiceAmount()
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 2)
			
				Text(invoiceDescription())
					.lineLimit(1)
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 2)
			}
			.padding([.leading, .trailing], 20)
			.padding([.top, .bottom])
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				copyButton()
				shareButton()
				editButton()
			}
			.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
			
			warningButton()
			
			Button {
				didTapSwapInButton()
			} label: {
				HStack {
					Image(systemName: "repeat") // alt: "arrowshape.bounce.forward.fill"
						.imageScale(.small)

					Text("Show a Bitcoin address")
				}
			}
			.padding(.vertical)
			
			Spacer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func mainLandscape() -> some View {
		
		HStack {
			
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
				.padding([.top, .bottom])
			
			VStack(alignment: HorizontalAlignment.center, spacing: 20) {
				copyButton()
				shareButton()
				editButton()
			}
			.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
			.padding()
			
			VStack(alignment: HorizontalAlignment.center) {
			
				Spacer()
				
				invoiceAmount()
					.font(.caption2)
					.foregroundColor(.secondary)
					.padding(.bottom, 6)
			
				Text(invoiceDescription())
					.lineLimit(1)
					.font(.caption2)
					.foregroundColor(.secondary)
				
				warningButton(paddingTop: 8)
				
				Spacer()
			}
			.frame(width: 240) // match width of QRcode box
			.padding([.top, .bottom])
			
		} // </HStack>
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
	func qrCodeView() -> some View {
		
		if let m = mvi.model as? Receive.Model_Generated,
		   let qrCodeValue = qrCode.value,
		   qrCodeValue.caseInsensitiveCompare(m.request) == .orderedSame,
		   let qrCodeImage = qrCode.image
		{
			qrCodeImage
				.resizable()
				.aspectRatio(contentMode: .fit)
				.contextMenu {
					Button(action: {
						copyImageToPasteboard()
					}) {
						Text("Copy")
					}
					Button(action: {
						shareImageToSystem()
					}) {
						Text("Share")
					}
					Button(action: {
						// We add a delay here to give the contextMenu time to finish it's own animation.
						// Otherwise the effect of the double-animations looks funky.
						DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
							withAnimation {
								isFullScreenQrcode = true
							}
						}
					}) {
						Text("Full Screen")
					}
				} // </contextMenu>
				.matchedGeometryEffect(id: "qrCodeView_inner", in: qrCodeAnimation_inner)
				.accessibilityElement()
				.accessibilityAddTraits(.isImage)
				.accessibilityLabel("QR code")
				.accessibilityHint("Lightning invoice")
				.accessibilityAction(named: "Copy Image") {
					copyImageToPasteboard()
				}
				.accessibilityAction(named: "Share Image") {
					shareImageToSystem()
				}
				.accessibilityAction(named: "Full Screen") {
					withAnimation {
						isFullScreenQrcode = true
					}
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
	func invoiceAmount() -> some View {
		
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
	func warningButton(paddingTop: CGFloat? = nil) -> some View {
		
		if notificationPermissions == .disabled {
			
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
			.padding(.top, paddingTop)
			.accessibilityLabel("Warning: background payments disabled")
			.accessibilityHint("Tap for more info")
		}
	}
	
	@ViewBuilder
	func payToOpenDisabledWarning() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.foregroundColor(Color(UIColor.tertiaryLabel))
			Text(
				"""
				Channel creation has been temporarily disabled. \
				You may not be able to receive some payments.
				"""
			)
			.multilineTextAlignment(.center)
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.leading, 10)
				.foregroundColor(Color(UIColor.tertiaryLabel))
		}
		.font(.caption)
		.padding(12)
		.background(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.appAccent, lineWidth: 1)
		)
		.padding([.leading, .trailing], 10)
	}
	
	@ViewBuilder
	func payToOpenMinimumWarning() -> some View {
		
		let minFunding = Utils.formatBitcoin(sat: payToOpen_minFundingSat, bitcoinUnit: .sat)
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Text(styled: String(format: NSLocalizedString(
				"""
				Your wallet is empty. The first payment you \
				receive must be at least **%@**.
				""",
				comment:	"Minimum amount description."),
				minFunding.string
			))
			.multilineTextAlignment(.center)
		}
		.font(.caption)
		.padding(12)
		.background(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.appAccent, lineWidth: 1)
		)
		.padding([.leading, .trailing], 10)
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
		if didAppear {
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
	// MARK: Notifications
	// --------------------------------------------------
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
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
		
		if lastIncomingPayment.state() == WalletPaymentState.success &&
			lastIncomingPayment.paymentHash.toHex() == model.paymentHash
		{
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		swapIn_enabled = context.swapIn.v1.status is WalletContext.V0ServiceStatusActive
		payToOpen_enabled = context.payToOpen.v1.status is WalletContext.V0ServiceStatusActive
		payToOpen_minFundingSat = context.payToOpen.v1.minFundingSat
	}
	
	func channelsChanged(_ channels: Lightning_kmpPeer.ChannelsMap) {
		log.trace("channelsChanged()")
		
		var availableCount = 0
		for (_, channel) in channels {
			
			if channel.asClosing() != nil ||
				channel.asClosed() != nil ||
				channel.asAborted() != nil
			{
				// ignore - channel isn't usable for incoming payments
			} else {
				availableCount += 1
			}
		}
		
		channelsCount = availableCount
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
	
	func didTapCopyButton() -> Void {
		log.trace("didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() -> Void {
		log.trace("didLongPressCopyButton()")
		
		smartModalState.display(dismissable: true) {
			
			CopyOptionsSheet(
				textType: NSLocalizedString("(Lightning invoice)", comment: "Type of text being copied"),
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
				textType: NSLocalizedString("(Lightning invoice)", comment: "Type of text being copied"),
				shareText: { shareTextToSystem() },
				shareImage: { shareImageToSystem() }
			)
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("didTapEditButton()")
		
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
	
	func didTapSwapInButton() -> Void {
		log.trace("didTapSwapInButton()")
		
		if swapIn_enabled {
			
			mvi.intent(Receive.IntentRequestSwapIn())
			
		} else {
			
			popoverState.display(dismissable: true) {
				SwapInDisabledPopover()
			}
		}
	}
	
	func navigationToBackgroundPayments() {
		log.trace("navigateToBackgroundPayments()")
		
		deepLinkManager.broadcast(DeepLink.backgroundPayments)
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_Generated {
			UIPasteboard.general.string = m.request
			toast.pop(
				NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
				colorScheme: colorScheme.opposite,
				style: .chrome
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("copyImageToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_Generated,
		   let qrCodeValue = qrCode.value,
		   qrCodeValue.caseInsensitiveCompare(m.request) == .orderedSame,
			let qrCodeCgImage = qrCode.cgImage
		{
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
		
		if let m = mvi.model as? Receive.Model_Generated {
			withAnimation {
				let url = "lightning:\(m.request)"
				sheet = ReceiveViewSheet.sharingUrl(url: url)
			}
		}
	}
	
	func shareImageToSystem() -> Void {
		log.trace("shareImageToSystem()")
		
		if let m = mvi.model as? Receive.Model_Generated,
		   let qrCodeValue = qrCode.value,
		   qrCodeValue.caseInsensitiveCompare(m.request) == .orderedSame,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			sheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
}
