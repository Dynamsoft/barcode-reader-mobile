package com.dynamsoft.dbrbundle.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.SimplifiedCaptureVisionSettings;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dbrbundle.R;
import com.dynamsoft.dce.ArcDrawingItem;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingItem;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.DrawingStyleManager;
import com.dynamsoft.dce.EnumCameraPosition;
import com.dynamsoft.dce.EnumCoordinateBase;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.Feedback;
import com.dynamsoft.dce.Note;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class BarcodeScannerActivity extends AppCompatActivity {
    public final static String EXTRA_SCANNER_CONFIG = "scanner_config";
    public final static String EXTRA_STATUS_CODE = "extra_status_code";
    public final static String EXTRA_ERROR_CODE = "extra_error_code";
    public final static String EXTRA_ERROR_STRING = "extra_error_string";
    public final static String EXTRA_ITEM_LIST = "extra_item_list";
    private static final String TAG = "BarcodeScannerActivity";
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private CameraView mCameraView;
    private Button btnToggle;
    private Button btnTorch;
    private BarcodeScannerConfig configuration;
    private final int radius = 40;
    private HashMap<String, BarcodeResultItem> mapResultItem;
    private DSRect scanRegion;
    private String templateName = "";
    @EnumScanningMode
    private int scanMode;
    private DecodedBarcodesResult cachedResult;
    private int maxConsecutiveStableFrames;
    private int cumulativeFrames = 0;
    private int expectedBarcodesCount;
    private boolean isTorchOn;
    private boolean useBackCamera = true;

    private CaptureVisionRouterException exceptionWhenConfigCvr;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner_barcode);
        PermissionUtil.requestCameraPermission(this);

        Intent requestIntent = getIntent();
        if (requestIntent != null) {
            configuration = (BarcodeScannerConfig) requestIntent.getSerializableExtra(EXTRA_SCANNER_CONFIG);
        }
        assert configuration != null;

        if (configuration.getLicense() != null) {
            LicenseManager.initLicense(configuration.getLicense(), (isSuccess, error) -> {
                if (!isSuccess) {
                    error.printStackTrace();
                }
            });
        }

        scanMode = configuration.getScanningMode();
        maxConsecutiveStableFrames = configuration.getMaxConsecutiveStableFramesToExit();
        expectedBarcodesCount = configuration.getExpectedBarcodesCount();

        ImageView closeButton = findViewById(R.id.iv_back);
        closeButton.setVisibility(configuration.isCloseButtonVisible() ? View.VISIBLE : View.GONE);
        closeButton.setOnClickListener(v -> {
            resultOK(BarcodeScanResult.EnumResultStatus.RS_CANCELED, null);
            finish();
        });
        mCameraView = findViewById(R.id.camera_view);

        mCamera = new CameraEnhancer(mCameraView, this);
        boolean isScanLaserVisible = configuration.isScanLaserVisible();
        mCameraView.setScanLaserVisible(isScanLaserVisible);

        addDrawingItemListener(mCameraView);
        try {
            mCamera.setScanRegion(configuration.getScanRegion());
        } catch (CameraEnhancerException e) {
            resultError(e.getErrorCode(), "Set scan region error: "+e.getMessage());
            finish();
        }

        initAutoZoom();
        initTorchButton();
        initToggleButton();

        initCVR();

        try {
            configCVR();
        } catch (CaptureVisionRouterException e) {
            exceptionWhenConfigCvr = e;
        }
    }

    private void initCVR() {
        mRouter = new CaptureVisionRouter();
        MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
        filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_BARCODE, true);
        if (scanMode == EnumScanningMode.SM_MULTIPLE) {
            filter.enableLatestOverlapping(EnumCapturedResultItemType.CRIT_BARCODE, true);
        }
        mRouter.addResultFilter(filter);
        // Add CapturedResultReceiver to receive the result callback when a video frame is processed.
        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            // Implement the callback method to receive DecodedBarcodesResult.
            // The method returns a DecodedBarcodesResult object that contains an array of BarcodeResultItems.
            // BarcodeResultItems is the basic unit from which you can get the basic info of the barcode like the barcode text and barcode format.
            public void onDecodedBarcodesReceived(@NonNull DecodedBarcodesResult result) {
                if (scanMode == EnumScanningMode.SM_SINGLE) {
                    resultSingle(result);
                } else {
                    resultMultiple(result);
                }
            }
        });
    }

    private void configCVR() throws CaptureVisionRouterException {
        // Set the camera enhancer as the input.
        mRouter.setInput(mCamera);

        if (configuration.getTemplateFile() != null && !configuration.getTemplateFile().isEmpty()) {
            String template = configuration.getTemplateFile();
            if (template.startsWith("{") || template.startsWith("[")) {
                mRouter.initSettings(template);
            } else {
                mRouter.initSettingsFromFile(template);
            }
        } else {
            mRouter.initSettingsFromFile("dbr-mobile-templates.json"); //See template file in assets/Templates folder
            if (scanMode == EnumScanningMode.SM_SINGLE) {
                templateName = EnumPresetTemplate.PT_READ_BARCODES;
            } else {
                templateName = "ReadMultipleBarcodes";
            }
            SimplifiedCaptureVisionSettings settings = mRouter.getSimplifiedSettings(templateName);
            if (settings.barcodeSettings != null) {
                settings.barcodeSettings.barcodeFormatIds = configuration.getBarcodeFormats();
                mRouter.updateSettings(templateName, settings);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(exceptionWhenConfigCvr != null) {
            resultError(exceptionWhenConfigCvr.getErrorCode(), exceptionWhenConfigCvr.getMessage());
            finish();
        }
        openCamera();
        // Start capturing. If success, you will receive results in the CapturedResultReceiver.
        mRouter.startCapturing(templateName, new CompletionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> {
                    resultError(errorCode, errorString);
                    finish();
                });
            }
        });
    }

    @Override
    public void onPause() {
        closeCamera();
        mRouter.stopCapturing();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        resultOK(BarcodeScanResult.EnumResultStatus.RS_CANCELED, null);
        super.onBackPressed();
    }

    private void resultSingle(DecodedBarcodesResult result) {
        if (result.getItems().length > 1) {
            if (configuration.isBeepEnabled()) {
                beep();
            }
            mRouter.stopCapturing();
            try {
                mCamera.setScanRegion(null);
                mCamera.close();
            } catch (CameraEnhancerException e) {
                throw new RuntimeException(e);
            }
            runOnUiThread(() -> {
                mCameraView.setScanLaserVisible(false);
                btnToggle.setVisibility(View.GONE);
                btnTorch.setVisibility(View.GONE);
            });

            drawSymbols(result);
        } else if (result.getItems().length == 1) {
            if (configuration.isBeepEnabled()) {
                beep();
            }
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
            finish();
        }
    }

    private void resultMultiple(DecodedBarcodesResult result) {
        if (result.getItems().length >= expectedBarcodesCount) {
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
            finish();
            return;
        }
        if (!sortResult(result)) {
            cumulativeFrames = 0;
            cachedResult = result;
        } else {
            cumulativeFrames++;
            if (cumulativeFrames >= maxConsecutiveStableFrames) {
                resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
                finish();
            }
        }
    }

    private boolean sortResult(DecodedBarcodesResult currentResult) {
        if (cachedResult == null) {
            return false;
        }
        BarcodeResultItem[] cachedItems = cachedResult.getItems();
        BarcodeResultItem[] currentItems = currentResult.getItems();
        if (cachedItems.length == 0 || currentItems.length == 0 || cachedItems.length != currentItems.length) {
            return false;
        } else {
            Arrays.sort(cachedItems, (o1, o2) -> {
                if (o1.getLocation().points[0].x != o2.getLocation().points[0].x) {
                    return o1.getLocation().points[0].x - o2.getLocation().points[0].x;
                } else {
                    return o1.getLocation().points[0].y - o2.getLocation().points[0].y;
                }
            });
            Arrays.sort(currentItems, (o1, o2) -> {
                if (o1.getLocation().points[0].x != o2.getLocation().points[0].x) {
                    return o1.getLocation().points[0].x - o2.getLocation().points[0].x;
                } else {
                    return o1.getLocation().points[0].y - o2.getLocation().points[0].y;
                }
            });
            for (int i = 0; i < cachedItems.length; i++) {
                BarcodeResultItem cachedItem = cachedItems[i];
                BarcodeResultItem currentItem = currentItems[i];
                if (!cachedItem.getText().equals(currentItem.getText()) || cachedItem.getType() != currentItem.getType()) {
                    return false;
                }
                int cachedX = 0;
                int cachedY = 0;
                int currentX = 0;
                int currentY = 0;
                for (int j = 0; j < 4; j++) {
                    cachedX += cachedItems[i].getLocation().points[j].x;
                    cachedY += cachedItems[i].getLocation().points[j].y;
                    currentX += currentItems[i].getLocation().points[j].x;
                    currentY += currentItems[i].getLocation().points[j].y;
                }
                int absX = Math.abs(cachedX / 4 - currentX / 4);
                int absY = Math.abs(cachedY / 4 - currentY / 4);
                if (absX > 30 || absY > 30) {
                    return false;
                }
            }
        }
        return true;
    }


    private void openCamera() {
        mCamera.open();
    }

    private void closeCamera() {
        mCamera.close();
    }

    private void initTorchButton() {
        btnTorch = findViewById(R.id.btn_torch);
        btnTorch.setVisibility(configuration.isTorchButtonVisible() ? View.VISIBLE : View.GONE);
        btnTorch.setOnClickListener(v -> {
            isTorchOn = !isTorchOn;
            if (isTorchOn) {
                turnOnTorch();
            } else {
                turnOffTorch();
            }
        });
    }


    private void turnOnTorch() {
        mCamera.turnOnTorch();
        btnTorch.setBackground(ContextCompat.getDrawable(this, R.drawable.icon_flash_on));
    }

    private void turnOffTorch() {
        mCamera.turnOffTorch();
        btnTorch.setBackground(ContextCompat.getDrawable(this, R.drawable.icon_flash_off));
    }

    private void initToggleButton() {
        btnToggle = findViewById(R.id.btn_toggle);
        btnToggle.setVisibility(configuration.isCameraToggleButtonVisible() ? View.VISIBLE : View.GONE);
        if (!configuration.isTorchButtonVisible() && configuration.isCameraToggleButtonVisible()) {
            resetToggleButton(0);
        }
        btnToggle.setOnClickListener(v -> {
            useBackCamera = !useBackCamera;
            mCamera.selectCamera(useBackCamera ? EnumCameraPosition.CP_BACK : EnumCameraPosition.CP_FRONT);
            if (configuration.isTorchButtonVisible()) {
                btnTorch.setVisibility(useBackCamera ? View.VISIBLE : View.GONE);
                resetToggleButton(useBackCamera ? dpToPx(50) : 0);
                if (!useBackCamera) {
                    isTorchOn = false;
                    turnOffTorch();
                }
            }
        });
    }

    private void resetToggleButton(int margin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnToggle.getLayoutParams();
        params.setMarginStart(margin);
        btnToggle.setLayoutParams(params);
    }

    private void initAutoZoom() {
        try {
            if (configuration.isAutoZoomEnabled()) {
                mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_AUTO_ZOOM);
            }
        } catch (CameraEnhancerException e) {
            e.printStackTrace();
        }
    }

    private void drawSymbols(DecodedBarcodesResult scanResult) {
        runOnUiThread(() -> {
            btnToggle.setVisibility(View.GONE);
            btnTorch.setVisibility(View.GONE);
        });
        int matchedStyle = DrawingStyleManager.createDrawingStyle(Color.WHITE, 3, Color.GREEN, Color.WHITE);
        DrawingLayer layer = mCameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID);
        layer.setDefaultStyle(matchedStyle);
        ArrayList<DrawingItem> drawingItemArrayList = new ArrayList<>();
        mapResultItem = new HashMap<>();
        int offsetX = 0;
        int offsetY = 0;
        if (scanRegion != null) {
            if (scanRegion.measuredInPercentage) {
                Size size = mCamera.getResolution();
                offsetX = (int) (scanRegion.left * size.getHeight());
                offsetY = (int) (scanRegion.top * size.getWidth());
            } else {
                offsetX = (int) scanRegion.left;
                offsetY = (int) scanRegion.top;
            }
        }
        BarcodeResultItem[] items = scanResult.getItems();
        for (int i = 0; i < items.length; i++) {
            BarcodeResultItem item = items[i];
            int arcCenterX = (item.getLocation().points[0].x + item.getLocation().points[2].x) / 2 + offsetX;
            int arcCenterY = (item.getLocation().points[0].y + item.getLocation().points[2].y) / 2 + offsetY;
            Point arcCenter = new Point(arcCenterX, arcCenterY);
            ArcDrawingItem drawingItem = new ArcDrawingItem(arcCenter, radius, EnumCoordinateBase.CB_IMAGE);
            drawingItem.addNote(new Note("index", i + ""), true);
            mapResultItem.put(i + "", item);
            drawingItemArrayList.add(drawingItem);
        }
        layer.setDrawingItems(drawingItemArrayList);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addDrawingItemListener(CameraView cameraView) {
        cameraView.setDrawingItemClickListener(clickedItem -> {
            String index = clickedItem.getNote("index").getContent();
            BarcodeResultItem clickedBarcodeItem = mapResultItem.get(index);
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, new BarcodeResultItem[]{clickedBarcodeItem});
            finish();
        });
//        mTouchView.setOnTouchListener((v, e) -> {
//            if (e.getAction() == MotionEvent.ACTION_DOWN) {
//                ArrayList<DrawingItem> items = mCameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID).getDrawingItems();
//                if (items.size() > 1) {
//                    for (DrawingItem item : items) {
//                        int centerX = ((ArcDrawingItem) item).getCentre().x;
//                        int centerY = ((ArcDrawingItem) item).getCentre().y;
//                        float touchX = e.getX();
//                        float touchY = e.getY();
//                        Point point = mCamera.convertPointToViewCoordinates(new Point(centerX, centerY));
//                        float density = getResources().getDisplayMetrics().density;
//                        if (isPointInCircle(touchX, touchY, point.x / density, point.y / density, 70)) {
//                            BarcodeResultItem dbrItem = mapResultItem.get(item.getNote("index").getContent());
//                            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, new BarcodeResultItem[]{dbrItem});
//                            finish();
//                        }
//                    }
//                }
//            }
//            return false;
//        });
    }

    private boolean isPointInCircle(float x, float y, float centerX, float centerY, float r) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        return distance <= r;
    }

    private void resultOK(int statusCode, BarcodeResultItem[] items) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_STATUS_CODE, statusCode);
        if (items != null && items.length > 0) {
            HashMap<Integer, HashMap<String, Object>> itemList = new HashMap<>();
            for (int i = 0; i < items.length; i++) {
                BarcodeResultItem item = items[i];
                HashMap<String, Object> map = new HashMap<>();
                map.put("text", item.getText());
                map.put("type", item.getFormat());
                map.put("typeString", item.getFormatString());
                map.put("bytes", item.getBytes());
                List<Integer> points = new ArrayList<>();
                for (Point p : item.getLocation().points) {
                    points.add(p.x);
                    points.add(p.y);
                }
                map.put("location", points);
                map.put("confidence", item.getConfidence());
                map.put("angle", item.getAngle());
                map.put("mirrored", item.isMirrored());
                map.put("moduleSize", item.getModuleSize());
                map.put("dpm", item.isDPM());
                map.put("taskName", item.getTaskName() == null ? "" : item.getTaskName());
                map.put("targetROIDefName", item.getTargetROIDefName() == null ? "" : item.getTargetROIDefName());
                itemList.put(i, map);
            }
            intent.putExtra(EXTRA_ITEM_LIST, itemList);
        }
        setResult(RESULT_OK, intent);
    }

    private void resultError(int errorCode, String errorString) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_STATUS_CODE, BarcodeScanResult.EnumResultStatus.RS_EXCEPTION);
        intent.putExtra(EXTRA_ERROR_CODE, errorCode);
        intent.putExtra(EXTRA_ERROR_STRING, errorString);
        setResult(RESULT_OK, intent);
    }

    private void beep() {
        Feedback.beep(this);
    }

    public int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static final class ResultContract extends ActivityResultContract<BarcodeScannerConfig, BarcodeScanResult> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, BarcodeScannerConfig barcodeScannerConfig) {
            Intent intent = new Intent(context, BarcodeScannerActivity.class);
            intent.putExtra(EXTRA_SCANNER_CONFIG, barcodeScannerConfig);
            return intent;
        }

        @Override
        public BarcodeScanResult parseResult(int i, @Nullable Intent intent) {
            return new BarcodeScanResult(i, intent);
        }
    }
}
