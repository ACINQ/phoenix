import SwiftUI

enum WalletIcon: CaseIterable, CustomStringConvertible {
	case oars
	case sparkles
	case moonStars
	case snowflake
	case flame
	case beachUmbrella
	case megaphone
	case music
	case heart
	case star
	case flag
	case briefcase
	case puzzlepiece
	case airplane
	case sailboat
	case bicycle
	case bird
	case fish
	case pawprint
	case teddybear
	case leaf
	case paintPalette
	case cupAndSaucer
	case fossilShell
	
	var description: String {
		return filename
	}
	
	var filename: String {
		return "\(Self.filenamePrefix)\(systemName)"
	}
	
	var systemName: String { switch self {
		case .oars          : "oar.2.crossed"
		case .sparkles      : "sparkles"
		case .moonStars     : "moon.stars"
		case .snowflake     : "snowflake"
		case .flame         : "flame"
		case .beachUmbrella : "beach.umbrella"
		case .megaphone     : "megaphone"
		case .music         : "music.quarternote.3"
		case .heart         : "heart"
		case .star          : "star"
		case .flag          : "flag"
		case .briefcase     : "briefcase"
		case .puzzlepiece   : "puzzlepiece.extension"
		case .airplane      : "airplane"
		case .sailboat      : "sailboat"
		case .bicycle       : "bicycle"
		case .bird          : "bird"
		case .fish          : "fish"
		case .pawprint      : "pawprint"
		case .teddybear     : "teddybear"
		case .leaf          : "leaf"
		case .paintPalette  : "paintpalette"
		case .cupAndSaucer  : "cup.and.saucer"
		case .fossilShell   : "fossil.shell"
	}}
	
	static var `default`: WalletIcon {
		return .fossilShell
	}
	
	static var filenamePrefix: String {
		return ":"
	}
	
	static func isValidFilename(_ filename: String) -> Bool {
		return fromFilename(filename) != nil
	}
	
	static func fromFilename(_ filename: String) -> WalletIcon? {
		let prefix = self.filenamePrefix
		guard filename.starts(with: prefix) else {
			return nil
		}
		let systemName = filename.substring(location: prefix.count)
		return fromSystemName(systemName)
	}
	
	static func fromSystemName(_ systemName: String) -> WalletIcon? {
		for item in self.allCases {
			if item.systemName == systemName {
				return item
			}
		}
		return nil
	}
	
	static func random() -> WalletIcon {
		let all = self.allCases
		let index = Int.random(in: 0 ..< all.count)
		return all[index]
	}
}
