import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloseWalletView_Options"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct CloseWalletView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.closeChannelsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	let popToRoot: () -> Void
	let encryptedNodeId = Biz.encryptedNodeId!
	
	@State var didAppear = false
	
	@State var drainWallet: Bool = true
	@State var deleteLocalData: Bool = false
	@State var deleteSeedBackup: Bool = false
	@State var deleteTransactionHistory: Bool = false
	
	@State var textFieldValue: String = ""
	@State var scannedValue: String? = nil
	@State var parsedBitcoinAddress: String? = nil
	@State var detailedErrorMsg: String? = nil
	
	@State var isScanningQrCode = false
	
	@State var reviewRequested = false
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	let backupTransactions_enabled_publisher = Prefs.shared.backupTransactions.isEnabledPublisher
	@State var backupTransactions_enabled = Prefs.shared.backupTransactions.isEnabled
	
	let backupSeed_enabled_publisher = Prefs.shared.backupSeed.isEnabled_publisher
	@State var backupSeed_enabled = Prefs.shared.backupSeed.isEnabled
	
	let manualBackup_taskDone_publisher = Prefs.shared.backupSeed.manualBackup_taskDone_publisher
	@State var manualBackup_taskDone = Prefs.shared.backupSeed.manualBackup_taskDone(
		encryptedNodeId: Biz.encryptedNodeId!
	)
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {

		ZStack {
			NavigationLink(
				destination: reviewScreen(),
				isActive: $reviewRequested
			) {
				EmptyView()
			}
			.accessibilityHidden(true)
			
			content()
		}
		.onAppear {
			onAppear()
		}
		.onReceive(backupTransactions_enabled_publisher) {
			self.backupTransactions_enabled = $0
		}
		.onReceive(backupSeed_enabled_publisher) {
			self.backupSeed_enabled = $0
		}
		.onReceive(manualBackup_taskDone_publisher) {
			self.manualBackup_taskDone =
				Prefs.shared.backupSeed.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
		}
		.navigationTitle(NSLocalizedString("Close wallet", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			if mvi.model is CloseChannelsConfiguration.ModelLoading {
				section_loading()
				
			} else if mvi.model is CloseChannelsConfiguration.ModelReady {
				section_balance()
				section_option1()
				section_option2()
				
				// What if the balance is zero ? Do we still display this ?
				// Yes, because the user could still have open channels (w/balance: zero local / non-zero remote),
				// and we want to allow them to close the channels if they desire.
			//	section_drain()
			}
			else if mvi.model is CloseChannelsConfiguration.ModelChannelsClosed {
			//	section_sent()
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
	func section_option1() -> some View {
		
		let optionEnabled = drainWallet
		
		Section(header: Text("Option #1")) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Toggle(isOn: drainWalletBinding()) {
					Text("Drain wallet")
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: optionOnImage(),
					offImage: optionOffImage()
				))
				
				Label {
					section_option1_bitcoinAddress(optionEnabled)
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				section_option1_nextButton(optionEnabled)
				
			} // </VStack>
			.padding(.vertical, 10)
			
		} // </Section>
		.sheet(isPresented: $isScanningQrCode) {
			
			ScanBitcoinAddressSheet(didScanQrCode: self.didScanQrCode)
		}
	}
	
	@ViewBuilder
	func section_option1_bitcoinAddress(_ optionEnabled: Bool) -> some View {
		
		let (_, dynamicSecondary) = dynamicColors(optionEnabled)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text("Send all funds to a Bitcoin wallet.")
				.font(.subheadline)
				.foregroundColor(dynamicSecondary)
				.fixedSize(horizontal: false, vertical: true)
							
			HStack(alignment: VerticalAlignment.center, spacing: 10) {
				
				// [Bitcoin Address TextField (X)]
				HStack(alignment: VerticalAlignment.center, spacing: 2) {
					TextField(
						NSLocalizedString("Bitcoin address", comment: "TextField placeholder"),
						text: $textFieldValue
					)
					.onChange(of: textFieldValue) { _ in
						checkBitcoinAddress()
					}
					.disabled(!optionEnabled)
					
					// Clear TextField button
					if !textFieldValue.isEmpty && optionEnabled {
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
				
				// [Scan QRCode Button]
				Button {
					didTapScanQrCodeButton()
				} label: {
					Image(systemName: "qrcode.viewfinder")
						.resizable()
						.frame(width: 30, height: 30)
				}
				.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				.disabled(!optionEnabled)
				
			} // </HStack>
			
			if let detailedErrorMsg = detailedErrorMsg {
				Text(detailedErrorMsg)
					.foregroundColor(Color.appNegative)
			}
			
			Text("All payment channels will be closed.")
				.font(.footnote)
				.foregroundColor(dynamicSecondary)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_option1_nextButton(_ optionEnabled: Bool) -> some View {
		
		let (_, dynamicSecondary) = dynamicColors(optionEnabled)
		
		VStack(alignment: HorizontalAlignment.center, spacing: 5) {
				
			let missingBitcoinAddress = drainWallet && (parsedBitcoinAddress == nil)
			let invalidBitcoinAddress = missingBitcoinAddress && !textFieldValue.isEmpty
				
			Button {
				option1_nextButtonTapped()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 5) {
					Text("Review")
					Image(systemName: "arrow.forward")
						.imageScale(.small)
				}
			}
			.disabled(!optionEnabled || missingBitcoinAddress || invalidBitcoinAddress)
			.font(.title3.weight(.medium))
				
			if invalidBitcoinAddress {
				Text("Invalid bitcoin address")
					.font(.callout)
					.foregroundColor(optionEnabled ? .appNegative : dynamicSecondary)
			}
				
		} // </VStack>
		.frame(maxWidth: .infinity)
	}
	
	@ViewBuilder
	func section_option2() -> some View {
		
		let optionEnabled = deleteLocalData
		let (dynamicPrimary, dynamicSecondary) = dynamicColors(optionEnabled)
		
		Section(header: Text("Option #2")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Toggle(isOn: deleteLocalDataBinding()) {
					Text("Delete all wallet data from this device.")
				}
				.toggleStyle(CheckboxToggleStyle(
					onImage: optionOnImage(),
					offImage: optionOffImage()
				))
			
				Label {
					Text("This will reset the app, as if you had just installed it.")
						.lineLimit(nil)
						.font(.subheadline)
						.foregroundColor(dynamicSecondary)
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Label {
					Text("iCloud")
				} icon: {
					Image(systemName: "icloud")
				}
				.foregroundColor(dynamicPrimary)
				
				Label {
					section_option2_iCloud(optionEnabled)
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Label {
					Text("Other")
				} icon: {
					Image(systemName: "server.rack")
				}
				.foregroundColor(dynamicPrimary)
				
				Label {
					section_option2_other(optionEnabled)
				} icon: {
					invisibleImage()
				}
				
			} // </VStack>
			.padding(.vertical, 5)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				section_option2_nextButton(optionEnabled)
				
			} // </VStack>
			.padding(.vertical, 10)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_option2_iCloud(_ optionEnabled: Bool) -> some View {
		
		let (dynamicPrimary, dynamicSecondary) = dynamicColors(optionEnabled)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				if !optionEnabled || !backupSeed_enabled {
					
					Label {
						Text("Delete seed backup from my iCloud account.")
							.foregroundColor(dynamicSecondary)
							.fixedSize(horizontal: false, vertical: true)
					} icon: {
						checkboxDisabledImage()
							.foregroundColor(dynamicSecondary)
					}
					
					if !backupSeed_enabled {
						Label {
							Text("Seed backup not stored in iCloud.")
								.font(.footnote)
								.foregroundColor(dynamicPrimary) // Stands out to provide explanation
						} icon: {
							invisibleImage()
						}
					}
					
				} else {
					
					Toggle(isOn: $deleteSeedBackup) {
						Text("Delete seed backup from my iCloud account.")
							.foregroundColor(dynamicPrimary)
					}
					.toggleStyle(CheckboxToggleStyle(
						onImage: checkboxOnImage(),
						offImage: checkboxOffImage()
					))
				}
				
			} // </VStack>
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				if !optionEnabled || !backupTransactions_enabled {
					
					Label {
						Text("Delete payment history from my iCloud account.")
							.lineLimit(nil)
							.foregroundColor(dynamicSecondary)
					} icon: {
						checkboxDisabledImage()
							.foregroundColor(dynamicSecondary)
					}
					
					if !backupTransactions_enabled {
						Label {
							Text("Payment history not stored in iCloud.")
								.font(.footnote)
								.foregroundColor(dynamicPrimary) // Stands out to provide explanation
						} icon: {
							invisibleImage()
						}
					}
					
				} else {
					
					Toggle(isOn: $deleteTransactionHistory) {
						Text("Delete payment history from my iCloud account.")
							.foregroundColor(dynamicPrimary)
					}
					.toggleStyle(CheckboxToggleStyle(
						onImage: checkboxOnImage(),
						offImage: checkboxOffImage()
					))
				}
				
			} // </VStack>
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_option2_other(_ optionEnabled: Bool) -> some View {
		
		let (_, dynamicSecondary) = dynamicColors(optionEnabled)
		
		Label {
			Text("We do not store any user-identifying information on our servers.")
		} icon: {
			Image(systemName: "face.smiling")
				.imageScale(.large)
		}
		.foregroundColor(dynamicSecondary)
	}
	
	@ViewBuilder
	func section_option2_nextButton(_ optionEnabled: Bool) -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 10) {
				
				Button {
					option2_nextButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Text("Review")
						Image(systemName: "arrow.forward")
							.imageScale(.small)
					}
				}
				.disabled(!optionEnabled)
				.font(.title3.weight(.medium))
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func optionOnImage() -> some View {
		Image(systemName: "record.circle")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func optionOffImage() -> some View {
		Image(systemName: "circle")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func checkboxOnImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func checkboxOffImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func checkboxDisabledImage() -> some View {
		Image(systemName: "square.dashed")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
	}
	
	@ViewBuilder
	func reviewScreen() -> some View {
		
		if drainWallet {
			if let bitcoinAddress = parsedBitcoinAddress {
				CloseWalletView_Drain(
					mvi: mvi,
					bitcoinAddress: bitcoinAddress,
					popToRoot: popToRoot
				)
			}
		} else {
			CloseWalletView_Delete(
				mvi: mvi,
				deleteTransactionHistory: deleteTransactionHistory,
				deleteSeedBackup: deleteSeedBackup
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
	
	func drainWalletBinding() -> Binding<Bool> {
		
		return Binding<Bool> {
			return drainWallet
		} set: { newValue in
			if newValue == true {
				drainWallet = true
				deleteLocalData = false
			} else {
				// Ignore
			}
		}
	}
	
	func deleteLocalDataBinding() -> Binding<Bool> {
		
		return Binding<Bool> {
			return deleteLocalData
		} set: { newValue in
			if newValue == true {
				deleteLocalData = true
				drainWallet = false
			} else {
				// Ignore
			}
		}
	}
	
	func dynamicColors(_ optionEnabled: Bool) -> (Color, Color) {
		
		let dynamicPrimary = optionEnabled ? Color.primary : Color.secondary
		let dynamicSecondary = optionEnabled ? Color.secondary : Color(UIColor.tertiaryLabel)
		
		return (dynamicPrimary, dynamicSecondary)
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
		
		let business = Biz.business
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
	
	func option1_nextButtonTapped() {
		log.trace("option1_nextButtonTapped()")
		reviewRequested = true
	}
	
	func option2_nextButtonTapped() {
		log.trace("option2_nextButtonTapped()")
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

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

extension MVIState where Model: CloseChannelsConfiguration.Model, Intent: CloseChannelsConfiguration.Intent {
	
	func channels() -> [CloseChannelsConfiguration.ModelChannelInfo]? {
		
		if let model = self.model as? CloseChannelsConfiguration.ModelReady {
			return model.channels
		} else if let model = self.model as? CloseChannelsConfiguration.ModelChannelsClosed {
			return model.channels
		} else {
			return nil
		}
	}
	
	func balanceSats() -> Int64 {
		
		// Note that there's a subtle difference between
		// - global balance => sum of local millisatoshi amount in each open channel
		// - closing balance => some of local satoshi amount in each open channel
		//
		// When closing a channel, the extra millisatoshi amount gets truncated.
		// For this reason, there could be a small difference.
		
		if let channels = channels() {
			return channels.map { $0.balance }.reduce(0, +)
		} else {
			return 0
		}
	}
}
