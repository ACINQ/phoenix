import SwiftUI
import PhoenixShared

struct ContentView: View {

    let controller: MVIController<Demo.Model, Demo.Intent>

    var body: some View {
        VStack() {
            Button(action: { self.controller.intent(intent: Demo.IntentConnect()) }) {
                Text("Connect")
                        .fontWeight(.bold)
                        .font(.title)
                        .foregroundColor(.white)
                        .frame(minWidth: 0, maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(10)
                        .padding()
            }

            Button(action: { }) {
                Text("Receive 125,000 sat")
                        .fontWeight(.bold)
                        .font(.title)
                        .foregroundColor(.white)
                        .frame(minWidth: 0, maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(10)
                        .padding()
            }
        }
        .withController(controller) { model in
        }
    }
}

class Refresher {
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController =
                UIHostingController(rootView: ContentView(controller: Demo.MockController(
                        model: Demo.ModelEmpty()
                )))
    }
}

//class ContentView_Previews: PreviewProvider {
//    static var previews: some View {
//        ContentView()
//    }
//}
