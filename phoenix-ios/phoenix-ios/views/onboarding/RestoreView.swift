import SwiftUI
import Combine
import CloudKit
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RestoreView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct RestoreView: View {
	
	@StateObject var fetcher = FetchSeedsObserver()
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			content()
		}
		.navigationBarTitle(
			NSLocalizedString("Restore my wallet", comment: "Navigation bar title"),
			displayMode: .inline
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			NavigationLink(destination: ManualRestoreView()) {
				Label {
					Text("Type in recovery phrase")
				} icon: {
					Image(systemName: "keyboard")
				}
				.font(.title3)
			}
			.padding(.top, 60)
			.padding(.bottom, 30)
			
			iCloudStatus()
			iCloudList()
			
			Spacer()
		}
	}
	
	@ViewBuilder
	func iCloudStatus() -> some View {
		
		if fetcher.isFetching {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				Text("Checking iCloud for wallet backups…")
			}
			
		} else if let error = fetcher.error {
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Error fetching wallet backups from iCloud")
					Text(verbatim: errorInfo(error))
						.font(.callout)
						.foregroundColor(.secondary)
				}
			} icon: {
				Image(systemName: "exclamationmark.icloud")
			}
			
		} else {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "checkmark.icloud")
				if fetcher.results.count == 1 {
					Text("Found 1 wallet in iCloud")
				} else {
					Text("Found \(fetcher.results.count) wallets in iCloud")
				}
			}
		}
	}
	
	@ViewBuilder
	func iCloudList() -> some View {
		
		if fetcher.results.isEmpty {
			EmptyView()
		} else {
			List {
				ForEach(fetcher.results) { seedBackup in
					row(seedBackup)
				}
			}
		}
	}
	
	@ViewBuilder
	func row(_ seedBackup: SeedBackup) -> some View {
		
		Button {
			didTapRow(seedBackup)
		} label: {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 6) {
					let name = seedBackup.name ?? ""
					if name.isEmpty {
						Text("Wallet").font(.headline)
					} else {
						Text(name).font(.headline)
					}
					
					let created = stringForDate(seedBackup.created)
					Text("created: \(created)")
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
			} icon: {
				Image(systemName: "bitcoinsign.circle")
			}
			.padding(.vertical, 4)
		}
	}
	
	func errorInfo(_ error: FetchSeedsError) -> String {
		
		switch error {
		case .cloudKit(let underlying /*: CKError */):
			switch underlying.errorCode {
				case CKError.networkFailure.rawValue:
					return NSLocalizedString("iCloud network failure",
					                comment: "reason for iCloud failure")
				case CKError.networkUnavailable.rawValue:
					return NSLocalizedString("Network unavailable. Check internet connection.",
					                comment: "reason for iCloud failure")
				case CKError.notAuthenticated.rawValue:
					return NSLocalizedString("Please sign into iCloud",
					                comment: "reason for iCloud failure")
				case CKError.serviceUnavailable.rawValue:
					return NSLocalizedString("iCloud service unavailable",
					                comment: "reason for iCloud failure")
				case CKError.zoneBusy.rawValue:
					return NSLocalizedString("iCloud service busy",
					                comment: "reason for iCloud failure")
				default:
					if #available(iOS 15.0, *) {
						if underlying.errorCode == CKError.accountTemporarilyUnavailable.rawValue {
							return NSLocalizedString("iCloud account temporarily unavailable",
							                comment: "reason for iCloud error")
						} else {
							return error.localizedDescription
						}
						
					} else {
						return error.localizedDescription
					}
			}
		case .unknown(let underlying /*: Error */):
			return underlying.localizedDescription
		}
	}
	
	func stringForDate(_ date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .short
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	func didTapRow(_ seedBackup: SeedBackup) {
		log.trace("didTapRow: \(seedBackup.name ?? "Wallet")")
		
		let mnemonics = seedBackup.mnemonics.components(separatedBy: " ")
		
		AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
			if error == nil {
				AppDelegate.get().loadWallet(
					mnemonics: mnemonics,
					walletRestoreType: .fromCloudBackup(name: seedBackup.name)
				)
			}
		}
	}
}

class FetchSeedsObserver: ObservableObject {
	
	@Published var isFetching: Bool = true
	@Published var results: [SeedBackup] = []
	@Published var error: FetchSeedsError? = nil
	
	private var cancellables = Set<AnyCancellable>()
	
	init() {
		let chain = AppDelegate.get().business.chain
		let q = DispatchQueue.main
		
		SyncSeedManager
			.fetchSeeds(chain: chain)
			.collect(.byTime(q, 1.0))
			.filter { (buffer: [SeedBackup]) in
				return !buffer.isEmpty
			}
			.sink {[weak self] completion in
				switch completion {
					case .finished:
						self?.isFetching = false
					case .failure(let error):
						self?.error = error
						self?.isFetching = false
				}
			} receiveValue: {[weak self] seeds in
				self?.results.append(contentsOf: seeds)
			}
			.store(in: &cancellables)
	}
}

extension SeedBackup: Identifiable {
	var id: String { recordID.recordName }
}
