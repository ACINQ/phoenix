import SwiftUI
import PhoenixShared

struct AboutView : View {

    var body: some View {
        VStack {
            Text("About here.")
        }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.appBackground)
                .edgesIgnoringSafeArea(.bottom)
                .navigationBarTitle("About", displayMode: .inline)
    }
}

class AboutView_Previews : PreviewProvider {
    static let mockModel = Configuration.ModelSimpleMode()

    static var previews: some View {
        mockView(AboutView()) { $0.configurationModel = mockModel }
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}