import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ConfigurationView: MVIView {

	@StateObject var mvi = MVIState({ $0.configuration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var isFaceID = true
	@State var isTouchID = false
	
	enum Tag: String {
		case AboutView
		case DisplayConfigurationView
		case ElectrumConfigurationView
		case TorView
		case PaymentOptionsView
		case CloudOptionsView
		case AppAccessView
		case RecoverySeedView
		case LogsConfigurationView
		case ChannelsConfigurationView
		case CloseChannelsView
		case ForceCloseChannelsView
	}
	@State private var selectedTag: Tag? = nil
	
	@State private var listViewId = UUID()
	
	@ViewBuilder
	var view: some View {
		
		List {
			let fullMode = mvi.model is Configuration.ModelFullMode

			Section(header: Text("General")) {
				
				NavigationLink(
					destination: AboutView(),
					tag: Tag.AboutView,
					selection: $selectedTag
				) {
					Label { Text("About") } icon: {
						Image(systemName: "info.circle")
					}
				}
				
				NavigationLink(
					destination: DisplayConfigurationView(),
					tag: Tag.DisplayConfigurationView,
					selection: $selectedTag
				) {
					Label { Text("Display") } icon: {
						Image(systemName: "paintbrush.pointed")
					}
				}
				
				NavigationLink(
					destination: ElectrumConfigurationView(),
					tag: Tag.ElectrumConfigurationView,
					selection: $selectedTag
				) {
					Label { Text("Electrum server") } icon: {
						Image(systemName: "link")
					}
				}
				
				NavigationLink(
					destination: ComingSoonView(title: "Tor"),
					tag: Tag.TorView,
					selection: $selectedTag
				) {
					Label { Text("Tor") } icon: {
						Image(systemName: "shield.lefthalf.fill")
					}
				}
				
				NavigationLink(
					destination: PaymentOptionsView(),
					tag: Tag.PaymentOptionsView,
					selection: $selectedTag
				) {
					Label { Text("Payment options & fees") } icon: {
						Image(systemName: "wrench")
					}
				}
				
				NavigationLink(
					destination: CloudOptionsView(),
					tag: Tag.CloudOptionsView,
					selection: $selectedTag
				) {
					Label { Text("Cloud backup") } icon: {
						Image(systemName: "icloud")
					}
				}
			}

			if fullMode {
				Section(header: Text("Security")) {
					
					NavigationLink(
						destination: AppAccessView(),
						tag: Tag.AppAccessView,
						selection: $selectedTag
					) {
						Label { Text("App access") } icon: {
							Image(systemName: isTouchID ? "touchid" : "faceid")
						}
					}
					NavigationLink(
						destination: RecoverySeedView(),
						tag: Tag.RecoverySeedView,
						selection: $selectedTag
					) {
						Label { Text("Recovery phrase") } icon: {
							Image(systemName: "key")
						}
					}
				}
			}

			Section(header: Text("Advanced")) {
				
				NavigationLink(
					destination: LogsConfigurationView(),
					tag: Tag.LogsConfigurationView,
					selection: $selectedTag
				) {
					Label { Text("Logs") } icon: {
						Image(systemName: "doc.text")
					}
				}
				if fullMode {
					NavigationLink(
						destination: ChannelsConfigurationView(),
						tag: Tag.ChannelsConfigurationView,
						selection: $selectedTag
					) {
						Label { Text("Payment channels") } icon: {
							Image(systemName: "bolt")
						}
					}
					NavigationLink(
						destination: CloseChannelsView(),
						tag: Tag.CloseChannelsView,
						selection: $selectedTag
					) {
						Label { Text("Close all channels") } icon: {
							Image(systemName: "xmark.circle")
						}
					}
					NavigationLink(
						destination: ForceCloseChannelsView(),
						tag: Tag.ForceCloseChannelsView,
						selection: $selectedTag
					) {
						Label { Text("Danger zone") } icon: {
							Image(systemName: "exclamationmark.triangle")
						}
					}.foregroundColor(.appNegative)
				}
			}
		}
		.listStyle(GroupedListStyle())
		.id(listViewId)
		.onAppear() {
			onAppear()
		}
		.onReceive(AppDelegate.get().externalLightningUrlPublisher) { (url: URL) in
			onExternalLightningUrl(url)
		}
		.navigationBarTitle(
			NSLocalizedString("Settings", comment: "Navigation bar title"),
			displayMode: .inline
		)
			
	} // end: view
	
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
		
		// SwiftUI BUG, and workaround.
		//
		// In iOS 14, the NavigationLink remains selected after we return to the ConfigurationView.
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
		
		if selectedTag != nil {
			selectedTag = nil
			listViewId = UUID()
		}
	}
	
	func onExternalLightningUrl(_ url: URL) {
		log.debug("onExternalLightningUrl()")
		
		// We previoulsy had a crash under the following conditions:
		// - navigate to ConfigurationView
		// - navigate to a subview (such as AboutView)
		// - switch to another app, and open a lightning URL with Phoenix
		// - crash !
		//
		// It works fine as long as the NavigationStack is popped to at least the ConfigurationView.
		//
		selectedTag = nil
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
