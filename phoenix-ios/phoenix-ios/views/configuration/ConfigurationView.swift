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
	
	@State private var selectedTag: String? = nil
	enum Tag: String {
		case AboutView
		case DisplayConfigurationView
		case ElectrumConfigurationView
		case TorView
		case PaymentOptionsView
		case AppAccessView
		case RecoverySeedView
		case LogsConfigurationView
		case ChannelsConfigurationView
		case CloseChannelsView
		case ForceCloseChannelsView
	}
	
	@ViewBuilder
	var view: some View {
		
		List {
			let fullMode = mvi.model is Configuration.ModelFullMode

			Section(header: Text("General")) {
				
				NavigationLink(
					destination: AboutView(),
					tag: Tag.AboutView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("About") } icon: {
						Image(systemName: "info.circle")
					}
				}
				NavigationLink(
					destination: DisplayConfigurationView(),
					tag: Tag.DisplayConfigurationView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("Display") } icon: {
						Image(systemName: "paintbrush.pointed")
					}
				}
				NavigationLink(
					destination: ElectrumConfigurationView(),
					tag: Tag.ElectrumConfigurationView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("Electrum server") } icon: {
						Image(systemName: "link")
					}
				}
				NavigationLink(
					destination: ComingSoonView(),
					tag: Tag.TorView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("Tor") } icon: {
						Image(systemName: "shield.lefthalf.fill")
					}
				}
				NavigationLink(
					destination: PaymentOptionsView(),
					tag: Tag.PaymentOptionsView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("Payment options & fees") } icon: {
						Image(systemName: "wrench")
					}
				}
			}

			if fullMode {
				Section(header: Text("Security")) {
					
					NavigationLink(
						destination: AppAccessView(),
						tag: Tag.AppAccessView.rawValue,
						selection: $selectedTag
					) {
						Label { Text("App access") } icon: {
							Image(systemName: "touchid")
						}
					}
					NavigationLink(
						destination: RecoverySeedView(),
						tag: Tag.RecoverySeedView.rawValue,
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
					tag: Tag.LogsConfigurationView.rawValue,
					selection: $selectedTag
				) {
					Label { Text("Logs") } icon: {
						Image(systemName: "doc.text")
					}
				}
				if fullMode {
					NavigationLink(
						destination: ChannelsConfigurationView(),
						tag: Tag.ChannelsConfigurationView.rawValue,
						selection: $selectedTag
					) {
						Label { Text("Payment channels") } icon: {
							Image(systemName: "bolt")
						}
					}
					NavigationLink(
						destination: CloseChannelsView(),
						tag: Tag.CloseChannelsView.rawValue,
						selection: $selectedTag
					) {
						Label { Text("Close all channels") } icon: {
							Image(systemName: "xmark.circle")
						}
					}
					NavigationLink(
						destination: ForceCloseChannelsView(),
						tag: Tag.ForceCloseChannelsView.rawValue,
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
		.onReceive(AppDelegate.get().externalLightningUrlPublisher, perform: { (url: URL) in
			
			// We previoulsy had a crash under the following conditions:
			// - navigate to ConfigurationView
			// - navigate to a subview (such as AboutView)
			// - switch to another app, and open a lightning URL with Phoenix
			// - crash !
			//
			// It works fine as long as the NavigationStack is popped to at least the ConfigurationView.
			//
			log.debug("externalLightningUrlPublisher fired")
			selectedTag = nil
		})
		.onAppear() {
			
			// This doesn't work anymore on iOS 14 :(
		//	UITableView.appearance().separatorInset =
		//		UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 50)
		}
		.navigationBarTitle("Settings", displayMode: .inline)
			
	} // end: view
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
