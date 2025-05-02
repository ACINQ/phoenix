import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "SpendOnChainFunds"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SpendOnChainFunds: View {
	
	enum Source {
		case expiredSwapIns
		case finalWallet
	}
	let source: Source
	
	@State var wallet: Lightning_kmpWalletState.WalletWithConfirmations
	let walletPublisher: AnyPublisher<Lightning_kmpWalletState.WalletWithConfirmations, Never>
	
	@State var btcAddressInputResult: Result<BitcoinUri, BtcAddressInput.DetailedError> = .failure(.emptyInput)
	
	@State var minerFeeInfo: MinerFeeInfo? = nil
	@State var satsPerByte: String = ""
	@State var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError> = Result.failure(.emptyInput)
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var isSending: Bool = false
	@State var sentTxId: Bitcoin_kmpTxId? = nil
	@State var showBlockchainExplorerOptions = false
	
	enum MaxNumberWidth: Preference {}
	let maxNumberWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxNumberWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxNumberWidth: CGFloat? = nil
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(source: Source) {
		self.source = source
		
		let currentWallet: Lightning_kmpWalletState.WalletWithConfirmations
		switch source {
		case .expiredSwapIns:
			currentWallet = Biz.business.balanceManager.swapInWalletValue()
			walletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
		case .finalWallet:
			currentWallet = Biz.business.peerManager.finalWalletValue()
			walletPublisher = Biz.business.peerManager.finalWalletPublisher()
		}
		
		self._wallet = State(initialValue: currentWallet)
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(title)
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_balance()
			section_options()
			section_button()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(walletPublisher) {
			walletChanged($0)
		}
		.onChange(of: btcAddressInputResult) { _ in
			btcAddressInputResultChanged()
		}
		.task {
			await fetchMempoolRecommendedFees()
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		switch source {
		case .expiredSwapIns:
			section_info_expiredSwapIns()
			
		case .finalWallet:
			section_info_finalWallet()
		}
	}
	
	@ViewBuilder
	func section_info_expiredSwapIns() -> some View {
		
		Section {
			Text(
				"""
				Use this screen to spend your on-chain funds that were not swapped \
				in time. The swap-in request has now expired.
				"""
			)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
		}
	}
	
	@ViewBuilder
	func section_info_finalWallet() -> some View {
		
		Section {
			Text(
				"""
				Use this screen to spend funds from your final wallet. \
				These funds come from channels that have been closed in the past. \
				This does not affect your existing Lightning channels.
				"""
			)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
		}
	}
	
	@ViewBuilder
	func section_balance() -> some View {
		
		Section {
			let (balanceBtc, balanceFiat) = formattedBalances()
			
			Group {
				Text(verbatim: balanceBtc.string).bold()
				+ Text(verbatim: " (≈ \(balanceFiat.string))")
					.foregroundColor(.secondary)
			}
			.fixedSize(horizontal: false, vertical: true) // Workaround for SwiftUI bugs
			
		} header: {
			Text("Available")
		}
	}
	
	@ViewBuilder
	func section_options() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				
				Text("Send all funds to a Bitcoin wallet.")
					.foregroundColor(.primary)
					.fixedSize(horizontal: false, vertical: true)
				
				BtcAddressInput(result: $btcAddressInputResult)
				
				if case let .failure(reason) = btcAddressInputResult,
					let errorMessage = reason.localizedErrorMessage()
				{
					Text(errorMessage)
						.foregroundColor(Color.appNegative)
				}
				
				if let minerFeeInfo {
					subsection_summary(minerFeeInfo)
						.padding(.top)
				}
				
			} // </VStack>
			
		} header: {
			Text("Options")
		}
	}
	
	@ViewBuilder
	func subsection_summary(_ minerFeeInfo: MinerFeeInfo) -> some View {
		
		let labelColor   = Color.primary
		let bitcoinColor = Color.primary
		let fiatColor    = Color(UIColor.systemGray2)
		let percentColor = Color.secondary
		let dividerColor = Color.secondary
		
		HStack(alignment: VerticalAlignment.top, spacing: 8) {
			
			// ===== COLUMN 1 =====
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				Text("Miner fee:")
					.foregroundColor(labelColor)
				
				Text(verbatim: "")
					.padding(.bottom, 4)
					.accessibilityHidden(true)
				
				Divider()
					.frame(width: 0, height: 1)
				
				Text("Receive:")
					.foregroundColor(labelColor)
				
			} // </VStack>
			
			Spacer(minLength: 0)
			
			// ===== COLUMN 2 =====
			VStack(alignment: HorizontalAlignment.trailing, spacing: 8) {
				
				let (minerFeeBtc, minerFeeFiat) = formattedMinerFees(minerFeeInfo)
				
				Text(verbatim: minerFeeBtc.string)
					.foregroundColor(bitcoinColor)
					.read(maxNumberWidthReader)
				
				Text(verbatim: minerFeeFiat.string)
					.foregroundColor(fiatColor)
					.read(maxNumberWidthReader)
					.padding(.bottom, 4)
				
				Divider()
					.foregroundColor(dividerColor)
					.frame(width: maxNumberWidth ?? 0, height: 1)
				
				let (rcvBtc, rcvFiat) = formattedReceives(minerFeeInfo)
				
				Text(verbatim: rcvBtc.string)
					.foregroundColor(bitcoinColor)
					.read(maxNumberWidthReader)
				
				Text(verbatim: rcvFiat.string)
					.foregroundColor(fiatColor)
					.read(maxNumberWidthReader)
				
			} // </VStack>
			
			Spacer(minLength: 0)
			
			// ===== COLUMN 3 =====
			HStack(alignment: VerticalAlignment.top, spacing: 8) {
				Text(verbatim: minerFeePercent(minerFeeInfo))
					.foregroundColor(percentColor)
				Button {
					showMinerFeeSheet()
				} label: {
					Image(systemName: "square.and.pencil")
						.resizable()
						.scaledToFit()
						.frame(width: 30, height: 30, alignment: .trailing)
				}
			} // </HStack>
			
		} // </HStack>
		.assignMaxPreference(for: maxNumberWidthReader.key, to: $maxNumberWidth)
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 8) {
				
				if let txId = sentTxId {
					
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Image(systemName: "paperplane")
							.renderingMode(.template)
						Text("Sent")
					}
					.foregroundColor(.appPositive)
					.font(.title3.weight(.medium))
					
					Button {
						showBlockchainExplorerOptions = true
					} label: {
						Text("Explore")
					}
					.confirmationDialog("Blockchain Explorer",
						isPresented: $showBlockchainExplorerOptions,
						titleVisibility: .automatic
					) {
						Button {
							exploreTx(txId, website: BlockchainExplorer.WebsiteMempoolSpace())
						} label: {
							Text(verbatim: "Mempool.space") // no localization needed
						}
						Button {
							exploreTx(txId, website: BlockchainExplorer.WebsiteBlockstreamInfo())
						} label: {
							Text(verbatim: "Blockstream.info") // no localization needed
						}
					} // </confirmationDialog>
					
				} else if minerFeeInfo == nil {
					
					Button {
						prepareButtonTapped()
					} label: {
						HStack(alignment: VerticalAlignment.center, spacing: 5) {
							Image(systemName: "hammer")
								.renderingMode(.template)
							Text("Prepare Transaction")
						}
					}
					.disabled(btcAddressInputResult.isError)
					.font(.title3.weight(.medium))
					
				} else {
					
					Button {
						sendButtonTapped()
					} label: {
						HStack(alignment: VerticalAlignment.center, spacing: 5) {
							Image(systemName: "paperplane")
								.renderingMode(.template)
							Text("Send")
						}
					}
					.disabled(btcAddressInputResult.isError || minerFeeTooHigh() || isSending)
					.font(.title3.weight(.medium))
				}
					
			} // </VStack>
			.frame(maxWidth: .infinity)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var title: String {
		
		switch source {
			case .expiredSwapIns : return String(localized: "Spend expired swap-ins")
			case .finalWallet    : return String(localized: "Spend from final wallet")
		}
	}
	
	var spendableBalance: Bitcoin_kmpSatoshi {
		
		switch source {
		case .expiredSwapIns:
			return wallet.readyForRefundBalance
			
		case .finalWallet:
			return wallet.anyConfirmedBalance
		}
	}
	
	func formattedBalances() -> (FormattedAmount, FormattedAmount) {
		return formattedAmounts(spendableBalance.sat)
	}
	
	func formattedMinerFees(_ minerFeeInfo: MinerFeeInfo) -> (FormattedAmount, FormattedAmount) {
		return formattedAmounts(minerFeeInfo.minerFee.sat)
	}
	
	func formattedReceives(_ minerFeeInfo: MinerFeeInfo) -> (FormattedAmount, FormattedAmount) {
		let balance = spendableBalance.sat
		let minerFee = minerFeeInfo.minerFee.sat
		let diff = (balance > minerFee) ? (balance - minerFee) : 0
		return formattedAmounts(diff)
	}
	
	func formattedAmounts(_ sats: Int64) -> (FormattedAmount, FormattedAmount) {
		let bitcoinAmt = Utils.formatBitcoin(currencyPrefs, sat: sats)
		let fiatAmt = Utils.formatFiat(currencyPrefs, sat: sats)
		return (bitcoinAmt, fiatAmt)
	}
	
	func minerFeeTooHigh() -> Bool {
		
		guard let minerFeeInfo else {
			return false
		}
		
		return minerFeeInfo.minerFee.sat > spendableBalance.sat
	}
	
	func minerFeePercent(_ minerFeeInfo: MinerFeeInfo) -> String {
		
		let value = Double(minerFeeInfo.minerFee.sat) / Double(spendableBalance.sat)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent

		// When the value is small, we show a fraction digit for better accuracy.
		// This also avoids showing a value such as "0%", and instead showing "0.4%".
		if value < 0.0095 {
			formatter.minimumFractionDigits = 1
			formatter.maximumFractionDigits = 1
			formatter.roundingMode = .up
		}
		
		return formatter.string(from: NSNumber(value: value)) ?? ""
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
	// MARK: Notifications
	// --------------------------------------------------
	
	func walletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("walletChanged()")
		
	#if DEBUG
	//	wallet = newValue.fakeBlockHeight(plus: Int32(144 * 31 * 3)) // 3 months: test expirationWarning
	//	wallet = newValue.fakeBlockHeight(plus: Int32(144 * 30 * 4)) // 4 months: test lockedUntilRefund
	//	wallet = newValue.fakeBlockHeight(plus: Int32(144 * 30 * 6)) // 6 months: test readyForRefund
		wallet = newValue
	#else
		wallet = newValue
	#endif
	}
	
	func btcAddressInputResultChanged() {
		log.trace("btcAddressInputResultChanged()")
		
		// Changing the address invalidates the miner fee info.
		if minerFeeInfo != nil {
			minerFeeInfo = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func dismissKeyboardIfVisible() -> Void {
		log.trace("dismissKeyboardIfVisible()")
		
		let keyWindow = UIApplication.shared.connectedScenes
			.filter({ $0.activationState == .foregroundActive })
			.map({ $0 as? UIWindowScene })
			.compactMap({ $0 })
			.first?.windows
			.filter({ $0.isKeyWindow }).first
		keyWindow?.endEditing(true)
	}
	
	func showMinerFeeSheet() {
		log.trace("showMinerFeeSheet()")
		
		guard case let .success(bitcoinUri) = btcAddressInputResult else {
			return
		}
		
		let btcAddr = bitcoinUri.address
		
		let target: MinerFeeSheet.Target
		switch source {
			case .expiredSwapIns : target = .expiredSwapIn(btcAddress: btcAddr)
			case .finalWallet    : target = .finalWallet(btcAddress: btcAddr)
		}
		
		dismissKeyboardIfVisible()
		smartModalState.display(dismissable: true) {
			
			MinerFeeSheet(
				target: target,
				minerFeeInfo: $minerFeeInfo,
				satsPerByte: $satsPerByte,
				parsedSatsPerByte: $parsedSatsPerByte,
				mempoolRecommendedResponse: $mempoolRecommendedResponse
			)
		}
	}
	
	func prepareButtonTapped() {
		log.trace("prepareButtonTapped()")
		
		showMinerFeeSheet()
	}
	
	func sendButtonTapped() {
		log.trace("sendButtonTapped()")
		
		switch source {
		case .expiredSwapIns:
			spendExpiredSwapInFunds()
			
		case .finalWallet:
			spendFinalWalletFunds()
		}
	}
	
	func exploreTx(_ txId: Bitcoin_kmpTxId, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txId, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	// --------------------------------------------------
	// MARK: Sending Logic
	// --------------------------------------------------
	
	func spendExpiredSwapInFunds() {
		log.trace("spendExpiredSwapInFunds()")
		
		guard
			let minerFeeInfo,
			let signedTx = minerFeeInfo.transaction
		else {
			return
		}
		
		isSending = true
		let electrumClient = Biz.business.electrumWatcher.client
		Task { @MainActor in
			do {
				let txId = try await electrumClient.broadcastTransaction(tx: signedTx)
				sentTxId = txId
			} catch {
				log.error("electrumClient.broadcastTransaction: error: \(error)")
			}
			
			isSending = false
		} // </Task>
	}
	
	func spendFinalWalletFunds() {
		log.trace("spendFinalWalletFunds()")
		
		guard
			let minerFeeInfo,
			let unsignedTx = minerFeeInfo.transaction,
			let peer = Biz.business.peerManager.peerStateValue(),
			let finalWallet = peer.finalWallet
		else {
			return
		}
		
		guard let signedTx = finalWallet.signTransaction(unsignedTx: unsignedTx) else {
			log.error("finalWallet.signTransaction(): returned null")
			return
		}
		
		isSending = true
		let electrumClient = Biz.business.electrumWatcher.client
		Task { @MainActor in
			do {
				let txId = try await electrumClient.broadcastTransaction(tx: signedTx)
				sentTxId = txId
			} catch {
				log.error("electrumClient.broadcastTransaction: error: \(error)")
			}
			
			isSending = false
		} // </Task>
	}
}
