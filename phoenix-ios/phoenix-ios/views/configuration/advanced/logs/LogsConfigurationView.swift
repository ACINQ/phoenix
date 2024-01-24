import SwiftUI
import PhoenixShared
import os.log
import OSLog

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LogsConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct LogsConfigurationView: View {

	@State var isExporting = false
	@State var shareUrl: URL? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme

	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle(NSLocalizedString("Logs", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			Color.primaryBackground
				.ignoresSafeArea(.all, edges: .all)
			
			content()
			toast.view()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Here you can export the application logs, or share them.")
				.padding()
			
			List {
				Button {
					export()
				} label: {
					Label {
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Text("Share the logs")
							if isExporting {
								Spacer()
								ProgressView().progressViewStyle(CircularProgressViewStyle())
							}
						}
					} icon: {
						Image(systemName: "square.and.arrow.up")
					}
				}
				.disabled(isExporting)
				
			} // </List>
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
			
		} // </VStack>
		.sheet(isPresented: shareUrlBinding()) {
			let items: [Any] = [shareUrl!]
			ActivityView(activityItems: items, applicationActivities: nil)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func shareUrlBinding() -> Binding<Bool> {
		
		return Binding(
			get: {
				return shareUrl != nil
			},
			set: {
				if !$0 {
					if let tempFileUrl = shareUrl {
						shareUrl = nil
						DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 5.0) {
							do {
								try FileManager.default.removeItem(at: tempFileUrl)
								log.debug("Deleted tmp file: \(tempFileUrl)")
							} catch {
								log.error("Error deleting tmp file: \(error)")
							}
						}
					}
				}
			}
		)
	}
	
	private func export() {
		log.trace("export()")
		
		guard isExporting == false else {
			return
		}
		
		isExporting = true
		Task.detached {
			await asyncExport()
		}
	}
	
	// --------------------------------------------------
	// MARK: Exporting
	// --------------------------------------------------
	
	nonisolated func asyncExport() async {
		log.trace("asyncExport()")
		
		do {
			let store = try OSLogStore(scope: .currentProcessIdentifier)
			let position = store.position(timeIntervalSinceLatestBoot: 0)
			let entries = try store.getEntries(at: position)
				.compactMap { $0 as? OSLogEntryLog }
				.filter { !$0.subsystem.hasPrefix("com.apple.") } // remove Apple spam (there's a LOT of it)
				.map { "[\($0.date.formatted(.iso8601))] [\($0.subsystem)] [\($0.category)] \($0.composedMessage)" }
			
			log.debug("entries.count = \(entries.count)")
			
			let random = UUID().uuidString
				.replacingOccurrences(of: "-", with: "")
				.substring(location: 0, length: 8)
			
			let tempDir = FileManager.default.temporaryDirectory
			let tempFilename = "\(random).log"
			let tempFile = tempDir.appendingPathComponent(tempFilename, isDirectory: false)
			
			let entriesData = entries.joined(separator: "\n").data(using: .utf8)!
			try entriesData.write(to: tempFile)
			
			await exportingFinished(tempFile)
			
		} catch {
			log.error("Error exporting logs: \(error)")
			await exportingFailed()
		}
	}
	
	@MainActor
	private func exportingFailed() {
		log.trace("exportingFailed()")
		assertMainThread()
		
		isExporting = false
		toast.pop(
			NSLocalizedString("Exporting Failed", comment: "TxHistoryExporter"),
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 15.0,
			alignment: .middle,
			showCloseButton: true
		)
	}
	
	@MainActor
	private func exportingFinished(_ tempFile: URL) {
		log.trace("exportingFinished()")
		assertMainThread()
		
		isExporting = false
		shareUrl = tempFile
	}
}
