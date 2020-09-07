import SwiftUI
import PhoenixShared

struct RestoreWalletView: MVIView {
    typealias Model = RestoreWallet.Model
    typealias Intent = RestoreWallet.Intent

    var body: some View {
        mvi { model, intent in
            view(model: model, intent: intent)
                    .padding(.top, keyWindow?.safeAreaInsets.bottom)
                    .padding(.bottom, keyWindow?.safeAreaInsets.top)
                    .padding([.leading, .trailing], 10)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea([.bottom, .leading, .trailing])
        }
                .navigationBarTitle("Restore my wallet", displayMode: .inline)
    }

    @ViewBuilder func view(model: RestoreWallet.Model, intent: @escaping IntentReceiver) -> some View {
        
        if let _ = model as? RestoreWallet.ModelWarning {
            WarningView(intent: intent)
            .zIndex(1)
            .transition(.move(edge: .bottom))
            .animation(.default)
        } else {
            RestoreView(model: model, intent: intent)
            .zIndex(0)
        }
    }

    struct WarningView: View {
        let intent: IntentReceiver

        @State private var warningAccepted = false

        var body: some View {
          VStack {
            Text("""
                 Do not import a seed that was NOT 
                 created by this application.

                 Also, make sure that you don't have 
                 another Phoenix wallet running with the
                 same seed.
                 """)
                    .font(.title3)
                    .padding()

            Toggle(isOn: $warningAccepted) {
                Text("I understand.").font(.title3)
            }
                    .padding([.top, .bottom], 16)
                    .padding([.leading, .trailing], 88)

            Button {
                intent(RestoreWallet.IntentAcceptWarning())
            } label: {
                HStack {
                    Image("ic_arrow_next")
                            .resizable()
                            .frame(width: 16, height: 16)
                    Text("Next")
                            .font(.title2)
                }
            }
                    .disabled(!$warningAccepted.wrappedValue)
                    .buttonStyle(PlainButtonStyle())
                    .padding([.top, .bottom], 8)
                    .padding([.leading, .trailing], 16)
                    .background(Color.white)
                    .cornerRadius(16)
                    .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.appHorizon, lineWidth: 2)
                    )

            Spacer()
          }
          .background(Color.appBackground)
        }
    }

    struct RestoreView: View {
        let model: RestoreWallet.Model
        let intent: IntentReceiver


        @State private var wordInput: String = ""
        @State private var mnemonics = [String]()

        var body: some View {
            VStack {
                Text("Your wallet's seed is a list of 12 english words.")
                        .font(.title3)

                TextField("Enter keywords from your seed", text: $wordInput).onChange(of: wordInput) { input in
                            intent(RestoreWallet.IntentFilterWordList(predicate: input))
                        }
                        .padding()
                        .disableAutocorrection(true)
                        .disabled(mnemonics.count == 12)

                ScrollView(.horizontal) {
                    LazyHStack {
                        if model is RestoreWallet.ModelWordlist && mnemonics.count < 12 {
                            ForEach((model as! RestoreWallet.ModelWordlist).words, id: \.self) { word in
                                Text(word)
                                        .underline()
                                        .frame(maxWidth: .infinity) // Hack to be able to tap ...
                                        .background(Color.appBackground) // ... everywhere in the row
                                        .onTapGesture {
                                            mnemonics.append(word)
                                            wordInput = ""
                                        }

                            }
                        }
                    }
                }
                        .frame(height: 32)
                        .padding([.leading, .trailing])

                Divider()
                        .padding()

                HStack(spacing: 0) {
                    ForEach([0..<6, 6..<12], id: \.self) { range in
                        VStack(spacing: 0) {
                            ForEach(range, id: \.self) { index in
                                HStack {
                                    Text("#\(index + 1) \(mnemonics.count > index ? mnemonics[index] : " _ ")")
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .padding([.leading, .trailing], 16)

                                    Button {
                                        mnemonics.removeSubrange(index..<mnemonics.count)
                                    } label: {
                                        HStack {
                                            Image("ic_cross")
                                                    .resizable()
                                                    .frame(width: 24, height: 24)
                                                    .foregroundColor(Color.red)
                                        }
                                    }
                                            .isHidden(mnemonics.count <= index)
                                }
                            }
                        }
                    }
                }
                        .padding(.bottom)

                if model is RestoreWallet.ModelInvalidSeed {
                    Text("""
                         This seed is invalid and cannot be imported.

                         Please try again
                         """)
                            .padding()
                            .foregroundColor(Color.red)
                }

                Spacer()

                Button {
                    intent(RestoreWallet.IntentValidateSeed(mnemonics: self.mnemonics))
                } label: {
                    HStack {
                        Image("ic_check_circle")
                                .resizable()
                                .frame(width: 16, height: 16)
                        Text("Import")
                                .font(.title2)
                    }
                }
                        .disabled(mnemonics.count != 12)
                        .buttonStyle(PlainButtonStyle())
                        .padding([.top, .bottom], 8)
                        .padding([.leading, .trailing], 16)
                        .background(Color.white)
                        .cornerRadius(16)
                        .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                        .stroke(Color.appHorizon, lineWidth: 2)
                        )

            }
                    .frame(maxHeight: .infinity)
        }
    }
}

class RestoreWalletView_Previews: PreviewProvider {
//    static let mockModel = RestoreWallet.ModelReady()
    static let mockModel = RestoreWallet.ModelInvalidSeed()
//    static let mockModel = RestoreWallet.ModelWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access"])
//    static let mockModel = RestoreWallet.ModelWordlist(words: ["abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account"])
//    static let mockModel = RestoreWallet.ModelWordlist(words: ["abc", "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid"])

    static var previews: some View {
        mockView(RestoreWalletView()) {
            $0.restoreWalletModel = mockModel
        }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
