import SwiftUI
import PhoenixShared

struct ContentView: View {

    static func UIKitAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().standardAppearance = appearance
    }

    var body: some View {
        appView(
            MVIView({ $0.content() }) { model, intent in
                NavigationView {
                    if model is Content.ModelIsInitialized {
                        HomeView()
                    } else if model is Content.ModelNeedInitialization {
                        InitializationView()
                    } else {
                        VStack {
                            // Maybe a better animation / transition screen ?
                            Image(systemName: "arrow.triangle.2.circlepath")
                                    .imageScale(.large)
                                    .rotationEffect(Angle(degrees: 360.0))
                                    .animation(.easeIn)
                        }
                                .edgesIgnoringSafeArea(.all)
                                .navigationBarTitle("", displayMode: .inline)
                                .navigationBarHidden(true)
                        // Phoenix Loading View ?
                    }
                }
            }
        )
    }
}


class ContentView_Previews: PreviewProvider {
    static let mockModel = Content.ModelNeedInitialization()

    static var previews: some View {
        mockView(ContentView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
