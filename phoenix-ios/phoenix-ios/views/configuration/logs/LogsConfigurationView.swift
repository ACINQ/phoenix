import SwiftUI
import PhoenixShared

struct LogsConfigurationView: View {

    @State var share: NSURL? = nil

    var body: some View {
        EmptyView()
        MVIView({ $0.logsConfiguration() }) { model, postIntent in
            VStack {
                Text("Here you can extract and visualize application logs, as well as share them.")
                        .foregroundColor(Color.primary)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.primaryBackground)
                List {
                    if let m = model as? LogsConfiguration.ModelReady {
                        Button {
                            share = NSURL(fileURLWithPath: m.path)
                        } label: {
                            Label { Text("Share the logs") } icon: {
                                Image(systemName: "square.and.arrow.up")
                            }
                        }
                                .sharing($share)
                        NavigationLink(destination: LogsConfigurationViewerView(filePath: m.path)) {
                            Label { Text("Look at the logs") } icon: {
                                Image(systemName: "eye")
                            }
                        }
                    }
                }
            }
                    .navigationBarTitle("Logs")
        }
    }
}

class LogsConfigurationView_Previews: PreviewProvider {
//    static let mockModel = LogsConfiguration.ModelLoading()
    static let mockModel = LogsConfiguration.ModelReady(path: "/tmp/fake-logs-file")

    static var previews: some View {
        mockView(LogsConfigurationView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
