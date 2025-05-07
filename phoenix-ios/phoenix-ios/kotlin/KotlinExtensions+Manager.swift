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
		if let value = self.electrumConfig.value as? ElectrumConfig {
			return value
		} else {
			return ElectrumConfig.Random()
		}
	}
}

extension BalanceManager {
	
	func swapInWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.swapInWallet.value as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value as! Connections
	}
	
	func asyncStream() -> AsyncStream<Connections> {
		
		return AsyncStream<Connections>(bufferingPolicy: .bufferingNewest(1)) { continuation in
			
			let swiftFlow = SwiftFlow<Connections>(origin: self.connections)

			let watcher = swiftFlow.watch {(connections: Connections?) in
				if let connections {
					continuation.yield(connections)
				}
			}
			
			continuation.onTermination = { _ in
				DispatchQueue.main.async {
					// I'm not sure what thread this will be called from.
					// And I've witnessed crashes when invoking `watcher.close()` from  a non-main thread.
					watcher.close()
				}
			}
		}
	}
}

extension SqliteContactsDb {
	
	func contactsListCurrentValue() -> [ContactInfo] {
		return contactsList.value as? [ContactInfo] ?? []
	}
	
	func contactsListCount() -> Int {
		return contactsListCurrentValue().count
	}
}

extension DatabaseManager {
	
	func databasesValue() -> PhoenixDatabases? {
		return databases.value as? PhoenixDatabases
	}
	
	func contactsDbValue() -> SqliteContactsDb? {
		return databasesValue()?.payments.contacts
	}
}

extension NodeParamsManager {
	
	func nodeParamsValue() -> Lightning_kmpNodeParams? {
		return nodeParams.value as? Lightning_kmpNodeParams
	}
}

extension PeerManager {
	
	func peerStateValue() -> Lightning_kmpPeer? {
		return peerState.value as? Lightning_kmpPeer
	}
	
	func channelsFlowValue() -> [Bitcoin_kmpByteVector32: LocalChannelInfo] {
		if let value = self.channelsFlow.value as? [Bitcoin_kmpByteVector32: LocalChannelInfo] {
			return value
		} else {
			return [:]
		}
	}
	
	func channelsValue() -> [LocalChannelInfo] {
		return channelsFlowValue().map { $1 }
	}
	
	func finalWalletValue() -> Lightning_kmpWalletState.WalletWithConfirmations {
		if let value = self.finalWallet.value as? Lightning_kmpWalletState.WalletWithConfirmations {
			return value
		} else {
			return Lightning_kmpWalletState.WalletWithConfirmations.empty()
		}
	}
}

extension WalletManager {
	
	func keyManagerValue() -> Lightning_kmpLocalKeyManager? {
		if let value = keyManager.value as? Lightning_kmpLocalKeyManager {
			return value
		} else {
			return nil
		}
	}
}
