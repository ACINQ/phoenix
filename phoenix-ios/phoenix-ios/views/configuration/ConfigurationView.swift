import SwiftUI
import PhoenixShared

struct ConfigurationView: View {

	var body: some View {
		MVIView({ $0.configuration() }) { model, postIntent in
			List {
				let fullMode = model is Configuration.ModelFullMode

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
					NavigationLink(destination:  EmptyView()) {
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
						NavigationLink(destination: EmptyView()) {
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
			
		} // end: mvi
	} // end: func
}

class ConfigurationView_Previews: PreviewProvider {
    static let mockModel = Configuration.ModelFullMode()

    static var previews: some View {
        mockView(ConfigurationView())
		.previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
