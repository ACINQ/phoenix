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
	
	@State var pushPermissionRequestedFromOS = true
	@State var bgAppRefreshDisabled = false
	@State var notificationsDisabled = false
	@State var alertsDisabled = false
	@State var badgesDisabled = false
	@State var showRequestPushPermissionPopoverTimer: Timer? = nil
	
	@State var modificationAmount: CurrencyAmount? = nil
	@State var currencyConverterOpen = false
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let lastIncomingPaymentPublisher = AppDelegate.get().business.paymentsManager.lastIncomingPaymentPublisher()
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	// Saving custom publisher in @State since otherwise it fires on every render
	@State var channelsPublisher = AppDelegate.get().business.peerPublisher().flatMap { $0.channelsPublisher() }
	
	let willEnterForegroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.willEnterForegroundNotification
	)
	
	// For the cicular buttons: [copy, share, edit]
	enum MaxButtonWidth: Preference {}
	let maxButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxButtonWidth: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			NavigationLink(
				destination: CurrencyConverterView(
					initialAmount: modificationAmount,
					didChange: currencyConverterDidChange,
					didClose: currencyConvertDidClose
				),
				isActive: $currencyConverterOpen
			) {
				EmptyView()
			}
			
			content()
		}
		.onAppear {
			onAppear()
		}
		.onDisappear {
			onDisappear()
		}
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
		.onReceive(willEnterForegroundPublisher) { _ in
			willEnterForeground()
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
		.navigationBarTitle(
			NSLocalizedString("Receive", comment: "Navigation bar title"),
			displayMode: .inline
		)
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
			qrCodeView
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
			
				Text(invoiceAmount())
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
			
			warningButton(paddingTop: 8)
			
			Button {
				didTapSwapInButton()
			} label: {
				HStack {
					Image(systemName: "repeat") // alt: "arrowshape.bounce.forward.fill"
						.imageScale(.small)

					Text("Show a Bitcoin address")
				}
			}
			.padding(.top)
			
			Spacer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func mainLandscape() -> some View {
		
		HStack {
			
			qrCodeView
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
				
				Text(invoiceAmount())
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
			
			qrCodeView
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
				}
				Spacer()
			}
		}
	}
	
	@ViewBuilder
	var qrCodeView: some View {
		
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
	func warningButton(paddingTop: CGFloat) -> some View {
		
		// There are several warnings we may want to display.
		
		if bgAppRefreshDisabled {
			
			// The user has disabled Background App Refresh.
			// In this case, we won't be able to receive payments
		 	// while the app is in the background.
			
			Button {
				showBgAppRefreshDisabledPopover()
			} label: {
				Image(systemName: "exclamationmark.octagon.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appNegative)
					.frame(width: 32, height: 32)
			}
			.padding(.top, paddingTop)
		}
		else if !pushPermissionRequestedFromOS {
			
			// When we first prompted them to enable push notifications,
			// the user said "no". Or they otherwise dismissed the popover window.
			// Thus we have never tried to enable push notifications.
			
			Button {
				showRequestPushPermissionPopover()
			} label: {
				Image(systemName: "exclamationmark.bubble.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 32, height: 32)
			}
			.padding(.top, paddingTop)
		}
		else if notificationsDisabled || (alertsDisabled && badgesDisabled) {
			
			// The user has totally disabled notifications for our app.
			// So if a payment is received while the app is in the background,
		 	// we won't be able to notify them (in any way, shape or form).
		 	//
		 	// Or the user didn't totally disable notifications,
		 	// but they effectively did. Because they disabled all alerts & badges.
			
			Button {
				showNotificationsDisabledPopover()
			} label: {
				Image(systemName: "exclamationmark.triangle.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appWarn)
					.frame(width: 32, height: 32)
			}
			.padding(.top, paddingTop)
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
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	func invoiceAmount() -> String {
		
		if let m = mvi.model as? Receive.Model_Generated {
			if let msat = m.amount?.msat {
				let btcAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit)
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					let fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
					return "\(btcAmt.string)  /  \(fiatAmt.string)"
				} else {
					return btcAmt.string
				}
				
			} else {
				return NSLocalizedString("any amount",
					comment: "placeholder: invoice is amount-less"
				)
			}
		} else {
			return "..."
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
	// MARK: Actions
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
			expirySeconds: Int64(60 * 60 * 24 * Prefs.shared.invoiceExpirationDays)
		))
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .neverAskedUser {
			
			// We want to ask the user:
			// "Do you want to be notified when you've received a payment?"
			//
			// But let's show the popup after a brief delay,
			// to allow the user to see what this view is about.
			
			showRequestPushPermissionPopoverTimer =
				Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false) { _ in
					showRequestPushPermissionPopover()
				}
			
		} else {
			
			checkPushPermissions()
		}
	}
	
	func onDisappear() -> Void {
		log.trace("onDisappear()")
		
		showRequestPushPermissionPopoverTimer?.invalidate()
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			log.debug("updating qr code...")
			
			// Issue #196: Use uppercase lettering for invoices and address QRs
			qrCode.generate(value: m.request.uppercased())
		}
	}
	
	func willEnterForeground() -> Void {
		log.trace("willEnterForeground()")
		
		let query = Prefs.shared.pushPermissionQuery
		if query != .neverAskedUser {
			
			checkPushPermissions()
		}
	}
	
	func requestPushPermission() -> Void {
		log.trace("requestPushPermission()")
		
		AppDelegate.get().requestPermissionForLocalNotifications { (granted: Bool) in
			
			let block = { checkPushPermissions() }
			if Thread.isMainThread {
				block()
			} else {
				DispatchQueue.main.async { block() }
			}
		}
	}
	
	func checkPushPermissions() -> Void {
		log.trace("checkPushPermission()")
		assert(Thread.isMainThread, "invoked from non-main thread")
		
		let query = Prefs.shared.pushPermissionQuery
		if query == .userAccepted {
			pushPermissionRequestedFromOS = true
		} else {
			// user denied or ignored, which means we haven't asked the OS for permission yet
			pushPermissionRequestedFromOS = false
		}
		
		bgAppRefreshDisabled = (UIApplication.shared.backgroundRefreshStatus != .available)
		
		let center = UNUserNotificationCenter.current()
		center.getNotificationSettings { settings in
			
			notificationsDisabled = (settings.authorizationStatus != .authorized)
			
			// Are we able to display notifications to the user ?
			// That is, can we display a message that says, "Payment received" ?
			//
			// Within Settings -> Notifications, there are 3 options
			// that can be enabled/disabled independently of each other:
			//
			// - Lock Screen
			// - Notification Center
			// - Banners
			//
			// If the user has disabled ALL of them, then we consider notifications to be disabled.
			// If the user has only disabled one, such as the Lock Screen,
			// we consider that an understandable privacy choice,
			// and don't highlight it in the UI.
			
			var count = 0
			if settings.lockScreenSetting == .enabled {
				count += 1
			}
			if settings.notificationCenterSetting == .enabled {
				count += 1
			}
			if settings.alertSetting == .enabled {
				count += 1
			}
			
			alertsDisabled = (count == 0)
			badgesDisabled = (settings.badgeSetting != .enabled)
		}
		
	}
	
	func showBgAppRefreshDisabledPopover() -> Void {
		log.trace("showBgAppRefreshDisabledPopover()")
		
		popoverState.display(dismissable: false) {
			BgAppRefreshDisabledPopover()
		}
	}
	
	func showNotificationsDisabledPopover() -> Void {
		log.trace("showNotificationsDisabledPopover()")
		
		popoverState.display(dismissable: false) {
			NotificationsDisabledPopover()
		}
	}
	
	func showRequestPushPermissionPopover() -> Void {
		log.trace("showRequestPushPermissionPopover()")
		
		let callback = {(response: PushPermissionPopoverResponse) -> Void in
			
			log.debug("PushPermissionPopupResponse: \(response.rawValue)")
			
			switch response {
			case .accepted :
				Prefs.shared.pushPermissionQuery = .userAccepted
				requestPushPermission()
				
			case .denied:
				Prefs.shared.pushPermissionQuery = .userDeclined
				checkPushPermissions()
				
			case .ignored:
				// Ask again next time
				checkPushPermissions()
			}
		}
		
		popoverState.display(dismissable: true) {
			RequestPushPermissionPopover(callback: callback)
		}
	}
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_Generated {
			UIPasteboard.general.string = m.request
			toast.pop(
				Text("Copied to pasteboard!").anyView,
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
				Text("Copied QR code image to pasteboard!").anyView,
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func didTapCopyButton() -> Void {
		log.trace("didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() -> Void {
		log.trace("didLongPressCopyButton()")
		
		shortSheetState.display(dismissable: true) {
			
			CopyOptionsSheet(copyText: {
				copyTextToPasteboard()
			}, copyImage: {
				copyImageToPasteboard()
			})
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
	
	func didTapShareButton() -> Void {
		log.trace("didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() -> Void {
		log.trace("didLongPressShareButton()")
		
		shortSheetState.display(dismissable: true) {
			
			ShareOptionsSheet(shareText: {
				shareTextToSystem()
			}, shareImage: {
				shareImageToSystem()
			})
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("didTapEditButton()")
		
		if let model = mvi.model as? Receive.Model_Generated {
			
			shortSheetState.display(dismissable: true) {
				
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
	
	func channelsChanged(_ channels: Lightning_kmpPeer.ChannelsMap) -> Void {
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
		
		shortSheetState.display(dismissable: true) {
			
			ModifyInvoiceSheet(
				mvi: mvi,
				savedAmount: $modificationAmount,
				amount: amount,
				desc: desc ?? "",
				currencyConverterOpen: $currencyConverterOpen
			)
		}
	}
}
