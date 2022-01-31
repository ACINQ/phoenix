import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PaymentsBackupView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct PaymentsBackupView: View {
	
	@State var backupTransactions_enabled = Prefs.shared.backupTransactions_isEnabled
	@State var backupTransactions_useCellularData = Prefs.shared.backupTransactions_useCellular
	@State var backupTransactions_useUploadDelay = Prefs.shared.backupTransactions_useUploadDelay
	
	@ViewBuilder
	var body: some View {
		
		List {
			section
		}
		.listStyle(.insetGrouped)
		.navigationTitle("Payments Backup")
	}
	
	@ViewBuilder
	var section: some View {
		
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
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your payment history will be stored in iCloud.")
					
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
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your payment history is only stored on this device.")
					
					Text(
						"""
						If you switch to a new device (or reinstall the app) \
						then you'll lose your payment history.
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
		
		// alignmentGuide explanation:
		//
		// The Toggle wants to vertically align its switch in the center of the body:
		//
		// |body| |switch|
		//
		// This works good when the body is a single line.
		// But with multiple lines it looks like:
		//
		// |line1|
		// |line2| |switch|
		// |line3|
		//
		// This isn't what we want.
		// So we use a custom VerticalAlignment to achieve our desired result.
		//
		// Here's how it works:
		// - The toggle queries its body for the VerticalAlignment.center value
		// - Our body is our Label
		// - So in `Step A` below, we override the Label's VerticalAlignment.center value to
		//   return instead the VerticalAlignment.centerTopLine value
		// - And in `Step B` below, we provide the value for VerticalAlignment.centerTopLine.
		//
		// A good resource on alignment guides can be found here:
		// https://swiftui-lab.com/alignment-guides/
		
		Toggle(isOn: $backupTransactions_useCellularData) {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text("Use cellular data")
					}
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center] // Step B
					}
					
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
				}
			} icon: {
				Image(systemName: "network")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			} // </Label>
			.alignmentGuide(VerticalAlignment.center) { d in
				d[VerticalAlignment.centerTopLine] // Step A
			}
		} // </Toggle>
	}
	
	@ViewBuilder
	func uploadDelaysOption() -> some View {
		
		Toggle(isOn: $backupTransactions_useUploadDelay) {
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
						
				}
			} icon: {
				Image(systemName: "timer")
					.renderingMode(.template)
					.imageScale(.medium)
					.foregroundColor(Color.appAccent)
			}
			.alignmentGuide(VerticalAlignment.center) { d in
				d[VerticalAlignment.centerTopLine]
			}
		}
	}
	
	func didToggle_backupTransactions_enabled(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_enabled(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_isEnabled = flag
	}
	
	func didToggle_backupTransactions_useCellularData(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_useCellularData(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_useCellular = flag
	}
	
	func didToggle_backupTransactions_useUploadDelay(_ flag: Bool) {
		log.trace("didToggle_backupTransactions_useUploadDelay(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_useUploadDelay = flag
	}
}
