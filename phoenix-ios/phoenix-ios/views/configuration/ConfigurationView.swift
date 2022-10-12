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
	case PaymentOptionsView
	case RecoveryPhraseView
	case CloseWalletView
	// Security
	case AppAccessView
	// Advanced
	case PrivacyView
	case LogsConfigurationView
	case ChannelsConfigurationView
}

struct ConfigurationView: View {
	
	@State var isFaceID = true
	@State var isTouchID = false
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@State private var listViewId = UUID()
	
	@State private var backupSeedState: BackupSeedState = .safelyBackedUp
	let backupSeedStatePublisher: AnyPublisher<BackupSeedState, Never>
	
	let externalLightningUrlPublisher: PassthroughSubject<String, Never>
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var didAppear = false
	
	init() {
		if let encryptedNodeId = Biz.encryptedNodeId {
			backupSeedStatePublisher = Prefs.shared.backupSeedStatePublisher(encryptedNodeId)
		} else {
			backupSeedStatePublisher = PassthroughSubject<BackupSeedState, Never>().eraseToAnyPublisher()
		}
		
		externalLightningUrlPublisher = AppDelegate.get().externalLightningUrlPublisher
	}
	
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

			Section(header: Text("General")) {
					
				NavigationLink(
					destination: AboutView(),
					tag: NavLinkTag.AboutView,
					selection: $navLinkTag
				) {
					Label { Text("About") } icon: {
						Image(systemName: "info.circle")
					}
				}
			
				NavigationLink(
					destination: DisplayConfigurationView(),
					tag: NavLinkTag.DisplayConfigurationView,
					selection: $navLinkTag
				) {
					Label { Text("Display") } icon: {
						Image(systemName: "paintbrush.pointed")
					}
				}
		
				NavigationLink(
					destination: PaymentOptionsView(),
					tag: NavLinkTag.PaymentOptionsView,
					selection: $navLinkTag
				) {
					Label { Text("Payment options & fees") } icon: {
						Image(systemName: "wrench")
					}
				}
			
				if hasWallet {
					
					NavigationLink(
						destination: RecoveryPhraseView(),
						tag: NavLinkTag.RecoveryPhraseView,
						selection: $navLinkTag
					) {
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
					
					NavigationLink(
						destination: CloseWalletView(popToRoot: { self.navLinkTag = nil }),
						tag: NavLinkTag.CloseWalletView,
						selection: $navLinkTag
					) {
						Label { Text("Close wallet") } icon: {
							Image(systemName: "xmark.circle")
						}
					}
				
				} // </if: hasWallet>
				
			} // </Section: General>

			if hasWallet {
				Section(header: Text("Security")) {

					NavigationLink(
						destination: AppAccessView(),
						tag: NavLinkTag.AppAccessView,
						selection: $navLinkTag
					) {
						Label { Text("App access") } icon: {
							Image(systemName: isTouchID ? "touchid" : "faceid")
						}
					}

				} // </Section: Security>

			} // <if: hasWallet>

			Section(header: Text("Advanced")) {

				NavigationLink(
					destination: PrivacyView(),
					tag: NavLinkTag.PrivacyView,
					selection: $navLinkTag
				) {
					Label { Text("Privacy") } icon: {
						Image(systemName: "eye")
					}
				}

				if hasWallet {

					NavigationLink(
						destination: ChannelsConfigurationView(),
						tag: NavLinkTag.ChannelsConfigurationView,
						selection: $navLinkTag
					) {
						Label { Text("Payment channels") } icon: {
							Image(systemName: "bolt")
						}
					}

				} // </if: hasWallet>
				
				NavigationLink(
					destination: LogsConfigurationView(),
					tag: NavLinkTag.LogsConfigurationView,
					selection: $navLinkTag
				) {
					Label { Text("Logs") } icon: {
						Image(systemName: "doc.text")
					}
				}
				
			} // </Section: Advanced>
		} // </List>
		.listStyle(.insetGrouped)
		.id(listViewId)
		.onAppear() {
			onAppear()
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
		.onReceive(backupSeedStatePublisher) {(state: BackupSeedState) in
			onBackupSeedState(state)
		}
		.onReceive(externalLightningUrlPublisher) {(url: String) in
			onExternalLightningUrl(url)
		}
			
	} // end: body
	
	func hasWallet() -> Bool {
		
		let walletManager = Biz.business.walletManager
		let hasWalletFlow = SwiftStateFlow<NSNumber>(origin: walletManager.hasWallet)
		
		if let value = hasWalletFlow.value_ {
			return value.boolValue
		} else {
			return false
		}
	}
	
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
				DispatchQueue.main.async { // iOS 14 issues workaround
					deepLinkChanged(deepLink)
				}
			}
			
		} else {
		
			if #unavailable(iOS 15.0) {
				// iOS 14 BUG and workaround.
				//
				// The NavigationLink remains selected after we return to the ConfigurationView.
				// For example:
				// - Tap on "About", to push the AboutView onto the NavigationView
				// - Tap "<" to pop the AboutView
				// - Notice that the "About" row is still selected (e.g. has gray background)
				//
				// There are several workaround for this issue:
				// https://developer.apple.com/forums/thread/660468
				//
				// We are implementing the least risky solution.
				// Which requires us to change the `List.id` property.
				
				if navLinkTag != nil && swiftUiBugWorkaround == nil {
					navLinkTag = nil
					listViewId = UUID()
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
			switch value {
				case .paymentHistory : break
				case .backup         : newNavLinkTag = NavLinkTag.RecoveryPhraseView
				case .drainWallet    : newNavLinkTag = NavLinkTag.CloseWalletView
				case .electrum       : newNavLinkTag = NavLinkTag.PrivacyView
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: 5.0)
				
				self.navLinkTag = newNavLinkTag // Trigger/push the view
			}
			
		} else {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 1.0)
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
			log.trace("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
		}
	}
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				self.swiftUiBugWorkaround = nil
			}
		}
	}
	
	func onBackupSeedState(_ newState: BackupSeedState) {
		log.trace("onBackupSeedState()")
		
		backupSeedState = newState
	}
	
	func onExternalLightningUrl(_ urlStr: String) {
		log.trace("onExternalLightningUrl()")
		
		if #unavailable(iOS 15.0) {
			// iOS 14 bug workaround
			//
			// We previoulsy had a crash under the following conditions:
			// - navigate to ConfigurationView
			// - navigate to a subview (such as AboutView)
			// - switch to another app, and open a lightning URL with Phoenix
			// - crash !
			//
			// It works fine as long as the NavigationStack is popped to at least the ConfigurationView.
			//
			// Apple has fixed the issue in iOS 15.
			navLinkTag = nil
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
