import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "ConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ConfigurationView: View {
	
	@State private var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var firstAppearance = false
	
	@State var biometricSupport = AppSecurity.shared.deviceBiometricSupport()
	
	@Namespace var linkID_About
	@Namespace var linkID_WalletCreationOptions
	@Namespace var linkID_DisplayConfiguration
	@Namespace var linkID_PaymentOptions
	@Namespace var linkID_ContactsList
	@Namespace var linkID_ChannelManagement
	@Namespace var linkID_LiquidityManagement
	@Namespace var linkID_Notifications
	@Namespace var linkID_AppAccess
	@Namespace var linkID_RecoveryPhrase
	@Namespace var linkID_ElectrumServer
	@Namespace var linkID_Tor
	@Namespace var linkID_PaymentsBackup
	@Namespace var linkID_WalletInfo
	@Namespace var linkID_ChannelsConfiguration
	@Namespace var linkID_LogsConfiguration
	@Namespace var linkID_Experimental
	@Namespace var linkID_DrainWallet
	@Namespace var linkID_ResetWallet
	@Namespace var linkID_ForceCloseChannels
	
	enum NavLinkTag: String, Codable {
		// General
		case About
		case WalletCreationOptions
		case DisplayConfiguration
		case PaymentOptions
		case ContactsList
		case Notifications
		// Fees
		case ChannelManagement
		case LiquidityManagement
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
		case Experimental
		// Danger Zone
		case DrainWallet
		case ResetWallet
		case ForceCloseChannels
	}
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
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
			.navigationDestination(for: NavLinkTag.self) { tag in
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func content() -> some View {

		List {
			let hasWallet = hasWallet()
			
			section_general(hasWallet)
			if hasWallet {
				section_fees(hasWallet)
			}
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
			
		#if DEBUG
			if !hasWallet {
				NavigationLink(value: NavLinkTag.WalletCreationOptions) {
					Label { Text("Wallet creation options") } icon: {
						Image(systemName: "quote.bubble")
					}
				}
				.id(linkID_WalletCreationOptions)
			}
		#endif
			
			NavigationLink(value: NavLinkTag.About) {
				Label { Text("About") } icon: {
					Image(systemName: "info.circle")
				}
			}
			.id(linkID_About)
		
			NavigationLink(value: NavLinkTag.DisplayConfiguration) {
				Label { Text("Display") } icon: {
					Image(systemName: "paintbrush.pointed")
				}
			}
			.id(linkID_DisplayConfiguration)
	
			if hasWallet {
				NavigationLink(value: NavLinkTag.PaymentOptions) {
					Label {
						HStack(alignment: VerticalAlignment.center, spacing: 0) {
							Text("Payment options")
							if notificationPermissions == .disabled {
								Spacer()
								Image(systemName: "exclamationmark.triangle")
									.renderingMode(.template)
									.foregroundColor(Color.appWarn)
							}
						} // </HStack>
					} icon: {
						Image(systemName: "wrench")
					} // </Label>
				}
				.id(linkID_PaymentOptions)
			} // </if hasWallet>
			
			if hasWallet && CONTACTS_ENABLED {
				NavigationLink(value: NavLinkTag.ContactsList) {
					Label { Text("Contacts") } icon: {
						Image(systemName: "person.2")
					}
				}
				.id(linkID_ContactsList)
			}
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.Notifications) {
					Label { Text("Notifications") } icon: {
						Image(systemName: "tray")
					}
				}
				.id(linkID_Notifications)
			}
			
		} // </Section: General>
	}
	
	@ViewBuilder
	func section_fees(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Fees")) {
			if hasWallet {
				NavigationLink(value: NavLinkTag.ChannelManagement) {
					Label { Text("Channel management") } icon: {
						Image(systemName: "wand.and.stars")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.LiquidityManagement) {
					Label { Text("Add liquidity") } icon: {
						if #available(iOS 17, *) {
							Image("bucket_monochrome_symbol")
						} else {
							Image("bucket_monochrome")
								.renderingMode(.template)
								.resizable()
								.aspectRatio(contentMode: .fit)
								.frame(width: 20, height: 20)
								.foregroundColor(.appAccent)
						}
					}
				}
				.id(linkID_LiquidityManagement)
			}
		}
	}
	
	@ViewBuilder
	func section_privacyAndSecurity(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Privacy & Security")) {

			if hasWallet {
				NavigationLink(value: NavLinkTag.AppAccess) {
					Label { Text("App access") } icon: {
						Image(systemName: isTouchID() ? "touchid" : "faceid")
					}
				}
				.id(linkID_AppAccess)
			} // </if hasWallet>
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.RecoveryPhrase) {
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
			
			NavigationLink(value: NavLinkTag.ElectrumServer) {
				Label { Text("Electrum server") } icon: {
					Image(systemName: "link")
				}
			}
			.id(linkID_ElectrumServer)
			
			NavigationLink(value: NavLinkTag.Tor) {
				Label { Text("Tor") } icon: {
					Image(systemName: "shield.lefthalf.fill")
				}
			}
			.id(linkID_Tor)
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.PaymentsBackup) {
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
				NavigationLink(value: NavLinkTag.WalletInfo) {
					Label {
						Text("Wallet info")
					} icon: {
						Image(systemName: "cube")
					}
				}
				.id(linkID_WalletInfo)
			}
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.ChannelsConfiguration) {
					Label { Text("Payment channels") } icon: {
						Image(systemName: "bolt")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			NavigationLink(value: NavLinkTag.LogsConfiguration) {
				Label { Text("Logs") } icon: {
					Image(systemName: "doc.text")
				}
			}
			.id(linkID_LogsConfiguration)
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.Experimental) {
					Label { Text("Experimental") } icon: {
						if #available(iOS 17, *) {
							Image(systemName: "flask")
						} else {
							Image(systemName: "testtube.2")
						}
					}
				}
				.id(linkID_Experimental)
			}

		} // </Section: Advanced>
	}
	
	@ViewBuilder
	func section_dangerZone(_ hasWallet: Bool) -> some View {
		
		Section(header: Text("Danger Zone")) {
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.DrainWallet) {
					Label { Text("Drain wallet") } icon: {
						Image(systemName: "xmark.circle")
					}
				}
				.id(linkID_DrainWallet)
			}
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.ResetWallet) {
					Label { Text("Reset wallet") } icon: {
						Image(systemName: "trash")
					}
				}
				.id(linkID_ResetWallet)
			}
			
			if hasWallet {
				NavigationLink(value: NavLinkTag.ForceCloseChannels) {
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
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		// General
			case .About                 : AboutView()
			case .WalletCreationOptions : WalletCreationOptions()
			case .DisplayConfiguration  : DisplayConfigurationView()
			case .PaymentOptions        : PaymentOptionsView()
			case .ContactsList          : ContactsList()
			case .Notifications         : NotificationsView(location: .embedded)
		// Fees
			case .ChannelManagement     : LiquidityPolicyView()
			case .LiquidityManagement   : LiquidityAdsView(location: .embedded)
		// Privacy & Security
			case .AppAccess             : AppAccessView()
			case .RecoveryPhrase        : RecoveryPhraseView()
			case .ElectrumServer        : ElectrumConfigurationView()
			case .Tor                   : TorConfigurationView()
			case .PaymentsBackup        : PaymentsBackupView()
		// Advanced
			case .WalletInfo            : WalletInfoView()
			case .ChannelsConfiguration : ChannelsConfigurationView()
			case .LogsConfiguration     : LogsConfigurationView()
			case .Experimental          : Experimental()
		// Danger Zone
			case .DrainWallet           : DrainWalletView()
			case .ResetWallet           : ResetWalletView()
			case .ForceCloseChannels    : ForceCloseChannelsView()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func hasWallet() -> Bool {
		
		return Biz.business.walletManager.isLoaded()
	}
	
	func isTouchID() -> Bool {
		
		switch biometricSupport {
			case .touchID_available    : fallthrough
			case .touchID_notEnrolled  : fallthrough
			case .touchID_notAvailable : return true
			default                    : return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace("onAppear()")
		
		if firstAppearance {
			firstAppearance = false
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async {
					deepLinkChanged(deepLink)
				}
			}
		} else {
			// Returning from subview
			
			biometricSupport = AppSecurity.shared.deviceBiometricSupport()
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		if let value = value {
			
			// Navigate towards deep link (if needed)
			var newNavLinkTag: NavLinkTag? = nil
			var delay: TimeInterval = 1.5 // seconds; multiply by number of screens we need to navigate
			switch value {
				case .paymentHistory     : break
				case .backup             : newNavLinkTag = .RecoveryPhrase       ; delay *= 1
				case .drainWallet        : newNavLinkTag = .DrainWallet          ; delay *= 1
				case .electrum           : newNavLinkTag = .ElectrumServer       ; delay *= 1
				case .backgroundPayments : newNavLinkTag = .PaymentOptions       ; delay *= 2
				case .liquiditySettings  : newNavLinkTag = .ChannelManagement    ; delay *= 1
				case .forceCloseChannels : newNavLinkTag = .ForceCloseChannels   ; delay *= 1
				case .swapInWallet       : newNavLinkTag = .WalletInfo           ; delay *= 2
			}
			
			if let newNavLinkTag = newNavLinkTag {
				navCoordinator.path.append(newNavLinkTag)
			}
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
}
