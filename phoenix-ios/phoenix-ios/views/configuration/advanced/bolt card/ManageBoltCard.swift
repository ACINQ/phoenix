import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "ManageBoltCard"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ManageBoltCard: View {
	
	enum NavLinkTag: Hashable, CustomStringConvertible {
		
		case CurrencyConverter(
			initialAmount : CurrencyAmount?,
			didChange     : ((CurrencyAmount?) -> Void)?,
			didClose      : (() -> Void)?
		)
		
		private var internalValue: Int {
			switch self {
				case .CurrencyConverter(_, _, _): return 1
			}
		}
		
		static func == (lhs: NavLinkTag, rhs: NavLinkTag) -> Bool {
			return lhs.internalValue == rhs.internalValue
		}
		
		func hash(into hasher: inout Hasher) {
			hasher.combine(self.internalValue)
		}
		
		var description: String {
			switch self {
				case .CurrencyConverter: return "CurrencyConverter"
			}
		}
	}
	
	struct SpendingLimitGraphInfo {
		let spent: String
		let spentAmount: Double
		
		let remaining: String
		let remainingAmount: Double
		
		let total: String
		let totalAmount: Double
	}
	
	@State var cardInfo: BoltCardInfo
	let isNewCard: Bool
	
	@State var name: String = ""
	@State var isActive: Bool = true
	
	@State var currencyList: [Currency] = [Currency.bitcoin(.sat)]
	
	@State var dailyLimit_currencyStr: String = Currency.bitcoin(.sat).shortName
	@State var dailyLimit_currency: Currency = Currency.bitcoin(.sat)
	@State var dailyLimit_amountStr: String = ""
	@State var dailyLimit_parsedAmount: Result<Double, TextFieldCurrencyStylerError> = .failure(.emptyInput)
	
	@State var monthlyLimit_currencyStr: String = Currency.bitcoin(.sat).shortName
	@State var monthlyLimit_currency: Currency = Currency.bitcoin(.sat)
	@State var monthlyLimit_amountStr: String = ""
	@State var monthlyLimit_parsedAmount: Result<Double, TextFieldCurrencyStylerError> = .failure(.emptyInput)
	
	@State var cardAmounts: SqliteCardsDb.CardAmounts? = nil
	
	@State var dailyCardPaymentsAmount: Double = 0
	@State var monthlyCardPaymentsAmount: Double = 0
	
	@State var isSaving: Bool = false
	@State var showDiscardChangesConfirmationDialog: Bool = false
	@State var showDeleteContactConfirmationDialog: Bool = false
	
	@State var didAppear: Bool = false
	@State var didDisplayWelcome: Bool = false
	
	@State var ignoreChanges: Bool = true
	@State var isFirstUserEdit: Bool = true
	
	@State var popoverPresent_dailyLimit: Bool = false
	@State var popoverPresent_monthlyLimit: Bool = false
	
	@State var cardWasArchived: Bool = false
	@State var cardWasReset: Bool = false
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@StateObject var toast = Toast()
	
	@ObservedObject var currencyPrefs = CurrencyPrefs.current
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	let didBecomeActivePublisher = NotificationCenter.default.publisher(
		for: UIApplication.didBecomeActiveNotification
	)
	
	init(cardInfo: BoltCardInfo, isNewCard: Bool) {
		self.cardInfo = cardInfo
		self.isNewCard = isNewCard
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("Manage Card")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarBackButtonHidden(true)
			.toolbar { toolbarItems() }
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
			.task {
				await fetchCardAmounts()
			}
	}
	
	@ToolbarContentBuilder
	func toolbarItems() -> some ToolbarContent {
		
		if isNewCard || cardWasArchived || cardWasReset {
			ToolbarItem(placement: .navigationBarLeading) {
				Button {
					saveButtonTapped()
				} label: {
					Text("Done").font(.headline)
				}
				.disabled(!canSave || isSaving) // subtle difference here
				.accessibilityLabel("Save changes")
			}
		} else {
			ToolbarItem(placement: .navigationBarLeading) {
				Button {
					cancelButtonTapped()
				} label: {
					Text("Cancel").font(.headline)
				}
				.disabled(isSaving)
				.accessibilityLabel("Discard changes")
			}
			ToolbarItem(placement: .navigationBarTrailing) {
				Button {
					saveButtonTapped()
				} label: {
					Text("Done").font(.headline)
				}
				.disabled(!hasChanges || !canSave || isSaving)
				.accessibilityLabel("Save changes")
			}
		}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
			toast.view()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_name()
			if !cardInfo.isForeign {
				section_status()
			}
			if !cardInfo.isForeign && !cardInfo.isArchived {
				section_limits()
			}
			section_managementTasks()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear {
			onAppear()
		}
		.onChange(of: cardInfo) { _ in
			cardInfoChanged()
		}
		.onChange(of: isActive) { _ in
			isActiveChanged()
		}
		.onChange(of: dailyLimit_currencyStr) { _ in
			dailyLimit_currencyPickerDidChange()
		}
		.onChange(of: monthlyLimit_currencyStr) { _ in
			monthlyLimit_currencyPickerDidChange()
		}
		.onChange(of: dailyLimit_currency) { _ in
			dailyLimit_currencyChanged()
		}
		.onChange(of: monthlyLimit_currency) { _ in
			monthlyLimit_currencyChanged()
		}
		.onChange(of: cardAmounts) { _ in
			cardAmountsChanged()
		}
		.onReceive(didBecomeActivePublisher) { _ in
			applicationDidBecomeActive()
		}
		.confirmationDialog("Discard changes?",
			isPresented: $showDiscardChangesConfirmationDialog,
			titleVisibility: Visibility.hidden
		) {
			Button("Discard changes", role: ButtonRole.destructive) {
				close()
			}
		}
	}
	
	@ViewBuilder
	func section_name() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(BoltCardInfo.defaultName, text: $name)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					name = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(Color(UIColor.tertiaryLabel))
				}
				.isHidden(name.isEmpty)
			}
			.padding(.all, 8)
			.background(
				RoundedRectangle(cornerRadius: 8)
					.fill(Color(UIColor.systemBackground))
			)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			
		} header: {
			Text("Name")
		}
	}
	
	@ViewBuilder
	func section_status() -> some View {
		
		Section {
			
			HStack(alignment: VerticalAlignment.centerTopLine) {
				
				Group {
					if isActive {
						Text("Active")
					} else if cardInfo.isArchived {
						Text("Frozen (archived)", comment: "translate: archived")
					} else {
						Text("Frozen")
					}
				}
				.font(.title3.weight(.medium))
				
				Spacer()
				
				Toggle("", isOn: $isActive)
					.labelsHidden()
					.disabled(cardInfo.isArchived)
					.padding(.trailing, 2)
					
			} // </HStack>
			
			Group {
				if isActive {
					Text("An active card can be used for payments.")
				} else {
					Text("All payment attempts will be rejected.")
				}
			}
			.font(.callout)
			.fixedSize(horizontal: false, vertical: true) // SwiftUI truncation bugs
			.foregroundColor(Color.secondary)
			.padding(.top, 8)
			.padding(.bottom, 4)
			
		} header: {
			Text("Status")
		}
	}
	
	@ViewBuilder
	func section_limits() -> some View {
		
		Section {
			
			section_limits_daily()
				.padding(.top, 4)
				.padding(.bottom, 8)
			
			section_limits_monthly()
				.padding(.top, 8)
				.padding(.bottom, 4)
			
		} header: {
			Text("Spending Limits")
		}
	}
	
	@ViewBuilder
	func section_limits_daily() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("**Daily** spending limit:")
				Spacer(minLength: 0)
				Button {
					popoverPresent_dailyLimit = true
				} label: {
					Image(systemName: "info.circle")
				}
				.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				.foregroundColor(.secondary)
				.popover(present: $popoverPresent_dailyLimit) {
					InfoPopoverWindow {
						Text("Limit applies from midnight to midnight (local time).")
					}
				}
				
			} // </HStack>
			.padding(.bottom, 8)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				TextField(
					"None",
					text: dailyLimit_currencyStyler().amountProxy
				)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.disabled(isSaving)
				.foregroundColor(dailyLimit_parsedAmount.isError ? Color.appNegative : Color.primaryForeground)
				
				Picker(
					selection: $dailyLimit_currencyStr,
					label: Text("")
				) {
					ForEach(currencyPickerOptions(), id: \.self) { option in
						Text(option).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.disabled(isSaving)
				.accessibilityLabel("") // see below
				.accessibilityHint("Currency picker")
				
				// For a Picker, iOS is setting the VoiceOver text twice:
				// > "sat sat, Button"
				//
				// If we change the accessibilityLabel to "foobar", then we get:
				// > "sat foobar, Button"
				//
				// So we have to set it to the empty string to avoid the double-word.
				
			} // </HStack>
			.padding(.horizontal, 8)
			.padding(.vertical, 2)
			.background(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			.padding(.bottom, 16)
			
			section_limits_graph(dailyLimit_graphInfo())
				.padding(.bottom, 8)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_limits_monthly() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				Text("**Monthly** spending limit:")
				Spacer(minLength: 0)
				Button {
					popoverPresent_monthlyLimit = true
				} label: {
					Image(systemName: "info.circle")
				}
				.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
				.foregroundColor(.secondary)
				.popover(present: $popoverPresent_monthlyLimit) {
					InfoPopoverWindow {
						Text("Limit applies from 1st of the month at midnight to the following 1st (local time).")
					}
				}
			} // </HStack>
			.padding(.bottom, 8)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				TextField(
					"None",
					text: monthlyLimit_currencyStyler().amountProxy
				)
				.keyboardType(.decimalPad)
				.disableAutocorrection(true)
				.disabled(isSaving)
				.foregroundColor(monthlyLimit_parsedAmount.isError ? Color.appNegative : Color.primaryForeground)
				
				Picker(
					selection: $monthlyLimit_currencyStr,
					label: Text("")
				) {
					ForEach(currencyPickerOptions(), id: \.self) { option in
						Text(option).tag(option)
					}
				}
				.pickerStyle(MenuPickerStyle())
				.disabled(isSaving)
				.accessibilityLabel("") // see below
				.accessibilityHint("Currency picker")
				
				// For a Picker, iOS is setting the VoiceOver text twice:
				// > "sat sat, Button"
				//
				// If we change the accessibilityLabel to "foobar", then we get:
				// > "sat foobar, Button"
				//
				// So we have to set it to the empty string to avoid the double-word.
				
			} // </HStack>
			.padding(.horizontal, 8)
			.padding(.vertical, 2)
			.background(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			.padding(.bottom, 16)
			
			section_limits_graph(monthlyLimit_graphInfo())
				.padding(.bottom, 8)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_limits_graph(_ graphInfo: SpendingLimitGraphInfo) -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "square.fill")
					.font(.subheadline)
					.imageScale(.small)
					.foregroundColor(spentBalanceColor)
				Text("Spent")
				Spacer(minLength: 0)
				Text("Remaining")
				Image(systemName: "square.fill")
					.imageScale(.small)
					.font(.subheadline)
					.foregroundColor(remainingBalanceColor)
			}
			
			ProgressView(value: graphInfo.spentAmount, total: graphInfo.totalAmount)
				.tint(spentBalanceColor)
				.background(remainingBalanceColor)
				.padding(.vertical, 4)
			
			HStack(alignment: VerticalAlignment.center, spacing: 2) {
				Text(graphInfo.spent)
				Spacer(minLength: 0)
				Text(graphInfo.remaining)
			}
			.font(.callout)
			.foregroundColor(.primary.opacity(0.8))
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_managementTasks() -> some View {
		
		Section {
			
			if !cardInfo.isArchived && !cardInfo.isForeign {
				Button {
					archiveCard()
				} label: {
					Text("Archive card…")
				}
			}
			if !cardInfo.isReset {
				Button {
					resetPhysicalCard()
				} label: {
					Text("Reset physical card…")
				}
			}
			Button("Delete card…", role: ButtonRole.destructive) {
				deleteCard()
			}
			
		} header: {
			Text("Management Tasks")
		}
	}
	
	@ViewBuilder
	func currencyText(_ option: CurrencyPickerOption) -> some View {
		
		// From what I can tell, Apple won't let us do any formatting here.
		// Things I've tried that don't work:
		//
		// #1
		// ```
		// HStack {Text("A") Text("B")}
		// ```
		// ^ You just get "A"
		//
		// #2
		// ```
		// Text("A") + Text("B").fontWeight(.thin)
		// ```
		// ^ You just get "AB" without the formatting
		
		switch option {
		case .currency(let currency):
			Text(currency.shortName)
		case .other:
			Text(option.description)
		}
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
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .CurrencyConverter(let initialAmount, let didChange, let didClose):
			CurrencyConverterView(
				initialAmount: initialAmount,
				didChange: didChange,
				didClose: didClose
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
	
	func dailyLimit_currencyStyler() -> TextFieldCurrencyStyler {
		
		return TextFieldCurrencyStyler(
			currency: dailyLimit_currency,
			amount: $dailyLimit_amountStr,
			parsedAmount: $dailyLimit_parsedAmount,
			hideMsats: false,
			userDidEdit: dailyLimit_userDidEdit
		)
	}
	
	func monthlyLimit_currencyStyler() -> TextFieldCurrencyStyler {
		
		return TextFieldCurrencyStyler(
			currency: monthlyLimit_currency,
			amount: $monthlyLimit_amountStr,
			parsedAmount: $monthlyLimit_parsedAmount,
			hideMsats: false,
			userDidEdit: monthlyLimit_userDidEdit
		)
	}
	
	func currencyPickerOptions() -> [String] {
		
		var options = [String]()
		for currency in currencyList {
			options.append(currency.shortName)
		}
		
		options.append(
			String(
				localized: "other",
				comment: "Option in currency picker list. Sends user to Currency Converter"
			)
		)
		
		return options
	}
	
	func dailyLimit_isInvalidAmount() -> Bool {
		return isInvalidAmount(dailyLimit_parsedAmount)
	}
	
	func monthlyLimit_isInvalidAmount() -> Bool {
		return isInvalidAmount(monthlyLimit_parsedAmount)
	}
	
	func isInvalidAmount(_ result: Result<Double, TextFieldCurrencyStylerError>) -> Bool {
		
		switch result {
		case .success(let amt):
			return amt <= 0
			
		case .failure(let reason):
			switch reason {
			case .emptyInput:
				return false
			case .invalidInput:
				return true
			}
		}
	}
	
	func dailyLimit_graphInfo() -> SpendingLimitGraphInfo {
		return graphInfo(dailyLimit_currency, dailyCardPaymentsAmount, dailyLimit_parsedAmount)
	}
	
	func monthlyLimit_graphInfo() -> SpendingLimitGraphInfo {
		return graphInfo(monthlyLimit_currency, monthlyCardPaymentsAmount, monthlyLimit_parsedAmount)
	}
	
	func graphInfo(
		_ currency: Currency,
		_ paymentsAmount: Double,
		_ parsedAmount: Result<Double, TextFieldCurrencyStylerError>
	) -> SpendingLimitGraphInfo {
		
		var spentAmount: Double = 0.0
		var spent = ""
		
		if cardAmounts != nil {
			spentAmount = paymentsAmount
			switch currency {
			case .bitcoin(let bitcoinUnit):
				spent = Utils.formatBitcoin(amount: spentAmount, bitcoinUnit: bitcoinUnit).digits
			case .fiat(let fiatCurrency):
				spent = Utils.formatFiat(amount: spentAmount, fiatCurrency: fiatCurrency).digits
			}
		} else {
			switch currency {
			case .bitcoin(let bitcoinUnit):
				spent = Utils.unknownBitcoinAmount(bitcoinUnit: bitcoinUnit).digits
			case .fiat(let fiatCurrency):
				spent = Utils.unknownFiatAmount(fiatCurrency: fiatCurrency).digits
			}
		}
		
		var totalAmount: Double = 0.0
		var total = ""
		
		var remainingAmount: Double = 0.0
		var remaining = ""
		
		switch parsedAmount {
		case .success(let limit):
			totalAmount = limit
			remainingAmount = max(0.0, limit - spentAmount)
			switch currency {
			case .bitcoin(let bitcoinUnit):
				total = Utils.formatBitcoin(amount: totalAmount, bitcoinUnit: bitcoinUnit).digits
				remaining = Utils.formatBitcoin(amount: remainingAmount, bitcoinUnit: bitcoinUnit).digits
			case .fiat(let fiatCurrency):
				total = Utils.formatFiat(amount: totalAmount, fiatCurrency: fiatCurrency).digits
				remaining = Utils.formatFiat(amount: remainingAmount, fiatCurrency: fiatCurrency).digits
			}
			
		case .failure(_):
			totalAmount = Double.greatestFiniteMagnitude
			total = "♾️"
			remainingAmount = Double.greatestFiniteMagnitude
			remaining = "♾️"
		}
		
		return SpendingLimitGraphInfo(
			spent: spent,
			spentAmount: spentAmount,
			remaining: remaining,
			remainingAmount: remainingAmount,
			total: total,
			totalAmount: totalAmount
		)
	}
	
	var spentBalanceColor: Color {
		if Biz.isTestnet {
			return Color.appAccentTestnet
		} else {
			return Color.appAccentMainnet
		}
	}
	
	var remainingBalanceColor: Color {
		return Color.appAccentOrange
	}
	
	var hasChanges: Bool {
		
		if cardInfo.sanitizedName != name {
			return true
		}
		if cardInfo.isActive != isActive {
			return true
		}
		
		if cardInfo.dailyLimit?.toCurrencyAmount() != dailyLimit_currencyAmount() {
			return true
		}
		
		if cardInfo.monthlyLimit?.toCurrencyAmount() != monthlyLimit_currencyAmount() {
			return true
		}
		
		return false
	}
	
	var canSave: Bool {
		
		if dailyLimit_isInvalidAmount() {
			return false
		}
		if monthlyLimit_isInvalidAmount() {
			return false
		}
		
		return true
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace(#function)
		
		if !didAppear {
			didAppear = true
			cardInfoChanged()
		}
	}
	
	func cardInfoChanged() {
		log.trace(#function)
		
		ignoreChanges = true
		
		name = cardInfo.sanitizedName
		isActive = cardInfo.isActive
		
		let dsl = cardInfo.dailyLimit?.toCurrencyAmount()
		let msl = cardInfo.monthlyLimit?.toCurrencyAmount()
		
		var plus: [Currency] = []
		if let dsl {
			plus.append(dsl.currency)
		}
		if let msl {
			plus.append(msl.currency)
		}
		currencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: plus)
		
		if let dsl {
			dailyLimit_currencyStr = dsl.currency.shortName
			dailyLimit_currency = dsl.currency
			
			let formattedAmt = Utils.format(currencyAmount: dsl)
			dailyLimit_parsedAmount = Result.success(formattedAmt.amount) // do this first !
			dailyLimit_amountStr = formattedAmt.digits
			
		} else {
			dailyLimit_currencyStr = currencyPrefs.currency.shortName
			dailyLimit_currency = currencyPrefs.currency
		}
		
		if let msl {
			monthlyLimit_currencyStr = msl.currency.shortName
			monthlyLimit_currency = msl.currency
			
			let formattedAmt = Utils.format(currencyAmount: msl)
			monthlyLimit_parsedAmount = Result.success(formattedAmt.amount) // do this first !
			monthlyLimit_amountStr = formattedAmt.digits
			
		} else {
			monthlyLimit_currencyStr = currencyPrefs.currency.shortName
			monthlyLimit_currency = currencyPrefs.currency
		}
		
		// If the user has only edit one limit, change the currency of the other to match.
		if dailyLimit_amountStr.isEmpty && !monthlyLimit_amountStr.isEmpty {
			dailyLimit_currencyStr = monthlyLimit_currencyStr
			dailyLimit_currency = monthlyLimit_currency
		}
		if monthlyLimit_amountStr.isEmpty && !dailyLimit_amountStr.isEmpty {
			monthlyLimit_currencyStr = dailyLimit_currencyStr
			monthlyLimit_currency = dailyLimit_currency
		}
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
			self.ignoreChanges = false
		}
	}
	
	func applicationDidBecomeActive() {
		log.trace(#function)
		
		if isNewCard && !didDisplayWelcome {
			didDisplayWelcome = true
			
			smartModalState.display(dismissable: true) {
				NewCardSheet()
			}
		}
	}
	
	func isActiveChanged() {
		log.trace(#function)
		
		maybeShowSaveToast()
	}
	
	func dailyLimit_currencyPickerDidChange() {
		log.trace(#function)
		
		guard !ignoreChanges else {
			log.debug("dailyLimit_currencyPickerDidChange(): ignoreChanges")
			return
		}
		
		if let newCurrency = currencyList.first(where: { $0.shortName == dailyLimit_currencyStr }) {
			if dailyLimit_currency != newCurrency {
				dailyLimit_currency = newCurrency
				
				// We might want to apply a different formatter
				let result = TextFieldCurrencyStyler.format(
					input     : dailyLimit_amountStr,
					currency  : dailyLimit_currency,
					hideMsats : false
				)
				dailyLimit_parsedAmount = result.1
				dailyLimit_amountStr = result.0
				
				// If the user hasn't edited the other field, change the currency to match.
				if monthlyLimit_amountStr.isEmpty {
					monthlyLimit_currencyStr = dailyLimit_currencyStr
				}
			}
			
		} else { // user selected "other"
			
			dailyLimit_currencyStr = dailyLimit_currency.shortName // revert to last real currency
			navigateTo(
				.CurrencyConverter(
					initialAmount : dailyLimit_currencyAmount(),
					didChange     : dailyLimit_currencyConverterAmountChanged,
					didClose      : nil
				)
			)
		}
	}
	
	func monthlyLimit_currencyPickerDidChange() {
		log.trace(#function)
		
		guard !ignoreChanges else {
			log.debug("\(#function): ignoreChanges")
			return
		}
		
		if let newCurrency = currencyList.first(where: { $0.shortName == dailyLimit_currencyStr }) {
			if monthlyLimit_currency != newCurrency {
				monthlyLimit_currency = newCurrency
				
				// We might want to apply a different formatter
				let result = TextFieldCurrencyStyler.format(
					input     : monthlyLimit_amountStr,
					currency  : monthlyLimit_currency,
					hideMsats : false
				)
				monthlyLimit_parsedAmount = result.1
				monthlyLimit_amountStr = result.0
				
				// If the user hasn't edited the other field, change the currency to match.
				if dailyLimit_amountStr.isEmpty {
					dailyLimit_currencyStr = monthlyLimit_currencyStr
				}
			}
			
		} else { // user selected "other"
			
			monthlyLimit_currencyStr = monthlyLimit_currency.shortName // revert to last real currency
			navigateTo(
				.CurrencyConverter(
					initialAmount : monthlyLimit_currencyAmount(),
					didChange     : monthlyLimit_currencyConverterAmountChanged,
					didClose      : nil
				)
			)
		}
	}
	
	func dailyLimit_currencyConverterAmountChanged(_ result: CurrencyAmount?) {
		log.trace(#function)
		
		if let newAmt = result {

			let plus: [Currency] = [newAmt.currency, monthlyLimit_currency]
			let newCurrencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: plus)
			if currencyList != newCurrencyList {
				currencyList = newCurrencyList
			}

			dailyLimit_currency = newAmt.currency
			dailyLimit_currencyStr = newAmt.currency.shortName

			let formattedAmt = Utils.format(currencyAmount: newAmt, policy: .showMsatsIfNonZero)
			dailyLimit_parsedAmount = Result.success(newAmt.amount)
			dailyLimit_amountStr = formattedAmt.digits

		} else {

			dailyLimit_parsedAmount = Result.failure(.emptyInput)
			dailyLimit_amountStr = ""
		}
	}
	
	func monthlyLimit_currencyConverterAmountChanged(_ result: CurrencyAmount?) {
		log.trace(#function)
		
		if let newAmt = result {

			let plus: [Currency] = [newAmt.currency, dailyLimit_currency]
			let newCurrencyList = Currency.displayable(currencyPrefs: currencyPrefs, plus: plus)
			if currencyList != newCurrencyList {
				currencyList = newCurrencyList
			}

			monthlyLimit_currency = newAmt.currency
			monthlyLimit_currencyStr = newAmt.currency.shortName

			let formattedAmt = Utils.format(currencyAmount: newAmt, policy: .showMsatsIfNonZero)
			monthlyLimit_parsedAmount = Result.success(newAmt.amount)
			monthlyLimit_amountStr = formattedAmt.digits

		} else {

			monthlyLimit_parsedAmount = Result.failure(.emptyInput)
			monthlyLimit_amountStr = ""
		}
	}
	
	func dailyLimit_currencyChanged() {
		log.trace(#function)
		
		updateDailyCardPaymentsAmount()
		maybeShowSaveToast()
	}
	
	func monthlyLimit_currencyChanged() {
		log.trace(#function)
		
		updateMonthlyCardPaymentsAmount()
		maybeShowSaveToast()
	}
	
	func dailyLimit_userDidEdit() {
		log.trace(#function)
		
		// This is called if the user manually edits the TextField.
		// Which is distinct from `amountDidChange`, which may be triggered via code.
		
		maybeShowSaveToast()
	}
	
	func monthlyLimit_userDidEdit() {
		log.trace(#function)
		
		// This is called if the user manually edits the TextField.
		// Which is distinct from `amountDidChange`, which may be triggered via code.
			
		maybeShowSaveToast()
	}
	
	func cardAmountsChanged() {
		log.trace(#function)
		
		updateDailyCardPaymentsAmount()
		updateMonthlyCardPaymentsAmount()
	}
	
	// --------------------------------------------------
	// MARK: Utils
	// --------------------------------------------------
	
	func dailyLimit_currencyAmount() -> CurrencyAmount? {
		
		if case .success(let amount) = dailyLimit_parsedAmount {
			return CurrencyAmount(currency: dailyLimit_currency, amount: amount)
		} else {
			return nil
		}
	}
	
	func monthlyLimit_currencyAmount() -> CurrencyAmount? {
		
		if case .success(let amount) = monthlyLimit_parsedAmount {
			return CurrencyAmount(currency: dailyLimit_currency, amount: amount)
		} else {
			return nil
		}
	}
	
	func updateDailyCardPaymentsAmount() {
		
		guard let cardAmounts else {
			dailyCardPaymentsAmount = 0.0
			log.debug("dailyCardPaymentsAmount = 0.0 (cardAmounts == nil)")
			return
		}
		
		switch dailyLimit_currency {
		case .bitcoin(let bitcoinUnit):
			let msat = cardAmounts.dailyBitcoinAmount()
			dailyCardPaymentsAmount = Utils.convertBitcoin(msat: msat, to: bitcoinUnit)
			log.debug("dailyCardPaymentsAmount = \(dailyCardPaymentsAmount) \(bitcoinUnit.shortName)")
			
		case .fiat(let fiatCurrency):
			dailyCardPaymentsAmount = cardAmounts.dailyFiatAmount(
				target: fiatCurrency,
				exchangeRates: currencyPrefs.fiatExchangeRates
			)
			log.debug("dailyCardPaymentsAmount = \(dailyCardPaymentsAmount) \(fiatCurrency.shortName)")
		}
	}
	
	func updateMonthlyCardPaymentsAmount() {
		
		guard let cardAmounts else {
			monthlyCardPaymentsAmount = 0.0
			log.debug("monthlyCardPaymentsAmount = 0.0 (cardAmounts == nil)")
			return
		}
		
		switch dailyLimit_currency {
		case .bitcoin(let bitcoinUnit):
			let msat = cardAmounts.dailyBitcoinAmount()
			monthlyCardPaymentsAmount = Utils.convertBitcoin(msat: msat, to: bitcoinUnit)
			log.debug("monthlyCardPaymentsAmount = \(monthlyCardPaymentsAmount) \(bitcoinUnit.shortName)")
			
		case .fiat(let fiatCurrency):
			monthlyCardPaymentsAmount = cardAmounts.dailyFiatAmount(
				target: fiatCurrency,
				exchangeRates: currencyPrefs.fiatExchangeRates
			)
			log.debug("monthlyCardPaymentsAmount = \(monthlyCardPaymentsAmount) \(fiatCurrency.shortName)")
		}
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func fetchCardAmounts() async {
		log.trace(#function)
		
		guard !cardInfo.isArchived else {
			log.debug("fetchCardAmounts(): skipping: isArchived")
			return
		}
		
		let cardsDb: SqliteCardsDb
		do {
			cardsDb = try await Biz.business.databaseManager.cardsDb()
		} catch {
			log.error("SqliteCardsDb unavailable: \(error)")
			return
		}
		
		let cardPayments: SqliteCardsDb.CardPayments
		do {
			cardPayments = try await cardsDb.fetchCardPayments(cardId: cardInfo.id)
		} catch {
			log.error("SqliteCardsDb.fetchCardPayments(): error: \(error)")
			return
		}
		
		cardAmounts = cardsDb.getCardAmounts(payments: cardPayments)
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
	
	func maybeShowSaveToast() {
		log.trace(#function)
		
		guard !ignoreChanges else {
			return
		}
		
		if isFirstUserEdit {
			isFirstUserEdit = false
			
			toast.pop(
				"Changes take effect after you Save",
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 5.0,
				alignment: .top(padding: 0),
				transition: .asymmetric(insertion: .push(from: .leading), removal: .move(edge: .trailing)),
				showCloseButton: false
			)
		}
	}
	
	func cancelButtonTapped() {
		log.trace(#function)
		
		if hasChanges && canSave {
			showDiscardChangesConfirmationDialog = true
		} else {
			close()
		}
	}
	
	func saveButtonTapped() {
		log.trace(#function)
		
		if hasChanges && canSave {
			saveCard()
		} else {
			close()
		}
	}
	
	func saveCard() {
		log.trace(#function)
		
		let updatedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
		let updatedIsFrozen = !isActive
		let updatedDailyLimit = dailyLimit_currencyAmount()?.toSpendingLimit()
		let updatedMonthlyLimit = monthlyLimit_currencyAmount()?.toSpendingLimit()
		
		Task {
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				
				// Get the most recent version of the card.
				// If a payment was made with the card while this screen was open,
				// then we might not have the lastest version of `lastKnownCounter`.
				//
				let currentCard = cardsDb.cardForId(cardId: cardInfo.id) ?? cardInfo
				let updatedCard = currentCard.doCopy(
					id               : currentCard.id,
					name             : updatedName,
					keys             : currentCard.keys,
					uid              : currentCard.uid,
					lastKnownCounter : currentCard.lastKnownCounter,
					isFrozen         : updatedIsFrozen,
					isArchived       : currentCard.isArchived,
					isReset          : currentCard.isReset,
					isForeign        : currentCard.isForeign,
					dailyLimit       : updatedDailyLimit,
					monthlyLimit     : updatedMonthlyLimit,
					createdAt        : currentCard.createdAt
				)
				
				try await cardsDb.saveCard(card: updatedCard)
				
			} catch {
				log.error("SqliteCardsDb.saveCard(): error: \(error)")
			}
			
			self.close()
		}
	}
	
	func close() {
		log.trace(#function)
		
		presentationMode.wrappedValue.dismiss()
	}
	
	// --------------------------------------------------
	// MARK: Management Tasks
	// --------------------------------------------------
	
	func archiveCard() {
		log.trace(#function)
		
		smartModalState.display(dismissable: false) {
			ArchiveCardSheet(card: cardInfo, didArchive: {
				cardInfo = $0
				cardWasArchived = true
			})
		}
	}
	
	func resetPhysicalCard() {
		log.trace(#function)
		
		smartModalState.display(dismissable: false) {
			ResetCardSheet(card: cardInfo, didRequestReset: { startResetPhysicalCardProcess() })
		}
	}
	
	func deleteCard() {
		log.trace(#function)
		
		smartModalState.display(dismissable: false) {
			DeleteCardSheet(card: cardInfo, didDelete: { close() })
		}
	}
	
	// --------------------------------------------------
	// MARK: Card Reset
	// --------------------------------------------------
	
	func startResetPhysicalCardProcess() {
		log.trace(#function)
		
		let input = NfcWriter.ResetInput(
			key0        : cardInfo.keys.key0_bytes,
			piccDataKey : cardInfo.keys.piccDataKey_bytes,
			cmacKey     : cardInfo.keys.cmacKey_bytes
		)
		
		NfcWriter.shared.resetCard(input) { (result: Result<Void, NfcWriter.WriteError>) in
			
			switch result {
			case .failure(let error):
				log.debug("NfcWriter.resetCard(): error: \(error)")
				showWriteErrorSheet(error)
				
			case .success():
				resetSuccess()
			}
		}
	}
	
	func resetSuccess() {
		log.trace(#function)
		
		// Step 1 of 2:
		// Show the success screen
		
		smartModalState.display(dismissable: true) {
			ResetSuccessSheet()
		}
		
		// Step 2 of 2:
		// Update the card in the database
		Task { @MainActor in
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				
				// Try to get the most recent version of the card,
				// just in-case any changes were made elsewhere in the system.
				//
				let currentCard = cardsDb.cardForId(cardId: cardInfo.id) ?? cardInfo
				let updatedCard = currentCard.resetCopy()
				
				try await cardsDb.saveCard(card: updatedCard)
				cardInfo = updatedCard
				cardWasReset = true
				
			} catch {
				log.error("SqliteCardsDb.saveCard(): error: \(error)")
			}
		}
	}
	
	func showWriteErrorSheet(_ error: NfcWriter.WriteError) {
		log.trace(#function)
		
		var shouldIgnoreError = false
		if case .scanningTerminated(let nfcError) = error {
			shouldIgnoreError = nfcError.isIgnorable()
		}
		
		guard !shouldIgnoreError else {
			log.debug("showWriteErrorSheet(): ignoring standard user error")
			return
		}
		
		smartModalState.display(dismissable: true) {
			WriteErrorSheet(error: error, context: .whileResetting)
		}
	}
}
