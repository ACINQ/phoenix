import SwiftUI
import WebKit

/// Allows you to display an embedded HTML file.
///
struct LocalWebView: UIViewRepresentable {
	
	let filename: String
	let scrollIndicatorInsets: UIEdgeInsets
	
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
		webView.scrollView.verticalScrollIndicatorInsets = scrollIndicatorInsets
		webView.scrollView.clipsToBounds = false
		return webView
	}
	
	func updateUIView(_ webView: WKWebView, context: Context) {
		
		var resourceName = filename
		
		let pathExtension = (filename as NSString).pathExtension
		if pathExtension.caseInsensitiveCompare("html") == .orderedSame {
			resourceName = (filename as NSString).deletingPathExtension
		}
		
		if let url = Bundle.main.url(forResource: resourceName, withExtension: "html") {
			// The "*.html" file is localized, but "stylesheet.css" is not:
			//
			// - mainBundle/<someLanguage>.lproj/*.html
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
