import SwiftUI
import PhoenixShared
import Popovers
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SummaryView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct SummaryView: View {
	
	let type: PaymentViewType
	
	@State var paymentInfo: WalletPaymentInfo
	@State var paymentInfoIsStale: Bool
	
	let fetchOptions = WalletPaymentFetchOptions.companion.All
	
	@State var showOriginalFiatValue = GlobalEnvironment.currencyPrefs.showOriginalFiatValue
	@State var showFiatValueExplanation = false
	
	@State var showDeletePaymentConfirmationDialog = false
	
	@State var didAppear = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
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
	
	init(type: PaymentViewType, paymentInfo: WalletPaymentInfo) {
		
		self.type = type
		
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
		
		switch type {
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
			if case .sheet(let closeAction) = type {
				VStack {
					HStack {
						Spacer()
						Button {
							closeAction()
						} label: {
							Image("ic_cross")
								.resizable()
								.frame(width: 30, height: 30)
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
		
		switch payment.state() {
		case .success:
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
					if payment is Lightning_kmpOutgoingPayment {
						Text("SENT")
							.accessibilityLabel("Payment sent")
					} else {
						Text("RECEIVED")
							.accessibilityLabel("Payment received")
					}
				}
				.font(Font.title2.bold())
				.padding(.bottom, 2)
				
				if let completedAtDate = payment.completedAtDate() {
					Text(completedAtDate.format())
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
			}
			.padding(.bottom, 30)
			
		case .pending:
			if payment.isOnChain() {
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
					if let depth = minFundingDepth() {
						let minutes = depth * 10
						Text("requires \(depth) confirmations")
							.font(.footnote)
							.multilineTextAlignment(.center)
							.foregroundColor(.secondary)
						Text("≈\(minutes) minutes")
							.font(.footnote)
							.multilineTextAlignment(.center)
							.foregroundColor(.secondary)
					}
					if let broadcastDate = onChainBroadcastDate() {
						Text(broadcastDate.format())
							.font(.subheadline)
							.foregroundColor(.secondary)
							.padding(.top, 12)
					}
				} // </VStack>
				.padding(.bottom, 30)
			} else {
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
			}
			
		case .failure:
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
				
				if let completedAtDate = payment.completedAtDate() {
					Text(completedAtDate.format())
						.font(Font.subheadline)
						.foregroundColor(.secondary)
				}
				
			} // </VStack>
			.padding(.bottom, 30)
			
		default:
			EmptyView()
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
		
		if #available(iOS 15.0, *) {
			if paymentInfo.payment.state() == WalletPaymentState.failure {
				buttonList_withDeleteOption()
			} else {
				buttonList_standardOptions()
			}
		} else {
			buttonList_standardOptions()
		}
	}
	
	@ViewBuilder
	func buttonList_standardOptions() -> some View {
		
		// Details | Edit
		//         ^
		//         And we want this line to be perfectly centered in the view.
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
		
			NavigationLink(destination: detailsView()) {
				Text("Details")
					.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
			
			if let buttonHeight = buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			NavigationLink(destination: editInfoView()) {
				Text("Edit")
					.frame(minWidth: buttonWidth, alignment: Alignment.leading)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
		}
		.padding([.top, .bottom])
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	@ViewBuilder
	@available(iOS 15.0, *)
	func buttonList_withDeleteOption() -> some View {
		
		// Details | Edit | Delete
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
			
			NavigationLink(destination: detailsView()) {
				Text("Details")
					.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
			
			if let buttonHeight = buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			NavigationLink(destination: editInfoView()) {
				Text("Edit")
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
				Text("Delete")
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
			type: type,
			paymentInfo: $paymentInfo
		)
	}
	
	@ViewBuilder
	func editInfoView() -> some View {
		EditInfoView(
			type: type,
			paymentInfo: $paymentInfo
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func minFundingDepth() -> Int32? {
		
		guard
			let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment,
			let received = incomingPayment.received,
			let newChannel = received.receivedWith.compactMap({ $0.asNewChannel() }).first,
			let channelId = newChannel.channelId,
			let channel = Biz.business.peerManager.getChannelWithCommitments(channelId: channelId),
			let nodeParams = Biz.business.nodeParamsManager.nodeParams.value_ as? Lightning_kmpNodeParams
		else {
			return nil
		}
		
		return channel.minDepthForFunding(nodeParams: nodeParams)
	}
	
	func onChainBroadcastDate() -> Date? {
		
		if let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asDualSwapIn() {
				if let received = incomingPayment.received {
					return received.receivedAtDate
				}
			}
		}
		
		return nil
	}
	
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
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func deletePayment() {
		log.trace("deletePayment()")
		
		Biz.business.databaseManager.paymentsDb { paymentsDb, _ in
			
			paymentsDb?.deletePayment(paymentId: paymentInfo.id(), completionHandler: { _, error in
				
				if let error = error {
					log.error("Error deleting payment: \(String(describing: error))")
				}
			})
		}
		
		switch type {
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
	@State var keyColumnWidths: [InfoGridRow_KeyColumn_Width] = []
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	func setKeyColumnWidths(_ value: [InfoGridRow_KeyColumn_Width]) {
		keyColumnWidths = value
	}
	func getKeyColumnWidths() -> [InfoGridRow_KeyColumn_Width] {
		return keyColumnWidths
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@State var popoverPresent_standardFees = false
	@State var popoverPresent_minerFees = false
	@State var popoverPresent_swapFees = false
	
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
			
			if let standardFees = paymentInfo.payment.standardFees() {
				paymentFeesRow(
					msat: standardFees.0,
					title: standardFees.1,
					explanation: standardFees.2,
					binding: $popoverPresent_standardFees
				)
			}
			if let minerFees = paymentInfo.payment.minerFees() {
				paymentFeesRow(
					msat: minerFees.0,
					title: minerFees.1,
					explanation: minerFees.2,
					binding: $popoverPresent_minerFees
				)
			}
			if let swapOutFees = paymentInfo.payment.swapOutFees() {
				paymentFeesRow(
					msat: swapOutFees.0,
					title: swapOutFees.1,
					explanation: swapOutFees.2,
					binding: $popoverPresent_swapFees
				)
			}
			
			paymentErrorRow()
		}
		.padding([.leading, .trailing])
	}
	
	@ViewBuilder
	func keyColumn(_ str: String) -> some View {
		
		Text(str).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func paymentServiceRow() -> some View {
		let identifier: String = #function
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			InfoGridRow(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Service", comment: "Label in SummaryInfoGrid"))
				
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
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("Desc", comment: "Label in SummaryInfoGrid"))
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Message", comment: "Label in SummaryInfoGrid"))
				
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Message", comment: "Label in SummaryInfoGrid"))
				
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Message", comment: "Label in SummaryInfoGrid"))
				
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Notes", comment: "Label in SummaryInfoGrid"))
				
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Type", comment: "Label in SummaryInfoGrid"))
				
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
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Output", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					// Bitcoin address (copyable)
					Text(pClosingInfo.closingAddress)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = pClosingInfo.closingAddress
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
	func paymentFeesRow(
		msat: Int64,
		title: String,
		explanation: String,
		binding: Binding<Bool>
	) -> some View {
		let identifier: String = "paymentFeesRow:\(title)"
		
		InfoGridRow(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(title)
			
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
					
					Text(" ")
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
						Templates.Container {
							Text(verbatim: explanation)
								.padding(.all, 4)
						}
					}
				}
			} // </HStack>
			
		} // </InfoGridRow>
	}
	
	@ViewBuilder
	func paymentErrorRow() -> some View {
		let identifier: String = #function
		
		if let pError = paymentInfo.payment.paymentFinalError() {
			
			InfoGridRow(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Error", comment: "Label in SummaryInfoGrid"))
				
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
			let outgoingPayment = paymentInfo.payment as? Lightning_kmpOutgoingPayment,
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
