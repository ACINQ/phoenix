import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AnyHTML"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

class AnyHTML: ObservableObject {
	
	let filename: String
	@Published var htmlString: String? = nil
	
	init(filename: String) {
		self.filename = filename
		loadHTML()
	}
	
	func loadHTML() -> Void {
		
		var resourceName = filename
		
		// The filename shouldn't have a file extension.
		// But let's guard against it just in case.
		//
		let pathExtension = (filename as NSString).pathExtension
		if pathExtension.caseInsensitiveCompare("html") == .orderedSame {
			resourceName = (filename as NSString).deletingPathExtension
		}
		
		guard let htmlUrl = Bundle.main.url(forResource: resourceName, withExtension: "html") else {
			return
		}
		let cssUrl = Bundle.main.url(forResource: resourceName, withExtension: "css")
		
		let traits = UITraitCollection.current // this is thread-local; need to fetch from main thread;
		
		DispatchQueue.global(qos: .userInitiated).async {
			
			do {
				var html = try String(contentsOf: htmlUrl)
				var css: String? = nil
				
				if let cssUrl = cssUrl {
					css = try String(contentsOf: cssUrl)
				}
				
				html = self.processHTML(html: html, css: css, traits: traits)
				
				DispatchQueue.main.async {[weak self] in
					self?.htmlString = html
				}
				
			} catch {
				log.error("Error reading resource [html|css]: \(resourceName)")
				return
			}
		}
	}
	
	func processHTML(html inHtml: String, css: String?, traits: UITraitCollection) -> String {
		
		var html = inHtml
		
		if var css = css {
			
			for (key, value) in cssReplacements(traits) {
				css = css.replacingOccurrences(of: "[[\(key)]]", with: value)
			}
			
			html = html.replacingOccurrences(of: "<style></style>", with: "<style>\(css)</style>")
		}
		
		for (key, value) in htmlReplacements(traits) {
			html = html.replacingOccurrences(of: "[[\(key)]]", with: value)
		}
		
		return html
	}
	
	func cssReplacements(_ traits: UITraitCollection) -> [String: String] {
		
		var replacements = [String: String]()
		replacements["background_color"] = UIColor.systemBackground.htmlString(traits)
		replacements["foreground_color"] = UIColor.label.htmlString(traits)
		replacements["link_color"]       = UIColor.link.htmlString(traits)
		
		return replacements
	}
	
	func htmlReplacements(_ traits: UITraitCollection) -> [String: String] {
		
		return [String: String]()
	}
}
