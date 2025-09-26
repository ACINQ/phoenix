import SwiftUI

struct WalletEmoji: Hashable {
	let emoji: String
	
	private init(emoji: String) {
		self.emoji = emoji
	}
	
	var description: String {
		return filename
	}
	
	var filename: String {
		return "\(Self.filenamePrefix)\(emoji)"
	}
	
	static var `default`: WalletEmoji {
		return WalletEmoji(emoji: "⚡")
	}
	
	static let rawList: [String] = [
		"😃", "🙃", "🤑", "😎", "🥳",
		"🧐", "🤠", "🤖", "👑", "🚀",
		"✈️", "🚣", "⛵", "🚗", "🏍️",
		"🧘", "⛹️", "🤾", "🚴", "🧗",
		"🏋️", "🤼", "🏌️", "🏇", "🤺",
		"⛷️", "🏂", "🏄", "🏊", "🥷",
		"💂", "🤵", "🤵‍♀️", "🧑‍🚀", "👷",
		"👮", "🧑‍🔬", "🧑‍🔧", "🧑‍🚒", "🧑‍🌾",
		"🧑‍🎓", "🧑‍⚖️", "👶", "🧒", "🧑",
		"🧓", "👧", "👩", "👵", "🐶",
		"🐱", "🐴", "🐭", "🐹", "🐰",
		"🦊", "🐻", "🐼", "🐨", "🐯",
		"🦁", "🐮", "🐷", "🐸", "🐵",
		"🐔", "🐧", "🐤", "🦄", "🐝",
		"🐙", "🐬", "🐋", "🦜", "🐟",
		"🐛", "🍏", "🍎", "🍌", "🍇",
		"🍓", "🍺", "🍿", "🥖", "🧀",
		"🍔", "🌹", "🌻", "☘️", "❄️",
		"⛰️", "🌴", "🌳", "🌲", "⚡",
		"🌧️", "🌩️", "🌦️", "☀️", "🎂",
		"🎁", "🎈", "🎉", "🎃", "🎄",
		"🎀", "🔥", "💫", "⭐", "✨",
		"💰", "💸", "📈", "🎯", "♥️"
	]
	
	static func list() -> [WalletEmoji] {
		rawList.map { emoji in
			WalletEmoji(emoji: emoji)
		}
	}
	
	static var filenamePrefix: String {
		return ":"
	}
	
	static func isValidFilename(_ filename: String) -> Bool {
		return fromFilename(filename) != nil
	}
	
	static func fromFilename(_ filename: String) -> WalletEmoji? {
		let prefix = self.filenamePrefix
		guard filename.starts(with: prefix) else {
			return nil
		}
		let emoji = filename.substring(location: prefix.count)
		return fromEmoji(emoji)
	}
	
	static func fromEmoji(_ emoji: String) -> WalletEmoji? {
		if rawList.contains(emoji) {
			return WalletEmoji(emoji: emoji)
		} else {
			return nil
		}
	}
	
	static func random() -> WalletEmoji {
		let all = self.rawList
		let index = Int.random(in: 0 ..< all.count)
		return WalletEmoji(emoji: all[index])
	}
}
