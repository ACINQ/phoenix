import SwiftUI
import PhoenixShared

class DIHolder : ObservableObject {
    let di: AppDI

    init(_ di: AppDI) { self.di = di }
}

struct ContentView: View {

    var body: some View {
        NavigationView {
            HomeView()
        }
            .environmentObject(DIHolder((UIApplication.shared.delegate as! AppDelegate).di))
    }
}
