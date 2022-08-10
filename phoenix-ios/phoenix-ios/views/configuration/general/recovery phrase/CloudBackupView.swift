import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "CloudBackupView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct CloudBackupView: View {
	
	@Binding var backupSeed_enabled: Bool
	
	@State var toggle_enabled: Bool
	
	@State var legal_appleRisk: Bool
	@State var legal_governmentRisk: Bool
	
	@State var animatingLegalToggleColor = false
	
	let encryptedNodeId: String
	let originalName: String
	@State var name: String
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var hasChanges: Bool {
		if name != originalName {
			return true
		}
		if backupSeed_enabled {
			// Currently enabled.
			return !toggle_enabled || !legal_appleRisk || !legal_governmentRisk
		} else {
			// Currently disabled.
			return toggle_enabled || legal_appleRisk || legal_governmentRisk
		}
	}
	
	var canSave: Bool {
		if backupSeed_enabled {
			// Currently enabled.
			// Saving to disable: user only needs to disable the toggle
			// Saving to change name: newName != oldName
			return !toggle_enabled || (name != originalName)
		} else {
			// Currently disabled.
			// To enable, user must enable the toggle, and accept the legal risks.
			return toggle_enabled && legal_appleRisk && legal_governmentRisk
		}
	}
	
	init(backupSeed_enabled: Binding<Bool>) {
		self._backupSeed_enabled = backupSeed_enabled
		let enabled = backupSeed_enabled.wrappedValue
		
		self._toggle_enabled = State<Bool>(initialValue: enabled)
		self._legal_appleRisk = State<Bool>(initialValue: enabled)
		self._legal_governmentRisk = State<Bool>(initialValue: enabled)
		
		let encryptedNodeId = AppDelegate.get().encryptedNodeId!
		let originalName = Prefs.shared.backupSeed.name(encryptedNodeId: encryptedNodeId) ?? ""
		
		self.encryptedNodeId = encryptedNodeId
		self.originalName = originalName
		self._name = State<String>(initialValue: originalName)
	}
	
	@ViewBuilder
	var body: some View {
		
		List {
			section_toggle()
			section_legal()
			section_name()
		}
		.listStyle(.insetGrouped)
		.navigationTitle("iCloud Backup")
		.navigationBarBackButtonHidden(true)
		.navigationBarItems(leading: backButton())
	}
	
	@ViewBuilder
	func backButton() -> some View {
		
		Button {
			didTapBackButton()
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				if hasChanges {
					if canSave {
						Image(systemName: "chevron.backward")
							.font(.title3.weight(.semibold))
						Text("Save")
							.padding(.leading, 3)
					} else {
						Image(systemName: "chevron.backward")
							.font(.title3.weight(.semibold))
							.foregroundColor(.appNegative)
						Text("Cancel")
							.padding(.leading, 3)
							.foregroundColor(.appNegative)
					}
				} else {
					Image(systemName: "chevron.backward")
						.font(.title3.weight(.semibold))
				}
			}
		}
	}
	
	@ViewBuilder
	func section_toggle() -> some View {
		
		Section {
			Toggle(isOn: $toggle_enabled) {
				Label("Enable iCloud backup", systemImage: "icloud")
			}
			.padding(.bottom, 5)
			
			// Implicit divider added here
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				Label {
					Text(
						"""
						Your recovery phrase will be stored in iCloud, \
						and Phoenix can automatically restore your wallet balance.
						"""
					)
				} icon: {
					Image(systemName: "lightbulb")
				}
			}
			.padding(.vertical, 10)
		}
	}
	
	@ViewBuilder
	func section_legal() -> some View {
		
		Section(header: Text("Legal")) {
			
			Toggle(isOn: $legal_appleRisk) {
				Text(
					"""
					I understand that certain Apple employees may be able \
					to access my iCloud data.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			
			Toggle(isOn: $legal_governmentRisk) {
				Text(
					"""
					I understand that Apple may share my iCloud data \
					with government agencies upon request.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			
		} // </Section>
		.onChange(of: toggle_enabled) { newValue in
			didToggleEnabled(newValue)
		}
	}
	
	@ViewBuilder
	func section_name() -> some View {
		
		Section(header: Text("Name")) {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					TextField("Wallet name (optional)", text: $name)
						.onChange(of: name) {
							name = String($0.prefix(64)) // limited to 64 characters
						}
					
					// Clear button (appears when TextField's text is non-empty)
					Button {
						name = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
					.isHidden(name == "")
				}
				.padding(.all, 8)
				.overlay(
					RoundedRectangle(cornerRadius: 8)
						.stroke(Color.textFieldBorder, lineWidth: 1)
				)
				.padding(.top, 10)
				
				Text("Naming your wallet makes it easier to manage multiple wallets.")
					.foregroundColor(.secondary)
					.padding(.vertical, 10)
				
			} // </VStack>
		} // </Section>
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		if toggle_enabled {
			Image(systemName: "square")
				.renderingMode(.template)
				.imageScale(.large)
				.foregroundColor(animatingLegalToggleColor ? Color.red : Color.primary)
		} else {
			Image(systemName: "square")
				.imageScale(.large)
		}
	}
	
	func didToggleEnabled(_ value: Bool) {
		log.trace("didToggleEnabled")
		
		if value {
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
				if toggle_enabled {
					withAnimation(Animation.linear(duration: 1.0).repeatForever(autoreverses: true)) {
						animatingLegalToggleColor = true
					}
				}
			}
		} else {
			animatingLegalToggleColor = false
		}
	}
	
	func didTapBackButton() {
		log.trace("didTapBackButton()")
		
		if canSave {
			if #available(iOS 15.0, *) {
				// No workaround needed
				backupSeed_enabled = toggle_enabled
			} else {
				// This causes a crash in iOS 14. Appears to be a SwiftUI bug.
				// Workaround is to delay the state change until after the animation has completed.
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.31) {
					backupSeed_enabled = toggle_enabled
				}
			}
			
			// Subtle optimizations:
			// - changing backupSeed_isEnabled causes upload/delete
			// - changing backupSeed_name can cause upload
			//
			// They can be performed in any order, and they will work correctly.
			// But it might result in 2 uploads.
			//
			if toggle_enabled {
				Prefs.shared.backupSeed.setName(name, encryptedNodeId: encryptedNodeId)
				Prefs.shared.backupSeed.isEnabled = toggle_enabled
			} else {
				Prefs.shared.backupSeed.isEnabled = toggle_enabled
				Prefs.shared.backupSeed.setName(name, encryptedNodeId: encryptedNodeId)
			}
		}
		presentationMode.wrappedValue.dismiss()
	}
}
