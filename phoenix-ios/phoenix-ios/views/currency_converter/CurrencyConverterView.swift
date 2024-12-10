import SwiftUI
import PhoenixShared

fileprivate let filename = "CurrencyConverterView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
	
	enum NavLinkTag: String, Codable {
		case CurrencySelector
	}

	let initialAmount: CurrencyAmount?
	let didChange: ((CurrencyAmount?) -> Void)?
	let didClose: (() -> Void)?
	
	@State var lastChange: CurrencyAmount?
	
	@State var currencies: [Currency] = GroupPrefs.shared.currencyConverterList
	
	@State var parsedRow: ParsedRow? = nil
	
	@State var replacingCurrency: Currency? = nil
	
	@State var didAppear = false
	@State var currencySelectorOpen = false
	
	@State var isRefreshingExchangeRates = false
	
	let refreshingExchangeRatesPublisher = Biz.business.currencyManager.refreshPublisher()
	
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
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@Environment(\.colorScheme) var colorScheme
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(
		initialAmount: CurrencyAmount? = nil,
		didChange: ((CurrencyAmount?) -> Void)? = nil,
		didClose: (() -> Void)? = nil
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
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			hiddenContent()
			content()
		}
		.onAppear {
			onAppear()
		}
		.onDisappear {
			onDisappear()
		}
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
	func hiddenContent() -> some View {
		
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
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Color.primaryBackground.frame(height: 25)
			
			List {
				currencyRows()
				lastRow()
					.listRowInsets(EdgeInsets()) // remove extra padding space in rows
					.listRowSeparator(.hidden)   // remove lines between items
					.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
			}
			.toolbar {
				EditButton()
			}
			.listStyle(.plain)
			.listBackgroundColor(Color(.systemBackground))
			
			footer()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func currencyRows() -> some View {
		
		ForEach(currencies) { currency in
			CurrencyConverterRow(
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
		case .CurrencySelector:
			CurrencySelector(
				selectedCurrencies: $currencies,
				replacingCurrency: $replacingCurrency,
				didSelectCurrency: didSelectCurrency
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
	// MARK: Notifications
	// --------------------------------------------------
	
	private func onAppear() {
		log.trace("onAppear()")
		
		// Careful: this function may be called when returning from the Receive/Send view
		if !didAppear {
			didAppear = true
			
			UIScrollView.appearance().keyboardDismissMode = .interactive
			
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
		} else {
			
			currencySelectorOpen = false
		}
	}
	
	private func onDisappear() {
		log.trace("onDisappear()")
		
		if !currencySelectorOpen, let didClose {
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
		
		if tag == .CurrencySelector {
			currencySelectorOpen = true
		}
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
		navigateTo(.CurrencySelector)
	}
	
	private func addRow() {
		log.trace("addRow()")
		
		replacingCurrency = nil
		navigateTo(.CurrencySelector)
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
