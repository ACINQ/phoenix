import Foundation
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "KotlinFutures"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

extension DatabaseManager {
	
	func getDatabases() -> Future<Lightning_kmpDatabases, Never> {
		
		return Future { promise in
			
			let flow = SwiftStateFlow<Lightning_kmpDatabases>(origin: self.databases)
			
			var watcher: Ktor_ioCloseable? = nil
			watcher = flow.watch { (databases: Lightning_kmpDatabases?) in
				
				if let databases = databases {
					promise(.success(databases))
					let _watcher = watcher
					DispatchQueue.main.async {
						_watcher?.close()
					}
				}
			}
		}
	}
}
