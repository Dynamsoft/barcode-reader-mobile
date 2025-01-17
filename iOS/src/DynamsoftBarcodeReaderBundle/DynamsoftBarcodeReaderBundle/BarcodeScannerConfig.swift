//
//  BarcodeScannerConfig.swift
//  DynamsoftBarcodeReaderBundle
//
//  Copyright © Dynamsoft Corporation.  All rights reserved.
//

import Foundation
import DynamsoftBarcodeReader

@objcMembers
@objc(DSBarcodeScannerConfig)
public class BarcodeScannerConfig: NSObject {
    public var license: String!
    public var templateFilePath: String?
    public var isTorchButtonVisible: Bool = true
    public var scanRegion: Rect?
    public var isBeepEnabled: Bool = false
    public var isScanLaserVisible: Bool = true
    public var isAutoZoomEnabled: Bool = false
    public var isCloseButtonVisible: Bool = true
    public var barcodeFormats: BarcodeFormat = .default
    public var isCameraToggleButtonVisible: Bool = false
}
