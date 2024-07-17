import SwiftUI

struct CameraPicker: UIViewControllerRepresentable {
	@Binding var result: PickerResult?
	
	func makeUIViewController(context: Context) -> UIImagePickerController {
		let imagePicker = UIImagePickerController()
		imagePicker.sourceType = .camera
		imagePicker.allowsEditing = true
		imagePicker.delegate = context.coordinator
		return imagePicker
	}
	
	func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {
	}

	func makeCoordinator() -> Coordinator {
		return Coordinator(self)
	}
	
	class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
		let parent: CameraPicker
		
		init(_ parent: CameraPicker) {
			self.parent = parent
		}
		
		func imagePickerController(
			_ picker: UIImagePickerController,
			didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]
		) {
			picker.dismiss(animated: true)
			
			if let image = info[.originalImage] as? UIImage {
				let result = PickerResult(image: image, file: nil)
				self.parent.result = result
			}
		}
	}
}
