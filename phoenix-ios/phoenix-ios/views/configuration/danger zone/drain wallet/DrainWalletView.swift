import SwiftUI
import PhoenixShared

fileprivate let filename = "DrainWalletView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DrainWalletView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	let popTo: (PopToDestination) -> Void
	let encryptedNodeId = Biz.encryptedNodeId!
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@State var textFieldValue: String = ""
	@State var scannedValue: String? = nil
	@State var parsedBitcoinAddress: String? = nil
	@State var detailedErrorMsg: String? = nil
	
	@State var isScanningQrCode = false
	
	@State var reviewRequested = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: reviewScreen(),
					isActive: $reviewRequested
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			content()
		}
		.onAppear {
			onAppear()
		}
		.navigationStackDestination(isPresented: $reviewRequested) { // For iOS 16+
			reviewScreen()
		}
		.navigationTitle(NSLocalizedString("Drain wallet", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
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
				//
				section_options()
				section_button()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelChannelsClosed {
				section_channelsClosed()
			}
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
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
	func section_channelsClosed() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				Image(systemName: "checkmark.circle.fill").foregroundColor(.appPositive)
				Text("Channels closed")
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
	func section_options() -> some View {
		
		Section() {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Text("Send all funds to a Bitcoin wallet.")
					.foregroundColor(.primary)
					.fixedSize(horizontal: false, vertical: true)
				
				//  [(TextField:BtcAddr) (Button::X)] [Button::ScanQrCode]
				HStack(alignment: VerticalAlignment.center, spacing: 10) {
					
					// [TextField::BtcAddr (Button::X)]
					HStack(alignment: VerticalAlignment.center, spacing: 2) {
						
						// TextField::BtcAddr
						TextField(
							NSLocalizedString("Bitcoin address", comment: "TextField placeholder"),
							text: $textFieldValue
						)
						.onChange(of: textFieldValue) { _ in
							checkBitcoinAddress()
						}
						
						// Button::X
						if !textFieldValue.isEmpty {
							Button {
								clearTextField()
							} label: {
								Image(systemName: "multiply.circle.fill")
									.foregroundColor(.secondary)
							}
							.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
							.accessibilityLabel("Clear textfield")
						}
						
					} // </HStack>
					.padding(.all, 8)
					.overlay(
						RoundedRectangle(cornerRadius: 8)
							.stroke(Color.textFieldBorder, lineWidth: 1)
					)
					
					// [Button::ScanQrCode]
					Button {
						didTapScanQrCodeButton()
					} label: {
						Image(systemName: "qrcode.viewfinder")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
					
				} // </HStack>
				
				if let detailedErrorMsg = detailedErrorMsg {
					Text(detailedErrorMsg)
						.foregroundColor(Color.appNegative)
				}
				
				Text("All payment channels will be closed.")
					.font(.footnote)
					.foregroundColor(.secondary)
				
			} // </VStack>
			
		} // </Section>
		.sheet(isPresented: $isScanningQrCode) {
			
			ScanBitcoinAddressSheet(didScanQrCode: self.didScanQrCode)
		}
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 5) {
					
				let missingBitcoinAddress = parsedBitcoinAddress == nil
				let invalidBitcoinAddress = missingBitcoinAddress && !textFieldValue.isEmpty
					
				Button {
					reviewButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Text("Review")
						Image(systemName: "arrow.forward")
							.imageScale(.small)
					}
				}
				.disabled(missingBitcoinAddress || invalidBitcoinAddress)
				.font(.title3.weight(.medium))
					
				if invalidBitcoinAddress {
					Text("Invalid bitcoin address")
						.font(.callout)
						.foregroundColor(.appNegative)
				}
					
			} // </VStack>
			.frame(maxWidth: .infinity)
		}
	}
	
	@ViewBuilder
	func reviewScreen() -> some View {
		
		if let bitcoinAddress = parsedBitcoinAddress {
			DrainWalletView_Confirm(
				mvi: mvi,
				bitcoinAddress: bitcoinAddress,
				popTo: popToWrapper
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func formattedBalances() -> (FormattedAmount, FormattedAmount?) {
		
		let balance_sats = mvi.balanceSats()
		
		let balance_bitcoin = Utils.formatBitcoin(sat: balance_sats, bitcoinUnit: currencyPrefs.bitcoinUnit)
		var balance_fiat: FormattedAmount? = nil
		if let exchangeRate = currencyPrefs.fiatExchangeRate() {
			balance_fiat = Utils.formatFiat(sat: balance_sats, exchangeRate: exchangeRate)
		}
		
		return (balance_bitcoin, balance_fiat)
	}
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		popToDestination = destination
		popTo(destination)
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
			
		} else {
			
			if popToDestination != nil {
				popToDestination = nil
				presentationMode.wrappedValue.dismiss()
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
		
		let business = Biz.business
		let result = Parser.shared.readBitcoinAddress(chain: business.chain, input: textFieldValue)
		
		if let error = result.left {
			
			log.debug("result.error = \(error)")
			
			if isScannedValue {
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
			
			let bitcoinUri = result.right!
			log.debug("result.info = \(bitcoinUri)")
			
			// Check to make sure the bitcoin address is for the correct chain.
			let parsedChain = bitcoinUri.chain
			let expectedChain = Biz.business.chain
			
			if parsedChain != expectedChain {
				
				detailedErrorMsg = String(format: NSLocalizedString(
					"""
					The address is for %@, but you're on %@
					""",
					comment: "Error message - parsing bitcoin address"),
					parsedChain.name, expectedChain.name
				)
				parsedBitcoinAddress = nil
				
			} else { // looks good
				
				parsedBitcoinAddress = bitcoinUri.address
				detailedErrorMsg = nil
			}
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
	
	func reviewButtonTapped() {
		log.trace("reviewButtonTapped()")
		reviewRequested = true
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

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
