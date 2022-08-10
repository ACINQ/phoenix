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
	
	@State var didAppear = false
	
	@State var textFieldValue: String = ""
	@State var scannedValue: String? = nil
	@State var parsedBitcoinAddress: String? = nil
	@State var detailedErrorMsg: String? = nil
	
	@State var isScanningQrCode = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		content()
			.onAppear {
				onAppear()
			}
			.navigationBarTitle(
				NSLocalizedString("Drain wallet", comment: "Navigation bar title"),
				displayMode: .inline
			)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if mvi.model is CloseChannelsConfiguration.ModelLoading {
				section_loading()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelReady {
				section_balance()
				
				// What if the balance is zero ? Do we still display this ?
				// Yes, because the user could still have open channels (w/balance: zero local / non-zero remote),
				// and we want to allow them to close the channels if they desire.
				section_drain()
			}
			else if mvi.model is CloseChannelsConfiguration.ModelChannelsClosed {
				section_sent()
			}
		}
		.listStyle(.insetGrouped)
	}
	
	@ViewBuilder
	func section_loading() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle())
				Text("Loading wallet...")
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_balance() -> some View {
		
		Section(header: Text("Wallet Balance")) {
			let (balance_bitcoin, balance_fiat) = formattedBalances()
			
			Group {
				if let balance_fiat = balance_fiat {
					Text(verbatim: balance_bitcoin.string).bold()
					+ Text(verbatim: " (≈ \(balance_fiat.string))")
						.foregroundColor(.secondary)
				} else {
					Text(verbatim: balance_bitcoin.string).bold()
				}
			} // </Group>
			.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs
			
		} // </Section>
	}
			
	@ViewBuilder
	func section_drain() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading) {
				Text(
					"""
					Funds can be sent to a Bitcoin wallet. \
					Make sure the address is correct before sending.
					"""
				)
				.fixedSize(horizontal: false, vertical: true)
				.padding(.top, 8)
				.padding(.bottom, 16)
				
				HStack(alignment: VerticalAlignment.center, spacing: 10) {
				
					// [Bitcoin Address TextField (X)]
					HStack {
						TextField(
							NSLocalizedString("Bitcoin address", comment: "TextField placeholder"),
							text: $textFieldValue
						)
						.onChange(of: textFieldValue) { _ in
							checkBitcoinAddress()
						}
						
						// Clear text field button
						Button {
							clearTextField()
						} label: {
							Image(systemName: "multiply.circle.fill")
								.foregroundColor(.secondary)
						}
						.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
						.isHidden(textFieldValue == "")
					}
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
					.background(Capsule().stroke(Color.textFieldBorder))
					
					// [Scan QRCode Button]
					Button {
						didTapScanQrCodeButton()
					} label: {
						Image(systemName: "qrcode.viewfinder")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				}
				.padding(.bottom, 10)
				
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
						cornerRadius: 100,
						borderStroke: Color.appAccent,
						disabledBorderStroke: Color(UIColor.separator)
					)
				)
				.disabled(parsedBitcoinAddress == nil)
				.padding(.bottom, 10)
				
				if let detailedErrorMsg = detailedErrorMsg {
					Text(detailedErrorMsg)
						.foregroundColor(Color.appNegative)
						.padding(.bottom, 10)
				}
				
				Text("All payment channels will be closed.")
					.font(.footnote)
					.foregroundColor(.secondary)
					.padding(.bottom, 10)
				
			} // </VStack>
		} // </Section>
		.sheet(isPresented: $isScanningQrCode) {
			
			ScanBitcoinAddressSheet(didScanQrCode: self.didScanQrCode)
		}
	}
	
	@ViewBuilder
	func section_sent() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Image(systemName: "paperplane.fill")
					.renderingMode(.template)
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 64, height: 64)
					.foregroundColor(Color.appPositive)
				
				Text("Funds sent")
					.font(.title)
					.padding(.bottom, 30)

				let expectedTxCount = nonZeroChannelsCount()
				let msg = (expectedTxCount > 1)
					? String(format: NSLocalizedString(
						"Expect to receive %d separate payments.",
						comment: "label text"
					), expectedTxCount)
					: NSLocalizedString(
						"The closing transaction is in your transactions list.",
						comment: "label text"
					)

				Text(styled: msg)
					.multilineTextAlignment(.center)
					.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs

			} // </VStack>
			.padding(.vertical, 8)
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	func channels() -> [CloseChannelsConfiguration.ModelChannelInfo]? {
		
		if let model = mvi.model as? CloseChannelsConfiguration.ModelReady {
			return model.channels
		} else if let model = mvi.model as? CloseChannelsConfiguration.ModelChannelsClosed {
			return model.channels
		} else {
			return nil
		}
	}
	
	func nonZeroChannelsCount() -> Int {
		
		if let channels = channels() {
			return channels.filter { $0.balance > 0 }.count
		} else {
			return 0
		}
	}
	
	func balanceSats() -> Int64 {
		
		// Todo: replace me with `business.peerManager.balance` (from commit d5f6fe8e)
		if let channels = channels() {
			return channels.map { $0.balance }.reduce(0, +)
		} else {
			return 0
		}
	}
	
	func formattedBalances() -> (FormattedAmount, FormattedAmount?) {
		
		// Todo: replace me with `business.peerManager.balance` (from commit d5f6fe8e)
		let balance_sats = balanceSats()
		
		let balance_bitcoin = Utils.formatBitcoin(sat: balance_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var balance_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			balance_fiat = Utils.formatFiat(sat: balance_sats, exchangeRate: exchangeRate)
		}
		
		return (balance_bitcoin, balance_fiat)
	}
	
	// --------------------------------------------------
	// MARK: View Lifecycle
	// --------------------------------------------------
	
	func onAppear(){
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink, deepLink == .drainWallet {
				// Reached our destination
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkManager.unbroadcast(deepLink)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
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
		let result = Parser.shared.readBitcoinAddress(chain: business.chain, input: textFieldValue)
		
		if let error = result.left {
			
			log.debug("result.error = \(error)")
			
			if let error = error as? BitcoinAddressError.ChainMismatch {
				detailedErrorMsg = String(format: NSLocalizedString(
					"""
					The address is for %@, \
					but you're on %@
					""",
					comment: "Error message - parsing bitcoin address"),
					error.addrChain.name, error.myChain.name
				)
			}
			else if error is BitcoinAddressError.UnknownBech32Version {
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
	
	func clearTextField() {
		log.trace("clearTextField()")
		
		textFieldValue = ""
	}
	
	func drainWallet() {
		log.trace("drainWallet()")
		
		popoverState.display(dismissable: false) {
			ConfirmationPopover(confirmAction: confirmDrainWallet)
		}
	}
	
	func confirmDrainWallet() -> Void {
		log.trace("confirmDrainWallet()")
		
		guard let bitcoinAddress = parsedBitcoinAddress else {
			log.trace("Ignoring: parsedBitcoinAddress is nil")
			return
		}
		
		mvi.intent(
			CloseChannelsConfiguration.IntentMutualCloseAllChannels(
				address: bitcoinAddress
			)
		)
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
		popoverState.close()
	}
	
	func didTapConfirm() -> Void {
		log.trace("confirm()")
		popoverState.close()
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
