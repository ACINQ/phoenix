import SwiftUI
import PhoenixShared

struct ConfigurationView: MVIView {
    typealias Model = Configuration.Model
    typealias Intent = Configuration.Intent
	
	var body: some View {
		mvi { model, intent in
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
						NavigationLink(destination: EmptyView()) {
							Label { Text("App access settings") } icon: {
								Image(systemName: "touchid")
							}
						}
						NavigationLink(destination: EmptyView()) {
							Label { Text("Recovery phrase") } icon: {
								Image(systemName: "key")
							}
						}
					}
				}

				Section(header: Text("Advanced")) {
					NavigationLink(destination: EmptyView()) {
						Label { Text("Logs") } icon: {
							Image(systemName: "doc.text")
						}
					}
                    if fullMode {
						NavigationLink(destination: ChannelsConfigurationView()) {
							Label { Text("My payment channels") } icon: {
								Image(systemName: "bolt")
							}
						}
						NavigationLink(destination: EmptyView()) {
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
        mockView(ConfigurationView()) {
            $0.configurationModel = mockModel
        }
		.previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
