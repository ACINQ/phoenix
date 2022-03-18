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
		log.trace("onModelChange()")
		
		if let m = model as? Receive.Model_SwapIn_Generated {
			log.debug("updating qr code...")
			qrCode.generate(value: m.address)
		}
	}
	
	func onIncomingSwapsChanged(_ incomingSwaps: [String: Lightning_kmpMilliSatoshi]) -> Void {
		log.trace("onIncomingSwapsChanged(): \(incomingSwaps)")
		
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
		log.trace("chainContextChanged()")
		
		swapIn_feePercent = context.swapIn.v1.feePercent * 100    // 0.01 => 1%
		swapIn_minFeeSat = context.payToOpen.v1.minFeeSat         // not yet segregated for swapIn - future work
		swapIn_minFundingSat = context.payToOpen.v1.minFundingSat // not yet segregated for swapIn - future work
	}
	
	func copyTextToPasteboard() -> Void {
		log.trace("copyTextToPasteboard()")
		
		if let m = mvi.model as? Receive.Model_SwapIn_Generated {
			UIPasteboard.general.string = m.address
			toast.pop(
				Text("Copied to pasteboard!").anyView,
				colorScheme: colorScheme.opposite
			)
		}
	}
	
	func copyImageToPasteboard() -> Void {
		log.trace("copyImageToPasteboard()")
		
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
			qrCode.value == m.address,
			let qrCodeCgImage = qrCode.cgImage
		{
			let uiImg = UIImage(cgImage: qrCodeCgImage)
			sheet = ReceiveViewSheet.sharingImg(img: uiImg)
		}
	}
	
	func didTapShareButton() {
		log.trace("didTapShareButton()")
		
		shareTextToSystem()
	}
	
	func didLongPressShareButton() {
		log.trace("didLongPressShareButton()")
		
		shortSheetState.display(dismissable: true) {
					
			ShareOptionsSheet(shareText: {
				shareTextToSystem()
			}, shareImage: {
				shareImageToSystem()
			})
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
}
