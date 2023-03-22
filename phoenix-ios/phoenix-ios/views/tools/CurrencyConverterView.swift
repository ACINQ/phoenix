import SwiftUI
import PhoenixShared
import Focuser
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CurrencyConverterView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ParsedRow: Equatable {
	let currency: Currency
	let parsedAmount: Result<Double, TextFieldCurrencyStylerError>
}

enum LastUpdated {
	case Now
	case Date(date: Date)
	case Never
}

struct CurrencyConverterView: View {
	
	let initialAmount: CurrencyAmount?
	let didChange: ((CurrencyAmount?) -> Void)?
	let didClose: (() -> Void)?
	
	@State var lastChange: CurrencyAmount?
	
	@State var currencies: [Currency] = GroupPrefs.shared.currencyConverterList
	
	@State var parsedRow: ParsedRow? = nil
	
	@State var currencySelectorOpen = false
	@State var replacingCurrency: Currency? = nil
	
	@State var didAppear = false
	@State var isRefreshingExchangeRates = false
	
	let refreshingExchangeRatesPublisher =
		Biz.business.currencyManager.refreshPublisher()
	
	let timer = Timer.publish(every: 15 /* seconds */, on: .current, in: .common).autoconnect()
	@State var currentDate = Date()
	
	enum CurrencyTextWidth: Preference {}
	let currencyTextWidthReader = GeometryPreferenceReader(
		key: AppendValue<CurrencyTextWidth>.self,
		value: { [$0.size.width] }
	)
	@State var currencyTextWidth: CGFloat? = nil
	
	enum FlagWidth: Preference {}
	let flagWidthReader = GeometryPreferenceReader(
		key: AppendValue<FlagWidth>.self,
		value: { [$0.size.width] }
	)
	@State var flagWidth: CGFloat? = nil
	
	@Environment(\.colorScheme) var colorScheme
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	init() {
		self.initialAmount = nil
		self.didChange = nil
		self.didClose = nil
	}
	
	init(
		initialAmount: CurrencyAmount?,
		didChange: @escaping (CurrencyAmount?) -> Void,
		didClose: @escaping () -> Void
	) {
		self.initialAmount = initialAmount
		self.didChange = didChange
		self.didClose = didClose
	}
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Currency Converter", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			if #unavailable(iOS 16.0) {
				NavigationLink(
					destination: currencySelectorView(),
					isActive: $currencySelectorOpen
				) {
					EmptyView()
				}
				.accessibilityHidden(true)
				
			} // else: uses.navigationStackDestination()
			
			// We want to measure various items within the List.
			// But we need to measure ALL of them.
			// And we can't do that with a List because it's lazy.
			//
			// So we have to use this hack,
			// which force-renders all the Text items using a hidden VStack.
			//
			ScrollView {
				VStack {
					ForEach(currencies) { currency in
						
						switch currency {
						case .bitcoin(let bitcoinUnit):
							Text(bitcoinUnit.shortName)
								.foregroundColor(Color.clear)
								.read(currencyTextWidthReader)
							//	.frame(width: currencyTextWidth, alignment: .leading) // required, or refreshes after display
							
						case .fiat(let fiatCurrency):
							Text(fiatCurrency.shortName)
								.foregroundColor(Color.clear)
								.read(currencyTextWidthReader)
							//	.frame(width: currencyTextWidth, alignment: .leading) // required, or refreshes after display
							
							Text(fiatCurrency.flag)
								.foregroundColor(Color.clear)
								.read(flagWidthReader)
							//	.frame(width: flagWidth, alignment: .leading) // required, or refreshes after display
						}
					}
				} // </VStack>
				.assignMaxPreference(for: currencyTextWidthReader.key, to: $currencyTextWidth)
				.assignMaxPreference(for: flagWidthReader.key, to: $flagWidth)
			} // </ScrollView>
			
			content()
			
		} // </ZStack>
		.onAppear {
			onAppear()
		}
		.onDisappear {
			onDisappear()
		}
		.navigationStackDestination( // For iOS 16+
			isPresented: $currencySelectorOpen,
			destination: currencySelectorView
		)
		.onChange(of: currencies) { _ in
			currenciesDidChange()
		}
		.onChange(of: parsedRow) { _ in
			parsedRowDidChange()
		}
		.onReceive(refreshingExchangeRatesPublisher) {
			refreshingExchangeRatesChanged($0)
		}
		.onReceive(timer) { _ in
			self.currentDate = Date()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Color.primaryBackground.frame(height: 25)
			
			List {
				if #available(iOS 15.0, *) {
					currencyRows()
					lastRow()
						.listRowInsets(EdgeInsets()) // remove extra padding space in rows
						.listRowSeparator(.hidden)   // remove lines between items
						.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
					
				} else {
					currencyRows_iOS14()
					lastRow()
						.listRowInsets(EdgeInsets(top: -1, leading: 0, bottom: 0, trailing: 0))
						.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
						.background(Color(.systemBackground))
				}
			}
			.toolbar {
				EditButton()
			}
			.listStyle(.plain)
			.listBackgroundColor(Color(.systemBackground)) // For iOS 16
			
			footer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	@available(iOS 15.0, *)
	func currencyRows() -> some View {
		
		ForEach(currencies) { currency in
			Row(
				currency: currency,
				parsedRow: $parsedRow,
				currencyTextWidth: $currencyTextWidth,
				flagWidth: $flagWidth
			)
			.buttonStyle(PlainButtonStyle())
			.swipeActions(allowsFullSwipe: false) {
				Button {
					editRow(currency)
				} label: {
					Label("Edit", systemImage: "square.and.pencil")
				}
				
				Button(role: .destructive) {
					deleteRow(currency)
				} label: {
					Label("Delete", systemImage: "trash.fill")
				}
			}
		}
		.onDelete { (_ /* indexSet */) in
			// Implementation not needed.
			// Starting with iOS 15, it uses button with destructive role instead.
		}
		.onMove { indexSet, index in
			moveRow(indexSet, to: index)
		}
		.listRowInsets(EdgeInsets()) // remove extra padding space in rows
		.listRowSeparator(.hidden)   // remove lines between items
	}
	
	@ViewBuilder
	func currencyRows_iOS14() -> some View {
		
		ForEach(currencies) { currency in
			
			Row_iOS14(
				currency: currency,
				parsedRow: $parsedRow,
				currencyTextWidth: $currencyTextWidth,
				flagWidth: $flagWidth
			)
			.listRowInsets(EdgeInsets(top: -1, leading: 0, bottom: 0, trailing: 0))
			.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
			.background(Color(.systemBackground))
			.buttonStyle(PlainButtonStyle())
		}
		.onDelete { (indexSet: IndexSet) in
			deleteRow_iOS14(indexSet)
		}
		.onMove { indexSet, index in
			moveRow(indexSet, to: index)
		}
		.listRowInsets(EdgeInsets()) // remove extra padding space in rows
	}
	
	@ViewBuilder
	func lastRow() -> some View {
		
		HStack {
			Spacer()
			
			Button {
				addRow()
			} label: {
				Image(systemName: "plus.circle.fill")
					.imageScale(.large)
					.font(.title)
					.foregroundColor(.appPositive)
			}
			.buttonStyle(PlainButtonStyle())
		}
		.padding(.trailing, 8)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			if isRefreshingExchangeRates {
				
				Text("Refreshing rates...")
				Spacer()
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle())
				
			} else {
				
				Text("Rates last updated: \(lastUpdatedText())")
				Spacer()
				Button {
					refreshRates()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Text("refresh")
						Image(systemName: "arrow.2.squarepath")
							.imageScale(.medium)
					}
				}
				.padding(.leading, 4)
			}
		
		} // </HStack>
		.font(.footnote)
		.padding(.horizontal)
		.padding([.top, .bottom], 6)
		.frame(maxWidth: .infinity, minHeight: 40)
		.background(
			Color(
				colorScheme == ColorScheme.light
				? UIColor.primaryBackground
				: UIColor.secondarySystemGroupedBackground
			)
			.edgesIgnoringSafeArea(.bottom) // background color should extend to bottom of screen
		)
	}
	
	@ViewBuilder
	func currencySelectorView() -> some View {
		
		CurrencySelector(
			selectedCurrencies: $currencies,
			replacingCurrency: $replacingCurrency,
			didSelectCurrency: didSelectCurrency
		)
	}
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	func lastUpdatedText() -> String {
		
		switch currenciesLastUpdated() {
		case .Now:
			return NSLocalizedString("now", comment: "")
		case .Never:
			return NSLocalizedString("never", comment: "")
		case .Date(let date):
			let now = currentDate // Use local @State to allow timer refresh
			let diff = now.timeIntervalSince1970 - date.timeIntervalSince1970
			if diff < (60 * 5) { // within last 5 minutes
				return NSLocalizedString("just now", comment: "")
			} else {
				let formatter = RelativeDateTimeFormatter()
				return formatter.localizedString(for: date, relativeTo: now)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	private func onAppear() {
		log.trace("onAppear()")
		
		if #unavailable(iOS 15.0) {
			// iOS 14 workaround
			log.debug("Setting UITableView.background = .systemBackground")
			UITableView.appearance().backgroundColor = .systemBackground
		}
		
		// Careful: this function may be called when returning from the Receive/Send view
		if !didAppear {
			didAppear = true
			
			UIScrollView.appearance().keyboardDismissMode = .interactive
			
			if #unavailable(iOS 15.0) {
				// iOS 14 bug workaround
				currencies = GroupPrefs.shared.currencyConverterList
			}
			if currencies.isEmpty {
				currencies = defaultCurrencies()
			}
			
			if didChange == nil {
				// Created via init() - use default initial value
				parsedRow = ParsedRow(
					currency: Currency.bitcoin(.btc),
					parsedAmount: Result.success(1.0)
				)
			} else {
				// Created via init(initialAmount:didChange:didClose:).
				// If an initialAmount was passed then use it.
				// Otherwise all rows should remain empty.
				if let initialAmount = initialAmount {
					lastChange = initialAmount
					parsedRow = ParsedRow(
						currency: initialAmount.currency,
						parsedAmount: Result.success(initialAmount.amount)
					)
				} else {
					lastChange = nil
					parsedRow = ParsedRow(
						currency: Currency.bitcoin(.btc),
						parsedAmount: Result.failure(.emptyInput)
					)
				}
			}
		}
	}
	
	private func onDisappear() {
		log.trace("onDisappear()")
		
		if #unavailable(iOS 15.0) {
			// iOS 14 workaround
			if !currencySelectorOpen {
				log.debug("Setting UITableView.background = .primaryBackground")
				UITableView.appearance().backgroundColor = .primaryBackground
			}
		}
		
		if !currencySelectorOpen, let didClose = didClose {
			didClose()
		}
	}
	
	private func defaultCurrencies() -> [Currency] {
		return [
			Currency.bitcoin(currencyPrefs.bitcoinUnit),
			Currency.fiat(currencyPrefs.fiatCurrency)
		]
	}
	
	private func currenciesDidChange() {
		log.trace("currenciesDidChange(): \(Currency.serializeList(currencies) ?? "<empty>")")
		
		if currencies == defaultCurrencies() {
			GroupPrefs.shared.currencyConverterList = []
		} else {
			GroupPrefs.shared.currencyConverterList = currencies
		}
	}
	
	private func parsedRowDidChange() {
		log.trace("parsedRowDidChange()")
		
		guard let didChange = didChange else {
			return
		}
		
		var newCurrencyAmount: CurrencyAmount? = nil
		if let parsedRow = parsedRow {
			if case .success(let amount) = parsedRow.parsedAmount {
				newCurrencyAmount = CurrencyAmount(currency: parsedRow.currency, amount: amount)
			}
		}
		
		if lastChange != newCurrencyAmount {
			didChange(newCurrencyAmount)
			lastChange = newCurrencyAmount
		}
	}
	
	private func currenciesLastUpdated() -> LastUpdated {
		
		var fiatCurrencies: Set<FiatCurrency> = Set(currencies.compactMap { (currency: Currency) in
			switch currency {
				case .fiat(let fiatCurrency): return fiatCurrency
				case .bitcoin(_): return nil
			}
		})
		
		if fiatCurrencies.isEmpty {
			// Either the `currencies` array is empty,
			// or it consists entirely of bitcoin units.
			// So we can consider our rates to always be fresh.
			return LastUpdated.Now
		}
		
		// There are 2 types of ExchangeRates:
		// - BitcoinPriceRate => Direct Fiat<->BTC rate
		// - UsdPriceRate => Indirect Fiat<->USD rate
		//
		// If there are any indirect rates, then we implictly depend upon the USD rate.
		
		let hasIndirectRates =
			currencyPrefs.fiatExchangeRates.contains { (rate: ExchangeRate) -> Bool in
				return fiatCurrencies.contains(rate.fiatCurrency) && (rate is ExchangeRate.UsdPriceRate)
			}
		
		if hasIndirectRates {
			fiatCurrencies.insert(FiatCurrency.usd)
		}
		
		let filteredRates = currencyPrefs.fiatExchangeRates.filter { (rate: ExchangeRate) in
			fiatCurrencies.contains(rate.fiatCurrency)
		}
		
		if filteredRates.count < fiatCurrencies.count {
			// There is at least one currency that has never been downloaded
			return LastUpdated.Never
		}
		
		// The BitcoinPriceRates are for currencies with high liquidity (e.g. USD, EUR).
		// The rates are expect to update frequently (e.g. every 20 minutes).
		//
		// The UsdPriceRates for for currencies with "low" liquidity.
		// (See CurrencyManager.kt for technical discussion on liquidity & conversion implications.)
		// The rates are only updated on the server once every hour.
		//
		// So there are considerations here.
		// If a UsdPriceRate was updated within the last hour, we can effectively consider it "fresh".
		
		let now = currentDate // Use local @State to allow timer refresh
		
		let refreshDates: [Date] = filteredRates.map { (rate: ExchangeRate) in
			
			if let fiatRate = rate as? ExchangeRate.UsdPriceRate {
				let diff = now.timeIntervalSince1970 - fiatRate.timestamp.timeIntervalSince1970
				if diff < (60 * 60) { // within the last hour
					return now
				} else {
					return rate.timestamp
				}
				
			} else {
				return rate.timestamp
			}
		}
		
		let lastUpdated = refreshDates.min()!
		log.debug("currenciesLastUpdated: \(lastUpdated.description(with: Locale.current))")
		
		return LastUpdated.Date(date: lastUpdated)
	}
	
	private func refreshingExchangeRatesChanged(_ value: Bool) {
		log.trace("refreshingExchangeRatesChanged(\(value))")
		
		isRefreshingExchangeRates = value
	}
	
	private func moveRow(_ indexSet: IndexSet, to index: Int) {
		log.trace("moveRow()")
		
		currencies.move(fromOffsets: indexSet, toOffset: index)
	}
	
	private func deleteRow(_ currency: Currency) {
		log.trace("deleteRow(Currency:)")
		
		if let idx = currencies.firstIndex(where: { $0 == currency }) {
			currencies.remove(at: idx)
		}
	}
	
	private func deleteRow_iOS14(_ indexSet: IndexSet) {
		log.trace("deleteRow(IndexSet:)")
		
		var newCurrencies = currencies
		newCurrencies.remove(atOffsets: indexSet)
		currencies = newCurrencies
	}
	
	private func editRow(_ currency: Currency) {
		log.trace("editRow()")
		
		replacingCurrency = currency
		currencySelectorOpen = true
	}
	
	private func addRow() {
		log.trace("addRow()")
		
		replacingCurrency = nil
		currencySelectorOpen = true
	}
	
	private func refreshRates() {
		log.trace("refreshRates()")
		
		Biz.business.currencyManager.refreshAll(
			targets : FiatCurrency.companion.values,
			force   : true
		)
	}
	
	private func didSelectCurrency(_ newCurrency: Currency) {
		log.trace("didSelectCurrency(\(newCurrency))")
		
		if let replaceMe = replacingCurrency {
			log.debug("replacing currency...")
			
			if let idx = currencies.firstIndex(where: { $0 == replaceMe }) {
				currencies[idx] = newCurrency
			}
			
		} else {
			log.debug("adding currency...")
			
			if !currencies.contains(newCurrency) {
				currencies.append(newCurrency)
			}
		}
	}
}

// MARK: -

@available(iOS 15.0, *)
fileprivate struct Row: View, ViewName {
	
	let currency: Currency
	
	@Binding var parsedRow: ParsedRow?
	@Binding var currencyTextWidth: CGFloat?
	@Binding var flagWidth: CGFloat?
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var isInvalidAmount: Bool = false
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	enum Field: Hashable {
		case amountTextfield
	}
	
	@FocusState private var focusedField: Field?
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("amount", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.focused($focusedField, equals: .amountTextfield)
					.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					clearTextField()
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(Color(UIColor.tertiaryLabel))
				}
				.isHidden(amount == "")
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			
			Text_CurrencyName(currency: currency, fontTextStyle: .body)
				.frame(width: currencyTextWidth, alignment: .leading)
				.padding(.leading, 10)
			
			switch currency {
			case .bitcoin:
				let fontHeight = UIFont.preferredFont(forTextStyle: .body).pointSize
				Image("bitcoin")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: flagWidth, height: fontHeight, alignment: .center)
					.padding(.leading, 3)
					.offset(x: 0, y: 2)
				
			case .fiat(let fiatCurrency):
				Text(fiatCurrency.flag)
					.frame(width: flagWidth, alignment: .center)
					.padding(.leading, 3)
			}
		}
		.padding()
		.onAppear {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: parsedRow) { _ in
			parsedRowDidChange()
		}
		.onChange(of: currencyPrefs.fiatExchangeRates) { _ in
			exchangeRatesDidChange()
		}
	}
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: currency,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}
	
	func onAppear() {
		log.trace("[Row:\(currency)] onAppear()")
		
		parsedRowDidChange(forceRefresh: true)
	}
	
	func exchangeRatesDidChange() {
		log.trace("[Row:\(currency)] exchangeRatesDidChange()")
		
		if focusedField != .amountTextfield {
			// The exchangeRates changed, and the user is modifying the value of some other currency.
			// Which means we may need to recalculate and update our amount.
			parsedRowDidChange(forceRefresh: true)
		}
	}
	
	func clearTextField() {
		log.trace("[Row:\(currency)] clearTextField()")
		
		focusedField = .amountTextfield
		parsedAmount = Result.failure(.emptyInput)
		amount = ""
		parsedRow = ParsedRow(currency: currency, parsedAmount: Result.failure(.emptyInput))
	}
	
	func amountDidChange() {
		log.trace("[Row:\(currency)] amountDidChange()")
		
		if focusedField == .amountTextfield {
			switch parsedAmount {
				case .failure(_): isInvalidAmount = true
				case .success(_): isInvalidAmount = false
			}
			parsedRow = ParsedRow(currency: currency, parsedAmount: parsedAmount)
		} else {
			log.trace("[Row:\(currency)] ignoring self-triggered event")
		}
	}
	
	func parsedRowDidChange(forceRefresh: Bool = false) {
		log.trace("[Row:\(currency)] parsedRowDidChange()")
		
		guard let parsedRow = parsedRow else {
			return
		}
		
		if !forceRefresh && parsedRow.currency == currency {
			log.trace("[Row:\(currency)] ignoring self-triggered event")
			return
		}
		
		var srcMsat: Int64? = nil
		var newParsedAmount: Result<Double, TextFieldCurrencyStylerError>? = nil
		var newAmount: String? = nil
		
		if case .success(let srcAmt) = parsedRow.parsedAmount {
			
			switch parsedRow.currency {
			case .bitcoin(let srcBitcoinUnit):
				srcMsat = Utils.toMsat(from: srcAmt, bitcoinUnit: srcBitcoinUnit)
				
			case .fiat(let srcFiatCurrency):
				if let srcExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: srcFiatCurrency) {
					srcMsat = Utils.toMsat(fromFiat: srcAmt, exchangeRate: srcExchangeRate)
				}
			}
		}
		
		if let srcMsat = srcMsat {
			
			switch currency {
			case .bitcoin(let dstBitcoinUnit):
					
				let dstFormattedAmt = Utils.formatBitcoin(msat: srcMsat, bitcoinUnit: dstBitcoinUnit, policy: .hideMsats)
				newParsedAmount = Result.success(dstFormattedAmt.amount)
				newAmount = dstFormattedAmt.digits
			
			case .fiat(let dstFiatCurrency):
				
				if let dstExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: dstFiatCurrency) {
					
					let dstFormattedAmt = Utils.formatFiat(msat: srcMsat, exchangeRate: dstExchangeRate)
					newParsedAmount = Result.success(dstFormattedAmt.amount)
					newAmount = dstFormattedAmt.digits
				}
			}
		}
		
		if let newParsedAmount = newParsedAmount, let newAmount = newAmount {
			isInvalidAmount = false
			parsedAmount = newParsedAmount
			amount = newAmount
		} else {
			isInvalidAmount = true
			parsedAmount = Result.failure(.emptyInput)
			amount = ""
		}
	}
}

// iOS 14 hack
enum FormFields {
	case amountTextfield
}
extension FormFields: FocusStateCompliant {
	static var last: FormFields {
		.amountTextfield
	}
	var next: FormFields? {
		switch self {
			default: return nil
		}
	}
}

fileprivate struct Row_iOS14: View, ViewName {
	
	let currency: Currency
	
	@Binding var parsedRow: ParsedRow?
	@Binding var currencyTextWidth: CGFloat?
	@Binding var flagWidth: CGFloat?
	
	@State var amount: String = ""
	@State var parsedAmount: Result<Double, TextFieldCurrencyStylerError> = Result.failure(.emptyInput)
	
	@State var isInvalidAmount: Bool = false
	
	@FocusStateLegacy var focusedField: FormFields?
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("amount", text: currencyStyler().amountProxy)
					.keyboardType(.decimalPad)
					.disableAutocorrection(true)
					.focusedLegacy($focusedField, equals: .amountTextfield)
					.foregroundColor(isInvalidAmount ? Color.appNegative : Color.primaryForeground)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					clearTextField()
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(Color(UIColor.tertiaryLabel))
				}
				.isHidden(amount == "")
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			
			Text(currency.shortName)
				.frame(width: currencyTextWidth, alignment: .leading)
				.padding(.leading, 10)
			
			switch currency {
			case .bitcoin:
				let fontHeight = UIFont.preferredFont(forTextStyle: .body).pointSize
				Image("bitcoin")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: flagWidth, height: fontHeight, alignment: .center)
					.padding(.leading, 3)
					.offset(x: 0, y: 2)
				
			case .fiat(let fiatCurrency):
				Text(fiatCurrency.flag)
					.frame(width: flagWidth, alignment: .center)
					.padding(.leading, 3)
			}
		}
		.padding()
		.onAppear {
			onAppear()
		}
		.onChange(of: amount) { _ in
			amountDidChange()
		}
		.onChange(of: parsedRow) { _ in
			parsedRowDidChange()
		}
		.onChange(of: currencyPrefs.fiatExchangeRates) { _ in
			exchangeRatesDidChange()
		}
	}
	
	func currencyStyler() -> TextFieldCurrencyStyler {
		return TextFieldCurrencyStyler(
			currency: currency,
			amount: $amount,
			parsedAmount: $parsedAmount,
			hideMsats: false
		)
	}
	
	func onAppear() {
		log.trace("[Row:\(currency)] onAppear()")
		
		parsedRowDidChange()
	}
	
	func exchangeRatesDidChange() {
		log.trace("[Row:\(currency)] exchangeRatesDidChange()")
		
		if focusedField != .amountTextfield {
			// The exchangeRates changed, and the user is modifying the value of some other currency.
			// Which means we may need to recalculate and update our amount.
			parsedRowDidChange()
		}
	}
	
	func clearTextField() {
		log.trace("[Row:\(currency)] clearTextField()")
		
		focusedField = .amountTextfield
		parsedAmount = Result.failure(.emptyInput)
		amount = ""
		parsedRow = ParsedRow(currency: currency, parsedAmount: Result.failure(.emptyInput))
	}
	
	func amountDidChange() {
		log.trace("[Row:\(currency)] amountDidChange()")
		
		if focusedField == .amountTextfield {
			switch parsedAmount {
				case .failure(_): isInvalidAmount = true
				case .success(_): isInvalidAmount = false
			}
			parsedRow = ParsedRow(currency: currency, parsedAmount: parsedAmount)
		} else {
			log.trace("[Row:\(currency)] ignoring self-triggered event")
		}
	}
	
	func parsedRowDidChange() {
		log.trace("[Row:\(currency)] parsedRowDidChange()")
		
		guard let parsedRow = parsedRow else {
			return
		}
		
		if parsedRow.currency == currency {
			log.trace("[Row:\(currency)] ignoring self-triggered event")
			return
		}
		
		var srcMsat: Int64? = nil
		var newParsedAmount: Result<Double, TextFieldCurrencyStylerError>? = nil
		var newAmount: String? = nil
		
		if case .success(let srcAmt) = parsedRow.parsedAmount {
			
			switch parsedRow.currency {
			case .bitcoin(let srcBitcoinUnit):
				srcMsat = Utils.toMsat(from: srcAmt, bitcoinUnit: srcBitcoinUnit)
				
			case .fiat(let srcFiatCurrency):
				if let srcExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: srcFiatCurrency) {
					srcMsat = Utils.toMsat(fromFiat: srcAmt, exchangeRate: srcExchangeRate)
				}
			}
		}
		
		if let srcMsat = srcMsat {
			
			switch currency {
			case .bitcoin(let dstBitcoinUnit):
					
				let dstFormattedAmt = Utils.formatBitcoin(msat: srcMsat, bitcoinUnit: dstBitcoinUnit, policy: .hideMsats)
				newParsedAmount = Result.success(dstFormattedAmt.amount)
				newAmount = dstFormattedAmt.digits
			
			case .fiat(let dstFiatCurrency):
				
				if let dstExchangeRate = currencyPrefs.fiatExchangeRate(fiatCurrency: dstFiatCurrency) {
					
					let dstFormattedAmt = Utils.formatFiat(msat: srcMsat, exchangeRate: dstExchangeRate)
					newParsedAmount = Result.success(dstFormattedAmt.amount)
					newAmount = dstFormattedAmt.digits
				}
			}
		}
		
		if let newParsedAmount = newParsedAmount, let newAmount = newAmount {
			isInvalidAmount = false
			parsedAmount = newParsedAmount
			amount = newAmount
		} else {
			isInvalidAmount = true
			parsedAmount = Result.failure(.emptyInput)
			amount = ""
		}
	}
}

// MARK: -


fileprivate struct CurrencySelector: View, ViewName {
	
	@Binding var selectedCurrencies: [Currency]
	@Binding var replacingCurrency: Currency?
	
	let didSelectCurrency: (Currency) -> Void
	
	@State var visibleBitcoinUnits: [BitcoinUnit] = BitcoinUnit.companion.values
	@State var visibleFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
	
	@State var selectedCurrency: Currency? = nil
	@State var searchText: String = ""
	
	enum BitcoinTextWidth: Preference {}
	let bitcoinTextWidthReader = GeometryPreferenceReader(
		key: AppendValue<BitcoinTextWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var bitcoinTextWidth: CGFloat? = nil
	
	enum FiatTextWidth: Preference {}
	let fiatTextWidthReader = GeometryPreferenceReader(
		key: AppendValue<FiatTextWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var fiatTextWidth: CGFloat? = nil
	
	enum FlagWidth: Preference {}
	let flagWidthReader = GeometryPreferenceReader(
		key: AppendValue<FlagWidth>.self,
		value: { [$0.size.width] }
	)
	@State private var flagWidth: CGFloat? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			// We want to measure various items within the List.
			// But we need to measure ALL of them.
			// And we can't do that with a List because it's lazy.
			//
			// So we have to use this hack,
			// which force-renders all the Text items using a hidden VStack.
			//
			ScrollView {
				VStack {
					ForEach(BitcoinUnit.companion.values) { bitcoinUnit in
						
						Text(bitcoinUnit.shortName)
							.foregroundColor(Color.clear)
							.read(bitcoinTextWidthReader)
							.frame(width: bitcoinTextWidth, alignment: .leading) // required, or refreshes after display
					}
					
					ForEach(FiatCurrency.companion.values) { fiatCurrency in

						Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
							.foregroundColor(Color.clear)
							.read(fiatTextWidthReader)
							.frame(width: fiatTextWidth, alignment: .leading) // required, or refreshes after display
						
						Text(fiatCurrency.flag)
							.foregroundColor(Color.clear)
							.read(flagWidthReader)
							.frame(width: flagWidth, alignment: .leading) // required, or refreshes after display
					}
				}
				.assignMaxPreference(for: bitcoinTextWidthReader.key, to: $bitcoinTextWidth)
				.assignMaxPreference(for: fiatTextWidthReader.key, to: $fiatTextWidth)
				.assignMaxPreference(for: flagWidthReader.key, to: $flagWidth)
			}
			
			if #available(iOS 15.0, *) {
				content
					.searchable(text: $searchText)
					.onChange(of: searchText) { _ in
						searchTextDidChange()
					}
			} else {
				content
			}
		}
		.navigationTitle(NSLocalizedString("Fiat currency", comment: "Navigation bar title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	var content: some View {
		
		List {
			
			Section(header: Text("Bitcoin")) {
				ForEach(visibleBitcoinUnits) { bitcoinUnit in
					
					Button {
						didSelect(bitcoinUnit)
					} label: {
						bitcoinRow(bitcoinUnit)
					}
					.buttonStyle(.plain)
					.disabled(isSelected(bitcoinUnit))
				}
			}
			
			Section(header: Text("Fiat")) {
				ForEach(visibleFiatCurrencies) { fiatCurrency in
					
					Button {
						didSelect(fiatCurrency)
					} label: {
						fiatRow(fiatCurrency)
					}
					.buttonStyle(.plain)
					.disabled(isSelected(fiatCurrency))
				}
			}
		}
		.listStyle(GroupedListStyle())
	}
	
	@ViewBuilder
	func bitcoinRow(_ bitcoinUnit: BitcoinUnit) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text(bitcoinUnit.shortName)
				.frame(width: bitcoinTextWidth, alignment: .leading)
			
			let imgSize = UIFont.preferredFont(forTextStyle: .body).pointSize
			Image("bitcoin")
				.resizable()
				.frame(width: imgSize, height: imgSize, alignment: .center)
				.padding(.leading, 6)
				.offset(x: 0, y: 2)
			
			Spacer()
			
			Text(verbatim: "  \(bitcoinUnit.explanation)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				.padding(.trailing, 4)
			
			Image(systemName: "checkmark")
				.foregroundColor(isSelected(bitcoinUnit) ? .appAccent : .clear)
		}
		.contentShape(Rectangle()) // allow spacer area to be tapped
	}
	
	@ViewBuilder
	func fiatRow(_ fiatCurrency: FiatCurrency) -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			Text_CurrencyName(fiatCurrency: fiatCurrency, fontTextStyle: .body)
				.frame(width: fiatTextWidth, alignment: .leading)
				
			Text(fiatCurrency.flag)
				.frame(width: flagWidth, alignment: .center)
				.padding(.leading, 3)
			
			Text(verbatim: "  \(fiatCurrency.longName)")
				.font(.footnote)
				.foregroundColor(Color.secondary)
				
			Spacer()
			
			Image(systemName: "checkmark")
				.foregroundColor(isSelected(fiatCurrency) ? Color.appAccent : .clear)
		}
		.contentShape(Rectangle()) // allow spacer area to be tapped
	}
	
	func isSelected(_ bitcoinUnit: BitcoinUnit) -> Bool {
		return isSelected(Currency.bitcoin(bitcoinUnit))
	}
	
	func isSelected(_ fiatCurrency: FiatCurrency) -> Bool {
		return isSelected(Currency.fiat(fiatCurrency))
	}
	
	func isSelected(_ currency: Currency) -> Bool {
		
		if currency == selectedCurrency {
			return true
		} else if currency == replacingCurrency {
			return false
		} else {
			return selectedCurrencies.contains(currency)
		}
	}
	
	func didSelect(_ bitcoinUnit: BitcoinUnit) {
		didSelect(Currency.bitcoin(bitcoinUnit))
	}
	
	func didSelect(_ fiatCurrency: FiatCurrency) {
		didSelect(Currency.fiat(fiatCurrency))
	}
	
	func didSelect(_ currency: Currency) {
		log.trace("[\(viewName)] didSelect(\(currency))")
		
		selectedCurrency = currency
		didSelectCurrency(currency)
		popView()
	}
	
	func searchTextDidChange() {
		log.trace("[\(viewName)] searchTextDidChange(): \(searchText)")
		
		let allBitcoinUnits: [BitcoinUnit] = BitcoinUnit.companion.values
		let allFiatCurrencies: [FiatCurrency] = FiatCurrency.companion.values
		
		let components = searchText
			.components(separatedBy: .whitespacesAndNewlines)
			.filter { !$0.isEmpty }
		
		if components.isEmpty {
			
			visibleBitcoinUnits = allBitcoinUnits
			visibleFiatCurrencies = allFiatCurrencies
			
		} else {
			
			visibleBitcoinUnits = allBitcoinUnits.filter { bitcoinUnit in
				components.allSatisfy { component in
					bitcoinUnit.shortName.localizedCaseInsensitiveContains(component)
				}
			}
			
			visibleFiatCurrencies = allFiatCurrencies.filter { fiatCurrency in
				components.allSatisfy { component in
					fiatCurrency.shortName.localizedCaseInsensitiveContains(component) ||
					fiatCurrency.longName.localizedCaseInsensitiveContains(component)
				}
			}
		}
	}
	
	func popView() {
		log.trace("[\(viewName)] popView()")
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.10) {
			presentationMode.wrappedValue.dismiss()
		}
	}
}
