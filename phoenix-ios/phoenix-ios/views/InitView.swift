import SwiftUI
import PhoenixShared

struct InitView : MVIView {
    typealias Model = Init.Model
    typealias Intent = Init.Intent

    var body: some View {
        mvi { model, intent in
            VStack(spacing: 32) {
                Button {
                    intent(Init.IntentCreateWallet())
                } label: {
                    HStack {
                        Image("ic_fire")
                                .resizable()
                                .frame(width: 16, height: 16)
                                .foregroundColor(.white)
                        Text("Create new wallet")
                                .font(.title2)
                    }
                }
                        .buttonStyle(PlainButtonStyle())
                        .padding([.top, .bottom], 8)
                        .padding([.leading, .trailing], 16)
                        .background(Color.appHorizon)
                        .foregroundColor(.appBackgroundLight)
                        .cornerRadius(16)

                NavigationLink(destination: RestoreWalletView()) {
                    HStack {
                        Image("ic_restore")
                                .resizable()
                                .frame(width: 16, height: 16)
                        Text("Restore my wallet")
                                .font(.title2)
                    }
                }
                        .buttonStyle(PlainButtonStyle())
                        .padding([.top, .bottom], 8)
                        .padding([.leading, .trailing], 16)
                        .background(Color.white)
                        .cornerRadius(16)
                        .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                        .stroke(Color.appHorizon, lineWidth: 2)
                        )

                NavigationLink(
                            destination:
                            Text("Wait for it!")
                                    .navigationBarTitle("", displayMode: .inline)
                                    .navigationBarHidden(true),
                            isActive: .constant(model is Init.ModelCreating),
                            label: { EmptyView() }
                    )
            }
                    .padding(.top, keyWindow?.safeAreaInsets.top)
                    .padding(.top, keyWindow?.safeAreaInsets.bottom)
                    .padding(.top, 10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                    .edgesIgnoringSafeArea(.all)
                    .navigationBarTitle("", displayMode: .inline)
                    .navigationBarHidden(true)
        }
    }
}

class InitView_Previews : PreviewProvider {
    static let mockModel = Init.ModelInitialization()

    static var previews: some View {
        mockView(InitView()) { $0.initModel = mockModel }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
