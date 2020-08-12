import SwiftUI
import PhoenixShared

class ObservableDI: ObservableObject {
    let di: DI

    init(_ di: DI) { self.di = di }
}

struct ContentView: View {
    static func UIKitAppearance() {
//        let appearance = UINavigationBarAppearance()
//        appearance.configureWithOpaqueBackground()
//        appearance.backgroundColor = UIColor(red: 0.9, green: 0.9, blue: 0.9, alpha: 1.0)
//        UINavigationBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        NavigationView {
            HomeView()
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
