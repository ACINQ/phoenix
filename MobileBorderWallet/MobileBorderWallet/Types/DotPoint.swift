import Foundation


struct DotPoint: Hashable, Comparable, CustomStringConvertible {
	let x: UInt16
	let y: UInt16
	
	var description: String {
		return "(\(x),\(y))"
	}
	
	static func < (lhs: DotPoint, rhs: DotPoint) -> Bool {
		return (lhs.y < rhs.y) || (lhs.y == rhs.y && lhs.x < rhs.x)
	}
}
