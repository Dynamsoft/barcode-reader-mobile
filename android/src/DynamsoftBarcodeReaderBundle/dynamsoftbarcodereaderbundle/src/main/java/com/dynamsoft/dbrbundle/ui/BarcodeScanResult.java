package com.dynamsoft.dbrbundle.ui;

import androidx.annotation.IntDef;

import com.dynamsoft.dbr.BarcodeResultItem;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class BarcodeScanResult implements Serializable {
    final static String EXTRA = "BarcodeScanResult";
    BarcodeResultItemProxy[] barcodeResults;
    @EnumResultStatus
    int resultStatus;
    int errorCode;
    String errorString;

    public BarcodeScanResult() {
    }

	public BarcodeResultItem[] getBarcodes() {
		return barcodeResults;
	}

    @EnumResultStatus
    public int getResultStatus() {
        return resultStatus;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorString() {
        return errorString;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {EnumResultStatus.RS_FINISHED, EnumResultStatus.RS_CANCELED, EnumResultStatus.RS_EXCEPTION})
    public @interface EnumResultStatus {
        int RS_FINISHED = 0;
        int RS_CANCELED = 1;
        int RS_EXCEPTION = 2;
    }
}
