import SwiftUI
import PhoenixShared

struct ConfigurationView : MVIView {
    typealias Model = Configuration.Model
    typealias Intent = Configuration.Intent

    var body: some View {
        mvi { model, intent in
            VStack(spacing: 0) {
                let fullMode = model is Configuration.ModelFullMode

                General()
                if fullMode { Security() }
                Advanced(fullMode: fullMode)

                Spacer()
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("Settings", displayMode: .inline)
        }
    }


    struct General: View {
        var body: some View {
            VStack {
                Text("General")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.headline)
                        .foregroundColor(.appHorizon)
                        .padding([.leading, .top], 16)

                Divider()

                NavigationLink(destination: AboutView()) {
                        Text("About").font(.subheadline)
                                .buttonStyle(PlainButtonStyle())
                                .foregroundColor(Color.black)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding([.leading, .trailing])
                }

                Divider()

                Text("Display")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding([.leading, .trailing])

                Divider()

                Text("Electrum Server")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding([.leading, .trailing])

                Divider()
            }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)

        }
    }

    struct Security : View {
        var body : some View {
            VStack {
                Text("Security")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.headline)
                        .foregroundColor(.appHorizon)
                        .padding([.leading, .top], 16)

                Divider()
                Text("Recovery phrase")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding([.leading, .trailing])

                Divider()

                Text("App access settings")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding([.leading, .trailing])

                Divider()
            }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
        }
    }

    struct Advanced : View {
        let fullMode: Bool

        var body : some View {
            VStack {
                Text("Advanced")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.headline)
                        .foregroundColor(.appHorizon)
                        .padding([.leading, .top], 16)

                if fullMode {
                    Divider()
                    Text("Channels list")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.subheadline)
                            .padding([.leading, .trailing])
                }

                Divider()
                Text("Logs")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding([.leading, .trailing])

                if fullMode {
                    Divider()
                    Text("Close all channels")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.subheadline)
                            .padding([.leading, .trailing])

                Divider()
                Text("Danger zone")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .foregroundColor(Color.red)
                        .padding([.leading, .trailing])
                }

                Divider()
            }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)

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