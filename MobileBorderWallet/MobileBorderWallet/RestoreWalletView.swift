import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RestoreWalletView"
)

fileprivate enum NavLinkTag_RestoreWalletView: Equatable, Hashable {
	case decrypt(backup: EntropyGridBackup)
}

struct RestoreWalletView: View {
	
	@State var isLoading = true
	@State var cloudBackups: [EntropyGridCloudBackup] = []
	
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
				ForEach(0..<cloudBackups.count, id: \.self) { index in
					row(cloudBackups[index])
				}
			}
			.listStyle(.insetGrouped)
		}
	}
	
	@ViewBuilder
	func row(_ cloudBackup: EntropyGridCloudBackup) -> some View {
		
		Button {
			didTapRow(cloudBackup)
		} label: {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 6) {
					let name = cloudBackup.name.trimmingCharacters(in: .whitespacesAndNewlines)
					if name.isEmpty {
						Text("Wallet").font(.headline)
					} else {
						Text(name).font(.headline)
					}
					
					Text("created: \(visibleStringForDate(cloudBackup.timestamp))")
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
				
				var availableBackups: [EntropyGridCloudBackup] = []
				for fileURL in files {
					if fileURL.pathExtension == "json" {
						let data = try Data(contentsOf: fileURL)
						
						let backup = try JSONDecoder().decode(EntropyGridCloudBackup.self, from: data)
						availableBackups.append(backup)
					}
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
	
	func didTapRow(_ cloudBackup: EntropyGridCloudBackup) {
		log.trace("didTapRow()")
		
		router.navPath.append(NavLinkTag_RestoreWalletView.decrypt(backup: cloudBackup.backup))
	}
}
