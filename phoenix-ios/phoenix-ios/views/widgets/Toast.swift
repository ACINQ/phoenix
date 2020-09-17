import SwiftUI

class Toast: ObservableObject {
    @Published private(set) var text: String? = nil

    func toast(text: String, duration: Double = 1.5) {
        withAnimation(.linear(duration: 0.15)) { self.text = text }
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            withAnimation(.linear(duration: 0.15)) { self.text = nil }
        }
    }

    @ViewBuilder func view() -> some View {
        if let text = text {
            VStack {
                Spacer()
                Text(text)
                        .padding()
                        .foregroundColor(.white)
                        .background(Color.appDark.opacity(0.4))
                        .cornerRadius(42)
                        .padding([.bottom], 42)
            }
                    .transition(.opacity)
                    .zIndex(1001)
        }
    }
}
