import SwiftUI

fileprivate let filename = "CloudBackupView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct CloudBackupView: View {
	
	@Binding var backupSeed_enabled: Bool
	
	@State var toggle_enabled: Bool = false
	
	@State var original_appleRisk: Bool = false
	@State var original_governmentRisk: Bool = false
	@State var original_advancedDataProtection: Bool = false
	
	@State var legal_1_appleRisk: Bool = false
	@State var legal_1_governmentRisk: Bool = false
	@State var legal_2_advancedDataProtection: Bool = false
	
	@State var animatingLegalToggleColor = false
	
	let encryptedNodeId: String
	@State var originalName: String = ""
	@State var name: String = ""
	
	@Environment(\.openURL) var openURL
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(backupSeed_enabled: Binding<Bool>) {
		
		self._backupSeed_enabled = backupSeed_enabled
		self.encryptedNodeId = Biz.encryptedNodeId!
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("iCloud Backup")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarBackButtonHidden(true)
			.navigationBarItems(leading: backButton())
			.onAppear {
				onAppear()
			}
			.onChange(of: toggle_enabled) { _ in
				checkAnimation()
			}
			.onChange(of: legal_1_appleRisk) { _ in
				checkAnimation()
			}
			.onChange(of: legal_1_governmentRisk) { _ in
				checkAnimation()
			}
			.onChange(of: legal_2_advancedDataProtection) { _ in
				checkAnimation()
			}
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
							.font(.headline.weight(.semibold))
						Text("Save")
							.padding(.leading, 3)
					} else {
						Image(systemName: "chevron.backward")
							.font(.headline.weight(.semibold))
							.foregroundColor(.appNegative)
						Text("Cancel")
							.padding(.leading, 3)
							.foregroundColor(.appNegative)
					}
				} else {
					Image(systemName: "chevron.backward")
						.font(.headline.weight(.semibold))
				}
			}
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_toggle()
			section_legal_1()
			section_legal_2()
			section_name()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
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
	func section_legal_1() -> some View {
		
		Section(header: Text("Legal: Option 1")) {
			
			let strikethroughActive = legal_2_advancedDataProtection
			
			Toggle(isOn: $legal_1_appleRisk) {
				Text(
					"""
					I understand that certain Apple employees may be able \
					to access my iCloud data.
					"""
				)
				.strikethrough(strikethroughActive)
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
			
			Toggle(isOn: $legal_1_governmentRisk) {
				Text(
					"""
					I understand that Apple may share my iCloud data \
					with government agencies upon request.
					"""
				)
				.strikethrough(strikethroughActive)
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
	}
	
	@ViewBuilder
	func section_legal_2() -> some View {
		
		Section {
			
			Toggle(isOn: $legal_2_advancedDataProtection) {
				Text(
					"""
					I have enabled "Advanced Data Protection" for iCloud.
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
			
		} header: {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Legal: Option 2")
				Spacer()
				Button {
					openAdvancedDataProtectionLink()
				} label: {
					Image(systemName: "info.circle")
				}
				.foregroundColor(.secondary)
			} // </HStack>
			
		} // </Section>
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
			let useRed = animatingLegalToggleColor && (!legal_1 && !legal_2)
			Image(systemName: "square")
				.renderingMode(.template)
				.imageScale(.large)
				.foregroundColor(useRed ? Color.red : Color.primary)
		} else {
			Image(systemName: "square")
				.imageScale(.large)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var legal_1: Bool {
		return legal_1_appleRisk && legal_1_governmentRisk
	}
	
	var legal_2: Bool {
		return legal_2_advancedDataProtection
	}
	
	var hasChanges: Bool {
		if name != originalName {
			return true
		}
		if legal_1_appleRisk != original_appleRisk {
			return true
		}
		if legal_1_governmentRisk != original_governmentRisk {
			return true
		}
		if legal_2_advancedDataProtection != original_advancedDataProtection {
			return true
		}
		
		if backupSeed_enabled {
			// Original state == enabled
			// Did user disabled backup ?
			return !toggle_enabled
		} else {
			// Original state == disabled
			// Did user enable backup ?
			return toggle_enabled
		}
	}
	
	var canSave: Bool {
		if backupSeed_enabled {
			// Original state == enabled
			
			if !toggle_enabled {
				// Saving to disable: user only needs to disable the toggle
				return true
			}
			if !legal_1 && !legal_2 {
				// Cannot save without accepting legal
				return false
			}
			
			// Otherwise can save as long as there's changes
			return hasChanges
			
		} else {
			// Original state == disabled
			// To enable, user must enable the toggle, and accept the legal risks.
			return toggle_enabled && (legal_1 || legal_2)
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		let enabled = backupSeed_enabled
		toggle_enabled = enabled
		
		let advancedDataProtection = Prefs.shared.advancedDataProtectionEnabled
		
		original_appleRisk = enabled && !advancedDataProtection
		original_governmentRisk = enabled && !advancedDataProtection
		original_advancedDataProtection = enabled && advancedDataProtection
		
		legal_1_appleRisk = original_appleRisk
		legal_1_governmentRisk = original_governmentRisk
		legal_2_advancedDataProtection = original_advancedDataProtection
		
		originalName = Prefs.shared.backupSeed.name(encryptedNodeId: encryptedNodeId) ?? ""
		name = originalName
	}
	
	// --------------------------------------------------
	// MARK: Animations
	// --------------------------------------------------
	
	func checkAnimation() {
		log.trace("checkAnimation()")
		
		if toggle_enabled && (!legal_1 && !legal_2) {
			startAnimatingLegalToggleColor()
		} else {
			stopAnimatingLegalToggleColor()
		}
	}
	
	func startAnimatingLegalToggleColor() {
		log.trace("startAnimatingLegalToggleColor()")
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
			if toggle_enabled {
				log.debug("animatingLegalToggleColor: starting animation...")
				withAnimation(Animation.linear(duration: 1.0).repeatForever(autoreverses: true)) {
					animatingLegalToggleColor = true
				}
			}
		}
	}
	
	func stopAnimatingLegalToggleColor() {
		log.trace("stopAnimatingLegalToggleColor()")
		
		animatingLegalToggleColor = false
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func openAdvancedDataProtectionLink() {
		log.trace("openAdvancedDataProtectionLink")
		
		if let url = URL(string: "https://support.apple.com/en-us/108756") {
			openURL(url)
		}
	}
	
	func didTapBackButton() {
		log.trace("didTapBackButton()")
		
		if hasChanges && canSave {
			
			backupSeed_enabled = toggle_enabled
			
			if toggle_enabled {
				Prefs.shared.advancedDataProtectionEnabled = legal_2_advancedDataProtection
			} else {
				Prefs.shared.advancedDataProtectionEnabled = false
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
		} else {
			log.trace("!hasChanges || !canSave")
		}
		presentationMode.wrappedValue.dismiss()
	}
}
