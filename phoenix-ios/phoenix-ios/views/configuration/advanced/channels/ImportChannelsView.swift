import SwiftUI
import PhoenixShared

fileprivate let filename = "ImportChannelsView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum ImportResultFailure {
	case Generic(error: KotlinThrowable)
	case UnknownVersion(version: Int32)
	case MalformedData
	case DecryptionError
	case Unknown
}

fileprivate enum ImportResult {
	case Success
	case Failure(reason: ImportResultFailure)
}


struct ImportChannelsView: View {
	
	@State var dataBlobText: String = ""
	
	@State var importInProgress: Bool = false
	@State var importResult: ChannelsImportResult? = nil
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Import channels", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_info()
			section_status()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				Text(
					"""
					This screen is a debugging tool that can be used to manually import \
					encrypted channels data.

					Use with caution.
					"""
				)
				.padding(.bottom, 20)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					TextField("Paste encrypted data blob here", text: $dataBlobText)
					
					// Clear button (appears when TextField's text is non-empty)
					Button {
						dataBlobText = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
					.isHidden(dataBlobText == "")
				} // </HStack>
				.padding(.all, 8)
				.background(
					RoundedRectangle(cornerRadius: 8)
						.fill(Color(UIColor.systemBackground))
				)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(Color.textFieldBorder, lineWidth: 1)
				)
			} // </VStack>
		} // </Section>
	}
	
	@ViewBuilder
	func section_status() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.center, spacing: 15) {
			
				Button {
					importButtonTapped()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						Text("Import")
						Image(systemName: "square.and.arrow.down")
							.imageScale(.small)
					}
				}
				.disabled(importInProgress || dataBlobTextIsEmpty())
				.font(.title3.weight(.medium))
				
				if importInProgress {
					
					HStack(alignment: VerticalAlignment.center, spacing: 5) {
						ProgressView()
							.progressViewStyle(CircularProgressViewStyle())
						Text("Importing data…")
					} // </HStack>
					
				} else if let result = importResult {
					
					switch toEnum(result) {
					case .Success:
						
						Group {
							HStack(alignment: VerticalAlignment.center, spacing: 5) {
								Image(systemName: "checkmark.circle")
								Text("Import successful")
							}
							Text("You must now restart Phoenix.").bold()
						}
						.foregroundColor(.appPositive)
					
					case .Failure(let reason):
						
						Group {
							HStack(alignment: VerticalAlignment.center, spacing: 5) {
								Image(systemName: "xmark.circle")
								Text("Import has failed")
							}
							switch reason {
							case .Generic(let error):
								Text(verbatim: error.message ?? "Unknown error thrown")
							case .UnknownVersion(let version):
								Text("Version \(version) is not supported")
							case .MalformedData:
								Text("Data is malformed. An encrypted hex blob is expected.")
							case .DecryptionError:
								Text("Data could not be decrypted by this wallet.")
							case .Unknown:
								Text("An unknown error has occurred.")
							} // </switch>
						}
						.foregroundColor(.appNegative)
						
					} // </switch>
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	func dataBlobTextIsEmpty() -> Bool {
		
		return trimmedDataBlobText().isEmpty
	}
	
	func trimmedDataBlobText() -> String {
		
		return dataBlobText.trimmingCharacters(in: .whitespacesAndNewlines)
	}
	
	func importButtonTapped() {
		log.trace("importButtonTapped()")
		
		importInProgress = true
		
		let data = dataBlobText
		let biz = Biz.business
		Task { @MainActor in
			
			// Give the UI time to update and display "importing..."
			// It looks better that way.
			try await Task.sleep(seconds: 0.5)
			
			let result: ChannelsImportResult
			do {
				result = try await ChannelsImportHelper.shared.doImportChannels(data: data, biz: biz)
			} catch {
				// Errors SHOULD be caught in Kotlin, and returned as a Failure result.
				// So if an error is thrown, it's a bug in Kotlin.
				log.error("ChannelsImportHelper.shared.doImportChannels(): threw error: \(error)")
				
				let message = "Threw error: \(error)"
				let kotlinError = KotlinThrowable(message: message)
				result = ChannelsImportResult.Failure.FailureGeneric(error: kotlinError)
			}
			
			importResult = result
			importInProgress = false
		}
	}

	private func toEnum(_ result: ChannelsImportResult) -> ImportResult {
		
		switch result {
		case _ as ChannelsImportResult.Success:
			return .Success
		
		case let r as ChannelsImportResult.FailureGeneric:
			return .Failure(reason: .Generic(error: r.error))
			
		case let r as ChannelsImportResult.FailureUnknownVersion:
			return .Failure(reason: .UnknownVersion(version: r.version))
			
		case _ as ChannelsImportResult.FailureMalformedData:
			return .Failure(reason: .MalformedData)
			
		case _ as ChannelsImportResult.FailureDecryptionError:
			return .Failure(reason: .DecryptionError)
			
		default:
			return .Failure(reason: .Unknown)
		}
	}
}
