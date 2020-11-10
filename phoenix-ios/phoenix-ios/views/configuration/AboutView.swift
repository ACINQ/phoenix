import SwiftUI
import PhoenixShared
import WebKit

struct AboutView: View {
	
	func versionString() -> String {
		return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
	}
	
	var body: some View {
		VStack {
			LocalWebView()
				.frame(maxWidth: .infinity, maxHeight: .infinity)
				.padding(.trailing, 20)
				.padding(.leading, 40)
			
			Text("Version \(versionString())")
				.padding([.top, .bottom], 4)
				.frame(height: 40)
				.frame(maxWidth: .infinity)
				.background(Color(.secondarySystemBackground))
		}
		.navigationBarTitle("About", displayMode: .inline)
	}
	
}

struct LocalWebView: UIViewRepresentable {
	
	func makeCoordinator() -> Coordinator {
		return Coordinator()
	}
	
	func makeUIView(context: Context) -> WKWebView {
		
		let prefs = WKWebpagePreferences()
		prefs.allowsContentJavaScript = false // for security
		
		let configuration = WKWebViewConfiguration()
		configuration.defaultWebpagePreferences = prefs
		
		let webView = WKWebView(frame: CGRect.zero, configuration: configuration)
		webView.navigationDelegate = context.coordinator
		webView.allowsBackForwardNavigationGestures = false
		webView.scrollView.isScrollEnabled = true
		return webView
	}
	
	func updateUIView(_ webView: WKWebView, context: Context) {
		if let url = Bundle.main.url(forResource: "about", withExtension: "html") {
			// The "about.html" file is localized, but "stylesheet.css" is not:
			//
			// - mainBundle/<someLanguage>.lproj/about.html
			// - mainBundle/stylesheet.css
			//
			let stylesheetDir = url.deletingLastPathComponent().deletingLastPathComponent()
			webView.loadFileURL(url, allowingReadAccessTo: stylesheetDir)
		}
	}
	
	class Coordinator : NSObject, WKNavigationDelegate {
		
		private var firstURL: URL? = nil
		
		func webView(_ webView: WKWebView,
					 decidePolicyFor navigationAction: WKNavigationAction,
					decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
		) {
			if let url = navigationAction.request.url {
				// This function gets called twice for the first URL.
				// Probably a bug in WKWebView.
				// We work around it by storing the "safe" URL.
				if firstURL == nil {
					firstURL = url
				}
				if url == firstURL {
					decisionHandler(.allow)
				} else {
					// Open links in web browser
					UIApplication.shared.open(url, options: [:], completionHandler: nil)
					decisionHandler(.cancel)
				}
			} else {
				decisionHandler(.allow)
			}
		}
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
