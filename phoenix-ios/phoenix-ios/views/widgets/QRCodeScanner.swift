import Foundation
import AVFoundation
import UIKit
import SwiftUI
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "QRCodeScanner"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct QrCodeScannerView: UIViewControllerRepresentable {

	let callback: (String) -> Void
	let supportedBarcodeTypes: [AVMetadataObject.ObjectType] = [.qr]

	init(onResult callback: @escaping (String) -> Void) {
		log.trace("QrCodeScannerView: init")
		
		self.callback = callback
	}
	
	func makeCoordinator() -> Coordinator {
		log.trace("QrCodeScannerView: makeCoordinator")
		
		return Coordinator(callback: self.callback)
	}
	
	func makeUIViewController(context: Context) -> CameraPreviewViewController {
		log.trace("QrCodeScannerView: makeUIViewController")
		
		let vc = CameraPreviewViewController()
		
	#if targetEnvironment(simulator)
		let msg = NSLocalizedString(
			"The simulator cannot access the camera. Please use the clipboard.",
			comment: "Warning for iOS Simulator (only for devs)"
		)
		vc.cameraPreviewView.showMessageView(message: msg)
	#else
		checkCameraAuthorizationStatus(vc, context.coordinator)
	#endif

		return vc
	}
	
	private func checkCameraAuthorizationStatus(_ vc: CameraPreviewViewController, _ coordinator: Coordinator) {
		
		let cameraAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
		let accessDeniedMessage = NSLocalizedString(
			"To scan a QR code you must give Phoenix permission to access the camera.",
			comment: "Informational warning in SendView"
		)

		switch cameraAuthorizationStatus {
		case .authorized:
			setupCamera(vc, coordinator)
			
		case .notDetermined:
			AVCaptureDevice.requestAccess(for: .video) { granted in
				DispatchQueue.main.sync {
					if granted {
						self.setupCamera(vc, coordinator)
					} else {
						vc.cameraPreviewView.showMessageView(message: accessDeniedMessage)
					}
				}
			}
			
		default:
			vc.cameraPreviewView.showMessageView(message: accessDeniedMessage)
		}
	}

	private func setupCamera(_ vc: CameraPreviewViewController, _ coordinator: Coordinator) {
		
		guard let backCamera = AVCaptureDevice.default(for: AVMediaType.video) else {
			log.warning("QrCodeScannerView: AVCaptureDevice.default(for: video) failed")
			return
		}
		guard let input = try? AVCaptureDeviceInput(device: backCamera) else {
			log.warning("QrCodeScannerView: AVCaptureDeviceInput(device: backCamera) failed")
			return
		}
		
		let view = vc.cameraPreviewView
		
		view.session.sessionPreset = .photo

		if view.session.canAddInput(input) {
			view.session.addInput(input)
		}
		
		let metadataOutput = AVCaptureMetadataOutput()
		if view.session.canAddOutput(metadataOutput) {
			view.session.addOutput(metadataOutput)

			metadataOutput.metadataObjectTypes = supportedBarcodeTypes
			metadataOutput.setMetadataObjectsDelegate(coordinator, queue: DispatchQueue.main)
		}
		
		let previewLayer = AVCaptureVideoPreviewLayer(session: view.session)
		previewLayer.videoGravity = .resizeAspectFill
		
		view.backgroundColor = UIColor.gray
		view.layer.addSublayer(previewLayer)
		view.previewLayer = previewLayer

		view.session.startRunning()
	}

	func updateUIViewController(_ vc: CameraPreviewViewController, context: Context) {
		log.trace("QrCodeScannerView: updateUIView")
		
		vc.cameraPreviewView.setContentHuggingPriority(.defaultHigh, for: .vertical)
		vc.cameraPreviewView.setContentHuggingPriority(.defaultLow, for: .horizontal)
	}
	
	/// This method isn't properly called (which may be considered a bug in SwiftUI).
	///
	/// One would imagine that this method would be called during a viewDidDisappear event.
	/// But that's not what actually happens.
	///
	/// - A view is pushed onto a NavigationView with a UIViewRepresentable / UIViewControllerRepresentable
	/// - The view is then popped from the Nav stack
	/// - Does dismantle get called here? You'd think so, but you'd be wrong.
	/// - Another view is then pushed onto the Nav stack
	/// - And now dismantle gets called...
	///
	/// So we don't rely on this method.
	/// And instead use a UIViewController, so we can listen for viewDidDisappear events.
	///
	static func dismantleUIViewController(_ uiViewController: UIViewControllerType, coordinator: Coordinator) {
		log.trace("QrCodeScannerView: dismantleUIViewController")
		
	//	vc.cameraPreviewView.session.stopRunning()
	}
	
	class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
		
		let callback: (String) -> Void
		
		private var scanInterval: Double = 1.0
		private var lastTime = Date(timeIntervalSince1970: 0)
		
		init(callback: @escaping (String) -> Void) {
			log.trace("QrCodeScannerView.Coordinator: init")
			
			self.callback = callback
		}
		
		deinit {
			log.trace("QrCodeScannerView.Coordinator: deinit")
		}
		
		func metadataOutput(
			_ output: AVCaptureMetadataOutput,
			didOutput metadataObjects: [AVMetadataObject],
			from connection: AVCaptureConnection
		) -> Void {
			
			if let metadataObject = metadataObjects.first,
			   let readableObject = metadataObject as? AVMetadataMachineReadableCodeObject,
			   let stringValue = readableObject.stringValue
			{
				foundBarcode(stringValue)
			}
		}

		func foundBarcode(_ stringValue: String) {
		
			let now = Date()
			
			// Avoid flooding controller
			if now.timeIntervalSince(lastTime) >= scanInterval {
				lastTime = now
				callback(stringValue)
			}
		}
	
	} // </class Coordinator>
	
} // </struct QrCodeScannerView>

class CameraPreviewViewController: UIViewController {
	
	let session = AVCaptureSession()
	
	deinit {
		log.trace("CameraPreviewViewController: deinit")
	}
	
	var cameraPreviewView: CameraPreviewView {
		return self.view as! CameraPreviewView
	}
	
	override func loadView() {
		log.trace("CameraPreviewViewController: loadView")
		
		self.view = CameraPreviewView()
	}
	
	override func viewDidDisappear(_ animated: Bool) {
		log.trace("CameraPreviewViewController: viewDidDisappear()")
		
		super.viewDidDisappear(animated)
		self.cameraPreviewView.session.stopRunning()
	}
}

class CameraPreviewView: UIView {

	let session = AVCaptureSession()
	var previewLayer: AVCaptureVideoPreviewLayer?

	private var messageView: EdgeInsetLabel?
	
	init() {
		log.trace("CameraPreviewView: init")
		
		super.init(frame: .zero)
	}

	required init?(coder: NSCoder) {
		fatalError("CameraPreviewView: init(coder:) has not been implemented")
	}
	
	deinit {
		log.trace("CameraPreviewView: deinit")
	}

	func showMessageView(message: String) {
		self.backgroundColor = UIColor.black

		messageView = EdgeInsetLabel(frame: self.bounds)
		messageView?.textInsets = UIEdgeInsets(top: 0, left: 40, bottom: 0, right: 40)
		messageView?.numberOfLines = 4
		messageView?.text = message
		messageView?.textColor = UIColor.white
		messageView?.textAlignment = .center
		
		addSubview(messageView!)
	}

	override func layoutSubviews() {
		super.layoutSubviews()
		messageView?.frame = self.bounds
	#if !targetEnvironment(simulator)
		previewLayer?.frame = self.bounds
	#endif
	}
}

class EdgeInsetLabel: UILabel {
	var textInsets = UIEdgeInsets.zero {
		didSet { invalidateIntrinsicContentSize() }
	}

	override func textRect(forBounds bounds: CGRect, limitedToNumberOfLines numberOfLines: Int) -> CGRect {
		let textRect = super.textRect(forBounds: bounds, limitedToNumberOfLines: numberOfLines)
		let invertedInsets = UIEdgeInsets(
			top: -textInsets.top,
			left: -textInsets.left,
			bottom: -textInsets.bottom,
			right: -textInsets.right)
		
		return textRect.inset(by: invertedInsets)
	}

	override func drawText(in rect: CGRect) {
		super.drawText(in: rect.inset(by: textInsets))
	}
}
