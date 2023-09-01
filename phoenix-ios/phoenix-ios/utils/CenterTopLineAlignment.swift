import SwiftUI

// alignmentGuide explanation:
//
// The Toggle wants to vertically align its switch in the center of the body:
//
// |body| |switch|
//
// This works good when the body is a single line.
// But with multiple lines it looks like:
//
// |line1|
// |line2| |switch|
// |line3|
//
// This isn't always what we want.
// Instead we can use a custom VerticalAlignment to achieve this:
//
// |line1| |switch|
// |line2|
// |line3|
//
// A good resource on alignment guides can be found here:
// https://swiftui-lab.com/alignment-guides/

extension VerticalAlignment {
	private enum CenterTopLineAlignment: AlignmentID {
		static func defaultValue(in d: ViewDimensions) -> CGFloat {
			return d[.bottom]
		}
	}
	
	static let centerTopLine = VerticalAlignment(CenterTopLineAlignment.self)
}


