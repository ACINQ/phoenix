import SwiftUI
import PhoenixShared
import DYPopoverView
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let explainFeesButtonViewId = "explainFeesButtonViewId"


struct PaymentView : View {

	let paymentInfo: WalletPaymentInfo
	
	// We need an explicit close operation here because:
	// - we're going to use a NavigationView
	// - we need to programmatically close the sheet from any layer in the navigation stack
	// - the general API to pop a view from the nav stack is `presentationMode.wrappedValue.dismiss()`
	// - the general API to dismiss a sheet is `presentationMode.wrappedValue.dismiss()`
	// - thus we cannot use the general API
	//
	let closeSheet: () -> Void
	
	var body: some View {
		
		NavigationView {
			
			SummaryView(paymentInfo: paymentInfo, closeSheet: closeSheet)
				.navigationBarTitle("", displayMode: .inline)
				.navigationBarHidden(true)
		}
		.edgesIgnoringSafeArea(.all)
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

fileprivate struct SummaryView: View {
	
	@State var paymentInfo: WalletPaymentInfo
	let closeSheet: () -> Void
	
	@State var explainFeesText: String = ""
	@State var explainFeesPopoverVisible = false
	@State var explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: 200, height:500)
	
	@State var showDeletePaymentConfirmationDialog = false
	
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
	//	self.payment = payment
		// ^^^ This compiles on Xcode 12.5, but crashes on the device.
		// To be more specific, it seems to crash on _some_ devices, and only when running in Release mode.
		self._paymentInfo = State<WalletPaymentInfo>(initialValue: paymentInfo)
		
		self.closeSheet = closeSheet
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {

			ScrollView {
				self.main
			}

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
		
		// Update text in explainFeesPopover
		explainFeesText = explainFeesPopoverText()
		
		// We don't have the full payment information.
		// We need to fetch all the metadata.
		
		let paymentId = paymentInfo.id()
		let options = WalletPaymentFetchOptions.companion.All
		
		AppDelegate.get().business.paymentsManager.getPayment(id: paymentId, options: options) {
			(result: WalletPaymentInfo?, error: Error?) in
			
			if let result = result {
				paymentInfo = result
				explainFeesText = explainFeesPopoverText()
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
			paymentMessaegRow
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
	var paymentMessaegRow: some View {
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

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

fileprivate struct DetailsView: View {
	
	@Binding var paymentInfo: WalletPaymentInfo
	let closeSheet: () -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.backward")
						.imageScale(.medium)
				}
				Spacer()
				Button {
					closeSheet()
				} label: {
					Image(systemName: "xmark") // must match size of chevron.backward above
						.imageScale(.medium)
				}
			}
			.font(.title2)
			.padding()
				
			ScrollView {
				DetailsInfoGrid(paymentInfo: $paymentInfo)
					.padding([.leading, .trailing])
			}
		}
		.navigationBarTitle(
			NSLocalizedString("Details", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.navigationBarHidden(true)
	}
}

fileprivate struct DetailsInfoGrid: InfoGridView {
	
	@Binding var paymentInfo: WalletPaymentInfo
	
	// <InfoGridView Protocol>
	@State var keyColumnWidths: [InfoGridRow_KeyColumn_Width] = []
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 140
	
	func setKeyColumnWidths(_ value: [InfoGridRow_KeyColumn_Width]) {
		keyColumnWidths = value
	}
	func getKeyColumnWidths() -> [InfoGridRow_KeyColumn_Width] {
		return keyColumnWidths
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: verticalSpacingBetweenRows) {
			
			if let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment {
				
				rows_incomingPayment(incomingPayment)
				
			} else if let outgoingPayment = paymentInfo.payment as? Lightning_kmpOutgoingPayment {
				
				rows_outgoingPayment(outgoingPayment)
			}
		}
		.padding(.bottom)
	}
	
	@ViewBuilder
	func rows_incomingPayment(_ incomingPayment: Lightning_kmpIncomingPayment) -> some View {
		
		if let paymentRequest = incomingPayment.origin.asInvoice()?.paymentRequest {
			
			header(NSLocalizedString("Payment Request", comment: "Title in DetailsView_IncomingPayment"))
		
			paymentRequest_invoiceCreated(paymentRequest)
			paymentRequest_amountRequested(paymentRequest)
			paymentRequest_paymentHash(paymentRequest)
		}
		
		if let received = incomingPayment.received {

			// There's usually just one receivedWith instance.
			// But there could technically be multiple, so we'll show a section for each if that's the case.
			
			let receivedWithArray = received.receivedWith.sorted { $0.identifiable < $1.identifiable }
			ForEach(receivedWithArray, id: \.identifiable) { receivedWith in
				
				header(NSLocalizedString("Payment Received", comment: "Title in DetailsView_IncomingPayment"))
				
				paymentReceived_receivedAt(received)
				paymentReceived_amountReceived(receivedWith)
				paymentReceived_via(receivedWith)
				paymentReceived_channelId(receivedWith)
			}
		}
	}
	
	@ViewBuilder
	func rows_outgoingPayment(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			header("lnurl-pay")
			
			lnurl_service(lnurlPay)
			lnurl_range(lnurlPay)
		}
		
		if let paymentRequest = outgoingPayment.details.asNormal()?.paymentRequest {
		
			header(NSLocalizedString("Payment Request", comment: "Title in DetailsView_IncomingPayment"))
		
			paymentRequest_invoiceCreated(paymentRequest)
			paymentRequest_amountRequested(paymentRequest)
			paymentRequest_paymentHash(paymentRequest)
		
		} else if let channelClosing = outgoingPayment.details.asChannelClosing() {
			
			header(NSLocalizedString("Channel Closing", comment: "Title in DetailsView_IncomingPayment"))
			
			channelClosing_channelId(channelClosing)
			if let onChain = outgoingPayment.status.asOnChain() {
				onChain_type(onChain) // this makes more sense in this section
			}
			channelClosing_btcAddress(channelClosing)
			channelClosing_addrType(channelClosing)
		}
		
		if let offChain = outgoingPayment.status.asOffChain() {
			
			header(NSLocalizedString("Payment Sent", comment: "Title in DetailsView_IncomingPayment"))
			
			offChain_completedAt(offChain)
			offChain_elapsed(outgoingPayment)
			offChain_amountReceived(outgoingPayment)
			offChain_fees(outgoingPayment)
			offChain_totalAmount(outgoingPayment)
			offChain_recipientPubkey(outgoingPayment)
		
		} else if let onChain = outgoingPayment.status.asOnChain() {
			
			header(NSLocalizedString("Closing Status", comment: "Title in DetailsView_IncomingPayment"))
			
			onChain_completedAt(onChain)
			onChain_claimed(onChain)
			onChain_btcTxids(onChain)
			
		} else if let failed = outgoingPayment.status.asFailed() {
			
			header(NSLocalizedString("Send Failed", comment: "Title in DetailsView_IncomingPayment"))
			
			failed_failedAt(failed)
			failed_reason(failed)
		}
		
		if outgoingPayment.parts.count > 0 && outgoingPayment.details.asChannelClosing() == nil {
			
			header(NSLocalizedString("Payment Parts", comment: "Title in DetailsView_IncomingPayment"))
			
			ForEach(outgoingPayment.parts.indices, id: \.self) { index in
				part_row(outgoingPayment.parts[index])
			}
		}
	}
	
	@ViewBuilder
	func header(_ title: String) -> some View {
		
		HStack {
			Spacer()
			Text(title)
				.lineLimit(1)
				.minimumScaleFactor(0.5)
				.font(.title3)
			Spacer()
		}
		.padding(.horizontal)
		.padding(.bottom, 12)
		.background(
			VStack {
				Spacer()
				RoundedRectangle(cornerRadius: 10)
					.frame(height: 1, alignment: .center)
					.foregroundColor(Color.appAccent)
			}
		)
		.padding(.top, 24)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func keyColumn(_ str: String) -> some View {
		
		Text(str.lowercased())
			.font(.subheadline)
			.fontWeight(.thin)
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func lnurl_service(_ lnurlPay: LNUrl.Pay) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("service", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(lnurlPay.lnurl.host)
		}
	}
	
	@ViewBuilder
	func lnurl_range(_ lnurlPay: LNUrl.Pay) -> some View {
		let identifier: String = #function
		
		if lnurlPay.maxSendable.msat > lnurlPay.minSendable.msat {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("range", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let minFormatted = Utils.formatBitcoin(msat: lnurlPay.minSendable, bitcoinUnit: .sat, policy: .showMsats)
				let maxFormatted = Utils.formatBitcoin(msat: lnurlPay.maxSendable, bitcoinUnit: .sat, policy: .showMsats)
				
				// is there a cleaner way to do this ???
				if minFormatted.hasFractionDigits {
				
					if maxFormatted.hasFractionDigits {
						
						Text(verbatim: "\(minFormatted.integerDigits)") +
						Text(verbatim: "\(minFormatted.decimalSeparator)\(minFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " – ") +
						Text(verbatim: "\(maxFormatted.integerDigits)") +
						Text(verbatim: "\(maxFormatted.decimalSeparator)\(maxFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: "\(minFormatted.integerDigits)") +
						Text(verbatim: "\(minFormatted.decimalSeparator)\(minFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " – ") +
						Text(verbatim: maxFormatted.digits) +
						Text(verbatim: " \(maxFormatted.type)")
					}
					
				} else {
					
					if maxFormatted.hasFractionDigits {
						
						Text(verbatim: minFormatted.digits) +
						Text(verbatim: " – ") +
						Text(verbatim: "\(maxFormatted.integerDigits)") +
						Text(verbatim: "\(maxFormatted.decimalSeparator)\(maxFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: minFormatted.digits) +
						Text(verbatim: " – ") +
						Text(verbatim: maxFormatted.digits) +
						Text(verbatim: " \(maxFormatted.type)")
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_invoiceCreated(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("invoice created", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: paymentRequest.timestampDate)
		}
	}
	
	@ViewBuilder
	func paymentRequest_amountRequested(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount requested", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			if let msat = paymentRequest.amount {
				commonValue_amount(msat: msat)
			} else {
				Text("Any amount")
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_paymentHash(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("payment hash", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let paymentHash = paymentInfo.payment.paymentHashString()
			Text(paymentHash)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = paymentHash
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func paymentReceived_receivedAt(_ received: Lightning_kmpIncomingPayment.Received) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
					
			keyColumn(NSLocalizedString("received at", comment: "Label in DetailsView_IncomingPayment"))
					
		} valueColumn: {
					
			commonValue_date(date: received.receivedAtDate)
		}
	}
	
	@ViewBuilder
	func paymentReceived_amountReceived(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount received", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amount(msat: receivedWith.amount)
		}
	}
	
	@ViewBuilder
	func paymentReceived_via(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
		
			keyColumn(NSLocalizedString("via", comment: "Label in DetailsView_IncomingPayment"))
		
		} valueColumn: {
		
			if let _ = receivedWith.asLightningPayment() {
				Text("Lightning network")
		
			} else if let _ = receivedWith.asNewChannel() {
				Text("New Channel (auto-created)")
		
			} else {
				Text("")
			}
		}
	}
	
	@ViewBuilder
	func paymentReceived_channelId(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		if let newChannel = receivedWith.asNewChannel() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("channel id", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let str = newChannel.channelId?.toHex() ?? "pending"
				Text(str)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = str
						}) {
							Text("Copy")
						}
					}
			}
		}
	}
	
	@ViewBuilder
	func channelClosing_channelId(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("channel id", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let str = channelClosing.channelId.toHex()
			Text(str)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = str
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func channelClosing_btcAddress(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("bitcoin address", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let bitcoinAddr = channelClosing.closingAddress
			Text(bitcoinAddr)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = bitcoinAddr
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func channelClosing_addrType(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("address type", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				if channelClosing.isSentToDefaultAddress {
					Text("Phoenix generated")
					Text("(derived from your seed)")
				} else {
					Text("External")
					Text("(you provided this address)")
				}
			}
		}
	}
	
	@ViewBuilder
	func offChain_completedAt(_ offChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOffChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
					
			keyColumn(NSLocalizedString("sent at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: offChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func offChain_elapsed(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		if let milliseconds = outgoingPayment.paymentTimeElapsed() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("elapsed", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				if milliseconds < 1_000 {
					Text("\(milliseconds) milliseconds")
					
				} else if milliseconds < (90 * 1_000) {
					Text("\(displayElapsedSeconds(milliseconds: milliseconds)) seconds")
					
				} else {
					let (minutes, seconds) = displayElapsedMinutes(milliseconds: milliseconds)
					
					Text("\(minutes):\(seconds) minutes")
				}
			}
		}
	}
	
	@ViewBuilder
	func offChain_amountReceived(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount received", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amount(msat: outgoingPayment.recipientAmount)
		}
	}
	
	@ViewBuilder
	func offChain_fees(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("fees paid", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let (display_msat, display_fiat) = displayAmounts(msat: outgoingPayment.fees)
			let display_percent = displayFeePercent(fees: outgoingPayment.fees, total: outgoingPayment.amount)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				if display_msat.hasFractionDigits { // has visible millisatoshi's
					Text(verbatim: "\(display_msat.integerDigits)") +
					Text(verbatim: "\(display_msat.decimalSeparator)\(display_msat.fractionDigits)")
						.foregroundColor(.secondary) +
					Text(verbatim: " \(display_msat.type)")
				} else {
					Text(verbatim: display_msat.string)
				}
				
				if let display_fiat = display_fiat {
					Text(verbatim: "≈ \(display_fiat.string)")
				}
				
				Text(verbatim: display_percent)
			}
		}
	}
	
	@ViewBuilder
	func offChain_totalAmount(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("total amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amount(msat: outgoingPayment.amount)
		}
	}
	
	@ViewBuilder
	func offChain_recipientPubkey(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("recipient pubkey", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(outgoingPayment.recipient.value.toHex())
		}
	}
	
	@ViewBuilder
	func onChain_type(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("type", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			switch onChain.closingType {
				case Lightning_kmpChannelClosingType.local   : Text(verbatim: "Local")
				case Lightning_kmpChannelClosingType.mutual  : Text(verbatim: "Mutual")
				case Lightning_kmpChannelClosingType.remote  : Text(verbatim: "Remote")
				case Lightning_kmpChannelClosingType.revoked : Text(verbatim: "Revoked")
				case Lightning_kmpChannelClosingType.other   : Text(verbatim: "Other")
				default                                      : Text(verbatim: "?")
			}
		}
	}
	
	@ViewBuilder
	func onChain_completedAt(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("completed at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: onChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func onChain_claimed(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("claimed amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let (display_sat, display_fiat) = displayAmounts(sat: onChain.claimed)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				Text(display_sat.string)
				if let display_fiat = display_fiat {
					Text(display_fiat.string)
				}
			}
		}
	}
	
	@ViewBuilder
	func onChain_btcTxids(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("bitcoin txids", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				ForEach(onChain.txids.indices, id: \.self) { index in
					
					let txid = onChain.txids[index].toHex()
					Text(txid)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = txid
							}) {
								Text("Copy")
							}
						}
				}
			}
		}
	}
	
	@ViewBuilder
	func failed_failedAt(_ failed: Lightning_kmpOutgoingPayment.StatusCompletedFailed) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("timestamp", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: failed.completedAtDate)
		}
	}
	
	@ViewBuilder
	func failed_reason(_ failed: Lightning_kmpOutgoingPayment.StatusCompletedFailed) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("reason", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(failed.reason.description)
		}
	}
	
	@ViewBuilder
	func part_row(_ part: Lightning_kmpOutgoingPayment.Part) -> some View {
		let identifier: String = #function
		let imgSize: CGFloat = 20
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			if let part_succeeded = part.status as? Lightning_kmpOutgoingPayment.PartStatusSucceeded {
				keyColumn(shortDisplayTime(date: part_succeeded.completedAtDate))
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.PartStatusFailed {
				keyColumn(shortDisplayTime(date: part_failed.completedAtDate))
				
			} else {
				keyColumn(shortDisplayTime(date: part.createdAtDate))
			}
			
		} valueColumn: {
		
			if let _ = part.status as? Lightning_kmpOutgoingPayment.PartStatusSucceeded {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image("ic_payment_sent")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(Color.appPositive)
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, policy: .showMsats)
						if formatted.hasFractionDigits { // has visible millisatoshi's
							Text(verbatim: "\(formatted.integerDigits)") +
							Text(verbatim: "\(formatted.decimalSeparator)\(formatted.fractionDigits)")
								.foregroundColor(.secondary) +
							Text(verbatim: " \(formatted.type)")
						} else {
							Text(verbatim: formatted.string)
						}
					}
				}
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.PartStatusFailed {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "xmark.circle")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(.appNegative)
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, policy: .showMsats)
						if formatted.hasFractionDigits { // has visible millisatoshi's
							Text(verbatim: "\(formatted.integerDigits)") +
							Text(verbatim: "\(formatted.decimalSeparator)\(formatted.fractionDigits)")
								.foregroundColor(.secondary) +
							Text(verbatim: " \(formatted.type)")
						} else {
							Text(verbatim: formatted.string)
						}
					}
					
					let code = part_failed.remoteFailureCode?.description ?? "local"
					Text(verbatim: "\(code): \(part_failed.details)")
				}
				
			} else {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image("ic_payment_sending")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(Color.borderColor)
						
						Text("pending")
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func commonValue_date(date: Date) -> some View {
		
		let (dayMonthYear, timeOfDay) = displayTimes(date: date)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			Text(dayMonthYear)
			Text(timeOfDay)
		}
	}
	
	@ViewBuilder
	func commonValue_amount(msat: Lightning_kmpMilliSatoshi) -> some View {
		
		let (display_msat, display_fiat) = displayAmounts(msat: msat)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			if display_msat.hasFractionDigits { // has visible millisatoshi's
				Text(verbatim: "\(display_msat.integerDigits)") +
				Text(verbatim: "\(display_msat.decimalSeparator)\(display_msat.fractionDigits)")
					.foregroundColor(.secondary) +
				Text(verbatim: " \(display_msat.type)")
			} else {
				Text(verbatim: display_msat.string)
			}
			
			if let display_fiat = display_fiat {
				Text(verbatim: "≈ \(display_fiat.string)")
			}
		}
	}
	
	func displayTimes(date: Date) -> (String, String) {
		
		let df = DateFormatter()
		df.dateStyle = .long
		df.timeStyle = .none
		let dayMonthYear = df.string(from: date)
		
		let tf = DateFormatter()
		tf.dateStyle = .none
		tf.timeStyle = .long
		let timeOfDay = tf.string(from: date)
		
		return (dayMonthYear, timeOfDay)
	}
	
	func shortDisplayTime(date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .none
		formatter.timeStyle = .medium
		
		return formatter.string(from: date)
	}
	
	func displayElapsedSeconds(milliseconds: Int64) -> String {
		
		let seconds = Double(milliseconds) / Double(1_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true
		formatter.minimumFractionDigits = 3
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: seconds))!
	}
	
	func displayElapsedMinutes(milliseconds: Int64) -> (String, String) {
		
		let minutes = milliseconds / (60 * 1_000)
		let seconds = milliseconds % (60 * 1_000) % 1_000
		
		let mFormatter = NumberFormatter()
		mFormatter.numberStyle = .decimal
		mFormatter.usesGroupingSeparator = true
		
		let minutesStr = mFormatter.string(from: NSNumber(value: minutes))!
		
		let sFormatter = NumberFormatter()
		sFormatter.minimumIntegerDigits = 2
		
		let secondsStr = sFormatter.string(from: NSNumber(value: seconds))!
		
		return (minutesStr, secondsStr)
	}
	
	func displayAmounts(msat: Lightning_kmpMilliSatoshi) -> (FormattedAmount, FormattedAmount?) {
		
		let display_msat = Utils.formatBitcoin(msat: msat, bitcoinUnit: .sat, policy: .showMsats)
		var display_fiat: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			display_fiat = Utils.formatFiat(msat: msat, exchangeRate: fiatExchangeRate)
		}
		
		return (display_msat, display_fiat)
	}
	
	func displayAmounts(sat: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount?) {
		
		let display_sat = Utils.formatBitcoin(sat: sat, bitcoinUnit: .sat)
		var display_fiat: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			display_fiat = Utils.formatFiat(sat: sat, exchangeRate: fiatExchangeRate)
		}
		
		return (display_sat, display_fiat)
	}
	
	func displayFeePercent(fees: Lightning_kmpMilliSatoshi, total: Lightning_kmpMilliSatoshi) -> String {
		
		let percent: Double
		if total.msat == 0 {
			percent = 0.0
		} else {
			percent = Double(fees.msat) / Double(total.msat)
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	struct InfoGridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
		
		let identifier: String
		let hSpacing: CGFloat
		let keyColumnWidth: CGFloat
		let keyColumn: KeyColumn
		let valueColumn: ValueColumn
		
		init(
			identifier: String,
			hSpacing: CGFloat,
			keyColumnWidth: CGFloat,
			@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
			@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
		) {
			self.identifier = identifier
			self.hSpacing = hSpacing
			self.keyColumnWidth = keyColumnWidth
			self.keyColumn = keyColumnBuilder()
			self.valueColumn = valueColumnBuilder()
		}
		
		var body: some View {
			
			InfoGridRow(identifier: identifier, hSpacing: hSpacing, keyColumnWidth: keyColumnWidth) {
				
				keyColumn
				
			} valueColumn: {
				
				valueColumn.font(.callout)
			}
		}
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

extension Lightning_kmpWalletPayment {
	
	fileprivate func paymentType() -> (String, String)? {
		
		// Will be displayed in the UI as:
		//
		// Type : value (explanation)
		//
		// where return value is: (value, explanation)
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asSwapIn() {
				let val = NSLocalizedString("Swap-In", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 1 -> 2", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = incomingPayment.origin.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.details.asSwapOut() {
				let val = NSLocalizedString("Swap-Out", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = outgoingPayment.details.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = outgoingPayment.details.asChannelClosing() {
				let val = NSLocalizedString("Channel Closing", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
		}
		
		return nil
	}
	
	fileprivate func paymentLink() -> URL? {
		
		var address: String? = nil
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
		
			if let swapIn = incomingPayment.origin.asSwapIn() {
				address = swapIn.address
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
		
			if let swapOut = outgoingPayment.details.asSwapOut() {
				address = swapOut.address
			}
		}
		
		if let address = address {
			let str: String
			if AppDelegate.get().business.chain.isTestnet() {
				str = "https://mempool.space/testnet/address/\(address)"
			} else {
				str = "https://mempool.space/address/\(address)"
			}
			return URL(string: str)
		}
		
		return nil
	}
	
	fileprivate func channelClosing() -> Lightning_kmpOutgoingPayment.DetailsChannelClosing? {
		
		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			if let result = outgoingPayment.details.asChannelClosing() {
				return result
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFees(currencyPrefs: CurrencyPrefs) -> (FormattedAmount, String)? {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
		
			// An incomingPayment may have fees if a new channel was automatically opened
			if let received = incomingPayment.received {
				
				let msat = received.receivedWith.map { $0.fees.msat }.reduce(0, +)
				if msat > 0 {
					
					let formattedAmt = Utils.format(currencyPrefs, msat: msat, policy: .showMsats)
					
					let exp = NSLocalizedString(
						"""
						In order to receive this payment, a new payment channel was opened. \
						This is not always required.
						""",
						comment: "Fees explanation"
					)
					
					return (formattedAmt, exp)
				}
				else {
					// I think it's nice to see "Fees: 0 sat" :)
					
					let formattedAmt = Utils.format(currencyPrefs, msat: 0, policy: .hideMsats)
					let exp = ""
					
					return (formattedAmt, exp)
				}
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.status.asFailed() {
				
				// no fees for failed payments
				return nil
				
			} else if let onChain = outgoingPayment.status.asOnChain() {
				
				// for on-chain payments, the fees are extracted from the mined transaction(s)
				
				let channelDrain: Lightning_kmpMilliSatoshi = outgoingPayment.recipientAmount
				let claimed = Lightning_kmpMilliSatoshi(sat: onChain.claimed)
				let fees = channelDrain.minus(other: claimed)
				let formattedAmt = Utils.format(currencyPrefs, msat: fees, policy: .showMsats)
				
				let txCount = onChain.component1().count
				let exp: String
				if txCount == 1 {
					exp = NSLocalizedString(
						"Bitcoin network fees paid for on-chain transaction. Payment required 1 transaction.",
						comment: "Fees explanation"
					)
				} else {
					exp = NSLocalizedString(
						"Bitcoin network fees paid for on-chain transactions. Payment required \(txCount) transactions.",
						comment: "Fees explanation"
					)
				}
				
				return (formattedAmt, exp)
				
			} else if let _ = outgoingPayment.status.asOffChain() {
				
				let msat = outgoingPayment.fees.msat
				let formattedAmt = Utils.format(currencyPrefs, msat: msat, policy: .showMsats)
				
				var parts = 0
				var hops = 0
				for part in outgoingPayment.parts {
					
					parts += 1
					hops = part.route.count
				}
				
				let exp: String
				if parts == 1 {
					if hops == 1 {
						exp = NSLocalizedString(
							"Lightning fees for routing the payment. Payment required 1 hop.",
							comment: "Fees explanation"
						)
					} else {
						exp = String(format: NSLocalizedString(
							"Lightning fees for routing the payment. Payment required %d hops.",
							comment: "Fees explanation"),
							hops
						)
					}
					
				} else {
					exp = String(format: NSLocalizedString(
						"Lightning fees for routing the payment. Payment was divided into %d parts, using %d hops.",
						comment: "Fees explanation"),
						parts, hops
					)
				}
				
				return (formattedAmt, exp)
			}
		}
		
		return nil
	}
	
	/// If the OutgoingPayment succeeded or failed, reports the total elapsed time.
	/// The return value is in number of milliseconds.
	///
	fileprivate func paymentTimeElapsed() -> Int64? {

		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			let started = outgoingPayment.createdAt
			var finished: Int64? = nil
			
			if let failed = outgoingPayment.status.asFailed() {
				finished = failed.completedAt
				
			} else if let succeeded = outgoingPayment.status.asSucceeded() {
				finished = succeeded.completedAt
			}
			
			if let finished = finished, finished > started {
				return finished - started
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFinalError() -> String? {

		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let failed = outgoingPayment.status.asFailed() {
				
				return failed.reason.description
			}
		}
		
		return nil
	}
}

fileprivate struct EditInfoView: View, ViewName {
	
	@Binding var paymentInfo: WalletPaymentInfo
	
	let defaultDescText: String
	let originalDescText: String?
	@State var descText: String
	
	let maxDescCount: Int = 64
	@State var remainingDescCount: Int
	
	let originalNotesText: String?
	@State var notesText: String
	
	let maxNotesCount: Int = 280
	@State var remainingNotesCount: Int
	
	@State var hasChanges = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(paymentInfo: Binding<WalletPaymentInfo>) {
		_paymentInfo = paymentInfo
		
		let pi = paymentInfo.wrappedValue
		
		defaultDescText = pi.paymentDescription(includingUserDescription: false) ?? ""
		let realizedDesc = pi.paymentDescription(includingUserDescription: true) ?? ""
		
		if realizedDesc == defaultDescText {
			originalDescText = nil
			_descText = State(initialValue: "")
			_remainingDescCount = State(initialValue: maxDescCount)
		} else {
			originalDescText = realizedDesc
			_descText = State(initialValue: realizedDesc)
			_remainingDescCount = State(initialValue: maxDescCount - realizedDesc.count)
		}
		
		originalNotesText = pi.metadata.userNotes
		let notes = pi.metadata.userNotes ?? ""
		
		_notesText = State(initialValue: notes)
		_remainingNotesCount = State(initialValue: maxNotesCount - notes.count)
	}
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					saveButtonTapped()
				} label: {
					HStack(alignment: .center, spacing: 4) {
						Image(systemName: "chevron.backward")
							.imageScale(.medium)
						Text("Save")
					}
				}
				Spacer()
			}
			.font(.title3)
			.padding()
			
			ScrollView {
				content
			}
		}
		.navigationBarTitle(
			NSLocalizedString("Edit Info", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.navigationBarHidden(true)
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Description:")
				.padding(.leading, 8)
				.padding(.bottom, 4)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(defaultDescText, text: $descText)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					descText = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(descText == "")
			}
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color(UIColor.separator), lineWidth: 1)
			)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Text("\(remainingDescCount) remaining")
					.font(.callout)
					.foregroundColor(remainingDescCount >= 0 ? Color.secondary : Color.appNegative)
			}
			.padding([.leading, .trailing], 8)
			.padding(.top, 4)
			
			Text("Notes:")
				.padding(.top, 20)
				.padding(.leading, 8)
				.padding(.bottom, 4)
				
			TextEditor(text: $notesText)
				.frame(minHeight: 80, maxHeight: 320)
				.padding(.all, 8)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(Color(UIColor.separator), lineWidth: 1)
				)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				
				Text("\(remainingNotesCount) remaining")
					.font(.callout)
					.foregroundColor(remainingNotesCount >= 0 ? Color.secondary : Color.appNegative)
			}
			.padding([.leading, .trailing], 8)
			.padding(.top, 4)
			
			Button {
				discardButtonTapped()
			} label: {
				Text("Discard Changes")
			}
			.disabled(!hasChanges)
			.padding(.top, 8)
		}
		.padding(.top)
		.padding([.leading, .trailing])
		.onChange(of: descText) {
			descTextDidChange($0)
		}
		.onChange(of: notesText) {
			notesTextDidChange($0)
		}
	}
	
	func descTextDidChange(_ newText: String) {
		log.trace("[\(viewName)] descTextDidChange()")
		
		remainingDescCount = maxDescCount - newText.count
		updateHasChanges()
	}
	
	func notesTextDidChange(_ newText: String) {
		log.trace("[\(viewName)] notesTextDidChange()")
		
		remainingNotesCount = maxNotesCount - newText.count
		updateHasChanges()
	}
	
	func updateHasChanges() {
		
		hasChanges =
			descText != (originalDescText ?? "") ||
			notesText != (originalNotesText ?? "")
	}
	
	func discardButtonTapped() {
		log.trace("[\(viewName)] discardButtonTapped()")
		
		let realizedDesc = originalDescText ?? ""
		if realizedDesc == defaultDescText {
			descText = ""
			remainingDescCount = maxDescCount
		} else {
			descText = realizedDesc
			remainingDescCount = maxDescCount - realizedDesc.count
		}
		
		let realizedNotes = originalNotesText ?? ""
		notesText = realizedNotes
		remainingNotesCount = maxNotesCount - realizedNotes.count
	}
	
	func saveButtonTapped() {
		log.trace("[\(viewName)] saveButtonTapped()")
		
		let paymentId = paymentInfo.id()
		var desc = descText.trimmingCharacters(in: .whitespacesAndNewlines)
		
		if desc.count > maxDescCount {
			let endIdx = desc.index(desc.startIndex, offsetBy: maxDescCount)
			let substr = desc[desc.startIndex ..< endIdx]
			desc = String(substr)
		}
		
		let newDesc = (desc == defaultDescText) ? nil : (desc.count == 0 ? nil : desc)
		
		var notes = notesText.trimmingCharacters(in: .whitespacesAndNewlines)
		
		if notes.count > maxNotesCount {
			let endIdx = notes.index(notes.startIndex, offsetBy: maxNotesCount)
			let substr = notes[notes.startIndex ..< endIdx]
			notes = String(substr)
		}
		
		let newNotes = notes.count == 0 ? nil : notes
		
		if (originalDescText != newDesc) || (originalNotesText != newNotes) {
		
			let business = AppDelegate.get().business
			business.databaseManager.paymentsDb { (paymentsDb: SqlitePaymentsDb?, _) in
				
				paymentsDb?.updateMetadata(id: paymentId, userDescription: newDesc, userNotes: newNotes) { (_, err) in
					
					if let err = err {
						log.error("paymentsDb.updateMetadata: \(String(describing: err))")
					}
				}
			}
		} else {
			log.debug("[\(viewName)] no changes - nothing to save")
		}
		
		presentationMode.wrappedValue.dismiss()
	}
}
