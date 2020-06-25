import SwiftUI
import Phoenix

struct ContentView: View {

    let controller: MVIController<LogController.Model, KotlinUnit>

    @State var txt: [String] = []

    var body: some View {
        List(txt, id: \.self) {
            Text($0)
        }
        .withController(controller) { model in
            self.txt = model.lines
        }
    }
}

class Refresher {
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController =
                UIHostingController(rootView: ContentView(controller: MVIControllerMock(
                        model: LogController.Model(lines: ["a", "b", "c"])
                )))
    }
}

//class ContentView_Previews: PreviewProvider {
//    static var previews: some View {
//        ContentView()
//    }
//}
