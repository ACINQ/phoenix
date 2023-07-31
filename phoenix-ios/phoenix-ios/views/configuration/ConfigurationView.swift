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
	case About
	case DisplayConfiguration
	case PaymentOptions
	case ChannelManagement
	// Privacy & Security
	case AppAccess
	case RecoveryPhrase
	case ElectrumServer
	case Tor
	case PaymentsBackup
	// Advanced
	case WalletInfo
	case ChannelsConfiguration
	case LogsConfiguration
	// Danger Zone
	case DrainWallet
	case ResetWallet
	case ForceCloseChannels
}

enum PopToDestination: CustomStringConvertible {
	case RootView(followedBy: DeepLink? = nil)
	case ConfigurationView(followedBy: DeepLink? = nil)
	
	public var description: String {
		switch self {
			case .RootView          : return "RootView"
			case .ConfigurationView : return "ConfigurationView"
		}
	}
}

struct ConfigurationView: View {
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			ConfigurationList(scrollViewProxy: scrollViewProxy)
		}
	}
}

fileprivate struct ConfigurationList: View {
	
	let scrollViewProxy: ScrollViewProxy
	
	@State var isFaceID = true
	@State var isTouchID = false
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State private var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var didAppear = false
	@State var popToDestination: PopToDestination? = nil
	
	@Namespace var linkID_About
	@Namespace var linkID_DisplayConfiguration
	@Namespace var linkID_PaymentOptions
	@Namespace var linkID_ChannelManagement
	@Namespace var linkID_AppAccess
	@Namespace var linkID_RecoveryPhrase
	@Namespace var linkID_ElectrumServer
	@Namespace var linkID_Tor
	@Namespace var linkID_PaymentsBackup
	@Namespace var linkID_WalletInfo
	@Namespace var linkID_ChannelsConfiguration
	@Namespace var linkID_LogsConfiguration
	@Namespace var linkID_DrainWallet
	@Namespace var linkID_ResetWallet
	@Namespace var linkID_ForceCloseChannels
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	init(scrollViewProxy: ScrollViewProxy) {
		
		self.scrollViewProxy = scrollViewProxy
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
			section_privacyAndSecurity(hasWallet)
			section_advanced(hasWallet)
			if hasWallet {
				section_dangerZone(hasWallet)
			}
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
			
			navLink(.About) {
				Label { Text("About") } icon: {
					Image(systemName: "info.circle")
				}
			}
			.id(linkID_About)
		
			navLink(.DisplayConfiguration) {
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
			.id(linkID_DisplayConfiguration)
	
			navLink(.PaymentOptions) {
				Label { Text("Payment options") } icon: {
					Image(systemName: "wrench")
				}
			}
			.id(linkID_PaymentOptions)
			
			navLink(.ChannelManagement) {
				Label { Text("Channel management") } icon: {
					Image(systemName: "wand.and.stars")
				}
			}
			.id(linkID_ChannelManagement)
			
		} // </Section: General>
	}
	
	@ViewBuilder
	func section_privacyAndSecurity(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Privacy & Security")) {

			if hasWallet {
				navLink(.AppAccess) {
					Label { Text("App access") } icon: {
						Image(systemName: isTouchID ? "touchid" : "faceid")
					}
				}
				.id(linkID_AppAccess)
			} // </if hasWallet>
			
			if hasWallet {
				navLink(.RecoveryPhrase) {
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
						Image(systemName: "key")
					}
				}
				.id(linkID_RecoveryPhrase)
			} // </if hasWallet>
			
			navLink(.ElectrumServer) {
				Label { Text("Electrum server") } icon: {
					Image(systemName: "link")
				}
			}
			.id(linkID_ElectrumServer)
			
			navLink(.Tor) {
				Label { Text("Tor") } icon: {
					Image(systemName: "shield.lefthalf.fill")
				}
			}
			.id(linkID_Tor)
			
			if hasWallet {
				navLink(.PaymentsBackup) {
					Label { Text("Payments backup") } icon: {
						Image(systemName: "icloud.and.arrow.up")
					}
				}
				.id(linkID_PaymentsBackup)
			} // </if hasWallet>

		} // </Section: Privacy & Security>
	}
	
	@ViewBuilder
	func section_advanced(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Advanced")) {

			if hasWallet {
				navLink(.WalletInfo) {
					Label {
						Text("Wallet info")
					} icon: {
						Image(systemName: "cube")
					}
				}
				.id(linkID_WalletInfo)
			}
			
			if hasWallet {
				navLink(.ChannelsConfiguration) {
					Label { Text("Payment channels") } icon: {
						Image(systemName: "bolt")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			navLink(.LogsConfiguration) {
				Label { Text("Logs") } icon: {
					Image(systemName: "doc.text")
				}
			}
			.id(linkID_LogsConfiguration)

		} // </Section: Advanced>
	}
	
	@ViewBuilder
	func section_dangerZone(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Danger Zone")) {
			
			if hasWallet {
				navLink(.DrainWallet) {
					Label { Text("Drain wallet") } icon: {
						Image(systemName: "xmark.circle")
					}
				}
				.id(linkID_DrainWallet)
			}
			
			if hasWallet {
				navLink(.ResetWallet) {
					Label { Text("Reset wallet") } icon: {
						Image(systemName: "trash")
					}
				}
				.id(linkID_ResetWallet)
			}
			
			if hasWallet {
				navLink(.ForceCloseChannels) {
					Label { Text("Force-close channels") } icon: {
						Image(systemName: "exclamationmark.triangle")
					}
					.foregroundColor(.appNegative)
				}
				.id(linkID_ForceCloseChannels)
			}
		} // </Section: Danger Zone>
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
	//	.id(linkID(for: tag)) // doesn't compile - don't understand why
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
			case .About                 : AboutView()
			case .DisplayConfiguration  : DisplayConfigurationView()
			case .PaymentOptions        : PaymentOptionsView()
			case .ChannelManagement     : LiquidityPolicyView()
		// Privacy & Security
			case .AppAccess             : AppAccessView()
			case .RecoveryPhrase        : RecoveryPhraseView()
			case .ElectrumServer        : ElectrumConfigurationView()
			case .Tor                   : TorConfigurationView()
			case .PaymentsBackup        : PaymentsBackupView()
		// Advanced
			case .WalletInfo            : WalletInfoView(popTo: popTo)
			case .ChannelsConfiguration : ChannelsConfigurationView()
			case .LogsConfiguration     : LogsConfigurationView()
		// Danger Zone
			case .DrainWallet           : DrainWalletView(popTo: popTo)
			case .ResetWallet           : ResetWalletView()
			case .ForceCloseChannels    : ForceCloseChannelsView()
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
		
			if let destination = popToDestination {
				popToDestination = nil
				switch destination {
				case .RootView(let deepLink):
					log.debug("popToDestination: \(destination), follwedBy: \(deepLink?.rawValue ?? "nil")")
					presentationMode.wrappedValue.dismiss()
					
				case .ConfigurationView(let deepLink):
					log.debug("popToDestination: \(destination), followedBy: \(deepLink?.rawValue ?? "nil")")
					if let deepLink {
						deepLinkManager.broadcast(deepLink)
					}
				}
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
				case .backup             : newNavLinkTag = .RecoveryPhrase       ; delay *= 1
				case .drainWallet        : newNavLinkTag = .DrainWallet          ; delay *= 1
				case .electrum           : newNavLinkTag = .ElectrumServer       ; delay *= 1
				case .backgroundPayments : newNavLinkTag = .DisplayConfiguration ; delay *= 2
				case .liquiditySettings  : newNavLinkTag = .ChannelManagement    ; delay *= 1
				case .forceCloseChannels : newNavLinkTag = .ForceCloseChannels   ; delay *= 1
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: delay)
				
				// Interesting bug in SwiftUI:
				// If the navLinkTag you're targetting is scrolled off the screen,
				// the you won't be able to navigate to it.
				// My understanding is that List is lazy, and this somehow prevents triggering the navigation.
				// The workaround is to manually scroll to the item to ensure it's onscreen,
				// at which point we can activate the navLinkTag trigger.
				//
				scrollViewProxy.scrollTo(linkID(for: newNavLinkTag))
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
					self.navLinkTag = newNavLinkTag // Trigger/push the view
				}
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
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo()")
		
		popToDestination = destination
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func linkID(for navLinkTag: NavLinkTag) -> any Hashable {
		
		switch navLinkTag {
			case .About                 : return linkID_About
			case .DisplayConfiguration  : return linkID_DisplayConfiguration
			case .PaymentOptions        : return linkID_PaymentOptions
			case .ChannelManagement     : return linkID_ChannelManagement
			
			case .AppAccess             : return linkID_AppAccess
			case .RecoveryPhrase        : return linkID_RecoveryPhrase
			case .ElectrumServer        : return linkID_ElectrumServer
			case .Tor                   : return linkID_Tor
			case .PaymentsBackup        : return linkID_PaymentsBackup
		
			case .WalletInfo            : return linkID_WalletInfo
			case .ChannelsConfiguration : return linkID_ChannelsConfiguration
			case .LogsConfiguration     : return linkID_LogsConfiguration
			
			case .DrainWallet           : return linkID_DrainWallet
			case .ResetWallet           : return linkID_ResetWallet
			case .ForceCloseChannels    : return linkID_ForceCloseChannels
		}
	}
	
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
