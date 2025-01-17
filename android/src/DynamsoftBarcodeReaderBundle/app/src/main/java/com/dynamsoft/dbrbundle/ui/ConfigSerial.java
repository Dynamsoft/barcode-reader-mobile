package com.dynamsoft.dbrbundle.ui;

import com.dynamsoft.dbr.EnumBarcodeFormat;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author: dynamsoft
 * Time: 2024/11/28
 * Description:
 */
class ConfigSerial implements Serializable {
	private final boolean isFlashButtonVisible;
	private final boolean isBeepEnabled;
	private final boolean isScanLaserVisible;
	private final boolean isAutoZoomEnabled;
	private final boolean isCloseButtonVisible;
	private final long format;
	private final String templateFilePath;
	private final String license;
	private final ArrayList<Float> dsRect;
	private final boolean isCameraToggleButtonVisible;

	public ConfigSerial(boolean isFlashButtonVisible, boolean isBeepEnabled,
	                    boolean isScanLaserVisible, boolean isAutoZoomEnabled, boolean isCloseButtonVisible,
	                    long format, String templateFilePath, String license, ArrayList<Float> dsRect,
	                    boolean isCameraToggleButtonVisible) {
		this.isFlashButtonVisible = isFlashButtonVisible;
		this.isBeepEnabled = isBeepEnabled;
		this.isScanLaserVisible = isScanLaserVisible;
		this.isAutoZoomEnabled = isAutoZoomEnabled;
		this.isCloseButtonVisible = isCloseButtonVisible;
		this.format = format;
		this.templateFilePath = templateFilePath;
		this.license = license;
		this.dsRect = dsRect;
		this.isCameraToggleButtonVisible = isCameraToggleButtonVisible;
	}

	public boolean isTorchButtonVisible() {
		return isFlashButtonVisible;
	}

	public boolean isBeepEnabled() {
		return isBeepEnabled;
	}

	public boolean isScanLaserVisible() {
		return isScanLaserVisible;
	}

	public boolean isAutoZoomEnabled() {
		return isAutoZoomEnabled;
	}

	public boolean isCloseButtonVisible() {
		return isCloseButtonVisible;
	}

	@EnumBarcodeFormat
	public long getBarcodeFormats() {
		return format;
	}

	public String getTemplateFilePath() {
		return templateFilePath;
	}

	public String getLicense() {
		return license;
	}

	public ArrayList<Float> getScanRegion() {
		return dsRect;
	}

	public boolean isCameraToggleButtonVisible() {
		return isCameraToggleButtonVisible;
	}
}
