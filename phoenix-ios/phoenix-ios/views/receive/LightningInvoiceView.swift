import SwiftUI
import PhoenixShared

fileprivate let filename = "LightningInvoiceView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LightningInvoiceView: View {
	
	@ObservedObject var mvi: MVIState<Receive.Model, Receive.Intent>
	@ObservedObject var toast: Toast
	
	@Binding var didAppear: Bool
	@Binding var showSendView: Bool
	
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
	@State var activeSheet: ReceiveViewSheet? = nil
	
	@State var channels: [LocalChannelInfo] = []
	@State var liquidityPolicy: LiquidityPolicy = GroupPrefs.shared.liquidityPolicy
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var modificationAmount: CurrencyAmount? = nil
	@State var currencyConverterOpen = false
	
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var inboundFeeWarning: InboundFeeWarning? = nil
	
	@Environment(\.horizontalSizeClass) var horizontalSizeClass: UserInterfaceSizeClass?
	@Environment(\.verticalSizeClass) var verticalSizeClass: UserInterfaceSizeClass?
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var smartModalState: SmartModalState
	
	let lastIncomingPaymentPublisher = Biz.business.paymentsManager.lastIncomingPaymentPublisher()
	let channelsPublisher = Biz.business.peerManager.channelsPublisher()
	
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
		.onReceive(channelsPublisher) {
			channelsChanged($0)
		}
		.onReceive(GroupPrefs.shared.liquidityPolicyPublisher) {
			liquidityPolicyChanged($0)
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
			await fetchMempoolRecommendedFees()
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
		
		if verticalSizeClass == UserInterfaceSizeClass.compact {
			mainLandscape()
		} else {
			mainPortrait()
		}
	}
	
	@ViewBuilder
	func mainPortrait() -> some View {
		
		VStack {
			
			Text("Lightning Invoice")
				.font(.title3)
				.foregroundColor(Color(UIColor.tertiaryLabel))
				.padding(.top)
			
			qrCodeWrapperView()
			
			if let warning = inboundFeeWarning {
				inboundFeeInfo(warning)
			}
			
			VStack(alignment: .center) {
			
				invoiceAmountView()
					.font(.footnote)
					.foregroundColor(.secondary)
					.padding(.bottom, 2)
			
				Text(invoiceDescription())
					.lineLimit(1)
					.font(.footnote)
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
			
			scanWithdrawButton()
				.padding([.top, .bottom])
				.padding(.top, 5) // a little extra
			
			if notificationPermissions == .disabled {
				backgroundPaymentsDisabledWarning()
			}
			
			Spacer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func mainLandscape() -> some View {
		
		HStack {
			
			qrCodeWrapperView()
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
				
				invoiceAmountView()
					.font(.footnote)
					.foregroundColor(.secondary)
					.padding(.bottom, 6)
			
				Text(invoiceDescription())
					.lineLimit(1)
					.font(.footnote)
					.foregroundColor(.secondary)
				
				scanWithdrawButton()
					.padding(.top, 8)
				
				if notificationPermissions == .disabled {
					backgroundPaymentsDisabledWarning()
						.padding(.top, 8)
				}
				if let warning = inboundFeeWarning {
					inboundFeeInfo(warning)
						.padding(.top, 8)
				}
				
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
	func scanWithdrawButton() -> some View {
		
		Button {
			withAnimation {
				showSendView = true
			}
		} label: {
			Label {
				Text("Scan withdraw")
			} icon: {
				Image(systemName: "qrcode.viewfinder")
			}
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
		.padding(.top)
		.padding(.horizontal)
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
		
		refreshInboundFeeWarning()
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func modelChanged(_ model: Receive.Model) {
		log.trace("modelChanged()")
		
		if let m = model as? Receive.Model_Generated {
			log.debug("updating qr code...")
			
			// Issue #196: Use uppercase lettering for invoices and address QRs
			qrCode.generate(value: m.request.uppercased())
		}
		
		refreshInboundFeeWarning()
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
	
	func channelsChanged(_ channels: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		
		self.channels = channels
		refreshInboundFeeWarning()
	}
	
	func liquidityPolicyChanged(_ newValue: LiquidityPolicy) {
		log.trace("liquidityPolicyChanged()")
		
		self.liquidityPolicy = newValue
		refreshInboundFeeWarning()
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
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func refreshInboundFeeWarning() {
		log.trace("refreshInboundFeeWarning()")
		
		inboundFeeWarning = calculateInboundFeeWarning()
	}
	
	func calculateInboundFeeWarning() -> InboundFeeWarning? {

		let availableForReceiveMsat = channels.availableForReceive()?.msat ?? Int64(0)
		let hasNoLiquidity = availableForReceiveMsat == 0
		
		let canRequestLiquidity = channels.canRequestLiquidity()
		
		let invoiceAmountMsat = invoiceAmount()?.msat
		
		var liquidityIsShort = false
		if let invoiceAmountMsat {
			liquidityIsShort = invoiceAmountMsat >= availableForReceiveMsat
		}
		
		if hasNoLiquidity || liquidityIsShort {
			
			if !liquidityPolicy.enabled {
				
				return InboundFeeWarning.liquidityPolicyDisabled
				
			} else {
				
				let hasNoChannels = channels.filter { !$0.isTerminated }.isEmpty
				let swapFeeSats = mempoolRecommendedResponse?.payToOpenEstimationFee(
					amount: Lightning_kmpMilliSatoshi(msat: invoiceAmountMsat ?? 0),
					hasNoChannels: hasNoChannels
				).sat
				
				if let swapFeeSats {
					
					// Check absolute fee
					
					if swapFeeSats > liquidityPolicy.effectiveMaxFeeSats
						&& !liquidityPolicy.effectiveSkipAbsoluteFeeCheck
					{
						return InboundFeeWarning.overAbsoluteFee(
							canRequestLiquidity: canRequestLiquidity,
							maxAbsoluteFeeSats: liquidityPolicy.effectiveMaxFeeSats,
							swapFeeSats: swapFeeSats
						)
					}
					
					// Check relative fee
					
					if let invoiceAmountMsat, invoiceAmountMsat > availableForReceiveMsat {
						
						let swapFeeMsat = Utils.toMsat(sat: swapFeeSats)
						
						let maxFeePercent = Double(liquidityPolicy.effectiveMaxFeeBasisPoints) / Double(10_000)
						let maxFeeMsat = Int64(Double(invoiceAmountMsat) * maxFeePercent)
						
						if swapFeeMsat > maxFeeMsat {
							return InboundFeeWarning.overRelativeFee(
								canRequestLiquidity: canRequestLiquidity,
								maxRelativeFeePercent: maxFeePercent,
								swapFeeSats: swapFeeSats
							)
						}
					}
				}
				
				if let swapFeeSats {
					return InboundFeeWarning.feeExpected(swapFeeSats: swapFeeSats)
				} else {
					return InboundFeeWarning.unknownFeeExpected
				}
				
			} // </else: liquidityPolicy.enabled>
		} // </if hasNoLiquidity || liquidityIsShort>
		
		return nil
	}
	
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
				activeSheet = ReceiveViewSheet.sharingUrl(url: url)
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
			activeSheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
}
