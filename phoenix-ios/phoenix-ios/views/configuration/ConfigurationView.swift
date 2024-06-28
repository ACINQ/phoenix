import SwiftUI
import PhoenixShared
import Combine

fileprivate let filename = "ConfigurationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate enum NavLinkTag: String {
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
	// Danger Zone
	case DrainWallet
	case ResetWallet
	case ForceCloseChannels
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
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State private var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var firstAppearance = false
	@State var popToDestination: PopToDestination? = nil
	
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
			
		#if DEBUG
			if !hasWallet {
				navLink(.WalletCreationOptions) {
					Label { Text("Wallet creation options") } icon: {
						Image(systemName: "quote.bubble")
					}
				}
				.id(linkID_WalletCreationOptions)
			}
		#endif
			
			navLink(.About) {
				Label { Text("About") } icon: {
					Image(systemName: "info.circle")
				}
			}
			.id(linkID_About)
		
			navLink(.DisplayConfiguration) {
				Label { Text("Display") } icon: {
					Image(systemName: "paintbrush.pointed")
				}
			}
			.id(linkID_DisplayConfiguration)
	
			if hasWallet {
				navLink(.PaymentOptions) {
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
			
			if hasWallet {
				navLink(.ContactsList) {
					Label { Text("Contacts") } icon: {
						Image(systemName: "person.2")
					}
				}
				.id(linkID_ContactsList)
			}
			
			if hasWallet {
				navLink(.Notifications) {
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
				navLink(.ChannelManagement) {
					Label { Text("Channel management") } icon: {
						Image(systemName: "wand.and.stars")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			if hasWallet {
				navLink(.LiquidityManagement) {
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
				navLink(.AppAccess) {
					Label { Text("App access") } icon: {
						Image(systemName: isTouchID() ? "touchid" : "faceid")
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
				case .backgroundPayments : newNavLinkTag = .PaymentOptions       ; delay *= 2
				case .liquiditySettings  : newNavLinkTag = .ChannelManagement    ; delay *= 1
				case .forceCloseChannels : newNavLinkTag = .ForceCloseChannels   ; delay *= 1
				case .swapInWallet       : newNavLinkTag = .WalletInfo           ; delay *= 2
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
			
		} else if tag == nil {
			
			// If there's a pending popToDestination, it's now safe to continue the flow.
			//
			// Note that performing this operation in `onAppear` doesn't work properly:
			// - it appears to work fine on the simulator, but isn't reliable on the actual device
			// - it seems that, IF using a `navLinkTag`, then we need to wait for the tag to be
			//   unset before it can be set properly again.
			//
			if let destination = popToDestination {
				log.debug("popToDestination: \(destination)")
				
				popToDestination = nil
				switch destination {
				case .RootView(_):
					presentationMode.wrappedValue.dismiss()
					
				case .ConfigurationView(let deepLink):
					if let deepLink {
						deepLinkManager.broadcast(deepLink)
					}
				
				case .TransactionsView:
					log.warning("Invalid popToDestination")
				}
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
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		popToDestination = destination
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func linkID(for navLinkTag: NavLinkTag) -> any Hashable {
		
		switch navLinkTag {
			case .About                 : return linkID_About
			case .WalletCreationOptions : return linkID_WalletCreationOptions
			case .DisplayConfiguration  : return linkID_DisplayConfiguration
			case .PaymentOptions        : return linkID_PaymentOptions
			case .ChannelManagement     : return linkID_ChannelManagement
			case .LiquidityManagement   : return linkID_LiquidityManagement
			case .ContactsList          : return linkID_ContactsList
			case .Notifications         : return linkID_Notifications
			
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
