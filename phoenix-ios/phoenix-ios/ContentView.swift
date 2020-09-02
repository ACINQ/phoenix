import SwiftUI
import PhoenixShared

class ObservableDI: ObservableObject {
    let di: DI

    init(_ di: DI) { self.di = di }
}

struct ContentView: MVIView {
    typealias Model = Content.Model
    typealias Intent = Content.Intent

    static func UIKitAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0.9, green: 0.9, blue: 0.9, alpha: 1.0)
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().standardAppearance = appearance
    }

    var body: some View {
        mvi { model, intent in
            NavigationView {
                if model is Content.ModelIsInitialized {
                    HomeView()
                } else {
                    InitView()
                }
            }
        }
                    .environmentObject(ObservableDI((UIApplication.shared.delegate as! AppDelegate).di))
    }
}

func mockView<V : View>(_ content: V, block: @escaping (MockDIBuilder) -> Void) -> some View {
    NavigationView { content }
            .environmentObject(
                    ObservableDI(
                            DI((UIApplication.shared.delegate as! AppDelegate).mocks.apply(block: block).di())
                    )
            )
}

class ContentView_Previews: PreviewProvider {
    static let mockModel = Content.ModelNeedInitialization()

    static var previews: some View {
        mockView(ContentView()) { $0.contentModel = mockModel }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}