import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloseChannelsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct CloseChannelsView : MVIView {
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })

	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@ViewBuilder
	var view: some View {

		main
			.padding(.top, 30)
			.padding([.leading, .trailing], 30)
			.padding(.bottom, 10)
			.navigationBarTitle("Close channels", displayMode: .inline)
	}
	
	@ViewBuilder
	var main: some View {
		
		if let model = mvi.model as? CloseChannelsConfiguration.ModelReady {
			if model.channels.count == 0 {
				EmptyWalletView()
			} else {
				StandardWalletView(model: model, postIntent: mvi.intent)
			}
		} else if let model = mvi.model as? CloseChannelsConfiguration.ModelChannelsClosed {
			FundsSentView(model: model)
		} else {
			LoadingWalletView()
		}
	}
}

fileprivate struct LoadingWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .center) {
		
			ProgressView()
				.progressViewStyle(CircularProgressViewStyle())
				.padding(.bottom, 5)
			
			Text("Checking channel state...")
			
			Spacer()
		}
	}
}

fileprivate struct EmptyWalletView : View {
	
	var body: some View {
		
		VStack(alignment: .leading) {
			
			Text("You currently don't have any channels that can be closed.")
				.padding(.bottom, 20)
			
			Text(styled: NSLocalizedString(
				"""
				Payment channels are automatically created when you receive payments. \
				Use the **Receive** screen to receive via the Lightning network.
				""",
				comment: "CloseChannelsView"
			))
			.padding(.bottom, 20)
			
			Text(styled: NSLocalizedString(
				"""
				You can also use the **Payment Channels** screen to inspect the state of your channels.
				""",
				comment: "CloseChannelsView"
			))

			Spacer()

			FooterView()
		}
	}
}

fileprivate struct StandardWalletView : View {

	let model: CloseChannelsConfiguration.ModelReady
	let postIntent: (CloseChannelsConfiguration.Intent) -> Void

	@State var textFieldValue: String = ""
	@State var scannedValue: String? = nil
	@State var parsedBitcoinAddress: String? = nil
	@State var detailedErrorMsg: String? = nil
	
	@State var isScanningQrCode = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {

		VStack(alignment: HorizontalAlignment.leading) {
			let totalSats = model.channels.map { $0.balance }.reduce(0, +)
			let formattedSats = Utils.formatBitcoin(sat: totalSats, bitcoinUnit: .sat)
			
			if model.channels.count == 1 {
				Text(
					"""
					You currenly have 1 Lightning channel \
					with a balance of \(formattedSats.string).
					"""
				)
			} else {
				Text(
					"""
					You currently have \(String(model.channels.count)) Lightning channels \
					with an aggragate balance of \(formattedSats.string).
					"""
				)
			}
			
			Text(
				"""
				Funds can be sent to a Bitcoin wallet. \
				Make sure the address is correct before sending.
				"""
			)
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
			
				// [Bitcoin Address TextField (X)]
				HStack {
					TextField("Bitcoin address", text: $textFieldValue)
						.onChange(of: textFieldValue) { _ in
							checkBitcoinAddress()
						}
					
					// Clear text field button
					Button {
						textFieldValue = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
					.isHidden(textFieldValue == "")
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(Capsule().stroke(Color(UIColor.separator)))
				
				// [Scan QRCode Button]
				Button {
					didTapScanQrCodeButton()
				} label: {
					Image(systemName: "qrcode.viewfinder")
						.resizable()
						.frame(width: 30, height: 30)
				}
			}
			.padding(.bottom, 10)
			
			if let detailedErrorMsg = detailedErrorMsg {
				Text(detailedErrorMsg)
					.foregroundColor(Color.appNegative)
			} else {
				Button {
					drainWallet()
				} label: {
					HStack {
						Image(systemName: "bitcoinsign.circle")
							.imageScale(.small)
						
						Text("Drain my wallet")
					}
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
				}
				.buttonStyle(
					ScaleButtonStyle(
						borderStroke: Color.appAccent,
						disabledBorderStroke: Color(UIColor.separator)
					)
				)
				.disabled(parsedBitcoinAddress == nil)
			}
			
			Spacer()
			
			FooterView()
		
		}// </VStack>
		.sheet(isPresented: $isScanningQrCode) {
			
			ScanBitcoinAddressSheet(didScanQrCode: self.didScanQrCode)
		}
	}
	
	func didTapScanQrCodeButton() -> Void {
		log.trace("didTapScanQrCodeButton()")
		
		scannedValue = nil
		isScanningQrCode = true
	}
	
	func didScanQrCode(result: String) -> Void {
		log.trace("didScanQrCode()")
		
		isScanningQrCode = false
		scannedValue = result
		textFieldValue = result
	}
	
	func checkBitcoinAddress() -> Void {
		log.trace("checkBitcoinAddress()")
		
		let isScannedValue = textFieldValue == scannedValue
		
		let business = AppDelegate.get().business
		let result = business.util.parseBitcoinAddress(input: textFieldValue)
		
		if let error = result.left {
			
			log.debug("result.error = \(error)")
			
			if let error = error as? Utilities.BitcoinAddressErrorChainMismatch {
				detailedErrorMsg = NSLocalizedString(
					"""
					The address is for \(error.addrChain.name), \
					but you're on \(error.myChain.name)
					""",
					comment: "Error message - parsing bitcoin address"
				)
			}
			else if error is Utilities.BitcoinAddressErrorUnknownBech32Version {
				detailedErrorMsg = NSLocalizedString(
					"Unknown Bech32 version",
					comment: "Error message - parsing bitcoin address"
				)
			}
			else if isScannedValue {
				// If the user scanned a non-bitcoin QRcode, we should notify them of the error
				detailedErrorMsg = NSLocalizedString(
					"The scanned QR code is not a bitcoin address",
					comment: "Error message - parsing bitcoin address"
				)
			}
			else {
				detailedErrorMsg = nil
			}
			
			parsedBitcoinAddress = nil
			
		} else {
			
			log.debug("result.info = \(result.right!)")
			
			parsedBitcoinAddress = result.right!.address
			detailedErrorMsg = nil
		}
		
		if !isScannedValue && scannedValue != nil {
			// The user has changed the textFieldValue,
			// so we're no longer dealing with the scanned value
			scannedValue = nil
		}
	}
	
	func drainWallet() -> Void {
		log.trace("drainWallet()")
		
		popoverState.display.send(PopoverItem(
		
			ConfirmationPopover(confirmAction: confirmDrainWallet).anyView,
			dismissable: false
		))
	}
	
	func confirmDrainWallet() -> Void {
		log.trace("confirmDrainWallet()")
		
		guard let bitcoinAddress = parsedBitcoinAddress else {
			return
		}
		
		postIntent(
			CloseChannelsConfiguration.IntentMutualCloseAllChannels(
				address: bitcoinAddress
			)
		)
	}
}

fileprivate struct FundsSentView : View {
	
	let model: CloseChannelsConfiguration.ModelChannelsClosed
	
	var body: some View {
		
		ScrollView {
			VStack {
				Image(systemName: "paperplane.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 64, height: 64)
					.foregroundColor(Color.appPositive)

				Text("Funds sent")
					.font(.title)
			}
			.padding(.bottom, 30)

			VStack(alignment: .leading) {

				if model.channels.count > 1 {
					Text("Expect to receive \(model.channels.count) separate payments.")
						.padding(.bottom, 10)
				}

				let msg = (model.channels.count <= 1)
					? NSLocalizedString(
						"""
						The closing transaction is in your transactions list on the __main__ screen. \
						And you can view the status of your channels in the __channels list__ screen.
						""",
						comment: "label text"
					)
					: NSLocalizedString(
						"""
						The closing transactions are in your transactions list on the __main__ screen. \
						And you can view the status of your channels in the __channels list__ screen.
						""",
						comment: "label text"
					)

				Text(msg)
					.lineLimit(nil) // text is getting truncated for some reason

			} // </VStack>
		}
	}
}

fileprivate struct FooterView : View {
	
	var body: some View {
		
		// The "send to bitcoin address" functionality isn't available in lightning-kmp yet.
		// When added, and integrated into Send screen, the code below should be uncommented.
		
	//	Text(styled: _NS_LocalizedString(
	//		"""
	//		Use this feature to transfer __all__ your funds to a Bitcoin address. \
	//		If you only want to send __some__ of your funds, then you can use the **Send** screen. \
	//		Just scan/enter a Bitcoin address and Phoenix does the rest.
	//		""",
	//		comment: "CloseChannelsView"
	//	))
	//	.font(.footnote)
	//	.foregroundColor(.secondary)
		
		Text(styled: NSLocalizedString(
			"Use this feature to transfer __all__ your funds to a Bitcoin address.",
			comment: "CloseChannelsView"
		))
		.font(.footnote)
		.foregroundColor(.secondary)
	}
}

fileprivate struct ConfirmationPopover : View {
	
	let confirmAction: () -> Void
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	var body: some View {
		
		VStack(alignment: .trailing) {
		
			VStack(alignment: .leading) {
				Text("Are you sure you want to proceed?")
			}
			.padding(.bottom, 20)
			
			HStack {
				Button {
					didTapCancel()
				} label : {
					Text("Cancel")
				}
				.padding(.trailing, 10)
				
				Button {
					didTapConfirm()
				} label : {
					Text("Send Funds")
				}
			}
			
		} // </VStack>
		.padding()
	}
	
	func didTapCancel() -> Void {
		log.trace("cancel()")
		popoverState.close.send()
	}
	
	func didTapConfirm() -> Void {
		log.trace("confirm()")
		popoverState.close.send()
		confirmAction()
	}
}

fileprivate struct ScanBitcoinAddressSheet: View, ViewName {
	
	let didScanQrCode: (String) -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				Spacer()
					.frame(width: 30)
				
				Spacer() // Text should be centered
				Text("Scan Bitcoin Address")
					.font(.headline)
				Spacer() // Text should be centered
				
				Button {
					didTapClose()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
			
			} // </HStack>
			.padding([.leading, .trailing], 20)
			.padding([.top, .bottom], 8)
			
			QrCodeScannerView {(request: String) in
				didScanQrCode(request)
			}
		
		} // </VStack>
	}
	
	func didTapClose() -> Void {
		log.trace("[\(viewName)] didTapClose()")
		
		presentationMode.wrappedValue.dismiss()
	}
}

// MARK: -

class CloseChannelsView_Previews: PreviewProvider {

	static var previews: some View {
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelLoading()
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")

		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelReady(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.normal
					)
				], address: "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				])
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
		
		NavigationView {
			CloseChannelsView().mock(
				CloseChannelsConfiguration.ModelChannelsClosed(channels: [
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					),
					CloseChannelsConfiguration.ModelChannelInfo(
						id: Bitcoin_kmpByteVector32.random(),
						balance: 500_000,
						status: CloseChannelsConfiguration.ModelChannelInfoStatus.closing
					)
				])
			)
		}
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
	}
}
