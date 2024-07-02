import SwiftUI
import PhoenixShared

fileprivate let filename = "LightningOfferView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LightningOfferView: View {
	
	@ObservedObject var inboundFeeState: InboundFeeState
	@ObservedObject var toast: Toast
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	@State var activeSheet: ReceiveViewSheet? = nil
	
	@State var isFullScreenQrcode = false
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@StateObject var qrCode = QRCode()
	
	// To workaround a bug in SwiftUI, we're using multiple namespaces for our animation.
	// In particular, animating the border around the qrcode doesn't work well.
	@Namespace private var qrCodeAnimation_inner
	@Namespace private var qrCodeAnimation_outer
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	@EnvironmentObject var smartModalState: SmartModalState
	
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
			
			Text("Lightning Offer")
				.font(.title3)
				.foregroundColor(Color(UIColor.tertiaryLabel))
				.padding(.top)
			
			qrCodeWrapperView()
				.padding(.bottom)
			
			addressView()
				.padding([.leading, .trailing], 40)
				.padding(.bottom)
			
			if let warning = inboundFeeState.calculateInboundFeeWarning(invoiceAmount: nil) {
				inboundFeeInfo(warning)
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				copyButton()
				shareButton()
			}
			.padding(.top)
			.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
			
			howToUseButton()
				.padding(.top)
			
			if notificationPermissions == .disabled {
				backgroundPaymentsDisabledWarning()
					.padding(.top)
			}
			
			Spacer()
			
		} // </VStack>
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
		.onReceive(NotificationsManager.shared.permissions) {
			notificationPermissionsChanged($0)
		}
		.task {
			await generateQrCode()
		}
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
				.accessibilityHint("Lightning offer")
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
	func addressView() -> some View {
		
		if let offerStr = qrCode.value {
			Text(offerStr)
				.font(.footnote)
				.multilineTextAlignment(.center)
				.lineLimit(2)
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
				.font(.footnote)
		}
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
		.padding(.horizontal)
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
		.disabled(qrCode.value == nil)
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
		.disabled(qrCode.value == nil)
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
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func notificationPermissionsChanged(_ newValue: NotificationPermissions) {
		log.trace("notificationPermissionsChanged()")
		
		notificationPermissions = newValue
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func generateQrCode() async {
		
		do {
			let offerData = try await Biz.business.nodeParamsManager.defaultOffer()
			
			let offerStr = offerData.defaultOffer.encode()
			qrCode.generate(value: offerStr)
			
		} catch {
			log.error("nodeParamsManager.defaultOffer(): error: \(error)")
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func showInboundFeeWarning(_ warning: InboundFeeWarning) {
		
		smartModalState.display(dismissable: true) {
			InboundFeeSheet(warning: warning)
		}
	}
	
	func didTapCopyButton() -> Void {
		log.trace("didTapCopyButton()")
		
		copyTextToPasteboard()
	}
	
	func didLongPressCopyButton() -> Void {
		log.trace("didLongPressCopyButton()")
		
		smartModalState.display(dismissable: true) {
			
			CopyOptionsSheet(
				textType: NSLocalizedString("(Lightning offer)", comment: "Type of text being copied"),
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
				textType: NSLocalizedString("(Lightning offer)", comment: "Type of text being copied"),
				shareText: { shareTextToSystem() },
				shareImage: { shareImageToSystem() }
			)
		}
	}
	
	func navigationToBackgroundPayments() {
		log.trace("navigateToBackgroundPayments()")
		
		deepLinkManager.broadcast(DeepLink.backgroundPayments)
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
