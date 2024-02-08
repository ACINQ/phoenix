import SwiftUI

fileprivate let filename = "AnyHTML"
#if DEBUG && false
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/// The `AnyHTML` class is an ObservableObject designed to asynchronously
/// load & manipulate HTML files in the app bundle.
///
/// It supports loading both a `*.html` & `*.css"` file.
///
/// The general idea is:
/// 1. Create an html file (e.g. "foo.html"), and add to your app bundle.
/// 2. Optionally create a corresponding css file with the same name. E.g. "foo.css"
/// 3. The html file can optionally be localized using standard Xcode localization.
///    This class will automatically load the correct localization for the user.
///    The css file can optionally be localized too (although this is less common).
/// 4. Add an empty style placeholder to your html file: `<style></style>`.
///    It will get replaced with the contents of your css file.
/// 5. Add any placeholders you want into either file using `[[some_key_name_here]]`
/// 6  Subclass this class, and override `cssReplacements` & `htmlReplacements` as needed
///
class AnyHTML: ObservableObject {
	
	let htmlFilename: String
	let cssFilename: String?
	@Published var htmlString: String? = nil
	
	init(htmlFilename: String, cssFilename: String? = nil) {
		self.htmlFilename = htmlFilename
		self.cssFilename = cssFilename
		
		loadHTML()
	}
	
	func loadHTML() -> Void {
		
		// The filename(s) shouldn't have a file extension.
		// But let's guard against it just in case.
		let htmlResourceName = stripFileExtension(htmlFilename, "html")
		
		let cssResourceName: String
		if let cssFilename {
			cssResourceName = stripFileExtension(cssFilename, "css")
		} else {
			cssResourceName = htmlResourceName
		}
		
		guard let htmlUrl = Bundle.main.url(forResource: htmlResourceName, withExtension: "html") else {
			return
		}
		let cssUrl = Bundle.main.url(forResource: cssResourceName, withExtension: "css")
		
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
				log.error("Error reading resource [html|css]: \(htmlResourceName), \(cssResourceName)")
				return
			}
		}
	}
	
	private func stripFileExtension(_ filename: String, _ ext: String) -> String {
		
		let pathExtension = (filename as NSString).pathExtension
		if pathExtension.caseInsensitiveCompare(ext) == .orderedSame {
			return (filename as NSString).deletingPathExtension
		} else {
			return filename
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
	
	/// Provides a replacement mapping of `[key: value]`,
	/// where text in the css file matching `[[key]]` is replaced by `value`.
	///
	/// For example, if the css file contains:
	/// `color: [[dynamic_color]];`
	///
	/// And you provide a mapping of:
	/// `["my_color": "blue"]`
	///
	/// Then the css becomes:
	/// `color: blue;`
	///
	/// This method is designed to be overriden by subclasses.
	///
	func cssReplacements(_ traits: UITraitCollection) -> [String: String] {
		
		var replacements = [String: String]()
		replacements["background_color"] = UIColor.systemBackground.htmlString(traits)
		replacements["foreground_color"] = UIColor.label.htmlString(traits)
		replacements["link_color"]       = UIColor.appAccent.htmlString(traits)
		
		return replacements
	}
	
	/// Provides a replacement mapping of `[key: value]`,
	/// where text in the css file matching `[[key]]` is replaced by `value`.
	///
	/// For example, if the html file contains:
	/// `Hello [[customer_name]]`
	///
	/// And you provide a mapping of:
	/// `["customer_name": "Satoshi Nakamoto"]`
	///
	/// Then the html becomes:
	/// `Hello Satoshi Nakamoto`
	///
	/// This method is designed to be overriden by subclasses.
	///
	func htmlReplacements(_ traits: UITraitCollection) -> [String: String] {
		
		return [String: String]()
	}
}
