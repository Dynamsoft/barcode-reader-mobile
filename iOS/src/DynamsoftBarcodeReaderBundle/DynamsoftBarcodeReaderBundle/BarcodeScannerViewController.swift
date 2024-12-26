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
    let button = UIButton(type: .system)
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
    
    public override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        let frame = view.bounds
        let orientation = UIDevice.current.orientation
        if orientation.isLandscape {
            button.frame = CGRect(x: 50, y: 20, width: 50, height: 50)
            cameraView.setTorchButton(frame: CGRect(x: frame.width / 2 - 25, y: frame.height - 100, width: 50, height: 50), torchOnImage: nil, torchOffImage: nil)
        } else if orientation.isPortrait {
            button.frame = CGRect(x: 20, y: 50, width: 50, height: 50)
            cameraView.setTorchButton(frame: CGRect(x: frame.width / 2 - 25, y: frame.height - 150, width: 50, height: 50), torchOnImage: nil, torchOffImage: nil)
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
        let frame = view.bounds
        cameraView.setTorchButton(frame: CGRect(x: frame.width / 2 - 25, y: frame.height - 150, width: 50, height: 50), torchOnImage: nil, torchOffImage: nil)
        cameraView.torchButtonVisible = config.isTorchButtonVisible
        
        let bundle = Bundle(for: type(of: self))
        let close = UIImage(named: "close", in: bundle, compatibleWith: nil)
        button.setImage(close?.withRenderingMode(.alwaysOriginal), for: .normal)
        button.frame = CGRect(x: 20, y: 50, width: 50, height: 50)
        button.addTarget(self, action: #selector(buttonClicked), for: .touchUpInside)
        button.isHidden = !config.isCloseButtonVisible
        view.addSubview(button)
    }
    
    private func stop() {
        cvr.stopCapturing()
        dce.close()
        dce.clearBuffer()
    }
    
    @objc func buttonClicked() {
        stop()
        onScannedResult?(.init(resultStatus: .canceled))
    }
}

extension BarcodeScannerViewController: CapturedResultReceiver {
    public func onDecodedBarcodesReceived(_ result: DecodedBarcodesResult) {
        if let items = result.items, items.count > 0 {
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
