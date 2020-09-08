import SwiftUI
import PhoenixShared

struct ConfigurationView : MVIView {
    typealias Model = Configuration.Model
    typealias Intent = Configuration.Intent

    var body: some View {
        mvi { model, intent in
            VStack(spacing: 0) {
                let fullMode = model is Configuration.ModelFullMode

                Section(header: "General") {
                    NavigationMenu(label: "About", destination: AboutView())
                    NavigationMenu(label: "Display", destination: DisplayConfigurationView())
                    NavigationMenu(label: "Electrum Server", destination: ElectrumConfigurationView())
                    NavigationMenu(label: "Tor", destination: EmptyView())
                }

                Section(header: "Security", visible: fullMode) {
                    NavigationMenu(label: "Recovery phrase", destination: EmptyView())
                    NavigationMenu(label: "App access settings", destination: EmptyView())
                }

                Section(header: "Advanced") {
                    NavigationMenu(label: "Channels list", destination: EmptyView(), visible: fullMode)
                    NavigationMenu(label: "Logs", destination: EmptyView())
                    NavigationMenu(label: "Close all channels", destination: EmptyView(), visible: fullMode)
                    NavigationMenu(label: "Danger zone", destination: EmptyView(), visible: fullMode)
                }

                Spacer()
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Settings", displayMode: .inline)
        }
    }

    struct Section<Content> : View where Content : View {
        let header: String
        let visible: Bool
        let menu: () -> Content

        init(header: String, visible: Bool = true, @ViewBuilder menu: @escaping () -> Content) {
            self.header = header
            self.visible = visible
            self.menu = menu
        }

        var body : some View {
            if visible {
                VStack {
                    Text(header)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.headline)
                            .foregroundColor(.appHorizon)
                            .padding([.leading, .top], 16)
                    Divider()
                    menu()
                }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white)
            }
        }
    }

    struct NavigationMenu<T : View> : View {
        let label: String
        let destination: T
        let visible: Bool

        init(label: String, destination: T, visible: Bool = true) {
            self.label = label
            self.destination = destination
            self.visible = visible
        }

        var body : some View {
            if visible {
                NavigationLink(destination: destination) {
                    Text(label)
                            .font(.subheadline)
                            .buttonStyle(PlainButtonStyle())
                            .foregroundColor(Color.black)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding([.leading, .trailing])
                }
                Divider()
            }
        }
    }
}

class ConfigurationView_Previews : PreviewProvider {
    static let mockModel = Configuration.ModelFullMode()

    static var previews: some View {
        mockView(ConfigurationView()) { $0.configurationModel = mockModel }
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}