import Foundation
import PhoenixShared


struct RecoveryPhrase: Codable {
	let mnemonics: String
	let languageCode: String // ISO 639-1
	
	var mnemonicsArray: [String] {
		return mnemonics.split(separator: " ").map { String($0) }
	}
	
	var language: MnemonicLanguage? {
		return MnemonicLanguage.fromLanguageCode(languageCode)
	}
	
	private init(mnemonicsArray: [String], mnemonicsString: String, languageCode: String) {
		
		precondition(mnemonicsArray.count == 12, "Invalid parameter: mnemonics.count")
		
		let space = " "
		precondition(mnemonicsArray.allSatisfy { !$0.contains(space) },
		  "Invalid parameter: mnemonics.word contains space")
		
		let mnemonicsData = mnemonicsString.data(using: .utf8)
		precondition(mnemonicsData != nil,
		  "Invalid parameter: mnemonics.word contains non-utf8 characters")
		
		self.mnemonics = mnemonicsString
		self.languageCode = languageCode
	}
	
	init(mnemonics mnemonicsArray: [String], languageCode: String = "en") {
		
		let mnemonicsString = mnemonicsArray.joined(separator: " ")
		self.init(mnemonicsArray: mnemonicsArray, mnemonicsString: mnemonicsString, languageCode: languageCode)
	}

	init(mnemonics mnemonicsArray: [String], language: MnemonicLanguage) {
		
		self.init(mnemonics: mnemonicsArray, languageCode: language.code)
	}
	
	init(mnemonics mnemonicsString: String, languageCode: String = "en") {
		
		let mnemonicsArray = mnemonicsString.split(separator: " ").map { String($0) }
		self.init(mnemonicsArray: mnemonicsArray, mnemonicsString: mnemonicsString, languageCode: languageCode)
	}
	
	init(mnemonics mnemonicsString: String, language: MnemonicLanguage) {
		
		self.init(mnemonics: mnemonicsString, languageCode: language.code)
	}
}
