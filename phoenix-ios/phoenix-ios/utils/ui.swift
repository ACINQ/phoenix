import SwiftUI

struct RoundedCorner: Shape {

    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape( RoundedCorner(radius: radius, corners: corners) )
    }
}

extension View {
    @ViewBuilder func isHidden(_ hidden: Bool, remove: Bool = false) -> some View {
        if hidden {
            if !remove {
                self.hidden()
            }
        } else {
            self
        }
    }
}

let keyWindow = UIApplication.shared.connectedScenes
        .filter { $0.activationState == .foregroundActive }
        .compactMap {$0 as? UIWindowScene}
        .first?.windows
        .filter {$0.isKeyWindow}
        .first

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
                .sRGB,
                red: Double(r) / 255,
                green: Double(g) / 255,
                blue:  Double(b) / 255,
                opacity: Double(a) / 255
        )
    }
}

/*
extension Image {

	// DO NOT USE.
	//
	// This technique results in images that look pixelated.
	// The original problem has been solved by importing the images via the Asset catalog,
	// and enabling the "preserve vector data" option.
	// So now you can just:
	//
	// Image("name_of_image") // <- use this instead
	//
	init(vector: String) {
		let uiImage = UIImage(named: vector)!
		let size = CGSize(width: 500, height: 500)
		let scaled = UIGraphicsImageRenderer(size: size).image { _ in
			uiImage.draw(in: CGRect(origin: .zero, size: size))
		}
		self.init(uiImage: scaled)
	}
}
*/

struct ActivityView: UIViewControllerRepresentable {

    let activityItems: [Any]
    let applicationActivities: [UIActivity]?

    func makeUIViewController(context: UIViewControllerRepresentableContext<ActivityView>) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ActivityView>) {}
}

extension View {
    func sharing<T>(_ value: Binding<T?>) -> some View {
        sheet(isPresented: Binding(get: { value.wrappedValue != nil }, set: { if !$0 { value.wrappedValue = nil } })) {
            ActivityView(activityItems: [value.wrappedValue!] as [Any], applicationActivities: nil)
        }
    }
}
