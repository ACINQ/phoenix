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
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@StateObject var toast = Toast()
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if AppDelegate.get().business.chain.isTestnet() {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			if mvi.model is Receive.ModelSwapIn {
				
				// Receive.ModelSwapInRequesting : Receive.ModelSwapIn
				// Receive.ModelSwapInGenerated : Receive.ModelSwapIn
				
				SwapInView(mvi: mvi, toast: toast)
				
			} else {
			
				ReceiveLightningView(mvi: mvi, toast: toast)
			}
			
			toast.view()
			
		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
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
		case editing(model: Receive.ModelGenerated)
	}
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var toast: Toast
	
	@StateObject var qrCode = QRCode()

	@State var didAppear = false
	@State var isFullScreenQrcode = false
	
	@State var unit: String = "sat"
	@State var sheet: ReceiveViewSheet? = nil
	
	@State var isSwapInEnabled = true
	@State var isPayToOpenEnabled = true
	
	@State var pushPermissionRequestedFromOS = true
	@State var bgAppRefreshDisabled = false
	@State var notificationsDisabled = false
	@State var alertsDisabled = false
	@State var badgesDisabled = false
	@State var showRequestPushPermissionPopoverTimer: Timer? = nil
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	let lastIncomingPaymentPublisher = AppDelegate.get().business.paymentsManager.lastIncomingPaymentPublisher()
	let chainContextPublisher = AppDelegate.get().business.appConfigurationManager.chainContextPublisher()
	
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
			
			case .editing(let model):
				
				ModifyInvoiceSheet(
					mvi: mvi,
					dismissSheet: { self.sheet = nil },
					initialAmount: model.amount,
					desc: model.desc ?? ""
				)
				.modifier(GlobalEnvironment()) // SwiftUI bug (prevent crash)
			
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
		} else if verticalSizeClass == UserInterfaceSizeClass.compact {
			mainLandscape()
		} else {
			mainPortrait()
		}
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
			
			if !isPayToOpenEnabled {
				payToOpenDisabledWarning()
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
		
		if let m = mvi.model as? Receive.ModelGenerated,
		   qrCode.value == m.request,
			let qrCodeCgImage = qrCode.cgImage,
		   let qrCodeImage = qrCode.image
		{
			qrCodeImage
				.resizable()
				.aspectRatio(contentMode: .fit)
				.contextMenu {
					Button(action: {
						let uiImg = UIImage(cgImage: qrCodeCgImage)
						UIPasteboard.general.image = uiImg
						toast.pop(
							Text("Copied QR code image to pasteboard!").anyView,
							colorScheme: colorScheme.opposite
						)
					}) {
						Text("Copy")
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
					Button(action: {
						let uiImg = UIImage(cgImage: qrCodeCgImage)
						sheet = ReceiveViewSheet.sharingImg(img: uiImg)
					}) {
						Text("Share")
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
			didTapCopyButton()
		}
		.disabled(!(mvi.model is Receive.ModelGenerated))
	}
	
	@ViewBuilder
	func shareButton() -> some View {
		
		ReceiveView.shareButton {
			didTapShareButton()
		}
		.disabled(!(mvi.model is Receive.ModelGenerated))
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
		.disabled(!(mvi.model is Receive.ModelGenerated))
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
			Text(
				"""
				Channel creation has been temporarily disabled. \
				You may not be able to receive some payments.
				"""
			)
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
		
		if let m = mvi.model as? Receive.ModelGenerated {
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
		
		if let m = mvi.model as? Receive.ModelGenerated {
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
	}
	
	func onDisappear() -> Void {
		log.trace("[\(viewName)] onDisappear()")
		
		showRequestPushPermissionPopoverTimer?.invalidate()
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("[\(viewName)] onModelChange()")
		
		if let m = model as? Receive.ModelGenerated {
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
		
		popoverState.display.send(PopoverItem(
			
			BgAppRefreshDisabledWarning().anyView,
			dismissable: false
		))
	}
	
	func showNotificationsDisabledWarning() -> Void {
		log.trace("[\(viewName)] showNotificationsDisabledWarning()")
		
		popoverState.display.send(PopoverItem(
			
			NotificationsDisabledWarning().anyView,
			dismissable: false
		))
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
		
		popoverState.display.send(PopoverItem(
		
			RequestPushPermissionPopover(callback: callback).anyView,
			dismissable: true
		))
	}
	
	func didTapCopyButton() -> Void {
		log.trace("[\(viewName)] didTapCopyButton()")
		
		if let m = mvi.model as? Receive.ModelGenerated {
			UIPasteboard.general.string = m.request
			toast.pop(
				Text("Copied to pasteboard!").anyView,
				colorScheme: colorScheme.opposite,
				style: .chrome
			)
		}
	}
	
	func didTapShareButton() -> Void {
		log.trace("[\(viewName)] didTapShareButton()")
		
		if let m = mvi.model as? Receive.ModelGenerated {
			withAnimation {
				let url = "lightning:\(m.request)"
				sheet = ReceiveViewSheet.sharingUrl(url: url)
			}
		}
	}
	
	func didTapEditButton() -> Void {
		log.trace("[\(viewName)] didTapEditButton()")
		
		if let model = mvi.model as? Receive.ModelGenerated {
			withAnimation {
				sheet = ReceiveViewSheet.editing(model: model)
			}
		}
	}
	
	func lastIncomingPaymentChanged(_ lastIncomingPayment: Lightning_kmpIncomingPayment) {
		log.trace("[\(viewName)] lastIncomingPaymentChanged()")
		
		guard let model = mvi.model as? Receive.ModelGenerated else {
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
		
		isSwapInEnabled = context.swapIn.v1.status is WalletContext.V0ServiceStatusActive
		isPayToOpenEnabled = context.payToOpen.v1.status is WalletContext.V0ServiceStatusActive
	}
	
	func didTapSwapInButton() -> Void {
		log.trace("[\(viewName)] didTapSwapInButton()")
		
		if isSwapInEnabled {
			
			mvi.intent(Receive.IntentRequestSwapIn())
			
		} else {
			
			popoverState.display.send(PopoverItem(
				
				SwapInDisabledPopover().anyView,
				dismissable: true
			))
		}
	}
}

struct ModifyInvoiceSheet: View, ViewName {

	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	let dismissSheet: () -> Void

	let initialAmount: Lightning_kmpMilliSatoshi?
	
	@State var unit: CurrencyUnit = CurrencyUnit(bitcoinUnit: BitcoinUnit.sat)
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var altAmount: String = ""
	@State var isInvalidAmount: Bool = false
	@State var isEmptyAmount: Bool = false
	
	@State var desc: String
	
	@EnvironmentObject private var currencyPrefs: CurrencyPrefs
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			unit: $unit,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}

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
					.padding([.top, .bottom], 8)
					.padding(.leading, 16)
					.padding(.trailing, 0)

				Picker(
					selection: $unit,
					label: Text(unit.abbrev).frame(minWidth: 40, alignment: Alignment.trailing)
				) {
					let options = CurrencyUnit.displayable(currencyPrefs: currencyPrefs)
					ForEach(0 ..< options.count) {
						let option = options[$0]
						Text(option.abbrev).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
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

			Spacer()
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
		
		// Regardless of whether or not the invoice currently has an amount,
		// we should default to the user's preferred currency.
		// In other words, we should default to fiat, if that's what the user prefers.
		//
		if currencyPrefs.currencyType == .fiat, let exchangeRate = currencyPrefs.fiatExchangeRate() {
			
			let fiatCurrency = currencyPrefs.fiatCurrency
			unit = CurrencyUnit(fiatCurrency: fiatCurrency)
			
			if let msat = msat {
				let targetAmt = Utils.convertToFiat(msat: msat, exchangeRate: exchangeRate)
				parsedAmount = Result.success(targetAmt)
				
				let formattedAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
				amount = formattedAmt.digits
			} else {
				refreshAltAmount()
			}
			
		} else {
			
			let bitcoinUnit = currencyPrefs.bitcoinUnit
			unit = CurrencyUnit(bitcoinUnit: bitcoinUnit)
			
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
				altAmount = NSLocalizedString("Any amount", comment: "error message")
			case .invalidInput:
				altAmount = NSLocalizedString("Enter a valid amount", comment: "error message")
			}
			
		case .success(let amt):
			isInvalidAmount = false
			isEmptyAmount = false
			
			if let bitcoinUnit = unit.bitcoinUnit {
				// amt    => bitcoinUnit
				// altAmt => fiatCurrency
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate() {
					
					let msat = Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit)
					altAmount = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate).string
					
				} else {
					// We don't know the exchange rate, so we can't display fiat value.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
				
			} else if let fiatCurrency = unit.fiatCurrency {
				// amt    => fiatCurrency
				// altAmt => bitcoinUnit
				
				if let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency) {
					
					let msat = Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate)
					altAmount = Utils.formatBitcoin(msat: msat, bitcoinUnit: currencyPrefs.bitcoinUnit).string
					
				} else {
					// We don't know the exchange rate !
					// We shouldn't get into this state since CurrencyUnit.displayable() already filters for this.
					altAmount = "?.?? \(currencyPrefs.fiatCurrency.shortName)"
				}
			}
		}
	}
	
	func didTapSaveButton() -> Void {
		log.trace("[\(viewName)] didTapSaveButton()")
		
		let trimmedDesc = desc.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
		
		if let amt = try? parsedAmount.get(), amt > 0 {
			
			if let bitcoinUnit = unit.bitcoinUnit {
				
				let msat = Lightning_kmpMilliSatoshi(msat: Utils.toMsat(from: amt, bitcoinUnit: bitcoinUnit))
				mvi.intent(Receive.IntentAsk(
					amount: msat,
					desc: trimmedDesc
				))
				
			} else if let fiatCurrency = unit.fiatCurrency,
			          let exchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: fiatCurrency)
			{
				let msat = Lightning_kmpMilliSatoshi(msat: Utils.toMsat(fromFiat: amt, exchangeRate: exchangeRate))
				mvi.intent(Receive.IntentAsk(
					amount: msat,
					desc: trimmedDesc
				))
			}
			
		} else {
			
			mvi.intent(Receive.IntentAsk(
				amount: nil,
				desc: trimmedDesc
			))
		}
		
		dismissSheet()
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
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
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
					popoverState.close.send()
				} label : {
					Text("Settings").font(.title3)
				}
				.padding(.trailing, 20)
				
				Button {
					popoverState.close.send()
				} label : {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			popoverState.close.send()
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
		.onReceive(popoverState.close) {
			willClose()
		}
	}
	
	func didDeny() -> Void {
		log.trace("[\(viewName)] didDeny()")
		
		callback(.denied)
		userIsIgnoringPopover = false
		popoverState.close.send()
	}
	
	func didAccept() -> Void {
		log.trace("[\(viewName)] didAccept()")
		
		callback(.accepted)
		userIsIgnoringPopover = false
		popoverState.close.send()
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
					popoverState.close.send()
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
	
	@StateObject var qrCode = QRCode()
	
	@State var sheet: ReceiveViewSheet? = nil
	
	@State var swapIn_feePercent: Double = 0.0
	@State var swapIn_minFeeSat: Int64 = 0
	@State var swapIn_minFundingSat: Int64 = 0
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
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
					Text("…")
				}
			}
			.padding([.leading, .trailing], 40)
			.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				
				ReceiveView.copyButton {
					didTapCopyButton()
				}
				.disabled(!(mvi.model is Receive.ModelSwapInGenerated))
				
				ReceiveView.shareButton {
					didTapShareButton()
				}
				.disabled(!(mvi.model is Receive.ModelSwapInGenerated))
				
			} // </HStack>
			
			feesInfoView
				.padding([.top, .leading, .trailing])
			
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
		
		if let m = mvi.model as? Receive.ModelSwapInGenerated,
			qrCode.value == m.address,
			let qrCodeCgImage = qrCode.cgImage,
			let qrCodeImage = qrCode.image
		{
			qrCodeImage
				.resizable()
				.contextMenu {
					Button(action: {
						let uiImg = UIImage(cgImage: qrCodeCgImage)
						UIPasteboard.general.image = uiImg
						toast.pop(
							Text("Copied QR code image to pasteboard!").anyView,
							colorScheme: colorScheme.opposite
						)
					}) {
						Text("Copy")
					}
					Button(action: {
						let uiImg = UIImage(cgImage: qrCodeCgImage)
						sheet = ReceiveViewSheet.sharingImg(img: uiImg)
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
					if mvi.model is Receive.ModelSwapInRequesting {
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
		
		if let m = mvi.model as? Receive.ModelSwapInGenerated {
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
		
		if let m = model as? Receive.ModelSwapInGenerated {
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
	
	func didTapCopyButton() -> Void {
		log.trace("[\(viewName)] didTapCopyButton()")
		
		if let m = mvi.model as? Receive.ModelSwapInGenerated {
			UIPasteboard.general.string = m.address
			toast.pop(
				Text("Copied to pasteboard!").anyView,
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func didTapShareButton() -> Void {
		log.trace("[\(viewName)] didTapShareButton()")
		
		if let m = mvi.model as? Receive.ModelSwapInGenerated {
			let url = "bitcoin:\(m.address)"
			sheet = ReceiveViewSheet.sharingUrl(url: url)
		}
	}
}

// MARK:-

class ReceiveView_Previews: PreviewProvider {

	static let request = "lntb17u1p0475jgpp5f69ep0f2202rqegjeddjxa3mdre6ke6kchzhzrn4rxzhyqakzqwqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp2xqrrss9qy9qsqsp5nhhdgpz3549mll70udxldkg48s36cj05epp2cjjv3rrvs5hptdfqlq6h3tkkaplq4au9tx2k49tcp3gx7azehseq68jums4p0gt6aeu3gprw3r7ewzl42luhc3gyexaq37h3d73wejr70nvcw036cde4ptgpckmmkm"

	static var previews: some View {

		NavigationView {
			ReceiveView().mock(Receive.ModelAwaiting())
		}
		.modifier(GlobalEnvironment())
		.previewDevice("iPhone 11")

		NavigationView {
			ReceiveView().mock(Receive.ModelGenerated(
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
