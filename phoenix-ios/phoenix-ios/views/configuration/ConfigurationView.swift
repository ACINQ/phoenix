import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate enum NavLinkTag: String {
	// General
	case AboutView
	case DisplayConfigurationView
	case MyWalletView
	case RecoveryPhraseView
	case PaymentOptionsView
	case DrainWalletView
	// Security
	case AppAccessView
	// Advanced
	case PrivacyView
	case ChannelsConfigurationView
	case LogsConfigurationView
	case ResetWalletView
}

struct ConfigurationView: View {
	
	@State var isFaceID = true
	@State var isTouchID = false
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State private var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var didAppear = false
	@State var popToRootRequested = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	init() {
		if let encryptedNodeId = Biz.encryptedNodeId {
			backupSeedStatePublisher = Prefs.shared.backupSeedStatePublisher(encryptedNodeId)
		} else {
			backupSeedStatePublisher = PassthroughSubject<BackupSeedState, Never>().eraseToAnyPublisher()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {

		content()
			.navigationTitle(NSLocalizedString("Settings", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {

		List {
			let hasWallet = hasWallet()
			
			section_general(hasWallet)
			if hasWallet {
				section_security()
			}
			section_advanced(hasWallet)
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onAppear() {
			onAppear()
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
		.onReceive(NotificationsManager.shared.permissions) {(permissions: NotificationPermissions) in
			notificationPermissionsChanged(permissions)
		}
		.onReceive(backupSeedStatePublisher) {(state: BackupSeedState) in
			backupSeedStateChanged(state)
		}
	}
	
	@ViewBuilder
	func section_general(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("General")) {
			
			navLink(.AboutView) {
				Label { Text("About") } icon: {
					Image(systemName: "info.circle")
				}
			}
		
			navLink(.DisplayConfigurationView) {
				Label {
					switch notificationPermissions {
					case .disabled:
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("Display")
							Spacer()
							Image(systemName: "exclamationmark.triangle")
								.renderingMode(.template)
								.foregroundColor(Color.appWarn)
						}
						
					default:
						Text("Display")
					}
				} icon: {
					Image(systemName: "paintbrush.pointed")
				}
			}
			
			if hasWallet {
				navLink(.MyWalletView) {
					Label {
						Text("My wallet")
					} icon: {
						Image(systemName: "person")
					}
				}
			}
			
			if hasWallet {
				navLink(.RecoveryPhraseView) {
					Label {
						switch backupSeedState {
						case .notBackedUp:
							HStack(alignment: VerticalAlignment.center, spacing: 0) {
								Text("Recovery phrase")
								Spacer()
								Image(systemName: "exclamationmark.triangle")
									.renderingMode(.template)
									.foregroundColor(Color.appWarn)
							}
						case .backupInProgress:
							HStack(alignment: VerticalAlignment.center, spacing: 0) {
								Text("Recovery phrase")
								Spacer()
								Image(systemName: "icloud.and.arrow.up")
							}
						case .safelyBackedUp:
							Text("Recovery phrase")
						}
					} icon: {
						Image(systemName: "squareshape.split.3x3")
					}
				}
			}
	
			navLink(.PaymentOptionsView) {
				Label { Text("Options & fees") } icon: {
					Image(systemName: "wrench")
				}
			}
		
			if hasWallet {
				navLink(.DrainWalletView) {
					Label { Text("Drain wallet") } icon: {
						Image(systemName: "xmark.circle")
					}
				}
			}
			
		} // </Section: General>
	}
	
	@ViewBuilder
	func section_security() -> some View {
		
		Section(header: Text("Security")) {

			navLink(.AppAccessView) {
				Label { Text("App access") } icon: {
					Image(systemName: isTouchID ? "touchid" : "faceid")
				}
			}

		} // </Section: Security>
	}
	
	@ViewBuilder
	func section_advanced(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Advanced")) {

			navLink(.PrivacyView) {
				Label { Text("Privacy") } icon: {
					Image(systemName: "eye")
				}
			}

			if hasWallet {
				navLink(.ChannelsConfigurationView) {
					Label { Text("Payment channels") } icon: {
						Image(systemName: "bolt")
					}
				}
			}
			
			navLink(.LogsConfigurationView) {
				Label { Text("Logs") } icon: {
					Image(systemName: "doc.text")
				}
			}

			if hasWallet {
				navLink(.ResetWalletView) {
					Label { Text("Reset wallet") } icon: {
						Image(systemName: "trash")
					}
				}
			}

		} // </Section: Advanced>
	}

	@ViewBuilder
	private func navLink<Content>(
		_ tag: NavLinkTag,
		label: () -> Content
	) -> some View where Content: View {
		
		NavigationLink(
			destination: navLinkView(tag),
			tag: tag,
			selection: $navLinkTag,
			label: label
		)
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		// General
			case .AboutView                 : AboutView()
			case .DisplayConfigurationView  : DisplayConfigurationView()
			case .MyWalletView              : WalletInfoView()
			case .RecoveryPhraseView        : RecoveryPhraseView()
			case .PaymentOptionsView        : PaymentOptionsView()
			case .DrainWalletView           : DrainWalletView(popToRoot: popToRoot)
		// Security
			case .AppAccessView             : AppAccessView()
		// Advanced
			case .PrivacyView               : PrivacyView()
			case .ChannelsConfigurationView : ChannelsConfigurationView()
			case .LogsConfigurationView     : LogsConfigurationView()
			case .ResetWalletView           : ResetWalletView()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	private func navLinkTagBinding(_ tag: NavLinkTag?) -> Binding<Bool> {
		
		if let tag { // specific tag
			return Binding<Bool>(
				get: { navLinkTag == tag },
				set: { if $0 { navLinkTag = tag } else if (navLinkTag == tag) { navLinkTag = nil } }
			)
		} else { // any tag
			return Binding<Bool>(
				get: { navLinkTag != nil },
				set: { if !$0 { navLinkTag = nil }}
			)
		}
	}
	
	func hasWallet() -> Bool {
		
		return Biz.business.walletManager.isLoaded()
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		let support = AppSecurity.shared.deviceBiometricSupport()
		switch support {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : isTouchID = true
			default                    : isTouchID = false
		}
		switch support {
			case .faceID_available    : fallthrough
			case .faceID_notEnrolled  : fallthrough
			case .faceID_notAvailable : isFaceID = true
			default                   : isFaceID = false
		}
		
		if !didAppear {
			didAppear = true
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async {
					deepLinkChanged(deepLink)
				}
			}
			
		} else {
		
			if popToRootRequested {
				popToRootRequested = false
				presentationMode.wrappedValue.dismiss()
			}
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		// This is a hack, courtesy of bugs in Apple's NavigationLink:
		// https://developer.apple.com/forums/thread/677333
		//
		// Summary:
		// There's some quirky code in SwiftUI that is resetting our navLinkTag.
		// Several bizarre workarounds have been proposed.
		// I've tried every one of them, and none of them work (at least, without bad side-effects).
		//
		// The only clean solution I've found is to listen for SwiftUI's bad behaviour,
		// and forcibly undo it.
		
		if let value = value {
			
			// Navigate towards deep link (if needed)
			var newNavLinkTag: NavLinkTag? = nil
			var delay: TimeInterval = 1.5 // seconds; multiply by number of screens we need to navigate
			switch value {
				case .paymentHistory     : break
				case .backup             : newNavLinkTag = .RecoveryPhraseView       ; delay *= 1
				case .drainWallet        : newNavLinkTag = .DrainWalletView          ; delay *= 1
				case .electrum           : newNavLinkTag = .PrivacyView              ; delay *= 2
				case .backgroundPayments : newNavLinkTag = .DisplayConfigurationView ; delay *= 2
				case .liquiditySettings  : newNavLinkTag = .PaymentOptionsView       ; delay *= 2
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: delay)
				
				self.navLinkTag = newNavLinkTag // Trigger/push the view
			}
			
		} else {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 0.0)
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
			log.debug("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
		}
	}
	
	func notificationPermissionsChanged(_ permissions: NotificationPermissions) {
		log.trace("notificationPermissionsChanged()")
		
		notificationPermissions = permissions
	}
	
	func backupSeedStateChanged(_ newState: BackupSeedState) {
		log.trace("backupSeedStateChanged()")
		
		backupSeedState = newState
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func popToRoot() {
		log.trace("popToRoot")
		
		popToRootRequested = true
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				log.debug("swiftUiBugWorkaround = nil")
				self.swiftUiBugWorkaround = nil
			}
		}
	}
}

class ConfigurationView_Previews: PreviewProvider {

	static var previews: some View {
		
		ConfigurationView().mock(
			Configuration.ModelFullMode()
		)
		.previewDevice("iPhone 11")
		
		ConfigurationView().mock(
			Configuration.ModelSimpleMode()
		)
		.previewDevice("iPhone 11")
	}
}
