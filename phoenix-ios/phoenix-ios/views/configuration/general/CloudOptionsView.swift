import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloudOptionsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

extension VerticalAlignment {
	private enum CenterTopLineAlignment: AlignmentID {
		static func defaultValue(in d: ViewDimensions) -> CGFloat {
			return d[.bottom]
		}
	}
	
	static let centerTopLine = VerticalAlignment(CenterTopLineAlignment.self)
}

struct CloudOptionsView: View {
	
	@State var backupEnabled = Prefs.shared.backupTransactions_isEnabled
	@State var useCellularData = Prefs.shared.backupTransactions_useCellular
	@State var useUploadDelays = Prefs.shared.backupTransactions_useUploadDelay
	
	@State var backupSeed = false
	
	var body: some View {
		
		Form {
			section_backupTransactions()
			section_backupSeed()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.navigationBarTitle(
			NSLocalizedString("Cloud Backup", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onChange(of: backupEnabled) { newValue in
			self.didToggleBackupEnabled(newValue)
		}
		.onChange(of: useCellularData) { newValue in
			self.didToggleCellularData(newValue)
		}
		.onChange(of: useUploadDelays) { newValue in
			self.didToggleUploadDelays(newValue)
		}
	}
	
	@ViewBuilder
	func section_backupTransactions() -> some View {
		
		Section {
			Toggle(isOn: $backupEnabled) {
				Text("Backup transactions")
			}
			.padding(.bottom, 5)
			
			// Implicit divider added here, between Toggle & VStack
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				statusLabel()
					.padding(.top, 5)
				
				if backupEnabled {
					cellularDataOption()
						.padding(.top, 30)
					
					uploadDelaysOption()
						.padding(.top, 20)
				}
				
				// NB: SwiftUI seems very buggy within Forms.
				// Be dilligent when you modify padding & spacing.
				//
				// I spent a lot of time looking for combinations that wouldn't create unintended
				// side effects in the UI (thanks to SwiftUI bugs).
				Spacer(minLength: 10)
			}
		} // </Section>
	}
	
	@ViewBuilder
	func section_backupSeed() -> some View {
		
		Section {
			HStack(alignment: VerticalAlignment.center) {
				Text("Backup seed")
					.padding(.trailing, 2)
				Text("(coming soon)")
					.foregroundColor(.secondary)
					.padding(.trailing, 2)
				
				Spacer()
				
				Toggle("", isOn: $backupSeed)
					.labelsHidden()
					.disabled(true)
			}
			
			// Implicit divider added here, between Toggle & VStack
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
						Text("You are responsible for backing up your seed.")
						
						Text(
							"""
							Save it somewhere safe (not on this phone). If you lose \
							your seed and your phone, you've lost your funds.
							"""
						)
						.foregroundColor(Color.gray)
						
						Text(
							"""
							If done correctly, self-backup is the most secure option.
							"""
						)
						.foregroundColor(Color.gray)
					}
				} icon: {
					Image(systemName: "rectangle.and.pencil.and.ellipsis")
						.renderingMode(.template)
						.imageScale(.medium)
						.foregroundColor(Color.appWarn)
				}
				
				// NB: SwiftUI seems very buggy within Forms.
				// Be dilligent when you modify padding & spacing.
				//
				// I spent a lot of time looking for combinations that wouldn't create unintended
				// side effects in the UI (thanks to SwiftUI bugs).
				Spacer(minLength: 10)
			}
		}
	}
	
	@ViewBuilder
	func statusLabel() -> some View {
		
		if backupEnabled {
			
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
					Text("Your payment history will be stored in iCloud.")
					
					Text(
						"""
						The data stored in the cloud is encrypted, \
						and can only be decrypted with your seed.
						"""
					)
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
		// The Toggle wants to align its switch in the vertical center of the body:
		//
		// |body| |switch|
		//
		// This works good when the body is a single line.
		// But with multiple lines it loosk odd:
		//
		// |line1|
		// |line2| |switch|
		// |line3|
		//
		// This isn't what we want.
		// So we use a custom VerticalAlignment to achieve our desired result.
		//
		// Here's how it works:
		// - The toggle queries its body for its VerticalAlignment.center value
		// - Our body is our Label
		// - So in `Step A` below, we override the Label's VerticalAlignment.center value to
		//   return instead the VerticalAlignment.centerTopLine value
		// - And in `Step B` below, we provide the value for VerticalAlignment.centerTopLine.
		//
		// A good resource on alignment guides can be found here:
		// https://swiftui-lab.com/alignment-guides/
		
		Toggle(isOn: $useCellularData) {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
						Text("Use cellular data")
					}
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center] // Step B
					}
					
					let explanation = useCellularData ?
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
			}
			.alignmentGuide(VerticalAlignment.center) { d in
				d[VerticalAlignment.centerTopLine] // Step A
			}
		}
	}
	
	@ViewBuilder
	func uploadDelaysOption() -> some View {
		
		Toggle(isOn: $useUploadDelays) {
			Label {
				VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
					Text("Randomize upload delays")
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
					
					let explanation = useUploadDelays ?
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
	
	func didToggleBackupEnabled(_ flag: Bool) {
		log.trace("didToggleBackupEnabled(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_isEnabled = flag
	}
	
	func didToggleCellularData(_ flag: Bool) {
		log.trace("didToggleCellularData(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_useCellular = flag
	}
	
	func didToggleUploadDelays(_ flag: Bool) {
		log.trace("didToggleUploadDelays(newValue = \(flag))")
		
		Prefs.shared.backupTransactions_useUploadDelay = flag
	}
}

class CloudOptionsView_Previews: PreviewProvider {

	static var previews: some View {
		
		CloudOptionsView()
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 7")
	}
}
