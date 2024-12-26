package com.dynamsoft.dbrbundle.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumColourChannelUsageType;
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
import com.dynamsoft.dce.EnumCoordinateBase;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.Feedback;
import com.dynamsoft.dce.Note;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.license.LicenseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * @author: dynamsoft
 * Time: 2024/9/29
 * Description:
 */
public class BarcodeScannerActivity extends AppCompatActivity {
	public final static String EXTRA_SCANNER_CONFIG = "scanner_config";
	public final static String EXTRA_STATUS_CODE = "extra_status_code";
	public final static String EXTRA_ERROR_CODE = "extra_error_code";
	public final static String EXTRA_ERROR_STRING = "extra_error_string";
	public final static String EXTRA_ITEM_LIST = "extra_item_list";
	private CameraEnhancer mCamera;
	private CaptureVisionRouter mRouter;
	private CameraView mCameraView;
	private View mTouchView;
	private boolean isTorchOn = false;
	private ConfigSerial configuration;
	private final int radius = 40;
	private HashMap<String, BarcodeResultItem> mapResultItem;
	private DSRect scanRegion;
	private String templateName = "";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scanner_barcode);
		PermissionUtil.requestCameraPermission(this);

		Intent requestIntent = getIntent();
		if (requestIntent != null) {
			configuration = (ConfigSerial) requestIntent.getSerializableExtra(EXTRA_SCANNER_CONFIG);
		}

		if (configuration.getLicense() != null) {
			LicenseManager.initLicense(configuration.getLicense(), this, (isSuccess, error) -> {
				if (!isSuccess) {
					error.printStackTrace();
				}
			});
		}


		boolean isCloseButtonVisible = configuration.isCloseButtonVisible();
		ImageView closeButton = findViewById(R.id.iv_back);
		closeButton.setVisibility(isCloseButtonVisible ? View.VISIBLE : View.GONE);
		findViewById(R.id.iv_back).setOnClickListener(v -> {
			resultOK(BarcodeScanResult.EnumResultStatus.RS_CANCELED, null);
			finish();
		});
		mTouchView = findViewById(R.id.touch_view);
		mCameraView = findViewById(R.id.camera_view);

		addDrawingItemListener();

		mCamera = new CameraEnhancer(mCameraView, this);
		mCamera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL);
		boolean isScanLaserVisible = configuration.isScanLaserVisible();
		mCameraView.setScanLaserVisible(isScanLaserVisible);

		setScanRegion();
	}

	private void configCVR() {
		mRouter = new CaptureVisionRouter(this);

		try {
			if (configuration.getTemplateFilePath() != null && !configuration.getTemplateFilePath().isEmpty()) {
				mRouter.initSettingsFromFile(configuration.getTemplateFilePath());
			} else {
				SimplifiedCaptureVisionSettings settings = mRouter.getSimplifiedSettings(EnumPresetTemplate.PT_READ_BARCODES);
				templateName = EnumPresetTemplate.PT_READ_BARCODES;
				if (settings.barcodeSettings != null) {
					settings.barcodeSettings.barcodeFormatIds = configuration.getBarcodeFormats();
					mRouter.updateSettings(EnumPresetTemplate.PT_READ_BARCODES, settings);
				}
			}

		} catch (CaptureVisionRouterException e) {
			e.printStackTrace();
			resultError(e.getErrorCode(), e.getMessage());
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		openCamera();
		configCVR();
		try {
			// Set the camera enhancer as the input.
			mRouter.setInput(mCamera);
		} catch (CaptureVisionRouterException e) {
			e.printStackTrace();
		}
		// Add CapturedResultReceiver to receive the result callback when a video frame is processed.
		mRouter.addResultReceiver(new CapturedResultReceiver() {
			@Override
			// Implement the callback method to receive DecodedBarcodesResult.
			// The method returns a DecodedBarcodesResult object that contains an array of BarcodeResultItems.
			// BarcodeResultItems is the basic unit from which you can get the basic info of the barcode like the barcode text and barcode format.
			public void onDecodedBarcodesReceived(@NonNull DecodedBarcodesResult result) {
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

					drawSymbols(result);
				} else if (result.getItems().length == 1) {
					if (configuration.isBeepEnabled()) {
						beep();
					}
					resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, result.getItems());
					finish();
				}
			}
		});

		// Start capturing. If success, you will receive results in the CapturedResultReceiver.
		mRouter.startCapturing(templateName, new CompletionListener() {
			@Override
			public void onSuccess() {
				initTorchButton();
				initAutoZoom();
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

	private void openCamera() {
		try {
			// Open the camera.
			mCamera.open();
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
	}

	private void closeCamera() {
		try {
			mCamera.close();
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
	}

	private void setScanRegion() {
		try {
			if (configuration.getScanRegion() != null && !configuration.getScanRegion().isEmpty()) {
				scanRegion = new DSRect(configuration.getScanRegion().get(0),
						configuration.getScanRegion().get(1), configuration.getScanRegion().get(2),
						configuration.getScanRegion().get(3),
						configuration.getScanRegion().get(4) == 1f);
				mCamera.setScanRegion(scanRegion);
			}
		} catch (CameraEnhancerException e) {
			e.printStackTrace();
		}
	}

	private void initTorchButton() {
		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		float density = displayMetrics.density;
		float screenWidth = displayMetrics.widthPixels/density;
		float screenHeight = displayMetrics.heightPixels/density;
		mCameraView.setTorchButtonVisible(configuration.isTorchButtonVisible());
		if(configuration.isTorchButtonVisible()){
			mCameraView.setTorchButton(new Point((int) (screenWidth/2 - 22), (int) (screenHeight*0.8)));
		}
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
		mCameraView.setTorchButtonVisible(false);
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
			int arcCenterX = (item.getLocation().points[0].x + offsetX + item.getLocation().points[2].x + offsetX) / 2;
			int arcCenterY = (item.getLocation().points[0].y + offsetY + item.getLocation().points[2].y + offsetY) / 2;
			Point arcCenter = new Point(arcCenterX, arcCenterY);
			ArcDrawingItem drawingItem = new ArcDrawingItem(arcCenter, radius, EnumCoordinateBase.CB_IMAGE);
			drawingItem.addNote(new Note("index", i + ""), true);
			mapResultItem.put(i + "", item);
			drawingItemArrayList.add(drawingItem);
		}
		layer.setDrawingItems(drawingItemArrayList);
	}

	@SuppressLint("ClickableViewAccessibility")
	private void addDrawingItemListener() {
		mTouchView.setOnTouchListener((v, e) -> {
			if (e.getAction() == MotionEvent.ACTION_DOWN) {
				ArrayList<DrawingItem> items = mCameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID).getDrawingItems();
				if (items.size() > 1) {
					for (DrawingItem item : items) {
						int centerX = ((ArcDrawingItem) item).getCentre().x;
						int centerY = ((ArcDrawingItem) item).getCentre().y;
						float touchX = e.getX();
						float touchY = e.getY();
						Point point = mCamera.convertPointToViewCoordinates(new Point(centerX, centerY));
						float density = getResources().getDisplayMetrics().density;
						if (isPointInCircle(touchX, touchY, point.x / density, point.y / density, 70)) {
							BarcodeResultItem dbrItem = mapResultItem.get(item.getNote("index").getContent());
							resultOK(BarcodeScanResult.EnumResultStatus.RS_FINISHED, new BarcodeResultItem[]{dbrItem});
							finish();
						}
					}
				}
			}
			return false;
		});
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

	public static final class ResultContract extends ActivityResultContract<BarcodeScannerConfig, BarcodeScanResult> {

		@NonNull
		@Override
		public Intent createIntent(@NonNull Context context, BarcodeScannerConfig barcodeScannerConfig) {
			Intent intent = new Intent(context, BarcodeScannerActivity.class);
			DSRect dsRect = barcodeScannerConfig.getScanRegion();
			ConfigSerial configSerial = new ConfigSerial(barcodeScannerConfig.isTorchButtonVisible(),
					barcodeScannerConfig.isBeepEnabled(),
					barcodeScannerConfig.isScanLaserVisible(), barcodeScannerConfig.isAutoZoomEnabled(),
					barcodeScannerConfig.isCloseButtonVisible(), barcodeScannerConfig.getBarcodeFormats(),
					barcodeScannerConfig.getTemplateFilePath(),
					barcodeScannerConfig.getLicense(),
					serializeScanRegion(dsRect));
			intent.putExtra(EXTRA_SCANNER_CONFIG, configSerial);
			return intent;
		}

		@Override
		public BarcodeScanResult parseResult(int i, @Nullable Intent intent) {
			return new BarcodeScanResult(i, intent);
		}

		private ArrayList<Float> serializeScanRegion(DSRect dsRect) {
			ArrayList<Float> scanRegion = new ArrayList<>();
			if (dsRect != null) {
				scanRegion.add(dsRect.left);
				scanRegion.add(dsRect.top);
				scanRegion.add(dsRect.right);
				scanRegion.add(dsRect.bottom);
				scanRegion.add(dsRect.measuredInPercentage ? 1f : 0f);
			}
			return scanRegion;
		}

	}
}
