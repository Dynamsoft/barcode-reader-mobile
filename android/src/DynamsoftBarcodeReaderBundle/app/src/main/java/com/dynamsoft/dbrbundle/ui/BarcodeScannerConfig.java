package com.dynamsoft.dbrbundle.ui;

import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.dbr.EnumBarcodeFormat;

/**
 * @author: dynamsoft
 * Time: 2024/10/8
 * Description:
 */
public final class BarcodeScannerConfig {
	public static final String TAG = "BarcodeScannerConfiguration";
	private boolean isFlashButtonVisible = true;
	private boolean isBeepEnabled;
	private boolean isScanLaserVisible = true;
	private boolean isAutoZoomEnabled;
	private boolean isCloseButtonVisible = true;
	private long format = EnumBarcodeFormat.BF_DEFAULT;
	private String templateFilePath;
	private String license;
	private DSRect dsRect;

	public boolean isTorchButtonVisible() {
		return isFlashButtonVisible;
	}

	public void setTorchButtonVisible(boolean flashButtonVisible) {
		isFlashButtonVisible = flashButtonVisible;
	}

	public boolean isBeepEnabled() {
		return isBeepEnabled;
	}

	public void setBeepEnabled(boolean beepEnabled) {
		isBeepEnabled = beepEnabled;
	}

	public boolean isScanLaserVisible() {
		return isScanLaserVisible;
	}

	public void setScanLaserVisible(boolean scanLaserVisible) {
		isScanLaserVisible = scanLaserVisible;
	}

	public boolean isAutoZoomEnabled() {
		return isAutoZoomEnabled;
	}

	public void setAutoZoomEnabled(boolean autoZoomEnabled) {
		isAutoZoomEnabled = autoZoomEnabled;
	}

	public boolean isCloseButtonVisible() {
		return isCloseButtonVisible;
	}

	public void setCloseButtonVisible(boolean closeButtonVisible) {
		isCloseButtonVisible = closeButtonVisible;
	}

	@EnumBarcodeFormat
	public long getBarcodeFormats() {
		return format;
	}

	public void setBarcodeFormats(long format) {
		this.format = format;
	}

	public String getTemplateFilePath() {
		return templateFilePath;
	}

	public void setTemplateFilePath(String templateFilePath) {
		this.templateFilePath = templateFilePath;
	}

	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public DSRect getScanRegion(){
		return dsRect;
	}
	public void setScanRegion(DSRect scanRegion){
		this.dsRect = scanRegion;
	}
}
