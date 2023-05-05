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
// Here's how it works:
// - The toggle queries its body for the VerticalAlignment.center value
// - Our body is our Label
// - So in `Step A` below, we override the Label's VerticalAlignment.center value to
//   return instead the VerticalAlignment.centerTopLine value
// - And in `Step B` below, we provide the value for VerticalAlignment.centerTopLine.
//
// Toggle(isOn: $state) {
//   Label {
//     VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
//       Text("line 1")
//         .alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
//           d[VerticalAlignment.center] // Step B
//         }
//       Text("line 2")
//       Text("line 3")
//     }
//   } icon: {
//     Image(systemName: "network")
//   } // </Label>
//   .alignmentGuide(VerticalAlignment.center) { d in
//     d[VerticalAlignment.centerTopLine] // Step A
//   }
// } // </Toggle>
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


