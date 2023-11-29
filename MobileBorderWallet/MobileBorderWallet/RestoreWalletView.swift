import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RestoreWalletView"
)

fileprivate enum NavLinkTag_RestoreWalletView: Equatable, Hashable {
	case decrypt(backup: EntropyGridBackup)
}

struct CloudBackupRow: Equatable, Identifiable {
	let fileURL: URL
	let cloudInfo: EntropyGridCloudBackup
	
	var id: String {
		return fileURL.absoluteString
	}
}

struct RestoreWalletView: View {
	
	@State var isLoading = true
	@State var cloudBackups: [CloudBackupRow] = []
	
	@EnvironmentObject var router: Router
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Restore Wallet")
			.navigationBarTitleDisplayMode(.inline)
			.navigationDestination(for: NavLinkTag_RestoreWalletView.self) { tag in
				
				switch tag {
				case .decrypt(let backup):
					PatternSketchView(type: .decrypt(backup: backup))
				}
			}
			.onAppear {
				onAppear()
			}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		if isLoading {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView().progressViewStyle(CircularProgressViewStyle())
				Text("Loading...")
			}
			.padding(.top, 40)
			
		} else if cloudBackups.isEmpty {
			
			Text("Please backup a wallet first")
				.padding(.top, 40)
			
		} else {
			
			List {
				ForEach(cloudBackups) { cloudBackupRow in
					listRow(cloudBackupRow)
						.swipeActions(allowsFullSwipe: false) {
							Button(role: .destructive) {
								deleteRow(cloudBackupRow)
							} label: {
								Label("Delete", systemImage: "trash.fill")
							}
						}
					
				} // </ForEach>
			} // </List>
			.listStyle(.insetGrouped)
		}
	}
	
	@ViewBuilder
	func listRow(_ row: CloudBackupRow) -> some View {
		
		Button {
			didTapRow(row)
		} label: {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 6) {
					let name = row.cloudInfo.name.trimmingCharacters(in: .whitespacesAndNewlines)
					if name.isEmpty {
						Text("Wallet").font(.headline)
					} else {
						Text(name).font(.headline)
					}
					
					Text("created: \(visibleStringForDate(row.cloudInfo.timestamp))")
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
			} icon: {
				Image(systemName: "bitcoinsign.circle")
			}
			.padding(.vertical, 4)
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		isLoading = true
		Task {
			do {
				// Get the document directory url
				let documentDirectory = try FileManager.default.url(
					for: .documentDirectory,
					in: .userDomainMask,
					appropriateFor: nil,
					create: true
				)
				
				let files = try FileManager.default.contentsOfDirectory(
					at: documentDirectory,
					includingPropertiesForKeys: nil
				)
				
				var availableBackups: [CloudBackupRow] = []
				for fileURL in files {
					if fileURL.pathExtension == "json" {
						let data = try Data(contentsOf: fileURL)
						
						let cloudInfo = try JSONDecoder().decode(EntropyGridCloudBackup.self, from: data)
						availableBackups.append(CloudBackupRow(fileURL: fileURL, cloudInfo: cloudInfo))
					}
				}
				
				availableBackups.sort { row1, row2 in
					// Return true if the first argument should be ordered before the second argument.
					// Otherwise return false.
					//
					// We want to sort the backups such that the most RECENT is at the beginning (index zero).
					return row1.cloudInfo.timestamp > row2.cloudInfo.timestamp
				}
				
				DispatchQueue.main.async {
					self.cloudBackups = availableBackups
					self.isLoading = false
				}
				
			} catch {
				log.error("Error: \(error)")
			}
			
		} // </Task>
	}
	
	func visibleStringForDate(_ date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .short
		formatter.timeStyle = .short
		return formatter.string(from: date)
	}
	
	func didTapRow(_ row: CloudBackupRow) {
		log.trace("didTapRow()")
		
		router.navPath.append(NavLinkTag_RestoreWalletView.decrypt(backup: row.cloudInfo.backup))
	}
	
	func deleteRow(_ row: CloudBackupRow) {
		log.trace("deleteRow(\(row.fileURL.lastPathComponent)")
		
		do {
			try FileManager.default.removeItem(at: row.fileURL)
			if let index = cloudBackups.firstIndex(where: { $0 == row }) {
				cloudBackups.remove(at: index)
			}
			
		} catch {
			log.debug("Error deleting file: \(error)")
		}
	}
}
