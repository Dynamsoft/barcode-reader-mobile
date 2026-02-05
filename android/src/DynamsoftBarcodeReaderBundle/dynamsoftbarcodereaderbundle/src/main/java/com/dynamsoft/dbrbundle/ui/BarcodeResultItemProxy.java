package com.dynamsoft.dbrbundle.ui;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.core.json.JsonParserProxy;
import com.dynamsoft.dbr.AztecDetails;
import com.dynamsoft.dbr.BarcodeDetails;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DataMatrixDetails;
import com.dynamsoft.dbr.ECISegment;
import com.dynamsoft.dbr.EnumBarcodeFormat;
import com.dynamsoft.dbr.OneDCodeDetails;
import com.dynamsoft.dbr.PDF417Details;
import com.dynamsoft.dbr.QRCodeDetails;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author: dynamsoft
 * Time: 2024/11/28
 * Description:
 */
public class BarcodeResultItemProxy extends BarcodeResultItem implements Serializable {
    @NonNull
    private static final JsonParserProxy jsonParser = JsonParserProxy.newDefaultProxy();
	private final long format;
	private final String formatString;
	private final String text;
	private final byte[] bytes;
    private transient Quadrilateral location = null;
    private final int[] locationPointsValue = new int[8]; //For location Serialize
    private final int confidence;
	private final int angle;
	private final int moduleSize;
	private final boolean mirrored;
	private final boolean dpm;
	private final String taskName;
	private final String targetROIDefName;

    private transient ECISegment[] eciSegments;
    private final String[] eciSegmentsJsons;//For eciSegments Serialize

    private transient BarcodeDetails barcodeDetails;

    private final String barcodeDetailsJson; //For barcodeDetails Serialize

    protected BarcodeResultItemProxy(BarcodeResultItem item) {
        super(0L);
        format = item.getFormat();
        formatString = item.getFormatString();
        text = item.getText();
        bytes = item.getBytes();

        for (int i = 0; i < item.getLocation().points.length; i++) {
            locationPointsValue[i * 2] = item.getLocation().points[i].x;
            locationPointsValue[i * 2 + 1] = item.getLocation().points[i].y;
        }

        confidence = item.getConfidence();
        angle = item.getAngle();
        moduleSize = item.getModuleSize();
        mirrored = item.isMirrored();
        dpm = item.isDPM();
        taskName = item.getTaskName();
        targetROIDefName = item.getTargetROIDefName();

        ECISegment[] segments = item.getECISegments();
        int segmentsLength = segments == null ? 0 : segments.length;
        eciSegmentsJsons = new String[segmentsLength];
        for (int i = 0; i < segmentsLength; i++) {
            eciSegmentsJsons[i] = jsonParser.toJson(segments[i]);
        }

        BarcodeDetails details = item.getDetails();
        if(details != null) {
            barcodeDetailsJson = jsonParser.toJson(details);
        } else {
            barcodeDetailsJson = null;
        }
    }

    @Override
    public int getType() {
        return EnumCapturedResultItemType.CRIT_BARCODE;
    }

    public long getFormat() {
        return this.format;
    }

    public String getFormatString() {
        return this.formatString;
    }

    public String getText() {
        return this.text;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

    public Quadrilateral getLocation() {
        if(location == null) {
            location = new Quadrilateral();
            for (int i = 0; i < location.points.length; i++) {
                location.points[i] = new Point(locationPointsValue[i * 2], locationPointsValue[i * 2 + 1]);
            }
        }
        return location;
    }

    public int getConfidence() {
        return this.confidence;
    }

    public int getAngle() {
        return this.angle;
    }

    public int getModuleSize() {
        return this.moduleSize;
    }

    public boolean isMirrored() {
        return this.mirrored;
    }

    public boolean isDPM() {
        return this.dpm;
    }

    public String getTaskName() {
        return this.taskName;
    }

    public String getTargetROIDefName() {
        return this.targetROIDefName;
    }

    @Nullable
    public ECISegment[] getECISegments() {
        if(eciSegments == null && eciSegmentsJsons != null && eciSegmentsJsons.length > 0) {
            eciSegments = new ECISegment[eciSegmentsJsons.length];
            for (int i = 0; i < eciSegmentsJsons.length; i++) {
                eciSegments[i] = jsonParser.fromJson(eciSegmentsJsons[i], ECISegment.class);
            }
        }
        return eciSegments;
    }

    @Nullable
    public BarcodeDetails getDetails() {
        if(barcodeDetails == null && barcodeDetailsJson != null) {
            if((format & EnumBarcodeFormat.BF_ONED) != 0L) {
                barcodeDetails = jsonParser.fromJson(barcodeDetailsJson, OneDCodeDetails.class);
            } else if(format == EnumBarcodeFormat.BF_QR_CODE) {
                barcodeDetails = jsonParser.fromJson(barcodeDetailsJson, QRCodeDetails.class);
            } else if(format == EnumBarcodeFormat.BF_PDF417) {
                barcodeDetails = jsonParser.fromJson(barcodeDetailsJson, PDF417Details.class);
            } else if(format == EnumBarcodeFormat.BF_AZTEC) {
                barcodeDetails = jsonParser.fromJson(barcodeDetailsJson, AztecDetails.class);
            } else if(format == EnumBarcodeFormat.BF_DATAMATRIX) {
                barcodeDetails = jsonParser.fromJson(barcodeDetailsJson, DataMatrixDetails.class);
            }
        }
        return barcodeDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BarcodeResultItemProxy that = (BarcodeResultItemProxy) o;
        return format == that.format && confidence == that.confidence && angle == that.angle && moduleSize == that.moduleSize && mirrored == that.mirrored && dpm == that.dpm && Objects.equals(text, that.text) && Objects.deepEquals(locationPointsValue, that.locationPointsValue) && Objects.equals(taskName, that.taskName) && Objects.equals(targetROIDefName, that.targetROIDefName) && Objects.deepEquals(eciSegmentsJsons, that.eciSegmentsJsons) && Objects.equals(barcodeDetailsJson, that.barcodeDetailsJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, text, Arrays.hashCode(locationPointsValue), confidence, angle, moduleSize, mirrored, dpm, taskName, targetROIDefName, Arrays.hashCode(eciSegmentsJsons), barcodeDetailsJson);
    }
}
