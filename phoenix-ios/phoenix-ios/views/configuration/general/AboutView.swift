import SwiftUI
import PhoenixShared
import WebKit

struct AboutView: View {
	
	func versionString() -> String {
		return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
	}
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			LocalWebView(
				filename: "about.html",
				scrollIndicatorInsets: UIEdgeInsets(top: 0, left: 0, bottom: 0, right: -20)
			)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.padding(.leading, 20)
			.padding(.trailing, 20) // must match verticalScrollIndicatorInsets.right
			
			Text("Version \(versionString())")
				.padding([.top, .bottom], 4)
				.frame(maxWidth: .infinity, minHeight: 40)
				.background(Color(.secondarySystemBackground))
		}
		.navigationBarTitle("About", displayMode: .inline)
	}	
}

class AboutView_Previews : PreviewProvider {
    static let mockModel = Configuration.ModelSimpleMode()

    static var previews: some View {
        AboutView()
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
