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
	
	@ViewBuilder
	var body: some View {
		ScrollViewReader { scrollViewProxy in
			ConfigurationList(scrollViewProxy: scrollViewProxy)
		}
	}
}

struct ConfigurationList: View {
	
	enum NavLinkTag: String {
		case WalletMetadata
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
	
	let scrollViewProxy: ScrollViewProxy
	
	@State var walletMetadata: WalletMetadata = WalletMetadata.default()
	
	@State var notificationPermissions = NotificationsManager.shared.permissions.value
	
	@State var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	@State var didAppear = false
	
	@State var biometricSupport = DeviceInfo.biometricSupport()
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	@State var popToDestination: PopToDestination? = nil
	@State var swiftUiBugWorkaround: NavLinkTag? = nil
	@State var swiftUiBugWorkaroundIdx = 0
	// </iOS_16_workarounds>
	
	@Namespace var linkID_WalletMetadata
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
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	// --------------------------------------------------
	// MARK: Init
	// --------------------------------------------------
	
	init(scrollViewProxy: ScrollViewProxy) {
		
		self.scrollViewProxy = scrollViewProxy
		backupSeedStatePublisher = Prefs.current.backupSeed.statePublisher()
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {

		content()
			.navigationTitle(NSLocalizedString("Settings", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func content() -> some View {

		List {
			let hasWallet = hasWallet()
			
			if hasWallet {
				section_walletInfo()
			}
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
		.onReceive(SecurityFileManager.shared.currentSecurityFilePublisher) { _ in
			securityFileChanged()
		}
		.onReceive(NotificationsManager.shared.permissions) {(permissions: NotificationPermissions) in
			notificationPermissionsChanged(permissions)
		}
		.onReceive(backupSeedStatePublisher) {(state: BackupSeedState) in
			backupSeedStateChanged(state)
		}
	}
	
	@ViewBuilder
	func section_walletInfo() -> some View {
		
		Section {
			navLink_label(.WalletMetadata) {
				HStack(alignment: VerticalAlignment.center, spacing: 8) {
					WalletImage(filename: walletMetadata.photo, size: 64)
					VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
						Text(walletMetadata.name)
							.font(.title3.weight(.medium))
						Text("Manage or switch wallets")
							.font(.subheadline)
							.foregroundStyle(.secondary)
					} // </VStack>
				} // </HStack>
			}
		}
	}
	
	@ViewBuilder
	func section_general(_ hasWallet: Bool) -> some View {
		
		Section {
			
		#if DEBUG
			if !hasWallet {
				navLink_label(.WalletCreationOptions) {
					Label { Text("Wallet creation options") } icon: {
						Image(systemName: "quote.bubble")
					}
				}
				.id(linkID_WalletCreationOptions)
			}
		#endif
			
			navLink_label(.About) {
				Label { Text("About") } icon: {
					Image(systemName: "info.circle")
				}
			}
			.id(linkID_About)
		
			navLink_label(.DisplayConfiguration) {
				Label { Text("Display") } icon: {
					Image(systemName: "paintbrush.pointed")
				}
			}
			.id(linkID_DisplayConfiguration)
	
			if hasWallet {
				navLink_label(.PaymentOptions) {
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
						.foregroundColor(.primary)
					} icon: {
						Image(systemName: "wrench")
					} // </Label>
				}
				.id(linkID_PaymentOptions)
			} // </if hasWallet>
			
			if hasWallet {
				navLink_label(.ContactsList) {
					Label { Text("Contacts") } icon: {
						Image(systemName: "person.2")
					}
				}
				.id(linkID_ContactsList)
			}
			
			if hasWallet {
				navLink_label(.Notifications) {
					Label { Text("Notifications") } icon: {
						Image(systemName: "tray")
					}
				}
				.id(linkID_Notifications)
			}
			
		} header: {
			Text("General")
		}
	}
	
	@ViewBuilder
	func section_fees(_ hasWallet: Bool) -> some View {
		
		Section {
			
			if hasWallet {
				navLink_label(.ChannelManagement) {
					Label { Text("Channel management") } icon: {
						Image(systemName: "wand.and.stars")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			if hasWallet {
				navLink_label(.LiquidityManagement) {
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
			
		} header: {
			Text("Fees")
		}
	}
	
	@ViewBuilder
	func section_privacyAndSecurity(_ hasWallet: Bool) -> some View {
		
		Section {

			if hasWallet {
				navLink_label(.AppAccess) {
					Label { Text("App access") } icon: {
						Image(systemName: isTouchID() ? "touchid" : "faceid")
					}
				}
				.id(linkID_AppAccess)
			} // </if hasWallet>
			
			if hasWallet {
				navLink_label(.RecoveryPhrase) {
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
			
			navLink_label(.ElectrumServer) {
				Label { Text("Electrum server") } icon: {
					Image(systemName: "link")
				}
			}
			.id(linkID_ElectrumServer)
			
			navLink_label(.Tor) {
				Label { Text("Tor") } icon: {
					Image(systemName: "shield.lefthalf.fill")
				}
			}
			.id(linkID_Tor)
			
			if hasWallet {
				navLink_label(.PaymentsBackup) {
					Label { Text("Cloud backup") } icon: {
						Image(systemName: "icloud")
					}
				}
				.id(linkID_PaymentsBackup)
			}

		} header: {
			Text("Privacy & Security")
		}
	}
	
	@ViewBuilder
	func section_advanced(_ hasWallet: Bool) -> some View {
		
		Section {

			if hasWallet {
				navLink_label(.WalletInfo) {
					Label {
						Text("Wallet info")
					} icon: {
						Image(systemName: "cube")
					}
				}
				.id(linkID_WalletInfo)
			}
			
			if hasWallet {
				navLink_label(.ChannelsConfiguration) {
					Label { Text("Payment channels") } icon: {
						Image(systemName: "bolt")
					}
				}
				.id(linkID_ChannelManagement)
			}
			
			navLink_label(.LogsConfiguration) {
				Label { Text("Logs") } icon: {
					Image(systemName: "doc.text")
				}
			}
			.id(linkID_LogsConfiguration)
			
			if hasWallet {
				navLink_label(.Experimental) {
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

		} header: {
			Text("Advanced")
		}
	}
	
	@ViewBuilder
	func section_dangerZone(_ hasWallet: Bool) -> some View {
		
		Section {
			
			if hasWallet {
				navLink_label(.DrainWallet) {
					Label { Text("Close channels") } icon: {
						Image(systemName: "xmark.circle")
					}
				}
				.id(linkID_DrainWallet)
			}
			
			if hasWallet {
				navLink_label(.ResetWallet) {
					Label { Text("Reset wallet") } icon: {
						Image(systemName: "trash")
					}
				}
				.id(linkID_ResetWallet)
			}
			
			if hasWallet {
				navLink_label(.ForceCloseChannels) {
					Label { Text("Force-close channels") } icon: {
						Image(systemName: "exclamationmark.triangle")
					}
					.foregroundColor(.appNegative)
				}
				.id(linkID_ForceCloseChannels)
			}
		} header: {
			Text("Danger Zone")
		}
	}

	@ViewBuilder
	func navLink_label<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		} // </iOS_16>
	}
	
	@ViewBuilder
	private func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
			case .WalletMetadata        : WalletMetadataView()
		// General
			case .About                 : AboutView()
			case .WalletCreationOptions : WalletCreationOptions()
			case .DisplayConfiguration  : DisplayConfigurationView()
			case .PaymentOptions        : PaymentOptionsView()
			case .ContactsList          : ContactsList(popTo: popTo)
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
			case .Experimental          : Experimental()
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
		
		if !didAppear {
			didAppear = true
			
			if let deepLink = deepLinkManager.deepLink {
				DispatchQueue.main.async {
					deepLinkChanged(deepLink)
				}
			}
		} else {
			// Returning from subview
			
			biometricSupport = DeviceInfo.biometricSupport()
		}
		
		walletMetadata = SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
	}
	
	func securityFileChanged() {
		log.trace(#function)
		walletMetadata = SecurityFileManager.shared.currentWallet() ?? WalletMetadata.default()
	}
	
	func notificationPermissionsChanged(_ permissions: NotificationPermissions) {
		log.trace(#function)
		notificationPermissions = permissions
	}
	
	func backupSeedStateChanged(_ newState: BackupSeedState) {
		log.trace(#function)
		backupSeedState = newState
	}
	
	// --------------------------------------------------
	// MARK: Navigation
	// --------------------------------------------------
	
	func navigateTo(_ tag: NavLinkTag) {
		log.trace("navigateTo(\(tag.rawValue))")
		
		if #available(iOS 17, *) {
			navCoordinator.path.append(tag)
		} else {
			navLinkTag = tag
		}
	}
	
	func linkID(for navLinkTag: NavLinkTag) -> any Hashable {
		
		switch navLinkTag {
			case .WalletMetadata        : return linkID_WalletMetadata
			
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
			case .Experimental          : return linkID_Experimental
			
			case .DrainWallet           : return linkID_DrainWallet
			case .ResetWallet           : return linkID_ResetWallet
			case .ForceCloseChannels    : return linkID_ForceCloseChannels
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.description ?? "nil")")
		
		if #available(iOS 17, *) {
			// Nothing to do here.
			// Everything is handled in `MainView_Small` & `MainView_Big`.
			
		} else { // iOS 16
			
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
			
			if let value {
				
				// Navigate towards deep link (if needed)
				var newNavLinkTag: NavLinkTag? = nil
				var delay: TimeInterval = 1.5 // seconds; multiply by number of screens we need to navigate
				switch value {
					case .payment(_)         : break
					case .paymentHistory     : break
					case .backup             : newNavLinkTag = .RecoveryPhrase       ; delay *= 1
					case .drainWallet        : newNavLinkTag = .DrainWallet          ; delay *= 1
					case .electrum           : newNavLinkTag = .ElectrumServer       ; delay *= 1
					case .backgroundPayments : newNavLinkTag = .PaymentOptions       ; delay *= 2
					case .liquiditySettings  : newNavLinkTag = .ChannelManagement    ; delay *= 1
					case .forceCloseChannels : newNavLinkTag = .ForceCloseChannels   ; delay *= 1
					case .swapInWallet       : newNavLinkTag = .WalletInfo           ; delay *= 2
					case .finalWallet        : newNavLinkTag = .WalletInfo           ; delay *= 2
					case .appAccess          : newNavLinkTag = .AppAccess            ; delay *= 1
				}
				
				if let newNavLinkTag {
					
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
	}
	
	func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if #available(iOS 17, *) {
			log.warning(
				"""
				navLinkTagChanged(): This function is for iOS 16 only ! This means there's a bug.
				The navLinkTag is being set somewhere, when the navCoordinator should be used instead.
				"""
			)
			
		} else { // iOS 16
			
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
	}
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		if #available(iOS 17, *) {
			log.warning("clearSwiftUiBugWorkaround(): This function is for iOS 16 only !")
		} else { // iOS 16
			
			let idx = self.swiftUiBugWorkaroundIdx
			DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
				if self.swiftUiBugWorkaroundIdx == idx {
					log.debug("swiftUiBugWorkaround = nil")
					self.swiftUiBugWorkaround = nil
				}
			}
		}
	}
	
	func popTo(_ destination: PopToDestination) {
		log.trace("popTo(\(destination))")
		
		if #available(iOS 17, *) {
			log.warning("popTo(): This function is for iOS 16 only !")
		} else { // iOS 16
			popToDestination = destination
		}
	}
}
