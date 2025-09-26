import SwiftUI

struct ActivityView: UIViewControllerRepresentable {

	let activityItems: [Any]
	let applicationActivities: [UIActivity]?

	func makeUIViewController(
		context: UIViewControllerRepresentableContext<ActivityView>
	) -> UIActivityViewController {
		UIActivityViewController(
			activityItems: activityItems,
			applicationActivities: applicationActivities
		)
	}
	
	func updateUIViewController(
		_ uiViewController: UIActivityViewController,
		context: UIViewControllerRepresentableContext<ActivityView>
	) {}
}

extension View {
	
	func sharing<T>(_ value: Binding<T?>) -> some View {
		
		sheet(isPresented: Binding(
			get: { value.wrappedValue != nil },
			set: { if !$0 { value.wrappedValue = nil } }
		)) {
			ActivityView(
				activityItems: [value.wrappedValue!] as [Any],
				applicationActivities: nil
			)
		}
	}
}
