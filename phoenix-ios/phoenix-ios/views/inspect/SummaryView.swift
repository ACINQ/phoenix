import SwiftUI
import PhoenixShared
import DYPopoverView
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SummaryView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let explainFeesButtonViewId = "explainFeesButtonViewId"

struct SummaryView: View {
	
	@State var paymentInfo: WalletPaymentInfo
	@State var paymentInfoIsStale: Bool
	
	let closeSheet: () -> Void
	
	@State var explainFeesText: String = ""
	@State var explainFeesPopoverVisible = false
	@State var explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: 200, height:500)
	
	@State var showDeletePaymentConfirmationDialog = false
	
	@State var didAppear = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
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
	
	init(paymentInfo: WalletPaymentInfo, closeSheet: @escaping () -> Void) {
		
		// Try to optimize by using the in-memory cache.
		// If we get a cache hit, we can skip the UI refresh/flicker.
		if let row = paymentInfo.toOrderRow() {
			
			let fetcher = AppDelegate.get().business.paymentsManager.fetcher
			let options = WalletPaymentFetchOptions.companion.All
			
			if let result = fetcher.getCachedPayment(row: row, options: options) {
				
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
		
		self.closeSheet = closeSheet
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {

			ScrollView {
				self.main
			}
			.frame(maxWidth: .infinity)

			// Close button in upper right-hand corner
			VStack {
				HStack {
					Spacer()
					Button {
						closeSheet()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.padding()
				}
				Spacer()
			}

			// An invisible layer used to detect taps, and dismiss the DYPopoverView.
			if explainFeesPopoverVisible {
				Color.clear
					.contentShape(Rectangle()) // required: https://stackoverflow.com/a/60151771/43522
					.onTapGesture {
						explainFeesPopoverVisible = false
					}
			}
		}
		.popoverView( // DYPopoverView
			content: { ExplainFeesPopover(explanationText: $explainFeesText) },
			background: { Color.mutedBackground },
			isPresented: $explainFeesPopoverVisible,
			frame: $explainFeesPopoverFrame,
			anchorFrame: nil,
			popoverType: .popover,
			position: .top,
			viewId: explainFeesButtonViewId
		)
		.onPreferenceChange(ExplainFeesPopoverHeight.self) {
			if let height = $0 {
				explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: explainFeesPopoverFrame.width, height: height)
			}
		}
		.onAppear { onAppear() }
	}
	
	@ViewBuilder
	var main: some View {
		
		let payment = paymentInfo.payment
		
		VStack {
			Spacer(minLength: 90)
			
			switch payment.state() {
			case .success:
				Image("ic_payment_sent")
					.renderingMode(.template)
					.resizable()
					.frame(width: 100, height: 100)
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appPositive)
					.padding(.bottom, 16)
				VStack {
					Group {
						if payment is Lightning_kmpOutgoingPayment {
							Text("SENT")
						} else {
							Text("RECEIVED")
						}
					}
					.font(Font.title2.bold())
					.padding(.bottom, 2)
					Text(payment.completedAt().formatDateMS())
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
				.padding(.bottom, 30)
				
			case .pending:
				Image("ic_payment_sending")
					.renderingMode(.template)
					.resizable()
					.foregroundColor(Color.borderColor)
					.frame(width: 100, height: 100)
					.padding(.bottom, 16)
				Text("PENDING")
					.font(Font.title2.bold())
					.padding(.bottom, 30)
				
			case .failure:
				Image(systemName: "xmark.circle")
					.renderingMode(.template)
					.resizable()
					.frame(width: 100, height: 100)
					.foregroundColor(.appNegative)
					.padding(.bottom, 16)
				VStack {
					Text("FAILED")
						.font(Font.title2.bold())
						.padding(.bottom, 2)
					
					Text("NO FUNDS HAVE BEEN SENT")
						.font(Font.title2.uppercaseSmallCaps())
						.padding(.bottom, 6)
					
					Text(payment.completedAt().formatDateMS())
						.font(Font.subheadline)
						.foregroundColor(.secondary)
					
				}
				.padding(.bottom, 30)
				
			default:
				EmptyView()
			}

			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				let isOutgoing = payment is Lightning_kmpOutgoingPayment
				let amount = Utils.format(currencyPrefs, msat: payment.amount, policy: .showMsats)

				if currencyPrefs.currencyType == .bitcoin &&
				   currencyPrefs.bitcoinUnit == .sat &&
				   amount.hasFractionDigits
				{
					// We're showing the value in satoshis, but the value contains a fractional
					// component representing the millisatoshis.
					// This can be a little confusing for those new to Lightning.
					// So we're going to downplay the millisatoshis visually.
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text(verbatim: isOutgoing ? "-" : "+")
							.font(.largeTitle)
							.foregroundColor(Color.secondary)
							.onTapGesture { toggleCurrencyType() }
						Text(verbatim: amount.integerDigits)
							.font(.largeTitle)
							.onTapGesture { toggleCurrencyType() }
						Text(verbatim: "\(amount.decimalSeparator)\(amount.fractionDigits)")
							.lineLimit(1)            // SwiftUI bugs
							.minimumScaleFactor(0.5) // Truncating text
							.font(.title)
							.foregroundColor(Color.secondary)
							.onTapGesture { toggleCurrencyType() }
					}
					.environment(\.layoutDirection, .leftToRight) // issue #237
					.padding(.trailing, 6)
					Text(verbatim: amount.type)
						.font(.title3)
						.foregroundColor(Color.appAccent)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
					
				} else {
					
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text(verbatim: isOutgoing ? "-" : "+")
							.font(.largeTitle)
							.foregroundColor(Color.secondary)
							.onTapGesture { toggleCurrencyType() }
						Text(amount.digits)
							.font(.largeTitle)
							.onTapGesture { toggleCurrencyType() }
					}
					.environment(\.layoutDirection, .leftToRight) // issue #237
					.padding(.trailing, 6)
					Text(amount.type)
						.font(.title3)
						.foregroundColor(Color.appAccent)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
				}
			}
			.padding([.top, .leading, .trailing], 8)
			.padding(.bottom, 33)
			.background(
				VStack {
					Spacer()
					RoundedRectangle(cornerRadius: 10)
						.frame(width: 70, height: 6, alignment: /*@START_MENU_TOKEN@*/.center/*@END_MENU_TOKEN@*/)
						.foregroundColor(Color.appAccent)
				}
			)
			.padding(.bottom, 24)
			
			SummaryInfoGrid(
				paymentInfo: $paymentInfo,
				explainFeesPopoverVisible: $explainFeesPopoverVisible
			)
			
			if #available(iOS 15.0, *) {
				if payment.state() == WalletPaymentState.failure {
					buttonList_withDeleteOption
				} else {
					buttonList
				}
			} else {
				buttonList
			}
		}
	}
	
	@ViewBuilder
	var buttonList: some View {
		
		// Details | Edit
		//         ^
		//         And we want this line to be perfectly centered in the view.
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
		
			NavigationLink(destination: DetailsView(
				paymentInfo: $paymentInfo,
				closeSheet: closeSheet
			)) {
				Text("Details")
					.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
			
			Divider()
				.frame(height: buttonHeight)
			
			NavigationLink(destination: EditInfoView(
				paymentInfo: $paymentInfo
			)) {
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
	var buttonList_withDeleteOption: some View {
		
		// Details | Edit | Delete
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
			
			NavigationLink(destination: DetailsView(
				paymentInfo: $paymentInfo,
				closeSheet: closeSheet
			)) {
				Text("Details")
					.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
			
			Divider()
				.frame(height: buttonHeight)
			
			NavigationLink(destination: EditInfoView(
				paymentInfo: $paymentInfo
			)) {
				Text("Edit")
					.frame(minWidth: buttonWidth, alignment: Alignment.center)
					.read(buttonWidthReader)
					.read(buttonHeightReader)
			}
			
			Divider()
				.frame(height: buttonHeight)
			
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
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func explainFeesPopoverText() -> String {
		
		let feesInfo = paymentInfo.payment.paymentFees(currencyPrefs: currencyPrefs)
		return feesInfo?.1 ?? ""
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		let business = AppDelegate.get().business
		let options = WalletPaymentFetchOptions.companion.All
		
		if !didAppear {
			didAppear = true
			
			// First time displaying the SummaryView (coming from HomeView)
			
			// Update text in explainFeesPopover
			explainFeesText = explainFeesPopoverText()
			
			if paymentInfoIsStale {
				
				// We either don't have the full payment information (missing metadata info),
				// or the payment information is possibly stale, and needs to be refreshed.
				
				if let row = paymentInfo.toOrderRow() {

					business.paymentsManager.fetcher.getPayment(row: row, options: options) { (result, _) in

						if let result = result {
							paymentInfo = result
							explainFeesText = explainFeesPopoverText()
						}
					}

				} else {
				
					business.paymentsManager.getPayment(id: paymentInfo.id(), options: options) { (result, _) in
						
						if let result = result {
							paymentInfo = result
							explainFeesText = explainFeesPopoverText()
						}
					}
				}
			}
			
		} else {
			
			// We are returning from the DetailsView/EditInfoView (via the NavigationController)
			// The payment metadata may have changed (e.g. description/notes modified).
			// So we need to refresh the payment info.
			
			business.paymentsManager.getPayment(id: paymentInfo.id(), options: options) { (result, _) in
				
				if let result = result {
					paymentInfo = result
					explainFeesText = explainFeesPopoverText()
				}
			}
		}
	}
	
	func deletePayment() {
		log.trace("deletePayment()")
		
		let business = AppDelegate.get().business
		business.databaseManager.paymentsDb { paymentsDb, _ in
			
			paymentsDb?.deletePayment(paymentId: paymentInfo.id(), completionHandler: { _, error in
				
				if let error = error {
					log.error("Error deleting payment: \(String(describing: error))")
				}
			})
		}
		
		closeSheet()
	}
}

// See InfoGridView for architecture discussion.
//
fileprivate struct SummaryInfoGrid: InfoGridView {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var explainFeesPopoverVisible: Bool
	
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
			
			paymentServiceRow
			paymentDescriptionRow
			paymentMessageRow
			paymentNotesRow
			paymentTypeRow
			channelClosingRow
			paymentFeesRow
			paymentErrorRow
		}
		.padding([.leading, .trailing])
	}
	
	@ViewBuilder
	func keyColumn(_ str: String) -> some View {
		
		Text(str).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	var paymentServiceRow: some View {
		let identifier: String = #function
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			InfoGridRow(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Service", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				Text(lnurlPay.lnurl.host)
			}
		}
	}
	
	@ViewBuilder
	var paymentDescriptionRow: some View {
		let identifier: String = #function
		
		InfoGridRow(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("Desc", comment: "Label in SummaryInfoGrid"))
			
		} valueColumn: {
			
			let description = paymentInfo.paymentDescription() ??
			                  NSLocalizedString("No description", comment: "placeholder text")
			Text(description)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = description
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	var paymentMessageRow: some View {
		let identifier: String = #function
		let successAction = paymentInfo.metadata.lnurl?.successAction
		
		if let sa_message = successAction as? LNUrl.PayInvoice_SuccessAction_Message {
			
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
			}
			
		} else if let sa_url = successAction as? LNUrl.PayInvoice_SuccessAction_Url {
			
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
				}
			}
		
		} else if let sa_aes = successAction as? LNUrl.PayInvoice_SuccessAction_Aes {
			
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
			}
		}
	}
	
	@ViewBuilder
	var paymentNotesRow: some View {
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
			}
		}
	}
	
	@ViewBuilder
	var paymentTypeRow: some View {
		let identifier: String = #function
		
		if let pType = paymentInfo.payment.paymentType() {
			
			InfoGridRow(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Type", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					Text(pType.0)
					+ Text(verbatim: " (\(pType.1))")
						.font(.footnote)
						.foregroundColor(.secondary)
					
					if let link = paymentInfo.payment.paymentLink() {
						Button {
							openURL(link)
						} label: {
							Text("blockchain tx")
						}
					}
				}
			}
		}
	}
	
	@ViewBuilder
	var channelClosingRow: some View {
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
			}
		}
	}
	
	@ViewBuilder
	var paymentFeesRow: some View {
		let identifier: String = #function
		
		if let pFees = paymentInfo.payment.paymentFees(currencyPrefs: currencyPrefs) {

			InfoGridRow(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("Fees", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					let amount: FormattedAmount = pFees.0
					
					if currencyPrefs.currencyType == .bitcoin &&
						currencyPrefs.bitcoinUnit == .sat &&
						amount.hasFractionDigits
					{
						let styledText: Text =
							Text(verbatim: "\(amount.integerDigits)")
						+	Text(verbatim: "\(amount.decimalSeparator)\(amount.fractionDigits)")
								.foregroundColor(.secondary)
								.font(.callout)
								.fontWeight(.light)
						+	Text(verbatim: " \(amount.type)")
						
						styledText
							.onTapGesture { toggleCurrencyType() }
						
					} else {
						Text(amount.string)
							.onTapGesture { toggleCurrencyType() }
					}
					
					if pFees.1.count > 0 {
						
						Button {
							explainFeesPopoverVisible = true
						} label: {
							Image(systemName: "questionmark.circle")
								.renderingMode(.template)
								.foregroundColor(.secondary)
								.font(.body)
						}
						.anchorView(viewId: explainFeesButtonViewId)
						.padding(.leading, 6)
					}
				}
			}
		}
	}
	
	@ViewBuilder
	var paymentErrorRow: some View {
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
			}
		}
	}

	func decrypt(aes sa_aes: LNUrl.PayInvoice_SuccessAction_Aes) -> LNUrl.PayInvoice_SuccessAction_Aes_Decrypted? {
		
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
				
				return LNUrl.PayInvoice_SuccessAction_Aes_Decrypted(
					description: sa_aes.description_,
					plaintext: plaintext_str
				)
			}
			
		} catch {
			log.error("Error decrypting LNUrl.PayInvoice_SuccessAction_Aes: \(String(describing: error))")
		}
		
		return nil
	}

	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}

fileprivate struct ExplainFeesPopover: View {
	
	@Binding var explanationText: String
	
	var body: some View {
		
		VStack {
			Text(explanationText)
				.padding()
				.background(GeometryReader { proxy in
					Color.clear.preference(
						key: ExplainFeesPopoverHeight.self,
						value: proxy.size.height
					)
				})
		}.frame(minHeight: 500)
		// ^^^ Why? ^^^
		//
		// We're trying to accomplish 2 things:
		// - allow the explanationText to be dynamically changed
		// - calculate the appropriate height for the text
		//
		// But there's a problem, which occurs like so:
		// - explainFeesPopoverFrame starts with hard-coded frame of (150, 500)
		// - SwiftUI performs layout of our body with inherited frame of (150, 500)
		// - We calculate appropriate height (X) for our text,
		//   and it gets reported via ExplainFeesPopoverHeight
		// - explainFeesPopoverFrame is resized to (150, X)
		// - explanationText is changed, triggering a view update
		// - SwiftUI performs layout of our body with previous frame of (150, X)
		// - Text cannot report appropriate height, as it's restricted to X
		//
		// So we force the height of our VStack to 500,
		// giving the Text room to size itself appropriately.
	}
}

fileprivate struct ExplainFeesPopoverHeight: PreferenceKey {
	static let defaultValue: CGFloat? = nil
	
	static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
		value = value ?? nextValue()
	}
}
