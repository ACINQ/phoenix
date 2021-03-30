import Foundation

/// Add this protocol to your view:
/// ```
/// struct MyView: View, ViewName
/// ```
///
/// And then you can quickly include the name of the view in log statements:
/// ```
/// log.trace("[\(viewName)] onAppear()")
/// ```
///
protocol ViewName {
	var viewName: String { get }
}

extension ViewName {
	
	var viewName: String {
		get {
			let thisType = type(of: self)
			return String(describing: thisType)
		}
	}
}
