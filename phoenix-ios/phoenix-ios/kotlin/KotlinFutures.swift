import Foundation
import Combine
import PhoenixShared

fileprivate let filename = "KotlinFutures"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
