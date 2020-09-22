import Foundation
import AVFoundation
import UIKit
import SwiftUI


struct QrCodeScannerView: UIViewRepresentable {

    var supportedBarcodeTypes: [AVMetadataObject.ObjectType] = [.qr]

    private let session = AVCaptureSession()
    private let delegate = QrCodeCameraDelegate()
    private let metadataOutput = AVCaptureMetadataOutput()

    func found(r: @escaping (String) -> Void) -> QrCodeScannerView {
        delegate.onResult = r
        return self
    }

    func setupCamera(_ uiView: CameraPreview) {
        if let backCamera = AVCaptureDevice.default(for: AVMediaType.video) {
            if let input = try? AVCaptureDeviceInput(device: backCamera) {
                session.sessionPreset = .photo

                if session.canAddInput(input) {
                    session.addInput(input)
                }
                if session.canAddOutput(metadataOutput) {
                    session.addOutput(metadataOutput)

                    metadataOutput.metadataObjectTypes = supportedBarcodeTypes
                    metadataOutput.setMetadataObjectsDelegate(delegate, queue: DispatchQueue.main)
                }
                let previewLayer = AVCaptureVideoPreviewLayer(session: session)

                uiView.backgroundColor = UIColor.gray
                previewLayer.videoGravity = .resizeAspectFill
                uiView.layer.addSublayer(previewLayer)
                uiView.previewLayer = previewLayer

                session.startRunning()

                // Keep getting metadata from camera
                DispatchQueue.global(qos: .background).async {
                    var isActive = true
                    while(isActive) {
                        DispatchQueue.main.sync {
                            if !self.session.isInterrupted && !self.session.isRunning {
                                isActive = false
                            }
                        }
                        sleep(1)
                    }
                }
            }
        }
    }

    func makeUIView(context: UIViewRepresentableContext<QrCodeScannerView>) -> CameraPreview {
        let cameraView = CameraPreview(session: session)

        #if targetEnvironment(simulator)
        cameraView.createMessageView(message: "The simulator cannot access the camera, \n please use the clipboard.")
        #else
        checkCameraAuthorizationStatus(cameraView)
        #endif

        return cameraView
    }

    static func dismantleUIView(_ uiView: CameraPreview, coordinator: ()) {
        uiView.session.stopRunning()
    }

    private func checkCameraAuthorizationStatus(_ uiView: CameraPreview) {
        let cameraAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        let accessDeniedMessage = "to be able to scan a QR code \nyou must allow Phoenix to access the camera."

        switch cameraAuthorizationStatus {
        case .authorized: setupCamera(uiView)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.sync {
                    if granted {
                        self.setupCamera(uiView)
                    } else {
                        uiView.createMessageView(message: accessDeniedMessage)
                    }
                }
            }
        default:
            uiView.createMessageView(message: accessDeniedMessage)
        }
    }

    func updateUIView(_ uiView: CameraPreview, context: UIViewRepresentableContext<QrCodeScannerView>) {
        uiView.setContentHuggingPriority(.defaultHigh, for: .vertical)
        uiView.setContentHuggingPriority(.defaultLow, for: .horizontal)
    }
}

class CameraPreview: UIView {
    private var messageView: UIView?

    var previewLayer: AVCaptureVideoPreviewLayer?
    var session = AVCaptureSession()

    init(session: AVCaptureSession) {
        super.init(frame: .zero)
        self.session = session
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func createMessageView(message: String) {
        self.backgroundColor = UIColor.black

        messageView = UILabel(frame: self.bounds)
        if let label = messageView as? UILabel {
            label.numberOfLines = 4
            label.text = message
            label.textColor = UIColor.white
            label.textAlignment = .center
            addSubview(label)
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        messageView?.frame = self.bounds
        #if !targetEnvironment(simulator)
        previewLayer?.frame = self.bounds
        #endif
    }
}

/*
    Delegate to read from camera input
*/
class QrCodeCameraDelegate: NSObject, AVCaptureMetadataOutputObjectsDelegate {

    var scanInterval: Double = 1.0
    var lastTime = Date(timeIntervalSince1970: 0)

    var onResult: (String) -> Void = { _  in }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        if let metadataObject = metadataObjects.first {
            guard let readableObject = metadataObject as? AVMetadataMachineReadableCodeObject else { return }
            guard let stringValue = readableObject.stringValue else { return }
            foundBarcode(stringValue)
        }
    }


    func foundBarcode(_ stringValue: String) {
        let now = Date()
        // Avoid flooding controller
        if now.timeIntervalSince(lastTime) >= scanInterval {
            lastTime = now
            self.onResult(stringValue)
        }
    }
}