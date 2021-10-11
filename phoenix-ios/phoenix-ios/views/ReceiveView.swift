import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ReceiveView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ReceiveView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.receive() })
	
	@State var lastDescription: String? = nil
	@State var lastAmount: Lightning_kmpMilliSatoshi? = nil
	
	@State var receiveLightningView_didAppear = false
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@StateObject var toast = Toast()
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			if mvi.model is Receive.Model_SwapIn {
				
				// Receive.Model_SwapIn_Requesting : Receive.Model_SwapIn
				// Receive.Model_SwapIn_Generated : Receive.Model_SwapIn
				
				SwapInView(
					mvi: mvi,
					toast: toast,
					lastDescription: $lastDescription,
					lastAmount: $lastAmount
				)
				
			} else {
			
				ReceiveLightningView(
					mvi: mvi,
					toast: toast,
					didAppear: $receiveLightningView_didAppear
				)
			}
			
			toast.view()
			
		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			lastDescription = m.desc
			lastAmount = m.amount
		}
	}
	
	/// Shared logic. Used by:
	/// - ReceiveLightningView
	/// - SwapInView
	///
	static func qrCodeBorderColor(_ colorScheme: ColorScheme) -> Color {
		
		return (colorScheme == .dark) ? Color(UIColor.separator) : Color.appAccent
	}
	
	/// Shared button builder. Used by:
	/// - ReceiveLightningView
	/// - SwapInView
	///
	@ViewBuilder
	static func actionButton(
		image: Image,
		width: CGFloat = 20,
		height: CGFloat = 20,
		xOffset: CGFloat = 0,
		yOffset: CGFloat = 0,
		action: @escaping () -> Void
	) -> some View {
		
		Button(action: action) {
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
			}
		}
	}
	
	/// Shared logic
	@ViewBuilder
	static func copyButton(action: @escaping () -> Void) -> some View {
		
		ReceiveView.actionButton(
			image: Image(systemName: "square.on.square"),
			width: 20, height: 20,
			xOffset: 0, yOffset: 0,
			action: action
		)
	}
	
	/// Shared logic
	@ViewBuilder
	static func shareButton(action: @escaping () -> Void) -> some View {
		
		ReceiveView.actionButton(
			image: Image(systemName: "square.and.arrow.up"),
			width: 21, height: 21,
			xOffset: 0, yOffset: -1,
			action: action
		)
	}
}

struct ReceiveLightningView: View, ViewName {
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var toast: Toast
	
	@Binding var didAppear: Bool
	
	@StateObject var qrCode = QRCode()

	@State var isFullScreenQrcode = false
	
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
	
	@State var modificationUnit = Currency.bitcoin(.sat)
	
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
	
	@ViewBuilder
	var body: some View {
		
		content()
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
		   qrCode.value == m.request,
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
	func copyButton() -> some View {
		
		ReceiveView.copyButton {
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
		
		ReceiveView.shareButton {
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
		
		ReceiveView.actionButton(
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
				showBgAppRefreshDisabledWarning()
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
				showNotificationsDisabledWarning()
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
	
	func onAppear() -> Void {
		log.trace("[\(viewName)] onAppear()")
		
		// Careful: this function may be called multiple times
		if didAppear {
			return
		}
		didAppear = true
			
		let defaultDesc = Prefs.shared.defaultPaymentDescription
		mvi.intent(Receive.IntentAsk(amount: nil, desc: defaultDesc))
		
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
		
		if currencyPrefs.currencyType == .fiat, currencyPrefs.fiatExchangeRate() != nil {
			
			let fiatCurrency = currencyPrefs.fiatCurrency
			modificationUnit = Currency.fiat(fiatCurrency)
			
		} else {
			
			let bitcoinUnit = currencyPrefs.bitcoinUnit
			modificationUnit = Currency.bitcoin(bitcoinUnit)
		}
	}
	
	func onDisappear() -> Void {
		log.trace("[\(viewName)] onDisappear()")
		
		showRequestPushPermissionPopoverTimer?.invalidate()
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("[\(viewName)] onModelChange()")
		
		if let m = model as? Receive.Model_Generated {
			log.debug("[\(viewName)] updating qr code...")
			qrCode.generate(value: m.request)
		}
	}
	
	func willEnterForeground() -> Void {
		log.trace("[\(viewName)] willEnterForeground()")
		
		let query = Prefs.shared.pushPermissionQuery
		if query != .neverAskedUser {
			
			checkPushPermissions()
		}
	}
	
	func requestPushPermission() -> Void {
		log.trace("[\(viewName)] requestPushPermission()")
		
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
		log.trace("[\(viewName)] checkPushPermission()")
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
	
	func showBgAppRefreshDisabledWarning() -> Void {
		log.trace("[\(viewName)] showBgAppRefreshDisabledWarning()")
		
		popoverState.display(dismissable: false) {
			BgAppRefreshDisabledWarning()
		}
	}
	
	func showNotificationsDisabledWarning() -> Void {
		log.trace("[\(viewName)] showNotificationsDisabledWarning()")
		
		popoverState.display(dismissable: false) {
			NotificationsDisabledWarning()
		}
	}
	
	func showRequestPushPermissionPopover() -> Void {
		log.trace("[\(viewName)] showRequestPushPermissionPopover()")
		
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
		log.trace("[\(viewName)] copyTextToPasteboard()")
		
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
		log.trace("[\(viewName)] copyImageToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_Generated,
			qrCode.value == m.request,
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
		log.trace("[\(viewName)] didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() -> Void {
		log.trace("[\(viewName)] didLongPressCopyButton()")
		
		shortSheetState.display(dismissable: true) {
			
			CopyOptionsSheet(copyText: {
				copyTextToPasteboard()
			}, copyImage: {
				copyImageToPasteboard()
			})
		}
	}
	
	func shareTextToSystem() -> Void {
		log.trace("[\(viewName)] shareTextToSystem()")
		
		if let m = mvi.model as? Receive.Model_Generated {
			withAnimation {
				let url = "lightning:\(m.request)"
				sheet = ReceiveViewSheet.sharingUrl(url: url)
			}
		}
	}
	
	func shareImageToSystem() -> Void {
		log.trace("[\(viewName)] shareImageToSystem()")
		
		if let m = mvi.model as? Receive.Model_Generated,
			qrCode.value == m.request,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			sheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
	
	func didTapShareButton() -> Void {
		log.trace("[\(viewName)] didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() -> Void {
		log.trace("[\(viewName)] didLongPressShareButton()")
		
		shortSheetState.display(dismissable: true) {
			
			ShareOptionsSheet(shareText: {
				shareTextToSystem()
			}, shareImage: {
				shareImageToSystem()
			})
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("[\(viewName)] didTapEditButton()")
		
		if let model = mvi.model as? Receive.Model_Generated {
			
			shortSheetState.display(dismissable: true) {
				
				ModifyInvoiceSheet(
					mvi: mvi,
					initialAmount: model.amount,
					desc: model.desc ?? "",
					unit: $modificationUnit
				)
			}
		}
	}
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("[\(viewName)] lastIncomingPaymentChanged()")
		
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
		log.trace("[\(viewName)] chainContextChanged()")
		
		swapIn_enabled = context.swapIn.v1.status is WalletContext.V0ServiceStatusActive
		payToOpen_enabled = context.payToOpen.v1.status is WalletContext.V0ServiceStatusActive
		payToOpen_minFundingSat = context.payToOpen.v1.minFundingSat
	}
	
	func channelsChanged(_ channels: Lightning_kmpPeer.ChannelsMap) -> Void {
		log.trace("[\(viewName)] channelsChanged()")
		
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
		log.trace("[\(viewName)] didTapSwapInButton()")
		
		if swapIn_enabled {
			
			mvi.intent(Receive.IntentRequestSwapIn())
			
		} else {
			
			popoverState.display(dismissable: true) {
				SwapInDisabledPopover()
			}
		}
	}
}

struct CopyOptionsSheet: View, ViewName {
	
	let copyText: () -> Void
	let copyImage: () -> Void
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
			Button {
				shortSheetState.close {
					copyText()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.on.square")
						.imageScale(.medium)
					Text("Copy Text")
					Spacer()
					Text("(Lightning invoice)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					borderStroke: Color.appAccent
				)
			)
			.padding(.bottom, 8)
			
			Button {
				shortSheetState.close {
					copyImage()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.on.square")
						.imageScale(.medium)
					Text("Copy Image")
					Spacer()
					Text("(QR code)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					borderStroke: Color.appAccent
				)
			)
		}
		.padding(.all)
	}
}

struct ShareOptionsSheet: View, ViewName {
	
	let shareText: () -> Void
	let shareImage: () -> Void
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
			Button {
				shortSheetState.close {
					shareText()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.and.arrow.up")
						.imageScale(.medium)
					Text("Share Text")
					Spacer()
					Text("(Lightning invoice)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					borderStroke: Color.appAccent
				)
			)
			.padding(.bottom, 8)
			
			Button {
				shortSheetState.close {
					shareImage()
				}
			} label: {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
					Image(systemName: "square.and.arrow.up")
						.imageScale(.medium)
					Text("Share Image")
					Spacer()
					Text("(QR code)")
						.font(.footnote)
						.foregroundColor(.secondary)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.contentShape(Rectangle()) // make Spacer area tappable
			}
			.buttonStyle(
				ScaleButtonStyle(
					borderStroke: Color.appAccent
				)
			)
		}
		.padding(.all)
	}
}

struct ModifyInvoiceSheet: View, ViewName {

	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>

	let initialAmount: Lightning_kmpMilliSatoshi?
	
	@State var desc: String
	
	@Binding var unit: Currency
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isEmptyAmount: Bool = false
	
	@EnvironmentObject private var currencyPrefs: CurrencyPrefs
	
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	// Workaround for SwiftUI bug
	enum TextHeight: Preference {}
	let textHeightReader = GeometryPreferenceReader(
		key: AppendValue<TextHeight>.self,
		value: { [$0.size.height] }
	)
	@State var textHeight: CGFloat? = nil
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			unit: $unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}

	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .leading) {
			Text("Edit payment request")
				.font(.title3)
				.padding([.top, .bottom])

			HStack {
				TextField("Amount (optional)", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
					.read(textHeightReader)
					.padding([.top, .bottom], 8)
					.padding(.leading, 16)
					.padding(.trailing, 0)
				
				Picker(
					selection: $unit,
					label: Text(unit.abbrev).frame(minWidth: 40, alignment: Alignment.trailing)
				) {
					let options = Currency.displayable(currencyPrefs: currencyPrefs)
					ForEach(0 ..< options.count) {
						let option = options[$0]
						Text(option.abbrev).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.frame(height: textHeight) // workaround for SwiftUI bug
				.padding(.trailing, 16)
			}
			.background(Capsule().stroke(Color(UIColor.separator)))

			Text(altAmount)
				.font(.caption)
				.foregroundColor(isInvalidAmount && !isEmptyAmount ? Color.appNegative : .secondary)
				.padding(.top, 0)
				.padding(.leading, 16)
				.padding(.bottom, 4)

			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField("Description (optional)", text: $desc)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					desc = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(desc == "")
			}
			.padding([.top, .bottom], 8)
			.padding(.leading, 16)
			.padding(.trailing, 8)
			.background(
				Capsule()
					.strokeBorder(Color(UIColor.separator))
			)
			.padding(.bottom)
			
			HStack {
				Spacer()
				Button("Save") {
					didTapSaveButton()
				}
				.font(.title2)
				.disabled(isInvalidAmount && !isEmptyAmount)
			}
			.padding(.bottom)

		} // </VStack>
		.padding([.leading, .trailing])
		.assignMaxPreference(for: textHeightReader.key, to: $textHeight)
		.onAppear() {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: unit) { _  in
			unitDidChange()
		}
		
	} // </body>
	
	func onAppear() -> Void {
		log.trace("[\(viewName)] onAppear()")
		
		let msat: Int64? = initialAmount?.msat
		
		switch unit {
		case .fiat(let fiatCurrency):
			
			if let msat = msat, let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
				
				let targetAmt = Utils.convertToFiat(msat: msat, exchangeRate: exchangeRate)
				parsedAmount = Result.success(targetAmt)
				
				let formattedAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				amount = formattedAmt.digits
			} else {
				refreshAltAmount()
			}
			
		case .bitcoin(let bitcoinUnit):
			
			if let msat = msat {
				
				let targetAmt = Utils.convertBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
				parsedAmount = Result.success(targetAmt)
				
				let formattedAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit, hideMsats: false)
				amount = formattedAmt.digits
			} else {
				refreshAltAmount()
			}
		}
	}
	
	func amountDidChange() -> Void {
		log.trace("[\(viewName)] amountDidChange()")
		
		refreshAltAmount()
	}
	
	func unitDidChange() -> Void {
		log.trace("[\(viewName)] unitDidChange()")
		
		// We might want to apply a different formatter
		let result = TextFieldCurrencyStyler.format(input: amount, unit: unit, hideMsats: false)
		parsedAmount = result.1
		amount = result.0
		
		refreshAltAmount()
	}
	
	func refreshAltAmount() -> Void {
		log.trace("[\(viewName)] refreshAltAmount()")
		
		switch parsedAmount {
		case .failure(let error):
			isInvalidAmount = true
			isEmptyAmount = error == .emptyInput
			
			switch error {
			case .emptyInput:
				altAmount = NSLocalizedString("Any amount", comment: "displayed when user doesn't specify an amount")
			case .invalidInput:
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			isInvalidAmount = false
			isEmptyAmount = false
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					
					let msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					altAmount = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate).string
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
				
			case .fiat(let fiatCurrency):
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					altAmount = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit).string
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since Currency.displayable() already filters for this.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
			}
		}
	}
	
	func didTapSaveButton() -> Void {
		log.trace("[\(viewName)] didTapSaveButton()")
		
		var msat: Lightning_kmpMilliSatoshi? = nil
		let trimmedDesc = desc.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			switch unit {
			case .bitcoin(let bitcoinUnit):
							
				msat = Lightning_kmpMilliSatoshi(msat:
					Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
				)
				
			case .fiat(let fiatCurrency):
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					msat = Lightning_kmpMilliSatoshi(msat:
						Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					)
				}
			}
		}
		
		shortSheetState.close {
			
			mvi.intent(Receive.IntentAsk(
				amount: msat,
				desc: trimmedDesc
			))
		}
	}
	
} // </ModifyInvoiceSheet>


struct BgAppRefreshDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled Background App Refresh for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"""
					This means you will not be able to receive payments when Phoenix \
					is in the background. To receive payments, Phoenix must be open and in the foreground.
					"""
				)
				.lineLimit(nil)
				.minimumScaleFactor(0.5) // problems with "foreground" being truncated
				.padding(.bottom, 4)
				
				Text(
					"""
					To fix this re-enable Background App Refresh via: \
					Settings -> General -> Background App Refresh
					"""
				)
				
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close()
		})
	}
}

struct NotificationsDisabledWarning: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("You have disabled notifications for this app.")
					.bold()
					.padding(.bottom, 4)
				
				Text(
					"""
					This means you will not be notified if you receive a payment while \
					Phoenix is in the background.
					"""
				)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					fixIt()
					popoverState.close()
				} label : {
					Text("Settings").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					popoverState.close()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close()
		})
	}
	
	func fixIt() -> Void {
		log.trace("[NotificationsDisabledWarning] fixIt()")
		
		if let bundleIdentifier = Bundle.main.bundleIdentifier,
		   let appSettings = URL(string: UIApplication.openSettingsURLString + bundleIdentifier)
		{
			if UIApplication.shared.canOpenURL(appSettings) {
				UIApplication.shared.open(appSettings)
			}
		}
	}
}

enum PushPermissionPopoverResponse: String {
	case ignored
	case denied
	case accepted
}

struct RequestPushPermissionPopover: View, ViewName {
	
	let callback: (PushPermissionPopoverResponse) -> Void
	
	@State private var userIsIgnoringPopover: Bool = true
	@Environment(\.popoverState) private var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Would you like to be notified when somebody sends you a payment?")
			}
			.padding(.bottom)
			
			HStack {
				Button {
					didDeny()
				} label : {
					Text("No").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					didAccept()
				} label : {
					Text("Yes").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(popoverState.publisher) { item in
			if item  == nil {
				willClose()
			}
		}
	}
	
	func didDeny() -> Void {
		log.trace("[\(viewName)] didDeny()")
		
		callback(.denied)
		userIsIgnoringPopover = false
		popoverState.close()
	}
	
	func didAccept() -> Void {
		log.trace("[\(viewName)] didAccept()")
		
		callback(.accepted)
		userIsIgnoringPopover = false
		popoverState.close()
	}
	
	func willClose() -> Void {
		log.trace("[\(viewName)] willClose()")
		
		if userIsIgnoringPopover {
			callback(.ignored)
		}
	}
}

fileprivate struct SwapInDisabledPopover: View, ViewName {
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: .trailing) {
			
			VStack(alignment: .leading) {
				Text("Some Services Disabled")
					.font(.title3)
					.padding(.bottom)
				
				Text(
					"""
					The bitcoin mempool is congested and fees are very high. \
					The on-chain swap service has been temporarily disabled.
					"""
				)
				.lineLimit(nil)
			}
			.padding(.bottom)
			
			HStack {
				Button {
					popoverState.close()
				} label : {
					Text("Try again later").font(.headline)
				}
			}
		}
		.padding()
	}
}

struct SwapInView: View, ViewName {
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	
	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var toast: Toast
	
	@Binding var lastDescription: String?
	@Binding var lastAmount: Lightning_kmpMilliSatoshi?
	
	@StateObject var qrCode = QRCode()
	
	@State var sheet: ReceiveViewSheet? = nil
	
	@State var swapIn_feePercent: Double = 0.0
	@State var swapIn_minFeeSat: Int64 = 0
	@State var swapIn_minFundingSat: Int64 = 0
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	let incomingSwapsPublisher = AppDelegate.get().business.paymentsManager.incomingSwapsPublisher()
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
	@ViewBuilder
	var body: some View {
		
		VStack {
			
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
				.padding([.top, .bottom])
			
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				
				Text("Address")
					.foregroundColor(.secondary)
				
				if let btcAddr = bitcoinAddress() {
					
					Text(btcAddr)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = btcAddr
								toast.pop(
									Text("Copied to pasteboard!").anyView,
									colorScheme: colorScheme.opposite
								)
							}) {
								Text("Copy")
							}
						}
				} else {
					Text(verbatim: "…")
				}
			}
			.padding([.leading, .trailing], 40)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				
				ReceiveView.copyButton {
					// using simultaneousGesture's below
				}
				.disabled(!(mvi.model is Receive.Model_SwapIn_Generated))
				.simultaneousGesture(LongPressGesture().onEnded { _ in
					didLongPressCopyButton()
				})
				.simultaneousGesture(TapGesture().onEnded {
					didTapCopyButton()
				})
				
				ReceiveView.shareButton {
					// using simultaneousGesture's below
				}
				.disabled(!(mvi.model is Receive.Model_SwapIn_Generated))
				.simultaneousGesture(LongPressGesture().onEnded { _ in
					didLongPressShareButton()
				})
				.simultaneousGesture(TapGesture().onEnded {
					didTapShareButton()
				})
				
			} // </HStack>
			
			feesInfoView
				.padding([.top, .leading, .trailing])
			
			Button {
				didTapLightningButton()
			} label: {
				HStack {
					Image(systemName: "repeat") // alt: "arrowshape.bounce.forward.fill"
						.imageScale(.small)

					Text("Show a Lightning invoice")
				}
			}
			.padding(.top)
			
			Spacer()
			
		} // </VStack>
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
			NSLocalizedString("Swap In", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onReceive(incomingSwapsPublisher) {
			onIncomingSwapsChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
	}
	
	@ViewBuilder
	var qrCodeView: some View {
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
			qrCode.value == m.address,
			let qrCodeImage = qrCode.image
		{
			qrCodeImage
				.resizable()
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
				}
			
		} else {
			VStack {
				// Remember: This view is on a white background. Even in dark mode.
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
					.padding(.bottom, 10)
			
				Group {
					if mvi.model is Receive.Model_SwapIn_Requesting {
						Text("Requesting Swap-In Address...")
					} else {
						Text("Generating QRCode...")
					}
				}
				.foregroundColor(Color(UIColor.darkGray))
				.font(.caption)
			}
		}
	}
	
	@ViewBuilder
	var feesInfoView: some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 8) {
			
			Image(systemName: "exclamationmark.circle")
				.imageScale(.large)
			
			let minFunding = Utils.formatBitcoin(sat: swapIn_minFundingSat, bitcoinUnit: .sat)
			
			let feePercent = formatFeePercent()
			let minFee = Utils.formatBitcoin(sat: swapIn_minFeeSat, bitcoinUnit: .sat)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Text(
					"""
					This is a swap address. It is not controlled by your wallet. \
					On-chain deposits sent to this address will be converted to Lightning channels.
					"""
				)
				.lineLimit(nil)
				.multilineTextAlignment(.leading)
				.padding(.bottom, 14)
				
				Text(styled: String(format: NSLocalizedString(
					"""
					Deposits must be at least **%@**. The fee is **%@%%** (%@ minimum).
					""",
					comment:	"Minimum amount description."),
					minFunding.string, feePercent, minFee.string
				))
				.lineLimit(nil)
				.multilineTextAlignment(.leading)
			}
		}
		.font(.subheadline)
		.padding()
		.background(
			RoundedRectangle(cornerRadius: 10)
				.foregroundColor(Color.mutedBackground)
		)
	}
	
	func bitcoinAddress() -> String? {
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			return m.address
		} else {
			return nil
		}
	}
	
	func formatFeePercent() -> String {
		
		let formatter = NumberFormatter()
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: swapIn_feePercent))!
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("[\(viewName)] onModelChange()")
		
		if let m = model as? Receive.Model_SwapIn_Generated {
			log.debug("[\(viewName)] updating qr code...")
			qrCode.generate(value: m.address)
		}
	}
	
	func onIncomingSwapsChanged(_ incomingSwaps: [String: Lightning_kmpMilliSatoshi]) -> Void {
		log.trace("[\(viewName)] onIncomingSwapsChanged(): \(incomingSwaps)")
		
		guard let bitcoinAddress = bitcoinAddress() else {
			return
		}
		
		// incomingSwaps: [bitcoinAddress: pendingAmount]
		//
		// If incomingSwaps has an entry for the bitcoin address that we're displaying,
		// then let's dismiss this sheet, and show the user the home screen.
		// 
		// Because the home screen has the "+X sat incoming" message
		
		if incomingSwaps[bitcoinAddress] != nil {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("[\(viewName)] chainContextChanged()")
		
		swapIn_feePercent = context.swapIn.v1.feePercent * 100    // 0.01 => 1%
		swapIn_minFeeSat = context.payToOpen.v1.minFeeSat         // not yet segregated for swapIn - future work
		swapIn_minFundingSat = context.payToOpen.v1.minFundingSat // not yet segregated for swapIn - future work
	}
	
	func copyTextToPasteboard() -> Void {
		log.trace("[\(viewName)] copyTextToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			UIPasteboard.general.string = m.address
			toast.pop(
				Text("Copied to pasteboard!").anyView,
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("[\(viewName)] copyImageToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
			qrCode.value == m.address,
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
		log.trace("[\(viewName)] didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() -> Void {
		log.trace("[\(viewName)] didLongPressCopyButton()")
		
		shortSheetState.display(dismissable: true) {
			
			CopyOptionsSheet(copyText: {
				copyTextToPasteboard()
			}, copyImage: {
				copyImageToPasteboard()
			})
		}
	}
	
	func shareTextToSystem() {
		log.trace("[\(viewName)] shareTextToSystem()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			let url = "bitcoin:\(m.address)"
			sheet = ReceiveViewSheet.sharingUrl(url: url)
		}
	}
	
	func shareImageToSystem() {
		log.trace("[\(viewName)] shareImageToSystem()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
			qrCode.value == m.address,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			sheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
	
	func didTapShareButton() {
		log.trace("[\(viewName)] didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() {
		log.trace("[\(viewName)] didLongPressShareButton()")
		
		shortSheetState.display(dismissable: true) {
					
			ShareOptionsSheet(shareText: {
				shareTextToSystem()
			}, shareImage: {
				shareImageToSystem()
			})
		}
	}
	
	func didTapLightningButton() {
		log.trace("[\(viewName)] didTapLightningButton()")
		
		mvi.intent(Receive.IntentAsk(
			amount: lastAmount,
			desc: lastDescription
		))
	}
}

// MARK:-

class ReceiveView_Previews: PreviewProvider {

	static let request = "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm"

	static var previews: some View {

		NavigationView {
			ReceiveView().mock(Receive.Model_Awaiting())
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")

		NavigationView {
			ReceiveView().mock(Receive.Model_Generated(
				request: request,
				paymentHash: "foobar",
				amount: Lightning_kmpMilliSatoshi(msat: 170000),
				desc: "1 Espresso Coin Panna"
			))
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")
	}
}
