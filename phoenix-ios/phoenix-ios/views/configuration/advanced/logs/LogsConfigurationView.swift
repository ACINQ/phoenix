import SwiftUI
import PhoenixShared
import OSLog

fileprivate let filename = "LogsConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
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
			.navigationTitle(NSLocalizedString("Logs & Diagnostics", comment: "Navigation bar title"))
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
			
			Text("Here you can shared the application logs and diagnostics for debugging purposes.")
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

				Button {
					exportDiagnostics()
				} label: {
					Label {
						Text("Export app diagnostics")
					} icon: {
						Image(systemName: "waveform.path.ecg.rectangle")
					}
				}.disabled(isExporting)

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
			await asyncExport_logFiles()
		}
	}

	private  func exportDiagnostics() {
		log.trace("exportDiagnostics()")
		guard isExporting == false else {
			return
		}
		isExporting = true
		Task.detached {
			do {
				defer {
					Task { @MainActor [self] in isExporting = false }
				}

				var result = try await DiagnosticsHelper.shared.getDiagnostics(business: Biz.business)

				let systemVersion = await UIDevice.current.systemVersion
				let model = await UIDevice.current.model
				let deviceName = await hardwareModel()

				result += "\n\(DiagnosticsHelper.shared.SEPARATOR)"
				result += "\nliquidity policy: \(GroupPrefs.current.liquidityPolicy.toKotlin())"

				result += "\n\(DiagnosticsHelper.shared.SEPARATOR)"
				result += "\nbtc unit: \(GroupPrefs.current.bitcoinUnit)"
				result += "\nfiat currency: \(GroupPrefs.current.fiatCurrency)"
				result += "\nspending pin enabled: \(Keychain.current.enabledSecurity.hasSpendingPin())"
				result += "\nlock pin enabled: \(Keychain.current.enabledSecurity.contains(.lockPin))"
				result += "\nbiometrics enabled: \(Keychain.current.enabledSecurity.contains(.biometrics))"

				result += "\n\(DiagnosticsHelper.shared.SEPARATOR)"
				result += "\niOS version: \(systemVersion)"
				result += "\ndevice: Apple \(model) (\(deviceName))"

				let random = UUID().uuidString
					.replacingOccurrences(of: "-", with: "")
					.substring(location: 0, length: 8)
	
				let tempDir = FileManager.default.temporaryDirectory
				let tempFilename = "\(random)-diagnostics.txt"
				let tempFileUrl = tempDir.appendingPathComponent(tempFilename, isDirectory: false)

				let writeOptions: Data.WritingOptions = .withoutOverwriting
				try Data().write(to: tempFileUrl, options: writeOptions)
				let fileHandle = try FileHandle(forWritingTo: tempFileUrl)

				if let data = result.data(using: .utf8) {
					try await fileHandle.asyncWrite(data: data)
				}

				await exportingFinished(tempFileUrl)
			} catch {
				log.error("Error exporting diagnostics: \(error)")
				await exportingFailed()
			}
		}
	}

	func hardwareModel() -> String {
		var systemInfo = utsname()
		uname(&systemInfo)
		return withUnsafeBytes(of: &systemInfo.machine) { bytes in
			bytes.compactMap { $0 == 0 ? nil : Character(UnicodeScalar($0)) }
				.map(String.init).joined()
		}
	}

	// --------------------------------------------------
	// MARK: Exporting
	// --------------------------------------------------

	nonisolated func asyncExport_logFiles() async {
		log.trace("asyncExport_logFiles()")
		
		do {
			let logsDirectory = try LoggerFactory.logsDirectory()
			
			let dirEnumOptions: FileManager.DirectoryEnumerationOptions = [
				.skipsHiddenFiles,
				.skipsPackageDescendants,
				.skipsSubdirectoryDescendants
			]
			let allUrls = try FileManager.default.contentsOfDirectory(
				at: logsDirectory,
				includingPropertiesForKeys: [],
				options: dirEnumOptions
			)
			
			let logFileUrls = allUrls.filter { $0.lastPathComponent.hasSuffix(".log") }
			
			let processAbbreviations = logFileUrls.map { url in
				
				let filename = url.lastPathComponent
				if filename.hasPrefix(LoggerFactory.friendlyProcessName_foreground) {
					return "FG"
				} else if filename.hasPrefix(LoggerFactory.friendlyProcessName_background) {
					return "BG"
				} else {
					return "??"
				}
			}
			
			let random = UUID().uuidString
				.replacingOccurrences(of: "-", with: "")
				.substring(location: 0, length: 8)
			
			let tempDir = FileManager.default.temporaryDirectory
			let tempFilename = "\(random).log"
			let tempFileUrl = tempDir.appendingPathComponent(tempFilename, isDirectory: false)
			
			let writeOptions: Data.WritingOptions = .withoutOverwriting
			try Data().write(to: tempFileUrl, options: writeOptions)
			
			let fileHandle = try FileHandle(forWritingTo: tempFileUrl)
			
			let channel = LogFileParser.asyncLightParseChannel(logFileUrls)
			for try await wrapper in channel {
				
				let processAbbreviation = processAbbreviations[wrapper.urlIndex]
				
				let string = "[\(processAbbreviation)] \(wrapper.entry.raw)\n"
				if let data = string.data(using: .utf8) {
					
					try await fileHandle.asyncWrite(data: data)
				}
			}
			
			try await fileHandle.asyncSyncAndClose()
			await exportingFinished(tempFileUrl)
			
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
