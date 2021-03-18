import SwiftUI
import PhoenixShared

struct ConfigurationView: MVIView {

	@StateObject var mvi = MVIState({ $0.configuration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@ViewBuilder
	var view: some View {
		
		List {
			let fullMode = mvi.model is Configuration.ModelFullMode

			Section(header: Text("General")) {
				NavigationLink(destination:  AboutView()) {
					Label { Text("About") } icon: {
						Image(systemName: "info.circle")
					}
				}
				NavigationLink(destination:  DisplayConfigurationView()) {
					Label { Text("Display") } icon: {
						Image(systemName: "paintbrush.pointed")
					}
				}
				NavigationLink(destination:  ElectrumConfigurationView()) {
					Label { Text("Electrum server") } icon: {
						Image(systemName: "link")
					}
				}
				NavigationLink(destination: ComingSoonView()) {
					Label { Text("Tor") } icon: {
						Image(systemName: "shield.lefthalf.fill")
					}
				}
			}

			if fullMode {
				Section(header: Text("Security")) {
					NavigationLink(destination: AppAccessView()) {
						Label { Text("App access") } icon: {
							Image(systemName: "touchid")
						}
					}
					NavigationLink(destination: RecoverySeedView()) {
						Label { Text("Recovery phrase") } icon: {
							Image(systemName: "key")
						}
					}
				}
			}

			Section(header: Text("Advanced")) {
				NavigationLink(destination: LogsConfigurationView()) {
					Label { Text("Logs") } icon: {
						Image(systemName: "doc.text")
					}
				}
				if fullMode {
					NavigationLink(destination: ChannelsConfigurationView()) {
						Label { Text("Payment channels") } icon: {
							Image(systemName: "bolt")
						}
					}
					NavigationLink(destination: CloseChannelsView()) {
						Label { Text("Close all channels") } icon: {
							Image(systemName: "xmark.circle")
						}
					}
					NavigationLink(destination: ForceCloseChannelsView()) {
						Label { Text("Danger zone") } icon: {
							Image(systemName: "exclamationmark.triangle")
						}
					}.foregroundColor(.appRed)
				}
			}
		}
		.listStyle(GroupedListStyle())
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
