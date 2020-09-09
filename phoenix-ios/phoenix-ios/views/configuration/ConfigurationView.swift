import SwiftUI
import PhoenixShared

struct ConfigurationView: MVIView {
    typealias Model = Configuration.Model
    typealias Intent = Configuration.Intent

    var body: some View {
        mvi { model, intent in
            VStack(spacing: 0) {
                let fullMode = model is Configuration.ModelFullMode

                Section(header: headerText("General")) {
                    NavigationMenu(label: Text("About"), destination: AboutView())
                    NavigationMenu(label: Text("Display"), destination: DisplayConfigurationView())
                    NavigationMenu(label: Text("Electrum Server"), destination: ElectrumConfigurationView())
                    NavigationMenu(label: Text("Tor"), destination: EmptyView())
                    Divider()
                }

                if fullMode {
                    Section(header: headerText("Security")) {
                        NavigationMenu(label: Text("Recovery phrase"), destination: EmptyView())
                        NavigationMenu(label: Text("App access settings"), destination: EmptyView())
                        Divider()
                    }
                }

                Section(header: headerText("Advanced")) {
                    NavigationMenu(label: Text("Logs"), destination: EmptyView())
                    if fullMode {
                        NavigationMenu(label: Text("Channels list"), destination: EmptyView())
                        NavigationMenu(label: Text("Close all channels"), destination: EmptyView())
                        NavigationMenu(label: Text("Danger zone").foregroundColor(Color.red), destination: EmptyView())
                    }
                    Divider()
                }

                Spacer()
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Settings", displayMode: .inline)
        }
    }

    func headerText(_ label: String) -> some View {
        Text(label)
                .bold()
                .font(.title3)
                .foregroundColor(.appHorizon)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
    }

    struct NavigationMenu<Parent, T: View> : View where Parent : View {
        let label: Parent
        let destination: T

        init(label: Parent, destination: T) {
            self.label = label
            self.destination = destination
        }

        var body: some View {
            Divider()
            NavigationLink(destination: destination) {
                label
                    .foregroundColor(Color.black)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding([.leading, .trailing])
            }
                    .padding(8)
                    .background(Color.white)
        }
    }
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