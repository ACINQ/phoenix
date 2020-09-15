import SwiftUI
import Combine


struct Popup<Content: View>: View {

    @Binding var show: Bool
    let content: Content

    let closeable: Bool
    let onOk: () -> Void

    @State var keyboardHeight: CGFloat = 0

    init(show: Binding<Bool>, closeable: Bool = true, onOk: @escaping () -> Void = {}, @ViewBuilder content: @escaping () -> Content) {
        self._show = show
        self.closeable = closeable
        self.onOk = onOk
        self.content = content()
    }

    var body: some View {
        Group {
            if (show) {
                VStack {
                    VStack(alignment: .leading) {
                        content
                        HStack {
                            Spacer()
                            Button("OK") {
                                onOk()
                                withAnimation { show = false }
                            }
                                    .font(.title2)
                                    .disabled(!closeable)
                        }
                                .padding()

                    }
                            .frame(maxWidth: .infinity)
                            .background(Color.white)
                            .cornerRadius(15)
                            .padding(32)
                            .padding([.bottom], keyboardHeight * 0.8)
                }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black.opacity(0.25))
                        .edgesIgnoringSafeArea(.all)
                        .zIndex(1000)
                        .transition(.opacity)
            }
        }
                .onReceive(Publishers.keyboardHeight) { kh in withAnimation(.easeOut(duration: 0.3)) { keyboardHeight = kh } }
    }

}
