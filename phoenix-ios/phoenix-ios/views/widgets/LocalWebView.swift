import SwiftUI
@preconcurrency import WebKit

/// Allows you to display an embedded HTML file.
///
struct LocalWebView: UIViewRepresentable {
	
	@ObservedObject var html: AnyHTML
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
		webView.scrollView.clipsToBounds = true
		webView.isOpaque = false // prevents white flash in dark mode
		return webView
	}
	
	func updateUIView(_ webView: WKWebView, context: Context) {
		
		if let htmlString = html.htmlString {
			
			webView.loadHTMLString(htmlString, baseURL: Bundle.main.resourceURL)
		}
	}
	
	class Coordinator : NSObject, WKNavigationDelegate {
		
		private var firstURL: URL? = nil
		
		func webView(
			_ webView: WKWebView,
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
