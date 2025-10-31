import Foundation
@preconcurrency import PhoenixShared
import Combine

extension AppConfigurationManager {
	
	var isTorEnabledValue: Bool {
		if let value = self.isTorEnabled.value as? Bool {
			return value
		} else {
			return false
		}
	}
	
	var electrumConfigValue: ElectrumConfig {
		if let value = self.electrumConfig.value {
			return value
		} else {
			return ElectrumConfig.Random()
		}
	}
}

extension BalanceManager {
	
	func swapInWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.swapInWallet.value {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension SqliteCardsDb {
	
	var cardsListValue: [BoltCardInfo] {
		return self.cardsList.value
	}
}

extension CurrencyManager {
	
	var ratesFlowValue: [ExchangeRate] {
		return self.ratesFlow.value
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value
	}
}

extension SqliteContactsDb {
	
	func contactsListCurrentValue() -> [ContactInfo] {
		return contactsList.value
	}
	
	func contactsListCount() -> Int {
		return contactsListCurrentValue().count
	}
}

extension DatabaseManager {
	
	func databasesValue() -> PhoenixDatabases? {
		return databases.value
	}
	
	func contactsDbValue() -> SqliteContactsDb? {
		return databasesValue()?.payments.contacts
	}
}

extension NodeParamsManager {
	
	func nodeParamsValue() -> Lightning_kmpNodeParams? {
		return nodeParams.value
	}
}

extension PeerManager {
	
	func peerStateValue() -> Lightning_kmpPeer? {
		return peerState.value
	}
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value {
			return value
		} else {
			return [:]
		}
	}
	
	func channelsValue() -> [LocalChannelInfo] {
		return channelsFlowValue().map { $1 }
	}
	
	func finalWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.finalWallet.value {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension WalletManager {
	
	func keyManagerValue() -> Lightning_kmpLocalKeyManager? {
		if let value = keyManager.value {
			return value
		} else {
			return nil
		}
	}
}
