package com.dynamsoft.dbrbundle.ui;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
import com.dynamsoft.dbrbundle.ui.utils.ViewUtil;
import com.dynamsoft.dce.ArcDrawingItem;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingItem;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.DrawingStyleManager;
import com.dynamsoft.dce.EnumCameraPosition;
import com.dynamsoft.dce.EnumCameraState;
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

public class BarcodeScannerActivity extends AppCompatActivity implements ViewTreeObserver.OnGlobalLayoutListener {
    public final static String EXTRA_SCANNER_CONFIG = "scanner_config";
    public final static String EXTRA_STATUS_CODE = "extra_status_code";
    public final static String EXTRA_ERROR_CODE = "extra_error_code";
    public final static String EXTRA_ERROR_STRING = "extra_error_string";
    public final static String EXTRA_ITEM_LIST = "extra_item_list";
    private static final String TAG = "BarcodeScannerActivity";

    private static final String KEY_CONFIG = "CONFIG";
    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private CameraView mCameraView;
    private BarcodeScannerConfig configuration;
    private final HashMap<String, BarcodeResultItem> mapResultItem = new HashMap<>(); // key: itemIndex, value: item; For returning item after clicking drawing item
    private String templateName = "";
    private DecodedBarcodesResult cachedResult;
    private int cumulativeFrames = 0;

    private CaptureVisionRouterException exceptionWhenConfigCvr;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner_barcode);
        PermissionUtil.requestCameraPermission(this);

        boolean isLight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
        WindowInsetsControllerCompat wic = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(isLight);
        wic.setAppearanceLightNavigationBars(isLight);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState != null) {
            configuration = (BarcodeScannerConfig) savedInstanceState.getSerializable(KEY_CONFIG);
        }

        if (configuration == null) {
            Intent requestIntent = getIntent();
            if (requestIntent != null) {
                configuration = (BarcodeScannerConfig) requestIntent.getSerializableExtra(EXTRA_SCANNER_CONFIG);
            }
        }
        assert configuration != null;

        if (configuration.getLicense() != null) {
            LicenseManager.initLicense(configuration.getLicense(), (isSuccess, error) -> {
                if (!isSuccess) {
                    Log.e(TAG, "onCreate: initLicense failed.", error);
                }
            });
        }

        ImageView closeButton = findViewById(R.id.iv_back);
        closeButton.setVisibility(configuration.isCloseButtonVisible() ? View.VISIBLE : View.GONE);
        closeButton.setOnClickListener(v -> {
            resultOK(BarcodeScanResult.EnumResultStatus.RS_CANCELED, null);
            finish();
        });

        initCamera();
        initCVR();

        try {
            configCVR();
        } catch (CaptureVisionRouterException e) {
            exceptionWhenConfigCvr = e;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putSerializable(KEY_CONFIG, configuration);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.setZoomFactorChangeListener(null);
        mCameraView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    private void initCamera() {
        mCameraView = findViewById(R.id.camera_view);

        mCamera = new CameraEnhancer(mCameraView, this);
        mCamera.selectCamera(configuration.cameraPosition);

        boolean isScanLaserVisible = configuration.isScanLaserVisible();
        mCameraView.setScanLaserVisible(isScanLaserVisible);
        addDrawingItemListener(mCameraView);

        mCameraView.getViewTreeObserver().addOnGlobalLayoutListener(this);

        try {
            if (configuration.isAutoZoomEnabled()) {
                mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_AUTO_ZOOM);
            }
        } catch (CameraEnhancerException e) {
            Log.e(TAG, "initAutoZoom: enableEnhancedFeatures failed.", e);
        }

        mCamera.setZoomFactor(configuration.getZoomFactor());
        mCamera.setZoomFactorChangeListener(factor -> configuration.setZoomFactor(factor));

        mCamera.setCameraStateListener(state -> {
            if (state == EnumCameraState.OPENED) {
                ViewUtil.configCameraViewButton(mCamera, configuration);
            }
        });
    }

    private void initCVR() {
        mRouter = new CaptureVisionRouter();
        MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
        filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_BARCODE, true);
        if (configuration.getScanningMode() == EnumScanningMode.SM_MULTIPLE) {
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
                if (configuration.getScanningMode() == EnumScanningMode.SM_SINGLE) {
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
            if (configuration.getScanningMode() == EnumScanningMode.SM_SINGLE) {
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
        if (exceptionWhenConfigCvr != null) {
            resultError(exceptionWhenConfigCvr.getErrorCode(), exceptionWhenConfigCvr.getMessage());
            finish();
        }
        mCamera.open();
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
        mCamera.close();
        mRouter.stopCapturing();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        resultOK(BarcodeScanResult.EnumResultStatus.RS_CANCELED, null);
        super.onBackPressed();
    }

    /// ViewTreeObserver.OnGlobalLayoutListener
    @Override
    public void onGlobalLayout() {
        try {
            if (configuration.getScanRegion() != null) {
                mCamera.setScanRegion(configuration.getScanRegion());
            } else {
                DSRect visibleRegion = mCameraView.getVisibleRegionOfVideo();
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == ORIENTATION_PORTRAIT && (visibleRegion.right - visibleRegion.left) > 0.02f) {
                    visibleRegion.right -= 0.01f;
                    visibleRegion.left += 0.01f;
                } else if (visibleRegion.bottom - visibleRegion.top > 0.02f) {
                    visibleRegion.bottom -= 0.01f;
                    visibleRegion.top += 0.01f;
                }
                mCamera.setScanRegion(visibleRegion);
                mCameraView.setScanRegionMaskVisible(false);
            }
        } catch (CameraEnhancerException e) {
            resultError(e.getErrorCode(), "Set scan region error: " + e.getMessage());
            finish();
        }
    }

    private void resultSingle(DecodedBarcodesResult result) {
        if (result.getItems().length > 0) {
            makeFeedback(configuration);
        }
        if (result.getItems().length > 1) {
            mRouter.stopCapturing();
            drawSymbols(mCameraView, result);
            mCamera.close();
            runOnUiThread(() -> {
                mCameraView.setScanLaserVisible(false);
                mCameraView.setScanRegionMaskVisible(false);
                mCameraView.setTorchButtonVisible(false);
                mCameraView.setCameraToggleButtonVisible(false);
            });
        } else if (result.getItems().length == 1) {
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
            finish();
        }
    }

    private void resultMultiple(DecodedBarcodesResult result) {
        if (result.getItems().length >= configuration.getExpectedBarcodesCount()) {
            makeFeedback(configuration);
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
            finish();
            return;
        }
        if (!sortResult(cachedResult, result)) {
            cumulativeFrames = 0;
            cachedResult = result;
        } else {
            cumulativeFrames++;
            if (cumulativeFrames >= configuration.getMaxConsecutiveStableFramesToExit()) {
                makeFeedback(configuration);
                resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
                finish();
            }
        }
    }

    private void drawSymbols(CameraView cameraView, DecodedBarcodesResult scanResult) {
        int matchedStyle = DrawingStyleManager.createDrawingStyle(Color.WHITE, 3, Color.GREEN, Color.WHITE);
        DrawingLayer layer = cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID);
        layer.setDefaultStyle(matchedStyle);
        ArrayList<DrawingItem> drawingItemArrayList = new ArrayList<>();
        mapResultItem.clear();
        BarcodeResultItem[] items = scanResult.getItems();
        for (int i = 0; i < items.length; i++) {
            BarcodeResultItem item = items[i];
            int arcCenterX = (item.getLocation().points[0].x + item.getLocation().points[2].x) / 2;
            int arcCenterY = (item.getLocation().points[0].y + item.getLocation().points[2].y) / 2;
            Point arcCenter = new Point(arcCenterX, arcCenterY);
            ArcDrawingItem drawingItem = new ArcDrawingItem(arcCenter, 40, EnumCoordinateBase.CB_IMAGE);
            drawingItem.addNote(new Note("index", i + ""), true);
            mapResultItem.put(i + "", item);
            drawingItemArrayList.add(drawingItem);
        }
        layer.setDrawingItems(drawingItemArrayList);
    }

    private void addDrawingItemListener(CameraView cameraView) {
        cameraView.setDrawingItemClickListener(clickedItem -> {
            String index = clickedItem.getNote("index").getContent();
            BarcodeResultItem clickedBarcodeItem = mapResultItem.get(index);
            resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, new BarcodeResultItem[]{clickedBarcodeItem});
            finish();
        });
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

    private static boolean sortResult(DecodedBarcodesResult cachedResult, DecodedBarcodesResult currentResult) {
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

    private static void makeFeedback(BarcodeScannerConfig config) {
        if (config.isVibrateEnabled()) {
            Feedback.vibrate();
        }
        if (config.isBeepEnabled()) {
            Feedback.beep();
        }
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
