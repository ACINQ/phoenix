import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "WalletReset"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


/// Facilitates deleting the local data for a user's wallet.
///
/// Architecture Notes:
/// This class is one step of a larger process that must take place.
/// In particular, the vast majority of the UI is deeply connected with the PhoenixShared instance.
/// And we're trying to reset PhoenixShared instance, along with the UI.
/// To accomplish this, we perform the following steps:
///
/// 1. Remove the ContentView & associated Window from the system.
///    This happens in the SceneDelegate.
///
/// 2. Use the WalletReset class to reset everything.
///
/// 3. Recreate the ContentView & associated Window.
///    Doing so will create a new UIHostingController,
///    which will come with a fresh State for every view,
///    ensuring that no references to the previous PhoenixShared instance are being used.
///
class WalletReset {
	
	/// Singleton instance
	public static let shared = WalletReset()
	
	enum Progress: Int, CustomStringConvertible {
		
		case starting              = 0
		case disconnecting         = 1
		case closingBiz            = 2
		case deletingDatabaseFiles = 3
		case resetingUserDefaults  = 4
		case deletingKeychainItems = 5
		case resettingBiz          = 6
		case done                  = 7
		
		var description: String {
			switch self {
				case .starting              : return "\(self.rawValue):starting"
				case .disconnecting         : return "\(self.rawValue):disconnecting"
				case .closingBiz            : return "\(self.rawValue):closingBiz"
				case .deletingDatabaseFiles : return "\(self.rawValue):deletingDatabaseFiles"
				case .resetingUserDefaults  : return "\(self.rawValue):resetingUserDefaults"
				case .deletingKeychainItems : return "\(self.rawValue):deletingKeychainItems"
				case .resettingBiz          : return "\(self.rawValue):resettingBiz"
				case .done                  : return "\(self.rawValue):done"
			}
		}
	}
	
	public var progress = CurrentValueSubject<Progress, Never>(.starting)
	
	private var didDisconnect = false
	private var cancellables = Set<AnyCancellable>()
	
	private init() { /* must use shared instance */ }
	
	public func start() {
		log.trace("start()")
		
		// Reset local variables
		didDisconnect = false
		
		// Start process
		step1()
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	private func connectionsChanged(_ connections: Connections) {
		log.trace("connectionsChanged()")
		
		switch connections.peer {
			case is Lightning_kmpConnection.ESTABLISHED  : log.debug("connections.peer = ESTABLISHED")
			case is Lightning_kmpConnection.ESTABLISHING : log.debug("connections.peer = ESTABLISHING")
			case is Lightning_kmpConnection.CLOSED       : log.debug("connections.peer = CLOSED")
			default                                      : log.debug("connections.peer = UNKNOWN")
		}
		switch connections.electrum {
			case is Lightning_kmpConnection.ESTABLISHED  : log.debug("connections.electrum = ESTABLISHED")
			case is Lightning_kmpConnection.ESTABLISHING : log.debug("connections.electrum = ESTABLISHING")
			case is Lightning_kmpConnection.CLOSED       : log.debug("connections.electrum = CLOSED")
			default                                      : log.debug("connections.electrum = UNKNOWN")
		}
		
		if connections.peer is Lightning_kmpConnection.CLOSED &&
			connections.electrum is Lightning_kmpConnection.CLOSED
		{
			if !didDisconnect {
				didDisconnect = true
				step2()
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Steps
	// --------------------------------------------------
	
	/**
	 * The first thing we need to do is close the network connections (peer & electrum).
	 * This brings the state machine to a normal rest,
	 * after which it's safe to start closing stuff, and perging stuff from memory.
	*/
	private func step1() {
		log.trace("step1()")
		progress.send(.disconnecting)
		
		Biz.business.connectionsManager.publisher.sink { (connections: Connections) in
			self.connectionsChanged(connections)
		}
		.store(in: &cancellables)
		
		Biz.business.appConnectionsDaemon?.incrementDisconnectCount(
			target: AppConnectionsDaemon.ControlTarget.companion.All
		)
	}
	
	/**
	 * The next step is to shutdown the phoenix-shared layer (i.e. the Kotlin Multiplatform stuff).
	 * To do this, we cancel the CoroutineScope of all managers, and close all database connections.
	 * This prevents any timers from firing, disconnects any StateFlow collectors,
	 * and generally disconnects all the plumbing that connects everything together.
	 */
	private func step2() {
		log.trace("step2()")
		progress.send(.closingBiz)
		
		Biz.stop()
		
		// Introducing a short delay here to allow any in-flight operations to finish.
		// This includes any necessary disk IO for the database stuff.
		//
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
			self.step3()
		}
	}
	
	/**
	 * Next we delete all the local database files.
	 * - app.sqlite      => stores fiat exchange rates & general preferences
	 * - channels.sqlite => stores all channel data
	 * - payments.qlite  => stores transaction history
	 */
	private func step3() {
		log.trace("step3()")
		progress.send(.deletingDatabaseFiles)
		
		let fm = FileManager.default
		guard
			let groupDir = fm.containerURL(forSecurityApplicationGroupIdentifier: "group.co.acinq.phoenix")
		else {
			return step4()
		}
		
		let dbDir = groupDir.appendingPathComponent("databases", isDirectory: true)
		
		let chainName = Biz.business.chain.name.lowercased()
		let nodeIdHash = Biz.nodeIdHash ?? "nil"
		
		log.debug("dbDir: \(dbDir.path)")
		log.debug("chainName: \(chainName)")
		log.debug("nodeIdHash: \(nodeIdHash)")
		
		let expectedFiles = [
			"app.sqlite",
			"app.sqlite-shm",
			"app.sqlite-wal",
			"channels-\(chainName)-\(nodeIdHash).sqlite",
			"channels-\(chainName)-\(nodeIdHash).sqlite-shm",
			"channels-\(chainName)-\(nodeIdHash).sqlite-wal",
			"payments-\(chainName)-\(nodeIdHash).sqlite",
			"payments-\(chainName)-\(nodeIdHash).sqlite-shm",
			"payments-\(chainName)-\(nodeIdHash).sqlite-wal"
		]
		
		for expectedFile in expectedFiles {
			
			let fileUrl = dbDir.appendingPathComponent(expectedFile, isDirectory: false)
			do {
				try fm.removeItem(at: fileUrl)
				log.info("Deleted database file: \(expectedFile)")
			} catch {
				log.error("Error deleting database file(\(expectedFile)): \(error)")
			}
		}
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
			self.step4()
		}
	}
	
	/**
	 * Next we reset the UserDefaults system.
	 * This includes a bunch of small things related to the user's wallet,
	 * such as the user's "default payment description" & "recent tip percentages".
	 */
	private func step4() {
		log.trace("step4()")
		progress.send(.resetingUserDefaults)
		
		let encrypedNodeId = Biz.encryptedNodeId ?? "nil"
		
		Prefs.shared.resetWallet(encryptedNodeId: encrypedNodeId)
		GroupPrefs.shared.resetWallet()
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
			self.step5()
		}
	}
	
	/**
	 * Next we delete the wallet's recovery phrase.
	 * This includes deleting the "security.json" file, and all the items stored in the keychain.
	 */
	private func step5() {
		log.trace("step5()")
		progress.send(.deletingKeychainItems)
		
		AppSecurity.shared.resetWallet()
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
			self.step6()
		}
	}
	
	/**
	 * Finally we reset the BusinessManager & GlobalEnvironment.
	 */
	private func step6() {
		log.trace("step6()")
		progress.send(.resettingBiz)
		
		Biz.reset()               // Must be 1st
		GlobalEnvironment.reset() // Must be 2nd
		
		LockState.shared.walletExistence = .doesNotExist
		LockState.shared.isUnlocked = true
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
			self.finish()
		}
	}
	
	private func finish() {
		log.trace("finish()")
		progress.send(.done)
		
		cancellables.removeAll()
	}
}
