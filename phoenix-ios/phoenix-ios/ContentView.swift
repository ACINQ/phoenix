import SwiftUI
import PhoenixShared

struct ContentView: View {

    let di = (UIApplication.shared.delegate as! AppDelegate).di

    var body: some View {
        NavigationView {
            HomeView()
        }
    }
}
