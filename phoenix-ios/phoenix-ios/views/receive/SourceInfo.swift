import Foundation

enum SourceType {
	case text
	case image
}

struct SourceInfo {
	let type: SourceType
	let isDefault: Bool
	let title: String
	let subtitle: String?
	let callback: () -> Void
}
