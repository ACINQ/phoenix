import SwiftUI
import PhoenixShared

class ObservableDI: ObservableObject {
    let di: DI

    init(_ di: DI) { self.di = di }
}

struct ContentView: View {

    var body: some View {
        NavigationView {
            HomeView()
        }
            .environmentObject(ObservableDI((UIApplication.shared.delegate as! AppDelegate).di))
    }
}
