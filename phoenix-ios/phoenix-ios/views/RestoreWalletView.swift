import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RestoreWalletView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct RestoreWalletView: View {

	@State var acceptedWarning = false
	@State var mnemonics = [String]()
	@State var autocomplete = [String]()
	
	var body: some View {
		MVIView({ $0.restoreWallet() },
			onModel: { change in
			
				if let model = change.newModel as? RestoreWallet.ModelFilteredWordlist {
					autocomplete = model.words
				}
				else if let model = change.newModel as? RestoreWallet.ModelValidMnemonics {
					finishAndRestoreWallet(model: model)
				}
				
			}) { model, postIntent in
			
				main(model: model, postIntent: postIntent)
					.padding(.top, keyWindow?.safeAreaInsets.bottom)
					.padding(.bottom, keyWindow?.safeAreaInsets.top)
					.padding([.leading, .trailing], 10)
					.edgesIgnoringSafeArea([.bottom, .leading, .trailing])
		}
		.navigationBarTitle("Restore my wallet", displayMode: .inline)
    }

	@ViewBuilder func main(
		model: RestoreWallet.Model,
		postIntent: @escaping (RestoreWallet.Intent) -> Void
	) -> some View {
		
		if !acceptedWarning {
			WarningView(acceptedWarning: $acceptedWarning)
				.zIndex(1)
				.transition(.move(edge: .bottom))
		} else {
			RestoreView(
				model: model,
				postIntent: postIntent,
				mnemonics: $mnemonics,
				autocomplete: $autocomplete
			)
			.zIndex(0)
		}
	}
	
	func finishAndRestoreWallet(model: RestoreWallet.ModelValidMnemonics) -> Void {
		
		AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
			if error == nil {
				AppDelegate.get().loadWallet(seed: model.seed)
			}
		}
	}
}

struct WarningView: View {
	
	@Binding var acceptedWarning: Bool
	@State var iUnderstand: Bool = false
	
	var body: some View {
		VStack {
			
			Text(
				"Do not import a seed that was NOT created by this application.\n\n" +
				"Also, make sure that you don't have another Phoenix wallet running with the same seed."
			)
			.font(.title3)
			.padding(.top, 20)

			Toggle(isOn: $iUnderstand) {
				Text("I understand.").font(.title3)
			}
			.padding([.top, .bottom], 16)
			.padding([.leading, .trailing], 88)

			Button {
				withAnimation {
					acceptedWarning = true
				}
				
			} label: {
				HStack {
					Image("ic_arrow_next")
						.resizable()
						.frame(width: 16, height: 16)
					Text("Next")
						.font(.title2)
				}
			}
			.disabled(!iUnderstand)
			.buttonStyle(PlainButtonStyle())
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.background(Color(UIColor.systemFill))
			.cornerRadius(16)
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
			)
			
			Spacer()
			
		} // </VStack>
		.background(Color(UIColor.systemBackground))
	}
}

struct RestoreView: View {
	
	let model: RestoreWallet.Model
	let postIntent: (RestoreWallet.Intent) -> Void

	@Binding var mnemonics: [String]
	@Binding var autocomplete: [String]
	
	@State var wordInput: String = ""
	@State var isProcessingPaste = false
	
	var body: some View {
	
		VStack {
			
			Text("Your wallet's seed is a list of 12 english words.")
				.font(.title3)
				.padding(.top, 20)

			TextField("Enter keywords from your seed", text: $wordInput)
				.onChange(of: wordInput) { _ in
					onInput()
				}
				.padding()
				.disableAutocorrection(true)
				.disabled(mnemonics.count == 12)

			// Autocomplete suggestions for mnemonics
			ScrollView(.horizontal) {
				LazyHStack {
					if autocomplete.count < 12 {
						ForEach(autocomplete, id: \.self) { word in

							Text(word)
								.underline()
								.frame(maxWidth: .infinity) // Hack to be able to tap ...
								.onTapGesture {
									selectMnemonic(word)
								}
						}
					}
				}
			}
			.frame(height: 32)
			.padding([.leading, .trailing])

			Divider()
				.padding()

			// List of mnemonics:
			// #1   #7
			// #2   #8
			// ...  ...
			ForEach(0..<6, id: \.self) { idx in

				let idxLeftColumn = idx
				let idxRightColumn = idx + 6

				VStack(spacing: 2) {
					HStack(alignment: .center, spacing: 0) {

						Text("#\(idxLeftColumn + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.trailing, 2)

						HStack {
							Text(mnemonic(idxLeftColumn))
								.font(.headline)
								.frame(maxWidth: .infinity, alignment: .leading)

							Button {
								mnemonics.removeSubrange(idxLeftColumn..<mnemonics.count)
							} label: {
								Image("ic_cross")
									.resizable()
									.frame(width: 24, height: 24)
									.foregroundColor(Color.appRed)
							}
							.isHidden(mnemonics.count <= idxLeftColumn)
						}
						.padding(.trailing, 8)

						Text("#\(idxRightColumn + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.trailing, 2)

						HStack {
							Text(mnemonic(idxRightColumn ))
								.font(.headline)
								.frame(maxWidth: .infinity, alignment: .leading)

							Button {
								mnemonics.removeSubrange(idxRightColumn..<mnemonics.count)
							} label: {
								Image("ic_cross")
									.resizable()
									.frame(width: 24, height: 24)
									.foregroundColor(Color.appRed)
							}
							.isHidden(mnemonics.count <= idxRightColumn)
						}

					} // </HStack>
					.padding([.leading, .trailing], 16)

				} // </VStack>
			} // </ForEach>

			if model is RestoreWallet.ModelInvalidMnemonics {
				Text(
					"This seed is invalid and cannot be imported.\n\n" +
					"Please try again"
				)
				.padding()
				.foregroundColor(Color.red)
			}

			Spacer()

			Button {
				onImportButtonTapped()
			} label: {
				HStack {
					Image(systemName: "checkmark.circle")
						.imageScale(.small)

					Text("Import")
				}
				.font(.title2)
			}
			.disabled(mnemonics.count != 12)
			.buttonStyle(PlainButtonStyle())
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.background(Color(UIColor.systemFill))
			.cornerRadius(16)
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
			)
			.padding(.bottom, 20)
		}
		.frame(maxHeight: .infinity)
		.onChange(of: autocomplete) { _ in
			onAutocompleteListChanged()
		}
	}
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	func onInput() -> Void {
		log.trace("onInput(): \"\(wordInput)\"")
		
		// When the user hits space, we auto-accept the first mnemonic in the autocomplete list
		if maybeSelectMnemonic(isAutocompleteTrigger: false) {
			return
		}
		
		updateAutocompleteList()
	}
	
	func updateAutocompleteList() {
		log.trace("updateAutocompleteList()")
		
		// Some keyboards will inject the entire word plus a space.
		//
		// For example, if using a sliding keyboard (e.g. SwiftKey),
		// then after sliding from key to key, and lifting your finger,
		// the entire word will be injected plus a space: "bacon "
		//
		// We should also consider the possibility that the user pasted in their seed.
		
		let tokens = wordInput.trimmingCharacters(in: .whitespaces).split(separator: " ")
		if let firstToken = tokens.first {
			postIntent(RestoreWallet.IntentFilterWordList(predicate: String(firstToken)))
		} else {
			autocomplete = []
		}
	}
	
	func onAutocompleteListChanged() {
		log.trace("onAutocompleteListChanged()")
		
		// Example flow that gets us here:
		//
		// - user is using fancy keyboard (e.g. SwiftKey)
		// - user slides on keyboard to type word
		// - the completed work is injected, plus a space: "Bacon "
		// - onInput("Bacon ") is called, but autocomplete list is empty
		// - we ask the business library for an updated autocomplete list
		// - the library responds with: ["bacon"]
		// - this function is called
		
		maybeSelectMnemonic(isAutocompleteTrigger: true)
	}
	
	@discardableResult
	func maybeSelectMnemonic(isAutocompleteTrigger: Bool) -> Bool {
		
		log.trace("maybeSelectMnemonic(isAutocompleteTrigger = \(isAutocompleteTrigger))")
		
		if wordInput.hasSuffix(" "),
		   let acceptedWord = autocomplete.first
		{
			// Example:
			// The input is "Bacon ", and the autocomplete list is ["bacon"],
			// so we should automatically select the mnemonic.
			//
			// This needs to happen if:
			//
			// - the user hits space when typing, so we want to automatically accept
			//   the first entry in the autocomplete list
			//
			// - the user is using a fancy keyboard (e.g. SwiftKey),
			//   and the input was injected at one time as a word plus space: "Bacon "
			
			selectMnemonic(acceptedWord)
			return true
		}
		
		if isAutocompleteTrigger && autocomplete.count >= 1 {
			
			// Example:
			// The user pasted in their seed, so the input has multiple tokens: "bacon fat animal ...",
			// We want to automatically select each mnemonic in the list (if it's an exact match).
			
			let tokens = wordInput.trimmingCharacters(in: .whitespaces).split(separator: " ")
			if let firstToken = tokens.first {
				
				let mnemonic = autocomplete[0]
				if firstToken.caseInsensitiveCompare(mnemonic) == .orderedSame {
					
					if !isProcessingPaste && tokens.count > 1 {
					
						// Paste processsing triggered:
						// - there are multiple tokens in the wordList
						// - the first token is an exact match
						// - let's process each token until we reach the end or a non-match
						
						isProcessingPaste = true
						log.debug("isProcessingPaste = true")
					}
					
					if isProcessingPaste {
						
						selectMnemonic(mnemonic)
						return true
					}
				}
			}
		}
		
		if isAutocompleteTrigger && isProcessingPaste {
			isProcessingPaste = false
			log.debug("isProcessingPaste = false")
		}
		
		return false
	}
	
	func selectMnemonic(_ word: String) -> Void {
		log.trace("selectMnemonic()")
		
		if (mnemonics.count < 12) {
			mnemonics.append(word)
		}
		
		let tokens = wordInput.trimmingCharacters(in: .whitespaces).split(separator: " ")
		if let token = tokens.first,
		   let range = wordInput.range(of: token)
		{
			wordInput.removeSubrange(wordInput.startIndex ..< range.upperBound)
			wordInput = wordInput.trimmingCharacters(in: .whitespaces)
			
		} else {
			wordInput = ""
		}
		
		log.debug("remaining wordInput: \"\(wordInput)\"")
		updateAutocompleteList()
	}
	
	func onImportButtonTapped() -> Void {
		
		postIntent(RestoreWallet.IntentValidate(mnemonics: self.mnemonics))
	}
}

class RestoreWalletView_Previews: PreviewProvider {
//    static let mockModel = RestoreWallet.ModelReady()
    static let mockModel = RestoreWallet.ModelInvalidMnemonics()
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access"])
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account"])
//    static let mockModel = RestoreWallet.ModelFilteredWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid"])

    static var previews: some View {
        mockView(RestoreWalletView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
