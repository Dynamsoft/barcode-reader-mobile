//
//  BarcodeScannerConfig.swift
//  DynamsoftBarcodeReaderBundle
//
//  Copyright © Dynamsoft Corporation.  All rights reserved.
//

import Foundation
import DynamsoftCaptureVisionBundle

@objcMembers
@objc(DSBarcodeScannerConfig)
public class BarcodeScannerConfig: NSObject {
    public var license: String!
    public var templateFile: String?
    public var isTorchButtonVisible: Bool = true
    public var scanRegion: Rect?
    public var isBeepEnabled: Bool = false
    public var isScanLaserVisible: Bool = true
    public var isAutoZoomEnabled: Bool = false
    public var isCloseButtonVisible: Bool = true
    public var barcodeFormats: BarcodeFormat = .default
    public var scanningMode:ScanningMode = .single
    public var maxConsecutiveStableFramesToExit: Int = 10
    public var expectedBarcodesCount: Int = 999
    public var isCameraToggleButtonVisible: Bool = false
    public var zoomFactor: CGFloat?
    public var isVibrateEnabled: Bool = false
}

@objc(DSScanningMode)
public enum ScanningMode:Int {
    case single
    case multiple
}
