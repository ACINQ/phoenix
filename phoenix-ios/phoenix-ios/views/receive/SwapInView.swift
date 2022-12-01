import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SwapInView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct SwapInView: View {
	
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
	
	let swapInWalletBalancePublisher = Biz.business.peerManager.swapInWalletBalancePublisher()
	@State var swapInWalletBalance: WalletBalance = WalletBalance.companion.empty()
	
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	
	// For the cicular buttons: [copy, share]
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
		
		GeometryReader { geometry in
			ScrollView(.vertical) {
				content()
					.frame(width: geometry.size.width)
					.frame(minHeight: geometry.size.height)
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack {
			
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
				.padding([.top, .bottom])
			
			addressView()
				.padding([.leading, .trailing], 40)
				.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				copyButton()
				shareButton()
			}
			.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
			
			feesInfoView()
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
			.padding(.vertical)
			
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
		.navigationTitle(NSLocalizedString("Swap In", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
		.onAppear {
			onAppear()
		}
		.onChange(of: mvi.model) { newModel in
			onModelChange(model: newModel)
		}
		.onReceive(swapInWalletBalancePublisher) {
			swapInWalletBalanceChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
	}
	
	@ViewBuilder
	func qrCodeView() -> some View {
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
		   let qrCodeValue = qrCode.value,
		   qrCodeValue.caseInsensitiveCompare(m.address) == .orderedSame,
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
				.accessibilityElement()
				.accessibilityAddTraits(.isImage)
				.accessibilityLabel("QR code")
				.accessibilityHint("Bitcoin address")
				.accessibilityAction(named: "Copy Image") {
					copyImageToPasteboard()
				}
				.accessibilityAction(named: "Share Image") {
					shareImageToSystem()
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
				.accessibilityElement(children: .combine)
			}
		}
	}
	
	@ViewBuilder
	func addressView() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			
			Text("Address")
				.foregroundColor(.secondary)
				.font(.subheadline)
			
			if let btcAddr = bitcoinAddress() {
				Text(btcAddr)
					.font(.footnote)
					.multilineTextAlignment(.center)
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
		
		} // </HStack>
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
		.disabled(!(mvi.model is Receive.Model_SwapIn_Generated))
		.simultaneousGesture(LongPressGesture().onEnded { _ in
			didLongPressCopyButton()
		})
		.simultaneousGesture(TapGesture().onEnded {
			didTapCopyButton()
		})
		.accessibilityAction(named: "Copy Text (bitcoin address)") {
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
		.disabled(!(mvi.model is Receive.Model_SwapIn_Generated))
		.simultaneousGesture(LongPressGesture().onEnded { _ in
			didLongPressShareButton()
		})
		.simultaneousGesture(TapGesture().onEnded {
			didTapShareButton()
		})
	}
	
	@ViewBuilder
	func feesInfoView() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 8) {
			
			Image(systemName: "info.circle")
				.imageScale(.large)
				.accessibilityHidden(true)
			
			let minFunding = Utils.formatBitcoin(sat: swapIn_minFundingSat, bitcoinUnit: .sat)
			
			let feePercent = formatFeePercent()
			let minFee = Utils.formatBitcoin(sat: swapIn_minFeeSat, bitcoinUnit: .sat)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Text(
					"""
					On-chain deposits sent to this address will be converted to Lightning channels.
					"""
				)
				.multilineTextAlignment(.leading)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				.padding(.bottom, 14)
				
				Text(styled: String(format: NSLocalizedString(
					"""
					Total deposits must be at least **%@**. The fee is **%@%%** (%@ minimum).
					""",
					comment:	"Minimum amount description."),
					minFunding.string, feePercent, minFee.string
				))
				.multilineTextAlignment(.leading)
				.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			}
		}
		.font(.subheadline)
		.padding()
		.background(
			RoundedRectangle(cornerRadius: 10)
				.foregroundColor(Color.mutedBackground)
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
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
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		// If the model updates before the view finishes drawing,
		// we might need to manually invoke onModelChange.
		//
		if mvi.model is Receive.Model_SwapIn_Generated {
			onModelChange(model: mvi.model)
		}
	}
	
	func onModelChange(model: Receive.Model) -> Void {
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_SwapIn_Generated {
			log.debug("updating qr code...")
			
			// Issue #196: Use uppercase lettering for invoices and address QRs
			qrCode.generate(value: m.address.uppercased())
		}
	}
	
	func swapInWalletBalanceChanged(_ walletBalance: WalletBalance) {
		log.trace("swapInWalletBalanceChanged()")
		
		// If we detect a new incoming payment on the swap-in address,
		// then let's dismiss this sheet, and show the user the home screen.
		//
		// Because the home screen has the "+X sat incoming" message
		
		let oldBalance = swapInWalletBalance.total.sat
		let newBalance = walletBalance.total.sat
		
		swapInWalletBalance = walletBalance
		if newBalance > oldBalance {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		swapIn_feePercent = context.swapIn.v1.feePercent * 100    // 0.01 => 1%
		swapIn_minFeeSat = context.payToOpen.v1.minFeeSat         // not yet segregated for swapIn - future work
		swapIn_minFundingSat = context.payToOpen.v1.minFundingSat // not yet segregated for swapIn - future work
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
				textType: NSLocalizedString("(Bitcoin address)", comment: "Type of text being copied"),
				copyText: {	copyTextToPasteboard() },
				copyImage: { copyImageToPasteboard() }
			)
		}
	}
	
	func didTapShareButton() {
		log.trace("didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() {
		log.trace("didLongPressShareButton()")
		
		smartModalState.display(dismissable: true) {
					
			ShareOptionsSheet(
				textType: NSLocalizedString("(Bitcoin address)", comment: "Type of text being copied"),
				shareText: { shareTextToSystem() },
				shareImage: { shareImageToSystem() }
			)
		}
	}
	
	func didTapLightningButton() {
		log.trace("didTapLightningButton()")
		
		mvi.intent(Receive.IntentAsk(
			amount: lastAmount,
			desc: lastDescription,
			expirySeconds: Int64(60 * 60 * 24 * Prefs.shared.invoiceExpirationDays)
		))
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			UIPasteboard.general.string = m.address
			toast.pop(
				NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("copyImageToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
			let qrCodeValue = qrCode.value,
			qrCodeValue.caseInsensitiveCompare(m.address) == .orderedSame,
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
	
	func shareTextToSystem() {
		log.trace("shareTextToSystem()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			let url = "bitcoin:\(m.address)"
			sheet = ReceiveViewSheet.sharingUrl(url: url)
		}
	}
	
	func shareImageToSystem() {
		log.trace("shareImageToSystem()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated,
			let qrCodeValue = qrCode.value,
			qrCodeValue.caseInsensitiveCompare(m.address) == .orderedSame,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			sheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
}
