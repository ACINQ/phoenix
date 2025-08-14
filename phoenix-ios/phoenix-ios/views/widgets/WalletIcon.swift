import SwiftUI

enum WalletIcon: CaseIterable, CustomStringConvertible {
	case airplane
	case balloon
	case beachUmbrella
	case bicycle
	case bird
	case books
	case briefcase
	case cart
	case clipboard
	case cloudDrizzle
	case club
	case cupAndSaucer
	case diamond
	case drop
	case fish
	case flag
	case flame
	case fleuron
	case fossilShell
	case gift
	case graduationCap
	case hammer
	case heart
	case house
	case leaf
	case megaphone
	case moonStars
	case mountain
	case music
	case oars
	case paintbrush
	case paintPalette
	case paperclip
	case pawprint
	case pin
	case popcorn
	case puzzlepiece
	case sailboat
	case snowflake
	case spade
	case sparkles
	case star
	case tag
	case teddybear
	case theaterMasks
	case thermometer
	case trophy
	case wrench
	case wallet
	case wineGlass
	
	var description: String {
		return filename
	}
	
	var filename: String {
		return "\(Self.filenamePrefix)\(systemName)"
	}
	
	var systemName: String { switch self {
		case .airplane      : "airplane"
		case .balloon       : "balloon"
		case .beachUmbrella : "beach.umbrella"
		case .bicycle       : "bicycle"
		case .bird          : "bird"
		case .books         : "books.vertical"
		case .briefcase     : "briefcase"
		case .cart          : "cart"
		case .clipboard     : "list.bullet.clipboard"
		case .cloudDrizzle  : "cloud.drizzle"
		case .club          : "suit.club"
		case .cupAndSaucer  : "cup.and.saucer"
		case .diamond       : "suit.diamond"
		case .drop          : "drop.fill"
		case .fish          : "fish"
		case .flag          : "flag"
		case .flame         : "flame"
		case .fleuron       : "fleuron"
		case .fossilShell   : "fossil.shell"
		case .gift          : "gift"
		case .graduationCap : "graduationcap"
		case .hammer        : "hammer"
		case .heart         : "heart"
		case .house         : "house"
		case .leaf          : "leaf"
		case .megaphone     : "megaphone"
		case .moonStars     : "moon.stars"
		case .mountain      : "mountain.2"
		case .music         : "music.quarternote.3"
		case .oars          : "oar.2.crossed"
		case .paintbrush    : "paintbrush"
		case .paintPalette  : "paintpalette"
		case .paperclip     : "paperclip"
		case .pawprint      : "pawprint"
		case .pin           : "pin"
		case .popcorn       : "popcorn"
		case .puzzlepiece   : "puzzlepiece.extension"
		case .sailboat      : "sailboat"
		case .snowflake     : "snowflake"
		case .spade         : "suit.spade"
		case .sparkles      : "sparkles"
		case .star          : "star"
		case .tag           : "tag"
		case .teddybear     : "teddybear"
		case .theaterMasks  : "theatermasks"
		case .thermometer   : "thermometer.sun"
		case .trophy        : "trophy"
		case .wrench        : "wrench.and.screwdriver"
		case .wallet        : if #available(iOS 18, *) { "wallet.bifold" } else { "wallet.pass" }
		case .wineGlass     : "wineglass"
	}}
	
	static var `default`: WalletIcon {
		return .wallet
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
