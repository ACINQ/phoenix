import SwiftUI
import PhoenixShared

fileprivate let filename = "SwapInView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

enum SwapInAddressType: CustomStringConvertible{
	case taproot
	case legacy
	
	var description: String {
		switch self {
			case .taproot : return "taproot"
			case .legacy  : return "legacy"
		}
	}
}

struct SwapInView: View {
	
	enum ReceiveViewSheet {
		case sharingUrl(url: String)
		case sharingImg(img: UIImage)
	}
	
	@ObservedObject var toast: Toast
	
	@State var swapInAddress: String? = nil
	@State var swapInAddressType: SwapInAddressType = .taproot
	
	@StateObject var qrCode = QRCode()
	
	@State var activeSheet: ReceiveViewSheet? = nil
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	@State var swapInAddressInfo: Lightning_kmpSwapInWallet.SwapInAddressInfo? = nil
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var smartModalState: SmartModalState
	
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
		
		contentWrapper()
			.navigationTitle(NSLocalizedString("Receive", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func contentWrapper() -> some View {
		
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
			
			Text("Bitcoin Address")
				.font(.title3)
				.foregroundColor(Color(UIColor.tertiaryLabel))
				.padding(.top)
			
			qrCodeWrapperView()
				.padding(.bottom)
			
			addressView()
				.padding([.leading, .trailing], 40)
				.padding(.bottom)
			
			HStack(alignment: VerticalAlignment.center, spacing: 30) {
				copyButton()
				shareButton()
				editButton()
			}
			.assignMaxPreference(for: maxButtonWidthReader.key, to: $maxButtonWidth)
			.padding(.bottom)
			
			migrationNotice()
			
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
		.onAppear {
			onAppear()
		}
		.task {
			for await wallet in Biz.business.balanceManager.swapInWalletSequence() {
				swapInWalletChanged(wallet)
			}
		}
		.task {
			guard let peer = Biz.business.peerManager.peerStateValue() else {
				return
			}
			for await info in peer.swapInWallet.swapInAddressSequence() {
				swapInAddressChanged(info)
			}
		}
		.onChange(of: swapInAddressType) {
			swapInAddressTypeChanged($0)
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
	}
	
	@ViewBuilder
	func qrCodeView() -> some View {
		
		if let address = swapInAddress,
		   let qrCodeValue = qrCode.value,
		   qrCodeValue.caseInsensitiveCompare(address) == .orderedSame,
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
					Text("Generating QRCode...")
				}
				.foregroundColor(Color(UIColor.darkGray))
				.font(.caption)
				.accessibilityElement(children: .combine)
			}
		}
	}
	
	@ViewBuilder
	func addressView() -> some View {
		
		if let btcAddr = swapInAddress {
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
		.disabled(swapInAddress == nil)
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
		.disabled(swapInAddress == nil)
		.simultaneousGesture(LongPressGesture().onEnded { _ in
			didLongPressShareButton()
		})
		.simultaneousGesture(TapGesture().onEnded {
			didTapShareButton()
		})
		.accessibilityAction(named: "Share Text (bitcoin address)") {
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
		.disabled(swapInAddress == nil)
	}
	
	@ViewBuilder
	func migrationNotice() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			Label {
				Text("Migration Notice")
			} icon: {
				Image(systemName: "info.circle").foregroundColor(.appAccent)
			}
			.font(.headline)
			
			Text("This is your new address. Do not reuse your old Bitcoin address from before migration.")
				.multilineTextAlignment(.center)
				.font(.callout)
		}
		.padding(.horizontal)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func updateSwapInAddress() {
		log.trace("updateSwapInAddress()")
		
		guard let keyManager = Biz.business.walletManager.keyManagerValue() else {
			return
		}
		
		let chain = Biz.business.chain
		let address: String
		switch swapInAddressType {
		case .taproot:
			let index = swapInAddressInfo?.index ?? Prefs.shared.swapInAddressIndex
			address = keyManager.swapInOnChainWallet
				.getSwapInProtocol(addressIndex: Int32(index))
				.address(chain: chain)
			
		case .legacy:
			address = keyManager.swapInOnChainWallet
				.legacySwapInProtocol
				.address(chain: chain)
		}
		
		if swapInAddress != address {
			swapInAddress = address
			
			// Issue #196: Use uppercase lettering for invoices and address QRs
			self.qrCode.generate(value: address.uppercased())
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		updateSwapInAddress()
	}
	
	func swapInWalletChanged(_ newWallet: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
		// If we detect a new incoming payment on the swap-in address,
		// then let's dismiss this sheet, and show the user the home screen.
		//
		// Because the home screen has the "+X sat incoming" message
		
		let oldBalance = swapInWallet.totalBalance.sat
		let newBalance = newWallet.totalBalance.sat
		
		swapInWallet = newWallet
		if newBalance > oldBalance {
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func swapInAddressChanged(_ newInfo: Lightning_kmpSwapInWallet.SwapInAddressInfo?) {
		log.trace("swapInAddressChanged()")
		
		self.swapInAddressInfo = newInfo
		updateSwapInAddress()
	}
	
	func swapInAddressTypeChanged(_ newType: SwapInAddressType) {
		log.trace("swapInAddressTypeChanged(new: \(newType)")
		
		updateSwapInAddress()
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
	
	func didTapEditButton() {
		log.trace("didTapEditButton()")
		
		smartModalState.display(dismissable: true) {
					
			BtcAddrOptionsSheet(swapInAddressType: $swapInAddressType)
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let address = swapInAddress {
			UIPasteboard.general.string = address
			toast.pop(
				NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("copyImageToPasteboard()")
		
		if let address = swapInAddress,
			let qrCodeValue = qrCode.value,
			qrCodeValue.caseInsensitiveCompare(address) == .orderedSame,
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
		
		if let address = swapInAddress {
			let url = "bitcoin:\(address)"
			activeSheet = ReceiveViewSheet.sharingUrl(url: url)
		}
	}
	
	func shareImageToSystem() {
		log.trace("shareImageToSystem()")
		
		if let address = swapInAddress,
			let qrCodeValue = qrCode.value,
			qrCodeValue.caseInsensitiveCompare(address) == .orderedSame,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			activeSheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
}
