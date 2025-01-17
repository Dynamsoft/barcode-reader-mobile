//
//  BarcodeScannerViewController.swift
//  DynamsoftBarcodeReaderBundle
//
//  Copyright © Dynamsoft Corporation.  All rights reserved.
//

import DynamsoftCore
import DynamsoftCameraEnhancer
import DynamsoftCaptureVisionRouter
import DynamsoftBarcodeReader
import DynamsoftLicense

@objc(DSBarcodeScannerViewController)
public class BarcodeScannerViewController: UIViewController {
    
    let dce = CameraEnhancer()
    let cameraView = CameraView()
    let cvr = CaptureVisionRouter()
    let radius = 20.0
    var tupleArray:[(CGPoint, BarcodeScanResult)] = .init()
    @objc public var config: BarcodeScannerConfig = .init()
    @objc public var onScannedResult: ((BarcodeScanResult) -> Void)?
    
    public override func viewDidLoad() {
        super.viewDidLoad()
        setupLicense()
        setupDCV()
        setupUI()
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        dce.open()
        var name = PresetTemplate.readBarcodes.rawValue
        if let path = config.templateFilePath {
            do {
                try cvr.initSettingsFromFile(path)
                name = ""
            } catch let error as NSError {
                self.onScannedResult?(.init(resultStatus: .exception, errorCode: error.code, errorString: error.localizedDescription))
                return
            }
        } else {
            let settings = try! cvr.getSimplifiedSettings(name)
            settings.barcodeSettings?.barcodeFormatIds = config.barcodeFormats
            try! cvr.updateSettings(name, settings: settings)
        }
        cvr.startCapturing(name) { isSuccess, error in
            if let error = error as? NSError, !isSuccess {
                self.onScannedResult?(.init(resultStatus: .exception, errorCode: error.code, errorString: error.localizedDescription))
            }
        }
    }
    
    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stop()
    }
    
    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        cameraView.scanLaserVisible = config.isScanLaserVisible
    }
    
    lazy var closeButton: UIButton = {
        let bundle = Bundle(for: type(of: self))
        let button = UIButton()
        let closeImage = UIImage(named: "close", in: bundle, compatibleWith: nil)
        button.setImage(closeImage?.withRenderingMode(.alwaysOriginal), for: .normal)
        button.addTarget(self, action: #selector(onCloseButtonTouchUp), for: .touchUpInside)
        return button
    }()
    
    lazy var torchButton: UIButton = {
        let bundle = Bundle(for: type(of: self))
        let button = UIButton()
        let torchOffImage = UIImage(named: "torchOff", in: bundle, compatibleWith: nil)
        let torchOnImage = UIImage(named: "torchOn", in: bundle, compatibleWith: nil)
        button.setImage(torchOffImage?.withRenderingMode(.alwaysOriginal), for: .normal)
        button.setImage(torchOnImage?.withRenderingMode(.alwaysOriginal), for: .selected)
        button.addTarget(self, action: #selector(onTorchButtonTouchUp), for: .touchUpInside)
        return button
    }()
    
    lazy var cameraButton: UIButton = {
        let bundle = Bundle(for: type(of: self))
        let button = UIButton()
        let switchCameraImage = UIImage(named: "switchCamera", in: bundle, compatibleWith: nil)
        button.setImage(switchCameraImage?.withRenderingMode(.alwaysOriginal), for: .normal)
        button.addTarget(self, action: #selector(onCameraButtonTouchUp), for: .touchUpInside)
        return button
    }()
}

extension BarcodeScannerViewController {
    private func setupDCV() {
        cameraView.frame = view.bounds
        cameraView.autoresizingMask = [.flexibleHeight, .flexibleWidth]
        view.insertSubview(cameraView, at: 0)
        dce.cameraView = cameraView
        try! cvr.setInput(dce)
        cvr.addResultReceiver(self)
        if config.isAutoZoomEnabled {
            dce.enableEnhancedFeatures(.autoZoom)
        }
        dce.setCameraStateListener(self)
        let layer = cameraView.getDrawingLayer(DrawingLayerId.DBR.rawValue)
        layer?.visible = false
    }
    
    private func setupUI() {
        closeButton.isHidden = !config.isCloseButtonVisible
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeButton)
        
        torchButton.isHidden = !config.isTorchButtonVisible
        torchButton.translatesAutoresizingMaskIntoConstraints = false
        
        cameraButton.isHidden = !config.isCameraToggleButtonVisible
        cameraButton.translatesAutoresizingMaskIntoConstraints = false
        
        let stackView = UIStackView(arrangedSubviews: [torchButton, cameraButton])
        stackView.axis = .horizontal
        stackView.spacing = 16
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        
        let safeArea = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: safeArea.leadingAnchor, constant: 20),
            closeButton.topAnchor.constraint(equalTo: safeArea.topAnchor, constant: 20),
            
            stackView.centerXAnchor.constraint(equalTo: safeArea.centerXAnchor),
            stackView.bottomAnchor.constraint(equalTo: safeArea.bottomAnchor, constant: -50),
        ])
    }
    
    private func stop() {
        cvr.stopCapturing()
        dce.close()
        dce.clearBuffer()
    }
    
    @objc func onCloseButtonTouchUp() {
        stop()
        onScannedResult?(.init(resultStatus: .canceled))
    }
    
    @objc func onTorchButtonTouchUp(_ sender: Any) {
        guard let button = sender as? UIButton else { return }
        button.isSelected.toggle()
        if button.isSelected {
            dce.turnOnTorch()
        } else {
            dce.turnOffTorch()
        }
    }
    
    @objc func onCameraButtonTouchUp() {
        let position = dce.getCameraPosition()
        switch position {
        case .back, .backDualWideAuto, .backUltraWide:
            try? dce.selectCamera(with: .front)
            torchButton.isHidden = true
            torchButton.isSelected = false
        case .front:
            try? dce.selectCamera(with: .back)
            torchButton.isHidden = !config.isTorchButtonVisible
        @unknown default:
            try? dce.selectCamera(with: .back)
            torchButton.isHidden = !config.isTorchButtonVisible
        }
    }
}

extension BarcodeScannerViewController: CapturedResultReceiver {
    public func onDecodedBarcodesReceived(_ result: DecodedBarcodesResult) {
            handleSingleResult(result)
    }
    
    private func handleSingleResult(_ result: DecodedBarcodesResult) {
        guard let items = result.items, items.count > 0 else { return }
        stop()
        if config.isBeepEnabled {
            Feedback.beep()
        }
        if items.count == 1 {
            if let item = items.first {
                onScannedResult?(.init(resultStatus: .finished, barcodes: [item]))
            }
        } else {
            let layer = cameraView.createDrawingLayer()
            let drawingStyleId = DrawingStyleManager.createDrawingStyle(.white, strokeWidth: 3.0, fill: .systemGreen, textColor: .white, font: UIFont.systemFont(ofSize: 15.0))
            var drawingItems:[DrawingItem] = []
            for item in items {
                let point = dce.convertPointToViewCoordinates(item.location.centrePoint)
                tupleArray.append((point, .init(resultStatus: .finished, barcodes: [item])))
                let arcItem = ArcDrawingItem(centre: point, radius: radius)
                arcItem.coordinateBase = .view
                arcItem.drawingStyleId = drawingStyleId
                drawingItems.append(arcItem)
            }
            layer.drawingItems = drawingItems
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
            DispatchQueue.main.async {
                self.cameraView.addGestureRecognizer(tapGesture)
            }
        }
    }
    
    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let tapLocation = gesture.location(in: view)
        for tuple in tupleArray {
            let distance = hypot(tapLocation.x - tuple.0.x, tapLocation.y - tuple.0.y)
            if distance <= radius {
                onScannedResult?(tuple.1)
                return
            }
        }
    }
}

extension BarcodeScannerViewController: CameraStateListener {
    public func onCameraStateChanged(_ currentState: CameraState) {
        if currentState == .opened {
            if let rect = config.scanRegion {
                try? dce.setScanRegion(rect)
            }
        } else if currentState == .closed {
            try? dce.setScanRegion(nil)
            DispatchQueue.main.async {
                self.cameraView.scanLaserVisible = false
            }
        }
    }
}

extension BarcodeScannerViewController: LicenseVerificationListener {
    
    private func setupLicense() {
        if let license = config.license {
            LicenseManager.initLicense(license, verificationDelegate: self)
        }
    }
    
    public func onLicenseVerified(_ isSuccess: Bool, error: (any Error)?) {
        
    }
}
