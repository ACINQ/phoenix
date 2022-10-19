import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "GroupPrefs"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


extension UserDefaults {
	static var group: UserDefaults {
		return UserDefaults(suiteName: "group.co.acinq.phoenix")!
	}
}

class GroupPrefs {
	
	fileprivate enum Key: String {
		case currencyType
		case fiatCurrency
		case bitcoinUnit
		case currencyConverterList
		case electrumConfig
	}
	
	public static let shared = GroupPrefs()
	
	var defaults: UserDefaults {
		return UserDefaults.group
	}
	
	var currencyType: CurrencyType {
		get {
			let key = Key.currencyType.rawValue
			let saved: CurrencyType? = defaults.getCodable(forKey: key)
			return saved ?? CurrencyType.bitcoin
		}
		set {
			let key = Key.currencyType.rawValue
			defaults.setCodable(value: newValue, forKey: key)
	  }
	}
	
	lazy private(set) var fiatCurrencyPublisher: CurrentValueSubject<FiatCurrency, Never> = {
		return CurrentValueSubject<FiatCurrency, Never>(self.fiatCurrency)
	}()
	
	var fiatCurrency: FiatCurrency {
		get {
			let key = Key.fiatCurrency.rawValue
			var saved: FiatCurrency? = nil
			if let str = defaults.string(forKey: key) {
				saved = FiatCurrency.deserialize(str)
			}
			return saved ?? FiatCurrency.localeDefault() ?? FiatCurrency.usd
		}
		set {
			let key = Key.fiatCurrency.rawValue
			let str = newValue.serialize()
			defaults.set(str, forKey: key)
			fiatCurrencyPublisher.send(newValue)
	  }
	}
	
	lazy private(set) var bitcoinUnitPublisher: CurrentValueSubject<BitcoinUnit, Never> = {
		return CurrentValueSubject<BitcoinUnit, Never>(self.bitcoinUnit)
	}()
	
	var bitcoinUnit: BitcoinUnit {
		get {
			let key = Key.bitcoinUnit.rawValue
			var saved: BitcoinUnit? = nil
			if let str = defaults.string(forKey: key) {
				saved = BitcoinUnit.deserialize(str)
			}
			return saved ?? BitcoinUnit.sat
		}
		set {
			let key = Key.bitcoinUnit.rawValue
			let str = newValue.serialize()
			defaults.set(str, forKey: key)
			bitcoinUnitPublisher.send(newValue)
		}
	}
	
	lazy private(set) var currencyConverterListPublisher: CurrentValueSubject<[Currency], Never> = {
		return CurrentValueSubject<[Currency], Never>(self.currencyConverterList)
	}()
	
	var currencyConverterList: [Currency] {
		get {
			let key = Key.currencyConverterList.rawValue
			if let list = defaults.string(forKey: key) {
				return Currency.deserializeList(list)
			} else {
				return [Currency]()
			}
		}
		set {
			let key = Key.currencyConverterList.rawValue
			if newValue.isEmpty {
				defaults.removeObject(forKey: key)
			} else {
				let list = Currency.serializeList(newValue)
				defaults.set(list, forKey: key)
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
	
	lazy private(set) var electrumConfigPublisher: CurrentValueSubject<ElectrumConfigPrefs?, Never> = {
		return CurrentValueSubject<ElectrumConfigPrefs?, Never>(self.electrumConfig)
	}()

	var electrumConfig: ElectrumConfigPrefs? {
		get {
			let key = Key.electrumConfig.rawValue
			let saved: ElectrumConfigPrefs? = defaults.getCodable(forKey: key)
			return saved
		}
		set {
			let key = Key.electrumConfig.rawValue
			defaults.setCodable(value: newValue, forKey: key)
			electrumConfigPublisher.send(newValue)
		}
	}
	
	// --------------------------------------------------
	// MARK: Migration
	// --------------------------------------------------
	
	public func performMigration(
		_ targetBuild: String,
		_ completionPublisher: CurrentValueSubject<Int, Never>
	) -> Void {
		log.trace("performMigration(to: \(targetBuild))")
		
		// NB: The first version released in the App Store was version 1.0.0 (build 17)
		
		if targetBuild.isVersion(equalTo: "40") {
			performMigration_toBuild40()
		}
	}
	
	private func performMigration_toBuild40() {
		log.trace("performMigration_toBuild40()")
		
		let MigrateToGroup = {(key: Key) in
			
			let savedGrp = UserDefaults.group.value(forKey: key.rawValue)
			if savedGrp == nil {
				
				let savedStd = UserDefaults.standard.value(forKey: key.rawValue)
				if savedStd != nil {
					
					UserDefaults.group.set(savedStd, forKey: key.rawValue)
					UserDefaults.standard.removeObject(forKey: key.rawValue)
				}
			}
		}
		
		MigrateToGroup(Key.currencyType)
		MigrateToGroup(Key.bitcoinUnit)
		MigrateToGroup(Key.fiatCurrency)
		MigrateToGroup(Key.currencyConverterList)
		MigrateToGroup(Key.electrumConfig)
	}
}
