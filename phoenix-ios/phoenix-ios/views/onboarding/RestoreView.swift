import SwiftUI
import Combine
import CloudKit

fileprivate let filename = "RestoreView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct RestoreView: View {
	
	enum NavLinkTag: String, Codable {
		case ManualRestore
	}
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@StateObject var fetcher = FetchSeedsObserver()
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Restore my wallet", comment: "Navigation bar title"))
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
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			content()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			navLink(.ManualRestore) {
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
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
	}
	
	@ViewBuilder
	func iCloudStatus() -> some View {
		
		if fetcher.isFetching {

			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				Text("Checking iCloud for wallet backups…")
			}
			.accessibilityElement(children: .combine)

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
			.accessibilityElement(children: .combine)
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
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
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
						Text(name)
							.font(.headline)
							.accessibilityLabel("wallet: \(name)")
					}
					
					Text("created: \(visibleStringForDate(seedBackup.created))")
						.font(.subheadline)
						.foregroundColor(.secondary)
						.accessibilityLabel("created: \(audibleStringForDate(seedBackup.created))")
				}
			} icon: {
				Image(systemName: "bitcoinsign.circle")
			}
			.padding(.vertical, 4)
		}
	}
	
	@ViewBuilder
	func navLink<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
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
		case .ManualRestore:
			ManualRestoreView()
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
					if underlying.errorCode == CKError.accountTemporarilyUnavailable.rawValue {
						return NSLocalizedString("iCloud account temporarily unavailable",
						                comment: "reason for iCloud error")
					} else {
						return error.localizedDescription
					}
			}
		case .unknown(let underlying /*: Error */):
			return underlying.localizedDescription
		}
	}
	
	func visibleStringForDate(_ date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .short
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	func audibleStringForDate(_ date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .long
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapRow(_ seedBackup: SeedBackup) {
		log.trace("didTapRow: \(seedBackup.name ?? "Wallet")")
		
		let recoveryPhrase = RecoveryPhrase(
			mnemonics    : seedBackup.mnemonics,
			languageCode : seedBackup.language
		)
		
		let chain = Biz.business.chain
		AppSecurity.shared.addWallet(chain: chain, recoveryPhrase: recoveryPhrase) { result in
			switch result {
			case .failure(let reason):
				log.error("Error adding wallet: \(reason)")
				
			case .success():
				Biz.loadWallet(
					trigger: .restoreFromCloudBackup(name: seedBackup.name),
					recoveryPhrase: recoveryPhrase
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
		let chain = Biz.business.chain
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
