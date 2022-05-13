import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "Prefs"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum BackupSeedState {
	case notBackedUp
	case backupInProgress
	case safelyBackedUp
}

class Prefs {
	
	private enum Keys: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
		case theme
		case fcmTokenInfo
		case pushPermissionQuery
		case electrumConfig
		case isTorEnabled
		case defaultPaymentDescription
		case hasCKRecordZone
		case hasDownloadedCKRecords
		case backupTransactions_enabled
		case backupTransactions_useCellularData
		case backupTransactions_useUploadDelay
		case backupSeed_enabled
		case backupSeed_hasUploadedSeed
		case backupSeed_name
		case showChannelsRemoteBalance
		case currencyConverterList
		case recentTipPercents
		case manualBackup_taskDone
		case isNewWallet
		case invoiceExpirationDays
		case maxFees
		case hideAmountsOnHomeScreen
	}
	
	public static let shared = Prefs()
	
	private init() {
		UserDefaults.standard.register(defaults: [
			Keys.isNewWallet.rawValue: true,
			Keys.invoiceExpirationDays.rawValue: 7
		])
	}
	
	var currencyType: CurrencyType {
		get {
			let key = Keys.currencyType.rawValue
			let saved: CurrencyType? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? CurrencyType.bitcoin
		}
		set {
			let key = Keys.currencyType.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		return CurrentValueSubject<FiatCurrency, Never>(self.fiatCurrency)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			let key = Keys.fiatCurrency.rawValue
			var saved: FiatCurrency? = nil
			if let str = UserDefaults.standard.string(forKey: key) {
				saved = FiatCurrency.deserialize(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			let key = Keys.fiatCurrency.rawValue
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: key)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		return CurrentValueSubject<BitcoinUnit, Never>(self.bitcoinUnit)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			let key = Keys.bitcoinUnit.rawValue
			var saved: BitcoinUnit? = nil
			if let str = UserDefaults.standard.string(forKey: key) {
				saved = BitcoinUnit.deserialize(str)
			}
			return saved ?? BitcoinUnit.sat
		}
		set {
			let key = Keys.bitcoinUnit.rawValue
			let str = newValue.serialize()
			UserDefaults.standard.set(str, forKey: key)
			bitcoinUnitPublisher.send(newValue)
		}
	}
	
	lazy private(set) var themePublisher: CurrentValueSubject<Theme, Never> = {
		return CurrentValueSubject<Theme, Never>(self.theme)
	}()
	
	var theme: Theme {
		get {
			let key = Keys.theme.rawValue
			let saved: Theme? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? Theme.system
		}
		set {
			let key = Keys.theme.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
			themePublisher.send(newValue)
		}
	}

	lazy private(set) var isTorEnabledPublisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.isTorEnabled)
	}()

	var isTorEnabled: Bool {
		get {
			 UserDefaults.standard.bool(forKey: Keys.isTorEnabled.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.isTorEnabled.rawValue)
			isTorEnabledPublisher.send(newValue)
		}
	}
	
	lazy private(set) var electrumConfigPublisher: CurrentValueSubject<ElectrumConfigPrefs?, Never> = {
		return CurrentValueSubject<ElectrumConfigPrefs?, Never>(self.electrumConfig)
	}()
	
	var electrumConfig: ElectrumConfigPrefs? {
		get {
			let key = Keys.electrumConfig.rawValue
			let saved: ElectrumConfigPrefs? = UserDefaults.standard.getCodable(forKey: key)
			return saved
		}
		set {
			let key = Keys.electrumConfig.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
			electrumConfigPublisher.send(newValue)
		}
	}
	
	var defaultPaymentDescription: String? {
		get {
			let key = Keys.defaultPaymentDescription.rawValue
			let saved: String? = UserDefaults.standard.string(forKey: key)
			return saved
		}
		set {
			let key = Keys.defaultPaymentDescription.rawValue
			UserDefaults.standard.setValue(newValue, forKey: key)
		}
	}
	
	var showChannelsRemoteBalance: Bool {
		get {
			UserDefaults.standard.bool(forKey: Keys.showChannelsRemoteBalance.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.showChannelsRemoteBalance.rawValue)
		}
	}
	
	/**
	 * Set to true, until the user has funded their wallet at least once.
	 * A false value does NOT indicate that the wallet has funds.
	 * Just that the wallet had either a non-zero balance, or a transaction, at least once.
	 */
	var isNewWallet: Bool {
		get {
			 UserDefaults.standard.bool(forKey: Keys.isNewWallet.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.isNewWallet.rawValue)
		}
	}
	
	var invoiceExpirationDays: Int {
		get {
			UserDefaults.standard.integer(forKey: Keys.invoiceExpirationDays.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.invoiceExpirationDays.rawValue)
		}
	}
	
	lazy private(set) var maxFeesPublisher: CurrentValueSubject<MaxFees?, Never> = {
		return CurrentValueSubject<MaxFees?, Never>(self.maxFees)
	}()
	
	var maxFees: MaxFees? {
		get {
			let key = Keys.maxFees.rawValue
			let result: MaxFees? = UserDefaults.standard.getCodable(forKey: key)
			return result
		}
		set {
			let key = Keys.maxFees.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
			log.debug("Prefs.maxFees: \(String(describing: newValue))")
			maxFeesPublisher.send(newValue)
		}
	}
	
	var hideAmountsOnHomeScreen: Bool {
		get {
			 UserDefaults.standard.bool(forKey: Keys.hideAmountsOnHomeScreen.rawValue)
		}
		set {
			UserDefaults.standard.set(newValue, forKey: Keys.hideAmountsOnHomeScreen.rawValue)
		}
	}
	
	// --------------------------------------------------
	// MARK: Currency Conversion
	// --------------------------------------------------
	
	lazy private(set) var currencyConverterListPublisher: CurrentValueSubject<[Currency], Never> = {
		return CurrentValueSubject<[Currency], Never>(self.currencyConverterList)
	}()
	
	var currencyConverterList: [Currency] {
		get {
			if let list = UserDefaults.standard.string(forKey: Keys.currencyConverterList.rawValue) {
				log.debug("get: currencyConverterList = \(list)")
				return Currency.deserializeList(list)
			} else {
				log.debug("get: currencyConverterList = nil")
				return [Currency]()
			}
		}
		set {
			if newValue.isEmpty {
				log.debug("set: currencyConverterList = nil")
				UserDefaults.standard.removeObject(forKey: Keys.currencyConverterList.rawValue)
			} else {
				let list = Currency.serializeList(newValue)
				log.debug("set: currencyConverterList = \(list)")
				UserDefaults.standard.set(list, forKey: Keys.currencyConverterList.rawValue)
			}
		}
	}
	
	var preferredFiatCurrencies: [FiatCurrency] {
		get {
			var resultArray = [self.fiatCurrency]
			var resultSet = Set<FiatCurrency>(resultArray)
			
			for currency in self.currencyConverterList {
				if case .fiat(let fiat) = currency {
					let (inserted, _) = resultSet.insert(fiat)
					if inserted {
						resultArray.append(fiat)
					}
				}
			}
			
			return resultArray
		}
	}
	
	// --------------------------------------------------
	// MARK: Recent Tips
	// --------------------------------------------------
	
	/**
	 * The SendView includes a Quick Tips feature,
	 * where we remember recent tip-percentages used by the user.
	 */
	
	/// Most recent is at index 0
	var recentTipPercents: [Int] {
		get {
			let key = Keys.recentTipPercents.rawValue
			let saved: [Int]? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? []
		}
	}
	
	func addRecentTipPercent(_ percent: Int) {
		var recents = recentTipPercents
		if let idx = recents.firstIndex(of: percent) {
			recents.remove(at: idx)
		}
		recents.insert(percent, at: 0)
		while recents.count > 6 {
			recents.removeLast()
		}
		
		let key = Keys.recentTipPercents.rawValue
		UserDefaults.standard.setCodable(value: recents, forKey: key)
	}
	
	// --------------------------------------------------
	// MARK: Push Notifications
	// --------------------------------------------------
	
	var fcmTokenInfo: FcmTokenInfo? {
		get {
			let key = Keys.fcmTokenInfo.rawValue
			let result: FcmTokenInfo? = UserDefaults.standard.getCodable(forKey: key)
			return result
		}
		set {
			let key = Keys.fcmTokenInfo.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
		}
	}
	
	var pushPermissionQuery: PushPermissionQuery {
		get {
			let key = Keys.pushPermissionQuery.rawValue
			let saved: PushPermissionQuery? = UserDefaults.standard.getCodable(forKey: key)
			return saved ?? .neverAskedUser
		}
		set {
			let key = Keys.pushPermissionQuery.rawValue
			UserDefaults.standard.setCodable(value: newValue, forKey: key)
		}
	}
	
	// --------------------------------------------------
	// MARK: Cloud Tx Backup
	// --------------------------------------------------
	
	private func recordZoneCreatedKey(_ encryptedNodeId: String) -> String {
		return "\(Keys.hasCKRecordZone.rawValue)-\(encryptedNodeId)"
	}
	
	func recordZoneCreated(encryptedNodeId: String) -> Bool {
		
		return UserDefaults.standard.bool(forKey: recordZoneCreatedKey(encryptedNodeId))
	}
	
	func setRecordZoneCreated(_ value: Bool, encryptedNodeId: String) {
		
		let key = recordZoneCreatedKey(encryptedNodeId)
		if value == true {
			UserDefaults.standard.setValue(value, forKey: key)
		} else {
			UserDefaults.standard.removeObject(forKey: key)
		}
	}
	
	private func hasDownloadedRecordsKey(_ encryptedNodeId: String) -> String {
		return "\(Keys.hasDownloadedCKRecords.rawValue)-\(encryptedNodeId)"
	}
	
	func hasDownloadedRecords(encryptedNodeId: String) -> Bool {
		
		return UserDefaults.standard.bool(forKey: hasDownloadedRecordsKey(encryptedNodeId))
	}
	
	func setHasDownloadedRecords(_ value: Bool, encryptedNodeId: String) {
		
		let key = hasDownloadedRecordsKey(encryptedNodeId)
		if value == true {
			UserDefaults.standard.setValue(value, forKey: key)
		} else {
			// Remove trace of account on disk
			UserDefaults.standard.removeObject(forKey: key)
		}
	}
	
	lazy private(set) var backupTransactions_isEnabledPublisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.backupTransactions_isEnabled)
	}()
	
	var backupTransactions_isEnabled: Bool {
		get {
			let key = Keys.backupTransactions_enabled.rawValue
			if UserDefaults.standard.object(forKey: key) != nil {
				return UserDefaults.standard.bool(forKey: key)
			} else {
				return true // default value
			}
		}
		set {
			let key = Keys.backupTransactions_enabled.rawValue
			UserDefaults.standard.set(newValue, forKey: key)
			backupTransactions_isEnabledPublisher.send(newValue)
		}
	}
	
	var backupTransactions_useCellular: Bool {
		get {
			let key = Keys.backupTransactions_useCellularData.rawValue
			if UserDefaults.standard.object(forKey: key) != nil {
				return UserDefaults.standard.bool(forKey: key)
			} else {
				return true // default value
			}
		}
		set {
			let key = Keys.backupTransactions_useCellularData.rawValue
			UserDefaults.standard.set(newValue, forKey: key)
		}
	}
	
	var backupTransactions_useUploadDelay: Bool {
		get {
			let key = Keys.backupTransactions_useUploadDelay.rawValue
			if UserDefaults.standard.object(forKey: key) != nil {
				return UserDefaults.standard.bool(forKey: key)
			} else {
				return false // default value
			}
		}
		set {
			let key = Keys.backupTransactions_useUploadDelay.rawValue
			UserDefaults.standard.set(newValue, forKey: key)
		}
	}
	
	// --------------------------------------------------
	// MARK: Cloud Seed Backup
	// --------------------------------------------------
	
	lazy private(set) var backupSeed_isEnabled_publisher: CurrentValueSubject<Bool, Never> = {
		return CurrentValueSubject<Bool, Never>(self.backupSeed_isEnabled)
	}()
	
	var backupSeed_isEnabled: Bool {
		get {
			let key = Keys.backupSeed_enabled.rawValue
			if UserDefaults.standard.object(forKey: key) != nil {
				return UserDefaults.standard.bool(forKey: key)
			} else {
				return false // default value
			}
		}
		set {
			let key = Keys.backupSeed_enabled.rawValue
			UserDefaults.standard.set(newValue, forKey: key)
			backupSeed_isEnabled_publisher.send(newValue)
		}
	}
	
	lazy private(set) var backupSeed_hasUploadedSeed_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func backupSeed_hasUploadedSeed_key(_ encryptedNodeId: String) -> String {
		return "\(Keys.backupSeed_hasUploadedSeed.rawValue)-\(encryptedNodeId)"
	}
	
	func backupSeed_hasUploadedSeed(encryptedNodeId: String) -> Bool {
		
		return UserDefaults.standard.bool(forKey: backupSeed_hasUploadedSeed_key(encryptedNodeId))
	}
	
	func backupSeed_setHasUploadedSeed(_ value: Bool, encryptedNodeId: String) {
		
		let key = backupSeed_hasUploadedSeed_key(encryptedNodeId)
		if value == true {
			UserDefaults.standard.setValue(value, forKey: key)
		} else {
			UserDefaults.standard.removeObject(forKey: key)
		}
		backupSeed_hasUploadedSeed_publisher.send()
	}
	
	lazy private(set) var backupSeed_name_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func backupSeed_name_key(_ encryptedNodeId: String) -> String {
		return "\(Keys.backupSeed_name)-\(encryptedNodeId)"
	}
	
	func backupSeed_name(encryptedNodeId: String) -> String? {
		
		return UserDefaults.standard.string(forKey: backupSeed_name_key(encryptedNodeId))
	}
	
	func backupSeed_setName(_ value: String?, encryptedNodeId: String) {
		
		let key = backupSeed_name_key(encryptedNodeId)
		let oldValue = backupSeed_name(encryptedNodeId: encryptedNodeId) ?? ""
		let newValue = value ?? ""
		
		if oldValue != newValue {
			if newValue.isEmpty {
				UserDefaults.standard.removeObject(forKey: key)
			} else {
				UserDefaults.standard.setValue(newValue, forKey: key)
			}
			backupSeed_setHasUploadedSeed(false, encryptedNodeId: encryptedNodeId)
			backupSeed_name_publisher.send()
		}
	}
	
	// --------------------------------------------------
	// MARK: Manual Seed Backup
	// --------------------------------------------------
	
	lazy private(set) var manualBackup_taskDone_publisher: PassthroughSubject<Void, Never> = {
		return PassthroughSubject<Void, Never>()
	}()
	
	private func manualBackup_taskDone_key(_ encryptedNodeId: String) -> String {
		return "\(Keys.manualBackup_taskDone)-\(encryptedNodeId)"
	}
	
	func manualBackup_taskDone(encryptedNodeId: String) -> Bool {
		
		return UserDefaults.standard.bool(forKey: manualBackup_taskDone_key(encryptedNodeId))
	}
	
	func manualBackup_setTaskDone(_ newValue: Bool, encryptedNodeId: String) {
		
		let key = manualBackup_taskDone_key(encryptedNodeId)
		if newValue {
			UserDefaults.standard.setValue(newValue, forKey: key)
		} else {
			UserDefaults.standard.removeObject(forKey: key)
		}
		manualBackup_taskDone_publisher.send()
	}
	
	// --------------------------------------------------
	// MARK: Seed Backup State
	// --------------------------------------------------
	
	func backupSeedStatePublisher(_ encryptedNodeId: String) -> AnyPublisher<BackupSeedState, Never> {
		
		let publisher = Publishers.CombineLatest3(
			backupSeed_isEnabled_publisher,       // CurrentValueSubject<Bool, Never>
			backupSeed_hasUploadedSeed_publisher, // PassthroughSubject<Void, Never>
			manualBackup_taskDone_publisher       // PassthroughSubject<Void, Never>
		).map { (backupSeed_isEnabled: Bool, _, _) -> BackupSeedState in
			
			let prefs = Prefs.shared
			
			let backupSeed_hasUploadedSeed = prefs.backupSeed_hasUploadedSeed(encryptedNodeId: encryptedNodeId)
			let manualBackup_taskDone = prefs.manualBackup_taskDone(encryptedNodeId: encryptedNodeId)
			
			if backupSeed_isEnabled {
				if backupSeed_hasUploadedSeed {
					return .safelyBackedUp
				} else {
					return .backupInProgress
				}
			} else {
				if manualBackup_taskDone {
					return .safelyBackedUp
				} else {
					return .notBackedUp
				}
			}
		}
		.handleEvents(receiveRequest: { _ in
			
			// Publishers.CombineLatest doesn't fire until all publishers have emitted a value.
			// We don't have have to worry about that with the CurrentValueSubject, because it always has a value.
			// But for the PassthroughSubject publishers, this poses a problem.
			//
			// The other related publishers (Merge & Zip) don't do exactly what we want either.
			// So we're taking the simplest approach, and force-firing the associated PassthroughSubject publishers.
			
			let prefs = Prefs.shared
			
			prefs.backupSeed_hasUploadedSeed_publisher.send()
			prefs.manualBackup_taskDone_publisher.send()
		})
		.eraseToAnyPublisher()
		
		return publisher
	}
}
