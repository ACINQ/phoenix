import SwiftUI
import PhoenixShared
import Popovers

fileprivate let filename = "SummaryView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SummaryView: View {
	
	let location: PaymentView.Location
	
	@State var paymentInfo: WalletPaymentInfo
	@State var paymentInfoIsStale: Bool
	
	let fetchOptions = WalletPaymentFetchOptions.companion.All
	
	@State var blockchainConfirmations: Int? = nil
	@State var showBlockchainExplorerOptions = false
	
	@State var showOriginalFiatValue = GlobalEnvironment.currencyPrefs.showOriginalFiatValue
	@State var showFiatValueExplanation = false
	
	@State var showDeletePaymentConfirmationDialog = false
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var smartModalState: SmartModalState
	
	enum ButtonWidth: Preference {}
	let buttonWidthReader = GeometryPreferenceReader(
		key: AppendValue<ButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var buttonWidth: CGFloat? = nil
	
	enum ButtonHeight: Preference {}
	let buttonHeightReader = GeometryPreferenceReader(
		key: AppendValue<ButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var buttonHeight: CGFloat? = nil
	
	init(location: PaymentView.Location, paymentInfo: WalletPaymentInfo) {
		
		self.location = location
		
		// Try to optimize by using the in-memory cache.
		// If we get a cache hit, we can skip the UI refresh/flicker.
		if let row = paymentInfo.toOrderRow() {
			
			let fetcher = Biz.business.paymentsManager.fetcher
			if let result = fetcher.getCachedPayment(row: row, options: fetchOptions) {
				
				self._paymentInfo = State(initialValue: result)
				self._paymentInfoIsStale = State(initialValue: false)
				
			} else {
				
				self._paymentInfo = State(initialValue: paymentInfo)
				self._paymentInfoIsStale = State(initialValue: true)
			}
			
		} else {
			
			self._paymentInfo = State(initialValue: paymentInfo)
			self._paymentInfoIsStale = State(initialValue: true)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			main()
				.navigationTitle("")
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
			
		case .embedded:
			
			main()
				.navigationTitle("Payment")
				.navigationBarTitleDisplayMode(.inline)
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		ZStack {

			// This technique is used to center the content vertically
			GeometryReader { geometry in
				ScrollView(.vertical) {
					content()
						.frame(width: geometry.size.width)
						.frame(minHeight: geometry.size.height)
				}
			}

			// Close button in upper right-hand corner
			if case .sheet(let closeAction) = location {
				VStack {
					HStack {
						Spacer()
						Button {
							closeAction()
						} label: {
							Image(systemName: "xmark").imageScale(.medium).font(.title2)
						}
						.padding()
						.accessibilityLabel("Close sheet")
						.accessibilitySortPriority(-1)
					}
					Spacer()
				}
			}
		
		} // </ZStack>
		.onAppear {
			onAppear()
		}
		.task {
			await monitorBlockchain()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack {
			Spacer(minLength: 25)
			header_status()
			header_amount()
			SummaryInfoGrid(paymentInfo: $paymentInfo, showOriginalFiatValue: $showOriginalFiatValue)
			buttonList()
			Spacer(minLength: 25)
		}
	}
	
	@ViewBuilder
	func header_status() -> some View {
		
		let payment = paymentInfo.payment
		let paymentState = payment.state()
		
		if paymentState == WalletPaymentState.successOnChain ||
		   paymentState == WalletPaymentState.successOffChain
		{
			Image("ic_payment_sent")
				.renderingMode(.template)
				.resizable()
				.frame(width: 100, height: 100)
				.aspectRatio(contentMode: .fit)
				.foregroundColor(Color.appPositive)
				.padding(.bottom, 16)
				.accessibilityHidden(true)
			VStack {
				Group {
					if payment is Lightning_kmpInboundLiquidityOutgoingPayment {
						Text("Liquidity Added")
					}
					else if payment is Lightning_kmpOutgoingPayment {
						Text("SENT")
							.accessibilityLabel("Payment sent")
					} else {
						Text("RECEIVED")
							.accessibilityLabel("Payment received")
					}
				}
				.textCase(.uppercase)
				.font(.title2.bold())
				.padding(.bottom, 2)
				
				if let onChainPayment = payment as? Lightning_kmpOnChainOutgoingPayment {
					header_blockchainStatus(onChainPayment)
					
				} else if let completedAtDate = payment.completedAtDate {
					Text(completedAtDate.format())
						.multilineTextAlignment(.center)
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
			}
			.padding(.bottom, 30)
			
		} else if paymentState == WalletPaymentState.pendingOnChain {
			
			Image(systemName: "hourglass.circle")
				.renderingMode(.template)
				.resizable()
				.foregroundColor(Color.borderColor)
				.frame(width: 100, height: 100)
				.padding(.bottom, 16)
				.accessibilityHidden(true)
			VStack(alignment: HorizontalAlignment.center, spacing: 2) {
				Text("WAITING FOR CONFIRMATIONS")
					.font(.title2.uppercaseSmallCaps())
					.multilineTextAlignment(.center)
					.padding(.bottom, 6)
					.accessibilityLabel("Pending payment")
					.accessibilityHint("Waiting for confirmations")
				
				if let onChainPayment = payment as? Lightning_kmpOnChainOutgoingPayment {
					header_blockchainStatus(onChainPayment)
				}
				
			} // </VStack>
			.padding(.bottom, 30)
			
		} else if paymentState == WalletPaymentState.pendingOffChain {
			
			Image("ic_payment_sending")
				.renderingMode(.template)
				.resizable()
				.foregroundColor(Color.borderColor)
				.frame(width: 100, height: 100)
				.padding(.bottom, 16)
				.accessibilityHidden(true)
			Text("PENDING")
				.font(.title2.bold())
				.padding(.bottom, 30)
				.accessibilityLabel("Pending payment")
			
		} else if paymentState == WalletPaymentState.failure {
			
			Image(systemName: "xmark.circle")
				.renderingMode(.template)
				.resizable()
				.frame(width: 100, height: 100)
				.foregroundColor(.appNegative)
				.padding(.bottom, 16)
				.accessibilityHidden(true)
			VStack {
				Text("FAILED")
					.font(.title2.bold())
					.padding(.bottom, 2)
					.accessibilityLabel("Failed payment")
				
				Text("NO FUNDS HAVE BEEN SENT")
					.font(.title2.uppercaseSmallCaps())
					.padding(.bottom, 6)
				
				if let completedAtDate = payment.completedAtDate {
					Text(completedAtDate.format())
						.multilineTextAlignment(.center)
						.font(Font.subheadline)
						.foregroundColor(.secondary)
				}
				
			} // </VStack>
			.padding(.bottom, 30)
			
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func header_blockchainStatus(_ onChainPayment: Lightning_kmpOnChainOutgoingPayment) -> some View {
		
		switch blockchainConfirmations {
		case .none:
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.secondary))
				
				Text("Checking blockchain…")
					.font(.callout)
					.foregroundColor(.secondary)
			}
			.padding(.top, 10)
			
		case .some(let confirmations):
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					showBlockchainExplorerOptions = true
				} label: {
					if confirmations == 1 {
						Text("1 confirmation")
							.font(.subheadline)
					} else if confirmations < 7 {
						Text("\(confirmations) confirmations")
							.font(.subheadline)
					} else {
						Text("6+ confirmations")
							.font(.subheadline)
					}
				}
				.confirmationDialog("Blockchain Explorer",
					isPresented: $showBlockchainExplorerOptions,
					titleVisibility: .automatic
				) {
					Button {
						exploreTx(onChainPayment.txId, website: BlockchainExplorer.WebsiteMempoolSpace())
					} label: {
						Text(verbatim: "Mempool.space") // no localization needed
					}
					Button {
						exploreTx(onChainPayment.txId, website: BlockchainExplorer.WebsiteBlockstreamInfo())
					} label: {
						Text(verbatim: "Blockstream.info") // no localization needed
					}
					Button("Copy transaction id") {
						copyTxId(onChainPayment.txId)
					}
				} // </confirmationDialog>
				
				if confirmations == 0 && supportsBumpFee(onChainPayment) {
					NavigationLink(destination: cpfpView(onChainPayment)) {
						Label {
							Text("Accelerate transaction")
						} icon: {
							Image(systemName: "paperplane").imageScale(.small)
						}
						.font(.subheadline)
					}
					.padding(.top, 3)
				}
				
				if let confirmedAt = onChainPayment.confirmedAt?.int64Value.toDate(from: .milliseconds) {
				
					Text("confirmed")
						.font(.subheadline)
						.foregroundColor(.secondary)
						.padding(.top, 20)
					
					Text(confirmedAt.format())
						.font(.subheadline)
						.foregroundColor(.secondary)
						.padding(.top, 3)
					
				} else {
					
					Text("broadcast")
						.font(.subheadline)
						.foregroundColor(.secondary)
						.padding(.top, 20)
					
					Text(onChainPayment.createdAt.toDate(from: .milliseconds).format())
						.font(.subheadline)
						.foregroundColor(.secondary)
						.padding(.top, 3)
				}
			}
			.padding(.top, 10)
		}
	}
	
	@ViewBuilder
	func header_amount() -> some View {
		
		let isOutgoing = paymentInfo.payment is Lightning_kmpOutgoingPayment
		let amount = formattedAmount()
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					if amount.hasSubFractionDigits {
						
						// We're showing sub-fractional values.
						// For example, we're showing millisatoshis.
						//
						// It's helpful to downplay the sub-fractional part visually.
						
						let hasStdFractionDigits = amount.hasStdFractionDigits
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text(verbatim: isOutgoing ? "-" : "+")
								.font(.largeTitle)
								.foregroundColor(Color.secondary)
							Text(verbatim: amount.integerDigits)
								.font(.largeTitle)
							Text(verbatim: amount.decimalSeparator)
								.font(hasStdFractionDigits ? .largeTitle : .title)
								.foregroundColor(hasStdFractionDigits ? Color.primary : Color.secondary)
							if hasStdFractionDigits {
								Text(verbatim: amount.stdFractionDigits)
									.font(.largeTitle)
									.foregroundColor(Color.primary)
							}
							Text(verbatim: amount.subFractionDigits)
								.font(.title)
								.foregroundColor(Color.secondary)
						}
						.environment(\.layoutDirection, .leftToRight) // Issue #237
						
					} else {
						
						HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
							Text(verbatim: isOutgoing ? "-" : "+")
								.font(.largeTitle)
								.foregroundColor(Color.secondary)
							Text(amount.digits)
								.font(.largeTitle)
						}
						.environment(\.layoutDirection, .leftToRight) // Issue #237
					}
					
					Text_CurrencyName(currency: amount.currency, fontTextStyle: .title3)
						.foregroundColor(.appAccent)
						.padding(.leading, 6)
						.padding(.bottom, 4)
					
				} // </HStack>
				.onTapGesture { toggleCurrencyType() }
				
				if currencyPrefs.currencyType == .fiat {
					
					AnimatedClock(state: clockStateBinding(), size: 20, animationDuration: 1.25)
						.padding(.leading, 8)
				}
				
			} // </HStack>
			.lineLimit(1)            // SwiftUI truncation bugs
			.minimumScaleFactor(0.5) // SwiftUI truncation bugs
			
			Group {
				if currencyPrefs.currencyType == .fiat && showFiatValueExplanation {
					if showOriginalFiatValue {
						Text("amount at time of payment")
					} else {
						Text("amount based on current exchange rate")
					}
				} else {
					Text(verbatim: "sats are the standard")
						.hidden()
						.accessibilityHidden(true)
				}
			}
			.font(.caption)
			.foregroundColor(.secondary)
			
		} // </VStack>
		.padding([.top, .leading, .trailing], 8)
		.padding(.bottom, 13)
		.background(
			VStack {
				Spacer()
				RoundedRectangle(cornerRadius: 10)
					.frame(width: 70, height: 6, alignment: .center)
					.foregroundColor(Color.appAccent)
			}
		)
		.padding(.bottom, 24)
		.accessibilityElement()
		.accessibilityLabel("\(isOutgoing ? "-" : "+")\(amount.string)")
	}
	
	@ViewBuilder
	func buttonList() -> some View {
		
		// Details | Edit | Delete
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
			
			NavigationLink(destination: detailsView()) {
				Label {
					Text("Details")
				} icon: {
					Image(systemName: "magnifyingglass").imageScale(.small)
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			}
			
			if let buttonHeight = buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			NavigationLink(destination: editInfoView()) {
				Label {
					Text("Edit")
				} icon: {
					Image(systemName: "pencil.line").imageScale(.small)
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.center)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			}
			
			if let buttonHeight = buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			Button {
				showDeletePaymentConfirmationDialog = true
			} label: {
				Label {
					Text("Delete")
				} icon: {
					Image(systemName: "eraser.line.dashed").imageScale(.small)
				}
				.foregroundColor(.appNegative)
				.frame(minWidth: buttonWidth, alignment: Alignment.leading)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			}
			.confirmationDialog("Delete payment?",
				isPresented: $showDeletePaymentConfirmationDialog,
				titleVisibility: Visibility.hidden
			) {
				Button("Delete payment", role: ButtonRole.destructive) {
					deletePayment()
				}
			}
		}
		.padding([.top, .bottom])
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	@ViewBuilder
	func detailsView() -> some View {
		DetailsView(
			location: wrappedLocation(),
			paymentInfo: $paymentInfo,
			showOriginalFiatValue: $showOriginalFiatValue,
			showFiatValueExplanation: $showFiatValueExplanation
		)
	}
	
	@ViewBuilder
	func editInfoView() -> some View {
		EditInfoView(
			location: wrappedLocation(),
			paymentInfo: $paymentInfo
		)
	}
	
	@ViewBuilder
	func cpfpView(_ onChainPayment: Lightning_kmpOnChainOutgoingPayment) -> some View {
		CpfpView(
			location: wrappedLocation(),
			onChainPayment: onChainPayment
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func formattedAmount() -> FormattedAmount {
		
		let msat = paymentInfo.payment.amount
		if showOriginalFiatValue && currencyPrefs.currencyType == .fiat {
			
			if let originalExchangeRate = paymentInfo.metadata.originalFiat {
				return Utils.formatFiat(msat: msat, exchangeRate: originalExchangeRate)
			} else {
				return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			}
			
		} else {
			return Utils.format(currencyPrefs, msat: msat, policy: .showMsatsIfNonZero)
		}
	}
	
	func clockStateBinding() -> Binding<AnimatedClock.ClockState> {
		
		return Binding {
			showOriginalFiatValue ? .past : .present
		} set: { value in
			switch value {
				case .past    : showOriginalFiatValue = true
				case .present : showOriginalFiatValue = false
			}
			showFiatValueExplanation = true
		}
	}
	
	func wrappedLocation() -> PaymentView.Location {
		
		switch location {
		case .sheet(_):
			return location
		case .embedded(_):
			return .embedded(popTo: popToWrapper)
		}
	}
	
	func supportsBumpFee(_ onChainPayment: Lightning_kmpOnChainOutgoingPayment) -> Bool {
		
		switch onChainPayment {
			case is Lightning_kmpSpliceOutgoingPayment     : return true
			case is Lightning_kmpSpliceCpfpOutgoingPayment : return true
			default                                        : return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func updateConfirmations(_ onChainPayment: Lightning_kmpOnChainOutgoingPayment) async -> Int {
		log.trace("checkConfirmations()")
		
		do {
			let result = try await Biz.business.electrumClient.kotlin_getConfirmations(txid: onChainPayment.txId)

			let confirmations = result?.intValue ?? 0
			log.debug("checkConfirmations(): => \(confirmations)")

			self.blockchainConfirmations = confirmations
			return confirmations
		} catch {
			log.error("checkConfirmations(): error: \(error)")
			return 0
		}
	}
	
	func monitorBlockchain() async {
		log.trace("monitorBlockchain()")
		
		guard let onChainPayment = paymentInfo.payment as? Lightning_kmpOnChainOutgoingPayment else {
			log.debug("monitorBlockchain(): not an on-chain payment")
			return
		}
		
		if let confirmedAtDate = onChainPayment.confirmedAtDate {
			let elapsed = confirmedAtDate.timeIntervalSinceNow * -1.0
			if elapsed > 24.hours() {
				// It was marked as mined more than 24 hours ago.
				// So there's really no need to check the exact confirmation count anymore.
				log.debug("monitorBlockchain(): confirmedAt > 24.hours.ago")
				self.blockchainConfirmations = 7
				return
			}
		}
		
		let confirmations = await updateConfirmations(onChainPayment)
		if confirmations > 6 {
			// No need to continue checking confirmation count,
			// because the UI displays "6+" from this point forward.
			log.debug("monitorBlockchain(): confirmations > 6")
			return
		}
		
		for await notification in Biz.business.electrumClient.notificationsPublisher().values {
			
			if notification is Lightning_kmpHeaderSubscriptionResponse {
				// A new block was mined !
				// Update confirmation count if needed.
				let confirmations = await updateConfirmations(onChainPayment)
				if confirmations > 6 {
					// No need to continue checking confirmation count,
					// because the UI displays "6+" from this point forward.
					log.debug("monitorBlockchain(): confirmations > 6")
					break
				}
				
			} else {
				log.debug("monitorBlockchain(): notification isNot HeaderSubscriptionResponse")
			}
			
			if Task.isCancelled {
				log.debug("monitorBlockchain(): Task.isCancelled")
				break
			} else {
				log.debug("monitorBlockchain(): Waiting for next electrum notification...")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		let business = Biz.business
		
		if !didAppear {
			didAppear = true
			
			// First time displaying the SummaryView (coming from HomeView)
			
			if paymentInfoIsStale {
				
				// We either don't have the full payment information (missing metadata info),
				// or the payment information is possibly stale, and needs to be refreshed.
				
				if let row = paymentInfo.toOrderRow() {

					business.paymentsManager.fetcher.getPayment(row: row, options: fetchOptions) { (result, _) in

						if let result = result {
							paymentInfo = result
						}
					}

				} else {
				
					business.paymentsManager.getPayment(id: paymentInfo.id(), options: fetchOptions) { (result, _) in
						
						if let result = result {
							paymentInfo = result
						}
					}
				}
			}
			
		} else {
			
			// We are returning from the DetailsView/EditInfoView (via the NavigationController)
			// The payment metadata may have changed (e.g. description/notes modified).
			// So we need to refresh the payment info.
			
			business.paymentsManager.getPayment(id: paymentInfo.id(), options: fetchOptions) { (result, _) in
				
				if let result = result {
					paymentInfo = result
				}
			}
			
			if let destination = popToDestination {
				log.debug("popToDestination: \(destination)")
				
				popToDestination = nil
				switch destination {
				case .RootView(_):
					log.debug("Unhandled popToDestination")
					
				case .ConfigurationView(_):
					log.debug("Unhandled popToDestination")
					
				case .TransactionsView:
					presentationMode.wrappedValue.dismiss()
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		popToDestination = destination
		if case .embedded(let popTo) = location {
			popTo(destination)
		}
	}
	
	func exploreTx(_ txId: Bitcoin_kmpTxId, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txId, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copyTxId(_ txId: Bitcoin_kmpTxId) {
		log.trace("copyTxId()")
		
		UIPasteboard.general.string = txId.toHex()
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func deletePayment() {
		log.trace("deletePayment()")
		
		Biz.business.databaseManager.paymentsDb { paymentsDb, _ in
			
			paymentsDb?.deletePayment(paymentId: paymentInfo.id(), completionHandler: { error in
				
				if let error = error {
					log.error("Error deleting payment: \(String(describing: error))")
				}
			})
		}
		
		switch location {
		case .sheet(let closeAction):
			closeAction()
		case .embedded:
			presentationMode.wrappedValue.dismiss()
		}
	}
}

// See InfoGridView for architecture discussion.
//
fileprivate struct SummaryInfoGrid: InfoGridView {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	
	// <InfoGridView Protocol>
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	@State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) {
		keyColumnSizes = value
	}
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size] {
		return keyColumnSizes
	}
	
	@State var rowSizes: [InfoGridRow_Size] = []
	func setRowSizes(_ sizes: [InfoGridRow_Size]) {
		rowSizes = sizes
	}
	func getRowSizes() -> [InfoGridRow_Size] {
		return rowSizes
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@State var popoverPresent_standardFees = false
	@State var popoverPresent_minerFees = false
	@State var popoverPresent_serviceFees = false
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			
			// Splitting this up into separate ViewBuilders,
			// because the compiler will sometimes choke while processing this method.
			
			paymentServiceRow()
			paymentDescriptionRow()
			paymentMessageRow()
			paymentNotesRow()
			paymentTypeRow()
			channelClosingRow()
			
			paymentFeesRow_StandardFees()
			paymentFeesRow_MinerFees()
			paymentFeesRow_ServiceFees()
			paymentDurationRow()
			
			paymentErrorRow()
		}
		.padding([.leading, .trailing])
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func keyColumn(verbatim title: String) -> some View {
		
		Text(title).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func paymentServiceRow() -> some View {
		let identifier: String = #function
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Service")
				
			} valueColumn: {
				
				Text(lnurlPay.initialUrl.host)
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentDescriptionRow() -> some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			
			keyColumn("Desc")
				.accessibilityLabel("Description")
			
		} valueColumn: {
			
			let description = paymentInfo.paymentDescription() ?? paymentInfo.defaultPaymentDescription()
			Text(description)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = description
					}) {
						Text("Copy")
					}
				}
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func paymentMessageRow() -> some View {
		let identifier: String = #function
		let successAction = paymentInfo.metadata.lnurl?.successAction
		
		if let sa_message = successAction as? LnurlPay.Invoice_SuccessAction_Message {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				Text(sa_message.message)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = sa_message.message
						}) {
							Text("Copy")
						}
					}
				
			} // </InfoGridRow>
			
		} else if let sa_url = successAction as? LnurlPay.Invoice_SuccessAction_Url {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					
					Text(sa_url.description_)
					
					if let url = URL(string: sa_url.url.description()) {
						Button {
							openURL(url)
						} label: {
							Text("open link")
						}
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = url.absoluteString
							}) {
								Text("Copy link")
							}
						}
					}
				} // </VStack>
				
			} // </InfoGridRow>
		
		} else if let sa_aes = successAction as? LnurlPay.Invoice_SuccessAction_Aes {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Message")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					
					Text(sa_aes.description_)
					
					if let sa_aes_decrypted = decrypt(aes: sa_aes) {
					
						if let url = URL(string: sa_aes_decrypted.plaintext) {
							Button {
								openURL(url)
							} label: {
								Text("open link")
							}
							.contextMenu {
								Button(action: {
									UIPasteboard.general.string = url.absoluteString
								}) {
									Text("Copy link")
								}
							}
						} else {
							Text(sa_aes_decrypted.plaintext)
								.contextMenu {
									Button(action: {
										UIPasteboard.general.string = sa_aes_decrypted.plaintext
									}) {
										Text("Copy")
									}
								}
						}
						
					} else {
						Text("<decryption error>")
					}
				}
			} // </InfoGridRow>
			
		} // </else if let sa_aes>
	}
	
	@ViewBuilder
	func paymentNotesRow() -> some View {
		let identifier: String = #function
		
		if let notes = paymentInfo.metadata.userNotes, notes.count > 0 {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Notes")
				
			} valueColumn: {
				
				Text(notes)
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentTypeRow() -> some View {
		let identifier: String = #function
		
		if let paymentTypeTuple = paymentInfo.payment.paymentType() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Type")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					let (type, explanation) = paymentTypeTuple
					Text(type)
					+ Text(verbatim: " (\(explanation))")
						.font(.footnote)
						.foregroundColor(.secondary)
					
					if let link = paymentInfo.payment.paymentLink() {
						Button {
							openURL(link)
						} label: {
							Text("view on blockchain")
						}
					}
				}
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func channelClosingRow() -> some View {
		let identifier: String = #function
		
		if let pClosingInfo = paymentInfo.payment.channelClosing() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Output")
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					// Bitcoin address (copyable)
					Text(pClosingInfo.address)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = pClosingInfo.address
							}) {
								Text("Copy")
							}
						}
					
					if pClosingInfo.isSentToDefaultAddress {
						Text("(This is your address - derived from your seed. You alone possess your seed.)")
							.font(.footnote)
							.foregroundColor(.secondary)
							.padding(.top, 4)
					}
				}
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_StandardFees() -> some View {
		
		if let standardFees = paymentInfo.payment.standardFees() {
			paymentFeesRow(
				msat: standardFees.0,
				title: standardFees.1,
				explanation: standardFees.2,
				binding: $popoverPresent_standardFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_MinerFees() -> some View {
		
		if let minerFees = paymentInfo.payment.minerFees() {
			paymentFeesRow(
				msat: minerFees.0,
				title: minerFees.1,
				explanation: minerFees.2,
				binding: $popoverPresent_minerFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow_ServiceFees() -> some View {
		
		if let serviceFees = paymentInfo.payment.serviceFees() {
			paymentFeesRow(
				msat: serviceFees.0,
				title: serviceFees.1,
				explanation: serviceFees.2,
				binding: $popoverPresent_serviceFees
			)
		}
	}
	
	@ViewBuilder
	func paymentFeesRow(
		msat: Int64,
		title: String,
		explanation: String,
		binding: Binding<Bool>
	) -> some View {
		let identifier: String = "paymentFeesRow:\(title)"
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier),
			keyColumnAlignment: .trailing
		) {
			
			keyColumn(verbatim: title)
			
		} valueColumn: {
				
			HStack(alignment: VerticalAlignment.center, spacing: 6) {
				
				let amount = formattedAmount(msat: msat)
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					if amount.hasSubFractionDigits {
						
						// We're showing sub-fractional values.
						// For example, we're showing millisatoshis.
						//
						// It's helpful to downplay the sub-fractional part visually.
						
						let hasStdFractionDigits = amount.hasStdFractionDigits
						
						Text(verbatim: amount.integerDigits)
						+	Text(verbatim: amount.decimalSeparator)
							.foregroundColor(hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: amount.stdFractionDigits)
						+	Text(verbatim: amount.subFractionDigits)
							.foregroundColor(.secondary)
							.font(.callout)
							.fontWeight(.light)
						
					} else {
						Text(amount.digits)
					}
					
					Text(verbatim: " ")
					Text_CurrencyName(currency: amount.currency, fontTextStyle: .body)
					
				} // </HStack>
				.onTapGesture { toggleCurrencyType() }
				.accessibilityLabel("\(amount.string)")
				
				if !explanation.isEmpty {
					
					Button {
						binding.wrappedValue.toggle()
					} label: {
						Image(systemName: "questionmark.circle")
							.renderingMode(.template)
							.foregroundColor(.secondary)
							.font(.body)
					}
					.popover(present: binding) {
						InfoPopoverWindow {
							Text(verbatim: explanation)
						}
					}
				}
			} // </HStack>
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func paymentDurationRow() -> some View {
		let identifier: String = #function
		
		if let _ = paymentInfo.payment as? Lightning_kmpInboundLiquidityOutgoingPayment {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Duration")
				
			} valueColumn: {
				
				Text("1 year")
				
			} // </InfoGridRow>
		}
	}
	
	@ViewBuilder
	func paymentErrorRow() -> some View {
		let identifier: String = #function
		
		if let pError = paymentInfo.payment.paymentFinalError() {
			
			InfoGridRow(
				identifier: identifier,
				vAlignment: .firstTextBaseline,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier),
				keyColumnAlignment: .trailing
			) {
				
				keyColumn("Error")
				
			} valueColumn: {
				
				Text(pError)
				
			} // </InfoGridRow>
		}
	}
	
	func formattedAmount(msat: Int64) -> FormattedAmount {
		
		if showOriginalFiatValue && currencyPrefs.currencyType == .fiat {
			
			if let originalExchangeRate = paymentInfo.metadata.originalFiat {
				return Utils.formatFiat(msat: msat, exchangeRate: originalExchangeRate)
			} else {
				return Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			}
			
		} else {
			
			return Utils.format(currencyPrefs, msat: msat, policy: .showMsatsIfNonZero)
		}
	}

	func decrypt(aes sa_aes: LnurlPay.Invoice_SuccessAction_Aes) -> LnurlPay.Invoice_SuccessAction_Aes_Decrypted? {
		
		guard
			let outgoingPayment = paymentInfo.payment as? Lightning_kmpLightningOutgoingPayment,
			let offchainSuccess = outgoingPayment.status.asOffChain()
		else {
			return nil
		}
		
		do {
			let aes = try AES256(
				key: offchainSuccess.preimage.toSwiftData(),
				iv: sa_aes.iv.toSwiftData()
			)
			
			let plaintext_data = try aes.decrypt(sa_aes.ciphertext.toSwiftData(), padding: .PKCS7)
			if let plaintext_str = String(bytes: plaintext_data, encoding: .utf8) {
				
				return LnurlPay.Invoice_SuccessAction_Aes_Decrypted(
					description: sa_aes.description_,
					plaintext: plaintext_str
				)
			}
			
		} catch {
			log.error("Error decrypting LnurlPay.Invoice_SuccessAction_Aes: \(String(describing: error))")
		}
		
		return nil
	}

	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}
