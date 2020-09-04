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
                    ButtonMenu(label: "Electrum Server")
                }

                Section(header: "Security", fullMode: fullMode) {
                    ButtonMenu(label: "Recovery phrase")
                    ButtonMenu(label: "App access settings")
                }

                Section(header: "Advanced") {
                    ButtonMenu(label: "Channels list", visible: fullMode)
                    ButtonMenu(label: "Logs")
                    ButtonMenu(label: "Close all channels", visible: fullMode)
                    ButtonMenu(label: "Danger zone", visible: fullMode).foregroundColor(Color.red)
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
        let fullMode: Bool
        let menu: () -> Content

        init(header: String, fullMode: Bool = true, @ViewBuilder menu: @escaping () -> Content) {
            self.header = header
            self.fullMode = fullMode
            self.menu = menu
        }

        var body : some View {
            if fullMode {
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

    struct ButtonMenu: View {
        let label: String
        let action: () -> Void
        let visible: Bool

        init(label: String, visible: Bool = true, action: @escaping () -> Void = { }) {
            self.label = label
            self.action = action
            self.visible = visible
        }

        var body : some View {
            if visible {
                Button {
                    action()
                } label: {
                    Text(label)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.subheadline)
                            .foregroundColor(Color.black)
                            .padding([.leading, .trailing])
                }
                Divider()
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
    static let mockModel = Configuration.ModelSimpleMode()

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