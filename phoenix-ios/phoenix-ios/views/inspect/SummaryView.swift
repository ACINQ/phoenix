import SwiftUI
import PhoenixShared
import Popovers
import Combine

fileprivate let filename = "SummaryView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SummaryView: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		case DetailsView
		case EditInfoView
		case CpfpView(onChainPayment: Lightning_kmpOnChainOutgoingPayment)
		case ContactView(contact: ContactInfo?, info: AddToContactsInfo?)
		
		var description: String {
			switch self {
			case .DetailsView       : return "DetailsView"
			case .EditInfoView      : return "EditInfoView"
			case .CpfpView(_)       : return "CpfpView"
			case .ContactView(_, _) : return "ContactView"
			}
		}
	}
	
	let location: PaymentView.Location
	
	@State var paymentInfo: WalletPaymentInfo
	@State var causedBy: Lightning_kmpWalletPayment? = nil
	
	@State var blockchainConfirmations: Int? = nil
	@State var showBlockchainExplorerOptions = false
	
	@State var showOriginalFiatValue = GlobalEnvironment.currencyPrefs.showOriginalFiatValue
	@State var showFiatValueExplanation = false
	
	@State var showDeletePaymentConfirmationDialog = false
	
	@State var didAppear = false
	
	enum ButtonListType: Int {
		case standard = 1
		case squeezed = 2
		case compact = 3
		case accessible = 4
	}
	@State var buttonListType: [DynamicTypeSize: ButtonListType] = [:]
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	// </iOS_16_workarounds>
	
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
	
	@StateObject var blockchainMonitorState = BlockchainMonitorState()
	
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	init(location: PaymentView.Location, paymentInfo: WalletPaymentInfo) {
		
		self.location = location
		self.paymentInfo = paymentInfo
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("Payment")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarHidden(location.isSheet ? true : false)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
			.background(
				location.isSheet ?
					Color.clear.ignoresSafeArea(.all, edges: .bottom) :
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
			)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
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
		.onChange(of: paymentInfo) { _ in
			paymentInfoChanged()
		}
		.onReceive(Biz.business.contactsManager.contactsListPublisher()) { _ in
			contactsChanged()
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
			summaryInfoGrid()
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
					if payment is Lightning_kmpManualLiquidityPurchasePayment {
						Text("Manual liquidity")
						
					} else if payment is Lightning_kmpAutomaticLiquidityPurchasePayment {
						Text("Channel management")
						
					} else if payment is Lightning_kmpOutgoingPayment {
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
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			let confirmations = blockchainConfirmations
			
			ZStack(alignment: Alignment(horizontal: .center, vertical: .top)) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.secondary))
					
					Text("Checking blockchain…")
						.font(.callout)
						.foregroundColor(.secondary)
					
				} // </HStack>
				.isHidden(confirmations != nil)
				
				Button {
					showBlockchainExplorerOptions = true
				} label: {
					Group {
						if let confirmations {
							if confirmations == 1 {
								Text("1 confirmation")
							} else if confirmations <= 6 {
								Text("\(confirmations) confirmations")
							} else {
								Text("6+ confirmations")
							}
						} else {
							Text("? confirmations")
						}
					} // </Group>
					.font(.subheadline)
					
				} // </Button>
				.isHidden(confirmations == nil)
				
			} // </ZStack>
			
			if confirmations == 0 && supportsBumpFee(onChainPayment) {
				Button {
					navigateTo(.CpfpView(onChainPayment: onChainPayment))
				} label: {
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
			
		} // </VStack>
		.padding(.top, 10)
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
	func summaryInfoGrid() -> some View {
		
		SummaryInfoGrid(
			paymentInfo: $paymentInfo,
			causedBy: $causedBy,
			showOriginalFiatValue: $showOriginalFiatValue,
			addToContacts: addToContacts,
			showContactView: showContactView,
			switchToPayment: switchToPayment
		)
	}
	
	@ViewBuilder
	func buttonList() -> some View {
		
		Group {
			let type = buttonListType[dynamicTypeSize] ?? ButtonListType.standard
			switch type {
				case .standard   : buttonList_standard()
				case .squeezed   : buttonList_squeezed()
				case .compact    : buttonList_compact()
				case .accessible : buttonList_accessibility()
			}
		} // </Group>
		.confirmationDialog("Delete payment?",
			isPresented: $showDeletePaymentConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Delete payment", role: ButtonRole.destructive) {
				deletePayment()
			}
		}
	}
	
	@ViewBuilder
	func buttonList_standard() -> some View {
		
		// We're making all the buttons the same size.
		//
		// ---------------------------
		//  Details |  Edit  | Delete
		// ---------------------------
		//   ^          ^         ^    < same size
		
		let type = ButtonListType.standard
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
			
			TruncatableView(identifier: "standard-details", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.DetailsView)
				} label: {
					buttonLabel_details()
						.lineLimit(1)
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "details")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "standard-edit", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.EditInfoView)
				} label: {
					buttonLabel_edit()
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.center)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "edit")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "standard-delete", fixedHorizontal: true, fixedVertical: true) {
				Button {
					showDeletePaymentConfirmationDialog = true
				} label: {
					buttonLabel_delete()
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.leading)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "delete")
			}
		}
		.padding(.all)
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	@ViewBuilder
	func buttonList_squeezed() -> some View {
		
		// There's not enough space to make all the buttons the same size.
		// So we're just making the left & right buttons the same size.
		// This still ensures that the center button is perfectly centered on screen.
		//
		// ---------------------------
		//  Details |  Edit  | Delete
		// ---------------------------
		//   ^                    ^    < same size
		
		let type = ButtonListType.squeezed
		
		HStack(alignment: VerticalAlignment.center, spacing: 16) {
			
			TruncatableView(identifier: "squeezed-details", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.DetailsView)
				} label: {
					buttonLabel_details()
						.lineLimit(1)
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.trailing)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "edit")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "squeezed-edit", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.EditInfoView)
				} label: {
					buttonLabel_edit()
						.lineLimit(1)
				}
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "edit")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "squeezed-delete", fixedHorizontal: true, fixedVertical: true) {
				Button {
					showDeletePaymentConfirmationDialog = true
				} label: {
					buttonLabel_delete()
						.lineLimit(1)
				}
				.frame(minWidth: buttonWidth, alignment: Alignment.leading)
				.read(buttonWidthReader)
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "delete")
			}
		}
		.padding(.horizontal, 10) // allow content to be closer to edges
		.padding(.vertical)
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	@ViewBuilder
	func buttonList_compact() -> some View {
		
		// There's a large font being used, and possibly a small screen too.
		// Thus horizontal space is tight.
		//
		// So we're going to just try to squeeze all the buttons into a single line.
		//
		// -----------------------
		// Details | Edit | Delete
		// -----------------------
		//             ^ might not be centered, but at least the buttons fit on 1 line
		
		let type = ButtonListType.compact
		
		HStack(alignment: VerticalAlignment.center, spacing: 8) {
			
			TruncatableView(identifier: "compatct-details", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.DetailsView)
				} label: {
					buttonLabel_details()
						.lineLimit(1)
				}
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "details")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "compact-edit", fixedHorizontal: true, fixedVertical: true) {
				Button {
					navigateTo(.EditInfoView)
				} label: {
					buttonLabel_edit()
						.lineLimit(1)
				}
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "edit")
			}
			
			if let buttonHeight {
				Divider().frame(height: buttonHeight)
			}
			
			TruncatableView(identifier: "compact-delete", fixedHorizontal: true, fixedVertical: true) {
				Button {
					showDeletePaymentConfirmationDialog = true
				} label: {
					buttonLabel_delete()
						.lineLimit(1)
				}
				.read(buttonHeightReader)
			} wasTruncated: {
				buttonListTruncationDetected(type, "delete")
			}
		}
		.padding(.horizontal, 4) // allow content to be closer to edges
		.padding(.vertical)
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	@ViewBuilder
	func buttonList_accessibility() -> some View {
		
		// There's a large font being used, and possibly a small screen too.
		// Horizontal space is so tight that we can't get the 3 buttons on a single line.
		//
		// So we're going to put them on multiple lines.
		//
		// --------------
		// Details | Edit
		//     Delete
		// --------------
		
		VStack(alignment: HorizontalAlignment.center, spacing: 16) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				
				Button {
					navigateTo(.DetailsView)
				} label: {
					buttonLabel_details()
						.lineLimit(1) // see note below
				}
				.read(buttonHeightReader)
				
				if let buttonHeight {
					Divider().frame(height: buttonHeight)
				}
				
				Button {
					navigateTo(.EditInfoView)
				} label: {
					buttonLabel_edit()
						.lineLimit(1) // see note below
				}
				.read(buttonHeightReader)
			}
			.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
			
			Button {
				showDeletePaymentConfirmationDialog = true
			} label: {
				buttonLabel_delete()
			}
			.layoutPriority(1)
			// ^^^^^^^^^^^^^^^ On iOS 16, the layout system seems to give as much
			// height as is available to these buttons. With the end result being that
			// the calculated buttonHeight is way too big, and the Divider looks really odd.
			// So we tell the UI to give priority to this button,
			// and we restrict the other buttons to 1 line.
		}
		.padding(.horizontal, 4) // allow content to be closer to edges
		.padding(.vertical)
	}
	
	@ViewBuilder
	func buttonLabel_details() -> some View {
		
		Label {
			Text("Details")
		} icon: {
			Image(systemName: "magnifyingglass").imageScale(.small)
		}
	}
	
	@ViewBuilder
	func buttonLabel_edit() -> some View {
		
		Label {
			Text("Edit")
		} icon: {
			Image(systemName: "pencil.line").imageScale(.small)
		}
	}
	
	@ViewBuilder
	func buttonLabel_delete() -> some View {
		
		Label {
			Text("Delete")
		} icon: {
			Image(systemName: "eraser.line.dashed").imageScale(.small)
		}
		.foregroundColor(.appNegative)
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .DetailsView:
			DetailsView(
				location: wrappedLocation(),
				paymentInfo: $paymentInfo,
				showOriginalFiatValue: $showOriginalFiatValue,
				showFiatValueExplanation: $showFiatValueExplanation,
				switchToPayment: switchToPayment
			)
			
		case .EditInfoView:
			EditInfoView(
				location: wrappedLocation(),
				paymentInfo: $paymentInfo
			)
			
		case .CpfpView(let onChainPayment):
			CpfpView(
				location: wrappedLocation(),
				onChainPayment: onChainPayment
			)
			
		case .ContactView(let contact, let info):
			ManageContact(
				location: manageContactLocation(),
				popTo: nil,
				info: info,
				contact: contact,
				contactUpdated: contactUpdated
			)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
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
	
	func wrappedLocation() -> PaymentView.Location {
		
		switch location {
		case .sheet(_):
			return location
		case .embedded(_):
			return .embedded(popTo: popToWrapper)
		}
	}
	
	func manageContactLocation() -> ManageContact.Location {
		
		switch location {
		case .sheet(let closeAction):
			return ManageContact.Location.sheet(closeAction: closeAction)
		case .embedded(_):
			return ManageContact.Location.embedded
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
	
	func monitorBlockchain() async {
		
		// Architecture note:
		// We need the ability to reset the View to display a completely different payment.
		// This means our Task to monitor the blockchain needs to properly respond whenever
		// the `paymentInfo` property is changed.
		
		var lastPaymentId: Lightning_kmpUUID? = nil
		
		// Note: When the task is cancelled, the `values` stream returns nil, and we exit the loop
		
		for await paymentInfo in blockchainMonitorState.paymentInfoPublisher.values {
			
			if let paymentInfo, paymentInfo.payment.id == lastPaymentId {
				log.debug("monitorBlockchain: ignoring duplicate paymentInfo")
				continue
			}
			lastPaymentId = paymentInfo?.payment.id
			
			if let currentTask = blockchainMonitorState.currentTaskPublisher.value {
				log.debug("monitorBlockchain: currentTask.cancel()")
				currentTask.cancel()
			}
			
			if let paymentInfo {
				log.debug("monitorBlockchain: processing new paymentInfo")
				
				let newTask = Task { @MainActor in
					await monitorBlockchain(paymentInfo)
				}
				blockchainMonitorState.currentTaskPublisher.send(newTask)
				
			} else {
				log.debug("monitorBlockchain: paymentInfo is nil")
				blockchainMonitorState.currentTaskPublisher.send(nil)
			}
		}
		
		if let currentTask = blockchainMonitorState.currentTaskPublisher.value {
			log.debug("monitorBlockchain: currentTask.cancel()")
			currentTask.cancel()
		}
		
		log.debug("monitorBlockchain: terminated")
	}
	
	func monitorBlockchain(_ paymentInfo: WalletPaymentInfo) async {
		
		let pid: String = paymentInfo.payment.id.description().prefix(maxLength: 8)
		log.trace("monitorBlockchain(\(pid))")
		
		guard let onChainPayment = paymentInfo.payment as? Lightning_kmpOnChainOutgoingPayment else {
			log.debug("monitorBlockchain(\(pid)): not an on-chain payment")
			return
		}
		
		if let confirmedAtDate = onChainPayment.confirmedAtDate {
			let elapsed = confirmedAtDate.timeIntervalSinceNow * -1.0
			if elapsed > 24.hours() {
				// It was marked as mined more than 24 hours ago.
				// So there's really no need to check the exact confirmation count anymore.
				log.debug("monitorBlockchain(\(pid)): confirmedAt > 24.hours.ago")
				self.blockchainConfirmations = 7
				return
			}
		}
		
		let isDone = await updateConfirmations(onChainPayment, pid)
		guard !isDone else {
			log.debug("monitorBlockchain(\(pid)): done")
			return
		}
		
		// Note: When the task is cancelled, the `values` stream returns nil, and we exit the loop
		
		for await notification in Biz.business.electrumClient.notificationsPublisher().values {
			
			if !(notification is Lightning_kmpHeaderSubscriptionResponse) {
				log.debug("monitorBlockchain(\(pid)): notification isNot HeaderSubscriptionResponse")
				continue
			}
			
			// A new block was mined !
			// Update confirmation count if needed.
			
			let isDone = await updateConfirmations(onChainPayment, pid)
			guard !isDone else {
				log.debug("monitorBlockchain(\(pid)): done")
				return
			}
			
			log.debug("monitorBlockchain(\(pid)): Waiting for next electrum notification...")
		}
		
		log.debug("monitorBlockchain(\(pid)): terminated")
	}
	
	func updateConfirmations(
		_ onChainPayment: Lightning_kmpOnChainOutgoingPayment,
		_ pid: String
	) async -> Bool {
		
		log.trace("updateConfirmations(\(pid))")
		
		let confirmations = await fetchConfirmations(onChainPayment, pid)
		guard !Task.isCancelled else {
			log.debug("updateConfirmations(\(pid)): Task.isCancelled")
			return true
		}
		self.blockchainConfirmations = confirmations
		
		if confirmations > 6 {
			// No need to continue checking confirmation count,
			// because the UI displays "6+" from this point forward.
			log.debug("updateConfirmations(\(pid)): confirmations > 6")
			return true
		} else {
			return false
		}
	}
	
	func fetchConfirmations(
		_ onChainPayment: Lightning_kmpOnChainOutgoingPayment,
		_ pid: String
	) async -> Int {
		
		log.trace("fetchConfirmations(\(pid))")
		
		do {
			let result = try await Biz.business.electrumClient.kotlin_getConfirmations(txid: onChainPayment.txId)
			
			let confirmations = result?.intValue ?? 0
			log.debug("fetchConfirmations(\(pid)): => \(confirmations)")
			return confirmations
		} catch {
			log.error("checkConfirmations(\(pid)): error: \(error)")
			return 0
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			
			// First time displaying the SummaryView (coming from HomeView)
			
			paymentInfoChanged()
			
		} else {
			log.trace("subsequent appearance")
			
			// We are returning from a subview (e.g. DetailsView, EditInfoView) via the NavigationController

			// The payment metadata may have changed (e.g. description/notes modified).
			// So we need to refresh the payment info.
			forceRefreshPaymentInfo()
			
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
	
	func contactUpdated(_ updatedContact: ContactInfo?) {
		log.trace("contactUpdated()")
		
		// Nothing to do here.
		// It's better to wait for the `contactsChanged` notification.
		//
		// That's because after we receive the `contactsChanged` notification,
		// we're sure that a re-fetch of the payment will contain the correct contact info.
		//
		// Whereas, if we fetch right now, there's a chance we're too early,
		// and the contact info could be out-of-date.
	}
	
	func contactsChanged() {
		log.trace("contactsChanged()")
		
		if didAppear {
			forceRefreshPaymentInfo()
		}
	}
	
	func forceRefreshPaymentInfo() {
		log.trace("forceRefreshPaymentInfo()")
		
		Biz.business.paymentsManager.getPayment(id: paymentInfo.payment.id) {
			(result: WalletPaymentInfo?, _) in
			
			if let result {
				if let contact = result.contact {
					log.debug("result.contact = \(contact.name)")
				} else {
					log.debug("result.contact = <nil>")
				}
				paymentInfo = result
			}
		}
	}
	
	func paymentInfoChanged() {
		log.trace("paymentInfoChanged()")

		blockchainMonitorState.paymentInfoPublisher.send(paymentInfo)
		
		if let liquidity = paymentInfo.payment as? Lightning_kmpAutomaticLiquidityPurchasePayment {
			Task { @MainActor in
				do {
					let paymentsManager = Biz.business.paymentsManager
					causedBy = try await paymentsManager.getIncomingPaymentForTxId(txId: liquidity.txId)
					log.debug("causedBy = \(causedBy?.id.description() ?? "<nil>")")
				} catch {
					log.error("listIncomingPaymentsForTxId(): error: \(error)")
				}
			}
		} else {
			log.debug("causedBy = nil (payment !is AutomaticLiquidityPurchasePayment)")
			causedBy = nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.description))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func popToWrapper(_ destination: PopToDestination) {
		log.trace("popToWrapper(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popToWrapper(): This function is for iOS 16 only !")
		} else {
			popToDestination = destination
			if case .embedded(let popTo) = location {
				popTo(destination)
			}
		}
	}
	
	func buttonListTruncationDetected(_ type: ButtonListType, _ identifier: String) {
		
		switch type {
		case .standard:
			log.debug("buttonListTruncationDetected: standard (\(identifier))")
			buttonListType[dynamicTypeSize] = .squeezed
		
		case .squeezed:
			log.debug("buttonListTruncationDetected: squeezed (\(identifier))")
			buttonListType[dynamicTypeSize] = .compact
			
		case .compact:
			log.debug("buttonListTruncationDetected: compact (\(identifier))")
			buttonListType[dynamicTypeSize] = .accessible
			
		case .accessible:
			log.debug("buttonListTruncationDetected: accessible (\(identifier))")
			break
		}
	}
	
	func addToContacts() {
		log.trace("addToContacts()")
		
		guard paymentInfo.contact == nil else {
			log.info("addToContacts(): ignoring: contact already exists")
			return
		}
		
		guard let info = paymentInfo.addToContactsInfo() else {
			log.info("addToContacts(): ignoring: missing required info")
			return
		}
		
		let count: Int = Biz.business.contactsManager.contactsListCurrentValue().count
		if count == 0 {
			// User doesn't have any contacts.
			// No choice but to create a new contact.
			addContact_createNew(info)
			
		} else {
			
			smartModalState.display(dismissable: true) {
				AddContactOptionsSheet(
					createNewContact: { addContact_createNew(info) },
					addToExistingContact: { addContact_selectExisting(info) }
				)
			}
		}
	}
	
	private func addContact_createNew(
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_createNew()")
		
		navigateTo(.ContactView(contact: nil, info: info))
	}
	
	private func addContact_selectExisting(
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_selectExisting()")
		
		smartModalState.display(dismissable: true) {
			ContactsListSheet(didSelectContact: { existingContact in
				log.debug("didSelectContact")
				smartModalState.onNextDidDisappear {
					addContact_addToExisting(existingContact, info)
				}
			})
		}
	}
	
	private func addContact_addToExisting(
		_ existingContact: ContactInfo,
		_ info: AddToContactsInfo
	) {
		log.trace("addContact_addToExisting()")
		
		navigateTo(.ContactView(contact: existingContact, info: info))
	}
	
	func showContactView(_ contact: ContactInfo) {
		log.trace("showContactView()")
		
		navigateTo(.ContactView(contact: contact, info: nil))
	}
	
	func switchToPayment(_ paymentId: Lightning_kmpUUID) {
		log.trace("switchToPayment: \(paymentId.description())")
		
		Biz.business.paymentsManager.getPayment(id: paymentId) {
			(result: WalletPaymentInfo?, _) in
			
			if let result {
				paymentInfo = result
				blockchainConfirmations = nil
			}
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
		
		Task { @MainActor in
			do {
				let paymentsDb = try await Biz.business.databaseManager.paymentsDb()
				try await paymentsDb.deletePayment(paymentId: paymentInfo.payment.id, notify: true)
				
			} catch {
				log.error("Error deleting payment: \(error)")
			}
		}
		
		switch location {
		case .sheet(let closeSheet):
			closeSheet()
		case .embedded:
			presentationMode.wrappedValue.dismiss()
		}
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

class BlockchainMonitorState: ObservableObject {
	let paymentInfoPublisher = CurrentValueSubject<WalletPaymentInfo?, Never>(nil)
	let currentTaskPublisher = CurrentValueSubject<Task<(), Never>?, Never>(nil)
}
