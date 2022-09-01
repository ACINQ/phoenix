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


struct ManualRestoreView: MVIView {

	@StateObject var mvi = MVIState({ $0.restoreWallet() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var acceptedWarning = false
	@State var mnemonics = [String]()
	@State var autocomplete = [String]()
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			// Animation Note:
			// We have 2 options:
			//
			// 1. Put the background here, in this layer.
			//    Which means when the WarningView dimisses (slides down vertically),
			//    then only the content (text & buttons) are moving.
			//    And the background stays in place.
			//
			// 2. Put the background in each layer below (WarningView & RestoreView).
			//    Which would mean when the WarningView dismisses (slides down vertically),
			//    then you see the background dismiss with it.
			//    Only to be replaced with the exact same background.
			//
			// In testing, I think #1 looks cleaner.
			//
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}
			
			main
				.frame(maxWidth: deviceInfo.textColumnMaxWidth)
		}
		.navigationBarTitle(
			NSLocalizedString("Manual restore", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onChange(of: mvi.model, perform: { model in
			onModelChange(model: model)
		})
	}

	@ViewBuilder
	var main: some View {
		
		if !acceptedWarning {
			WarningView(acceptedWarning: $acceptedWarning)
				.zIndex(1)
				.transition(.move(edge: .bottom))
		} else {
			EnterSeedView(
				mvi: mvi,
				mnemonics: $mnemonics,
				autocomplete: $autocomplete
			)
			.zIndex(0)
		}
	}
	
	func onModelChange(model: RestoreWallet.Model) -> Void {
		log.trace("onModelChange()")
		
		if let model = model as? RestoreWallet.ModelValidMnemonics {
			finishAndRestoreWallet(model: model)
		}
	}
	
	func finishAndRestoreWallet(model: RestoreWallet.ModelValidMnemonics) -> Void {
		log.trace("finishAndRestoreWallet()")
		
		AppSecurity.shared.addKeychainEntry(mnemonics: mnemonics) { (error: Error?) in
			if error == nil {
				AppDelegate.get().loadWallet(
					mnemonics: mnemonics,
					seed: model.seed,
					walletRestoreType: .fromManualEntry
				)
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
				"""
				Do not import a seed that was NOT created by this application.
				
				Also, make sure that you don't have another Phoenix wallet running with the same seed.
				"""
			)
			.padding(.top, 30)
			
			HStack {
				Spacer()
				Text("I understand")
					.font(.headline)
					.padding(.trailing, 10)
				Toggle("", isOn: $iUnderstand).labelsHidden()
				Spacer()
			}
			.padding([.top, .bottom], 16)
			.accessibilityElement(children: .combine)

			Button {
				withAnimation {
					acceptedWarning = true
				}
			} label: {
				HStack {
					Text("Next")
					Image(systemName: "checkmark")
						.imageScale(.medium)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.disabled(!iUnderstand)
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 16,
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent,
					disabledBorderStroke: Color(UIColor.separator)
				)
			)
			.accessibilityElement()
			.accessibilityLabel("Next")
			
			Spacer()
			
		} // </VStack>
		.padding([.leading, .trailing])
	}
}

struct EnterSeedView: View {
	
	@ObservedObject var mvi: MVIState<RestoreWallet.Model, RestoreWallet.Intent>

	@Binding var mnemonics: [String]
	@Binding var autocomplete: [String]
	
	@State var wordInput: String = ""
	@State var isProcessingPaste = false
	
	@Namespace var topID
	@Namespace var inputID
	let keyboardWillShow = NotificationCenter.default.publisher(for:
		UIApplication.keyboardWillShowNotification
	)
	let keyboardDidHide = NotificationCenter.default.publisher(for:
		UIApplication.keyboardDidHideNotification
	)
	
	let maxAutocompleteCount = 12
	
	var body: some View {
		
		ScrollViewReader { scrollViewProxy in
			ScrollView {
				ZStack {
					
					// This interfers with tapping on buttons.
					// It only seems to affect the device (not the simulator)
					//
					// A better solution is to use keyboardDismissMode, as below.
					//
//					Color(UIColor.clear)
//						.frame(maxWidth: .infinity, maxHeight: .infinity)
//						.contentShape(Rectangle())
//						.onTapGesture {
//							// dismiss keyboard if visible
//							let keyWindow = UIApplication.shared.connectedScenes
//								.filter({ $0.activationState == .foregroundActive })
//								.map({ $0 as? UIWindowScene })
//								.compactMap({ $0 })
//								.first?.windows
//								.filter({ $0.isKeyWindow }).first
//							keyWindow?.endEditing(true)
//						}
					
					main(scrollViewProxy)
				}
			}
		}
		.onAppear {
			UIScrollView.appearance().keyboardDismissMode = .interactive
		}
	}
	
	@ViewBuilder
	func main(_ scrollViewProxy: ScrollViewProxy) -> some View {
	
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {

			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Text("Your wallet's seed is a list of 12 English words.")
					.id(topID)
				Spacer()
			}
			.padding([.leading, .trailing], 16)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(
					NSLocalizedString("Enter keywords from your seed", comment: "TextField placeholder"),
					text: $wordInput
				)
				.onChange(of: wordInput) { _ in
					onInput()
				}
				.autocapitalization(.none)
				.disableAutocorrection(true)
				.disabled(mnemonics.count == 12)
				.padding(.trailing, 4)
				
				// Clear button (appears when TextField's text is non-empty)
				Button {
					wordInput = ""
				} label: {
					Image(systemName: "multiply.circle.fill")
						.foregroundColor(.secondary)
				}
				.isHidden(wordInput == "")
			}
			.padding([.top, .bottom], 8)
			.padding(.leading, 16)
			.padding(.trailing, 8)
			.background(Color.primaryBackground)
			.cornerRadius(100)
			.overlay(
				RoundedRectangle(cornerRadius: 16)
					.stroke(Color.textFieldBorder, lineWidth: 1.5)
			)
			.padding(.top)
			.id(inputID)

			// Autocomplete suggestions for mnemonics
			ScrollView(.horizontal) {
				LazyHStack {
					if autocomplete.count < maxAutocompleteCount {
						ForEach(autocomplete, id: \.self) { word in

							Button {
								selectMnemonic(word)
							} label: {
								Text(word)
									.underline()
									.foregroundColor(Color.primary)
							}
						}
					}
				}
			}
			.frame(height: 32)

			Divider().padding(.bottom)
			
			mnemonicsList()

			if mvi.model is RestoreWallet.ModelInvalidMnemonics {
				Text(
					"""
					This seed is invalid and cannot be imported.
					
					Please try again
					"""
				)
				.padding([.top, .bottom])
				.foregroundColor(Color.red)
			}

			HStack { // Center button using: HStack {[space, button, space]}
				Spacer()
				
				Button {
					onImportButtonTapped()
				} label: {
					HStack {
						Text("Import")
						Image(systemName: "checkmark.circle")
							.imageScale(.medium)
					}
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
				}
				.disabled(mnemonics.count != 12)
				.buttonStyle(
					ScaleButtonStyle(
						cornerRadius: 16,
						backgroundFill: Color.primaryBackground,
						borderStroke: Color.appAccent,
						disabledBorderStroke: Color(UIColor.separator)
					)
				)
				
				Spacer()
			}
			.padding(.top)
			
		} // </VStack>
		.padding(.top, 30)
		.padding([.leading, .trailing], 20)
		.padding(.bottom) // buffer space at bottom of scrollView
		.onChange(of: mvi.model, perform: { model in
			onModelChange(model: model)
		})
		.onChange(of: autocomplete) { _ in
			onAutocompleteListChanged()
		}
		.onReceive(keyboardWillShow) { notification in
			// Maybe add this for smaller phones like iPhone 8:
		//	withAnimation {
		//		scrollViewProxy.scrollTo(inputID, anchor: .top)
		//	}
		}
		.onReceive(keyboardDidHide) { _ in
		//	withAnimation {
		//		scrollViewProxy.scrollTo(topID, anchor: .top)
		//	}
		}
	}
	
	@ViewBuilder
	func mnemonicsList() -> some View {
		
		// Design:
		//
		// #1 hammer  (x) #7 bacon  (x)
		// #2 fat     (x) #8 animal (x)
		// ...
		//
		// Architecture:
		//
		// There are 3 ways to layout the design:
		//
		// 1) VStack of HStack's
		//
		// So each row is its own HStack.
		// And VStack puts all the rows together.
		//
		// VStack {
		//   ForEach {
		//     HStack {
		//       {} #1
		//       {} hammer (x)
		//       {} #7
		//       {} bacon (x)
		//     }
		//   }
		// }
		//
		// The problem with this design is that items aren't aligned vertically.
		// For example, it will end up looking like this:
		//
		// #9 bacon
		// #10 animal
		//
		// What we want is for "bacon" & "animal" to be aligned vertically.
		// But we can't get that with this design unless we:
		// - hardcode the width of the number-row (difficult to do with adaptive text sizes)
		// - use tricks to calculate the proper width based on current font size (possible option)
		//
		// 2) HStack of VStack's
		//
		// So each column is its own VStack.
		// And HStack puts all the columns together.
		//
		// HStack {
		//   VStack { ForEach{} } // column: #1
		//   VStack { ForEach{} } // column: hammer (x)
		//   VStack { ForEach{} } // column: #7
		//   VStack { ForEach{} } // column: bacon (x)
		// }
		//
		// This fixes the vertical alignment problem with the previous design.
		// But we have to be very careful to ensure that each VStack is the same height.
		// This works if:
		// - we use the same font size for all text
		// - the button size is <= the font size
		//
		// 3) Use a LazyVGrid and design the columns manually
		//
		// let columns: [GridItem] = [
		//   GridItem(.flexible(?), spacing: 2),
		//   GridItem(.flexible(?), spacing: 8),
		//   GridItem(.flexible(?), spacing: 2),
		//   GridItem(.flexible(?), spacing: 0)
		// ]
		//
		// The problem is that GridItem isn't very flexible.
		// What we want to say is:
		// - make columns [0, 2] the minimum possible size
		// - make columns [1, 3] the remainder
		//
		// But that's not an option. We have to define the min & max width.
		// So we're back to the exact same problem we had with design #1.
		//
		// Thus, we're going with design #2 for now.
		
		let row_bottomSpacing: CGFloat = 6
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			// #1
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0..<6, id: \.self) { idx in
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text(verbatim: "#\(idx + 1) ")
							.font(Font.headline.weight(.regular))
							.foregroundColor(Color(UIColor.tertiaryLabel))
					}
					.padding(.bottom, row_bottomSpacing)
				}
			}
			.padding(.trailing, 2)
			
			// hammer (x)
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(0..<6, id: \.self) { idx in
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text(mnemonic(idx))
							.font(.headline)
							.frame(maxWidth: .infinity, alignment: .leading)
						
						Button {
							mnemonics.removeSubrange(idx..<mnemonics.count)
						} label: {
							Image(systemName: "xmark")
								.font(Font.caption.weight(.thin))
								.foregroundColor(Color(UIColor.tertiaryLabel))
						}
						.isHidden(mnemonics.count <= idx)
					}
					.padding(.bottom, row_bottomSpacing)
				}
			}
			.padding(.trailing, 8)
			
			// #7
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(6..<12, id: \.self) { idx in
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text(verbatim: "#\(idx + 1) ")
							.font(Font.headline.weight(.regular))
							.foregroundColor(Color(UIColor.tertiaryLabel))
					}
					.padding(.bottom, row_bottomSpacing)
				}
			}
			.padding(.trailing, 2)
			
			// bacon (x)
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				ForEach(6..<12, id: \.self) { idx in
					HStack(alignment: VerticalAlignment.center, spacing: 0) {
						Text(mnemonic(idx ))
							.font(.headline)
							.frame(maxWidth: .infinity, alignment: .leading)
						
						Button {
							mnemonics.removeSubrange(idx..<mnemonics.count)
						} label: {
							Image(systemName: "xmark")
								.font(Font.caption.weight(.thin))
								.foregroundColor(Color(UIColor.tertiaryLabel))
						}
						.isHidden(mnemonics.count <= idx)
					}
					.padding(.bottom, row_bottomSpacing)
				}
			}
		}
		.environment(\.layoutDirection, .leftToRight) // issue #237
	}
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	func onModelChange(model: RestoreWallet.Model) -> Void {
		log.trace("[RestoreView] onModelChange()")
		
		if let model = model as? RestoreWallet.ModelFilteredWordlist {
			log.debug("ModelFilteredWordlist.words = \(model.words)")
			if autocomplete == model.words {
				// Careful:
				// autocomplete = model.words
				// ^ this won't do anything. Will not call: onAutocompleteListChanged()
				//
				// So we need to do it manually.
				// For more info, see issue #109
				//
				onAutocompleteListChanged()
			} else {
				autocomplete = model.words
			}
		}
	}
	
	func onInput() -> Void {
		log.trace("[RestoreView] onInput(): \"\(wordInput)\"")
		
		// When the user hits space, we auto-accept the first mnemonic in the autocomplete list
		if maybeSelectMnemonic(isAutocompleteTrigger: false) {
			return
		} else {
			updateAutocompleteList()
		}
	}
	
	func updateAutocompleteList() {
		log.trace("[RestoreView] updateAutocompleteList()")
		
		// Some keyboards will inject the entire word plus a space.
		//
		// For example, if using a sliding keyboard (e.g. SwiftKey),
		// then after sliding from key to key, and lifting your finger,
		// the entire word will be injected plus a space: "bacon "
		//
		// We should also consider the possibility that the user pasted in their seed.
		
		let tokens = wordInput.trimmingCharacters(in: .whitespaces).split(separator: " ")
		if let firstToken = tokens.first {
			log.debug("[RestoreView] Requesting autocomplete for: '\(firstToken)'")
			mvi.intent(RestoreWallet.IntentFilterWordList(
				predicate: String(firstToken),
				uuid: UUID().uuidString
			))
		} else {
			log.debug("[RestoreView] Clearing autocomplete list")
			mvi.intent(RestoreWallet.IntentFilterWordList(
				predicate: "",
				uuid: UUID().uuidString
			))
		}
	}
	
	func onAutocompleteListChanged() {
		log.trace("[RestoreView] onAutocompleteListChanged()")
		
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
		log.trace("[RestoreView] maybeSelectMnemonic(isAutocompleteTrigger = \(isAutocompleteTrigger))")
		
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
						log.debug("[RestoreView] isProcessingPaste = true")
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
			log.debug("[RestoreView] isProcessingPaste = false")
		}
		
		if wordInput.hasSuffix(" "),
		   let acceptedWord = autocomplete.first,
		   autocomplete.count < maxAutocompleteCount // only if autocomplete list is visible
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
		
		return false
	}
	
	func selectMnemonic(_ word: String) -> Void {
		log.trace("[RestoreView] selectMnemonic()")
		
		if (mnemonics.count < 12) {
			mnemonics.append(word)
		}
		
		if let token = wordInput.trimmingCharacters(in: .whitespaces).split(separator: " ").first,
		   let range = wordInput.range(of: token)
		{
			var input = wordInput
			input.removeSubrange(wordInput.startIndex ..< range.upperBound)
			input = input.trimmingCharacters(in: .whitespaces)
			
			log.debug("[RestoreView] Remaining wordInput: \"\(input)\"")
			wordInput = input
			
		} else {
			log.debug("[RestoreView] Remaining wordInput: \"\"")
			wordInput = ""
		}
	}
	
	func onImportButtonTapped() -> Void {
		log.trace("[RestoreView] onImportButtonTapped()")
		
		mvi.intent(RestoreWallet.IntentValidate(mnemonics: self.mnemonics))
	}
}

class ManualRestoreView_Previews: PreviewProvider {
	
	static let filteredWordList1 = [
		"abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access"
	]
	
	static let filteredWordList2 = [
		"abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account"
	]
	
	static let filteredWordList3 = [
		"abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid"
	]

	static var previews: some View {
		
		ManualRestoreView().mock(
			RestoreWallet.ModelReady()
		)
		.previewDevice("iPhone 11")
		
		ManualRestoreView().mock(
			RestoreWallet.ModelInvalidMnemonics()
		)
		.previewDevice("iPhone 11")
		
		ManualRestoreView().mock(
			RestoreWallet.ModelFilteredWordlist(uuid: "", predicate: "ab", words: filteredWordList1)
		)
		.previewDevice("iPhone 11")
		
		ManualRestoreView().mock(
			RestoreWallet.ModelFilteredWordlist(uuid: "", predicate: "ab", words: filteredWordList2)
		)
		.previewDevice("iPhone 11")
		
		ManualRestoreView().mock(
			RestoreWallet.ModelFilteredWordlist(uuid: "", predicate: "ab", words: filteredWordList3)
		)
		.previewDevice("iPhone 11")
	}
}
