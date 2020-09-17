import SwiftUI
import Combine


struct Popup<Content: View>: View {

    let content: (() -> Content)?

    @State var keyboardHeight: CGFloat = 0

    init(show: Bool, @ViewBuilder content: @escaping () -> Content) {
        if (show) {
            self.content = content
        } else {
            self.content = nil
        }
    }

    init<T>(of: T?, @ViewBuilder content: @escaping (T) -> Content) {
        if let value = of {
            self.content = { content(value) }
        } else {
            self.content = nil
        }
    }

    var body: some View {
        Group {
            if let content = self.content {
                VStack {
                    VStack(alignment: .leading) {
                        content()
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
