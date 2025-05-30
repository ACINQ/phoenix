import SwiftUI

fileprivate let filename = "PaymentsBackupView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentsBackupView: View {
	
	@State var backupTransactions_enabled = Prefs.shared.backupTransactions.isEnabled
	@State var backupTransactions_useCellularData = Prefs.shared.backupTransactions.useCellular
	@State var backupTransactions_useUploadDelay = Prefs.shared.backupTransactions.useUploadDelay
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Payments Backup")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section() -> some View {
		
		Section {
			
			Toggle(isOn: $backupTransactions_enabled) {
				Label("iCloud backup", systemImage: "icloud")
			}
			
			// Implicit divider added here
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				statusLabel()
					.padding(.top, 5)
				
				if backupTransactions_enabled {
					cellularDataOption()
						.padding(.top, 30)
					
					uploadDelaysOption()
						.padding(.top, 20)
				}
			} // </VStack>
			.padding(.vertical, 10)
			
		} // </Section>
		.onChange(of: backupTransactions_enabled) { newValue in
			didToggle_backupTransactions_enabled(newValue)
		}
		.onChange(of: backupTransactions_useCellularData) { newValue in
			didToggle_backupTransactions_useCellularData(newValue)
		}
		.onChange(of: backupTransactions_useUploadDelay) { newValue in
			didToggle_backupTransactions_useUploadDelay(newValue)
		}
	}
	
	@ViewBuilder
	func statusLabel() -> some View {
		
		if backupTransactions_enabled {
			
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your payment history and contacts will be stored in iCloud.")

					Text(
						"""
						The data stored in the cloud is encrypted, \
						and can only be decrypted with your seed.
						"""
					)
					.lineLimit(nil)          // SwiftUI bugs
					.minimumScaleFactor(0.5) // Truncating text
					.foregroundColor(Color.gray)
				}
			} icon: {
				Image(systemName: "externaldrive.badge.icloud")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
			
		} else {
			
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your payment history and contacts are only stored on this device.")
					
					Text(
						"""
						If you switch to a new device (or reinstall the app) \
						then you'll lose this information.
						"""
					)
					.foregroundColor(Color.gray)
				}
			} icon: {
				Image(systemName: "internaldrive")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appWarn)
			}
		}
	}
	
	@ViewBuilder
	func cellularDataOption() -> some View {
		
		ToggleAlignment {
			
			LabelAlignment {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Use cellular data")
					
					let explanation = backupTransactions_useCellularData ?
					NSLocalizedString(
						"Uploads can occur over cellular connections.",
						comment: "Explanation for 'Use cellular data' toggle"
					) :
					NSLocalizedString(
						"Uploads will only occur over WiFi.",
						comment: "Explanation for 'Use cellular data' toggle"
					)
					
					Text(explanation)
						.lineLimit(nil) // text is getting truncated for some reason
						.font(.callout)
						.foregroundColor(Color.secondary)
				} // </VStack>
			} icon: {
				Image(systemName: "network")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $backupTransactions_useCellularData)
				.labelsHidden()
			
		} // </ToggleAlignment>
	}
	
	@ViewBuilder
	func uploadDelaysOption() -> some View {
		
		ToggleAlignment {
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Randomize upload delays")
						.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
							d[VerticalAlignment.center]
						}
					
					let explanation = backupTransactions_useUploadDelay ?
					NSLocalizedString(
						"Avoids payment correlation using timestamp metadata.",
						comment: "Explanation for 'Randomize upload delays' toggle"
					) :
					NSLocalizedString(
						"Payments are uploaded to the cloud immediately upon completion.",
						comment: "Explanation for 'Randomize upload delays' toggle"
					)
					
					Text(explanation)
						.lineLimit(nil) // text is getting truncated for some reason
						.font(.callout)
						.foregroundColor(Color.secondary)
				} // </VStack>
			} icon: {
				Image(systemName: "timer")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </LabelAlignment>
			
		} toggle: {
			
			Toggle("", isOn: $backupTransactions_useUploadDelay)
				.labelsHidden()
			
		} // </ToggleAlignment>
	}
	
	func didToggle_backupTransactions_enabled(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_enabled(newValue = \(flag))")
		
		Prefs.shared.backupTransactions.isEnabled = flag
	}
	
	func didToggle_backupTransactions_useCellularData(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_useCellularData(newValue = \(flag))")
		
		Prefs.shared.backupTransactions.useCellular = flag
	}
	
	func didToggle_backupTransactions_useUploadDelay(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_useUploadDelay(newValue = \(flag))")
		
		Prefs.shared.backupTransactions.useUploadDelay = flag
	}
}
