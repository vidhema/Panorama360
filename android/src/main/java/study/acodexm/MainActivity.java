package study.acodexm;


import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import acodexm.panorama.R;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import study.acodexm.control.AndroidRotationVector;
import study.acodexm.control.AndroidSettingsControl;
import study.acodexm.control.CameraControl;
import study.acodexm.control.ViewControl;
import study.acodexm.gallery.GalleryActivity;
import study.acodexm.settings.ActionMode;
import study.acodexm.settings.GridSize;
import study.acodexm.settings.PictureMode;
import study.acodexm.settings.PictureQuality;
import study.acodexm.settings.SettingsControl;
import study.acodexm.settings.UserPreferences;
import study.acodexm.utils.ImagePicker;
import study.acodexm.utils.ImageRW;
import study.acodexm.utils.LOG;

public class MainActivity extends AndroidApplication implements SensorEventListener, ViewControl, NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PART = "PART_";
    private static final int START_PROCESSING = 100;
    private static final int STOP_PROCESSING = 101;
    private static final int COUNT_IMAGES = 102;
    private static final int PROCESS_PART_IMAGES = 103;
    private static final int SAVED_PART_IMAGE = 104;
    private static final int PROCESS_ALL_PARTS = 105;

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("MyLib");
    }

    @BindView(R.id.capture)
    ImageView captureBtn;
    @BindView(R.id.scope)
    ImageView scope;
    @BindView(R.id.refresh_picture)
    ImageView refreshBtn;
    @BindView(R.id.open_gallery)
    ImageView galleryBtn;
    @BindView(R.id.mode_auto)
    Switch mSwitchAuto;
    @BindView(R.id.mode_manual)
    Switch mSwitchManual;
    @BindView(R.id.picture_panorama)
    Switch mSwitchPanorama;
    @BindView(R.id.picture_auto)
    Switch mSwitchAutoPicture;
    @BindView(R.id.picture_multithreaded)
    Switch mSwitchMultithreaded;
    @BindView(R.id.picture_wide)
    Switch mSwitchWide;
    @BindView(R.id.picture_360)
    Switch mSwitch360;
    @BindView(R.id.quality_high)
    Switch mSwitchHigh;
    @BindView(R.id.quality_low)
    Switch mSwitchLow;
    @BindView(R.id.quality_very_low)
    Switch mSwitchVeryLow;
    @BindView(R.id.save_dir)
    TextView mSaveDir;
    @BindView(R.id.steady_shot)
    ProgressBar mProgressBar;

    // multithreading
    Thread imageHandler;
    Handler threadHandler;
    private ArrayList<Integer> usedPositions = new ArrayList<>();
    private int imageCount = 0;
    private boolean mRunning = false;

    private SurfaceView mSurfaceView;
    private GLSurfaceView glView;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private RotationVector rotationVector = new AndroidRotationVector();
    private SettingsControl mSettingsControl = new AndroidSettingsControl();
    private float[] rotationMatrix = rotationVector.getValues();
    private CameraControl mCameraControl;
    private ShutterState mShutterState;
    private UserPreferences mPreferences;
    private SphereManualControl mManualControl;
    private boolean onBackBtnPressed = false;
    private int DOUBLE_BACK_PRESSED_DELAY = 2500;
    private boolean isNotSaving = true;
    private PicturePosition mPicturePosition;
    private GridSize mGridSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //load preferences
        mPreferences = new UserPreferences(this);
        mGridSize = new GridSize(mPreferences.getLat(), mPreferences.getLon());
        mSettingsControl.setGridSize(mGridSize);
        mCameraControl = new CameraSurface(this, mSettingsControl);
        //getting camera surface view
        mSurfaceView = mCameraControl.getSurface();
        FrameLayout layout = new FrameLayout(getContext());
        //crating main view from activity_main layout
        View mainView = LayoutInflater.from(getContext()).inflate(R.layout.activity_main, layout, false);
        // setting up gyroscope
        mSensorManager = (SensorManager) getContext().getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //creating and configuring new instance of LibGDX spherical view
        AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
        cfg.useGyroscope = true;
        cfg.useAccelerometer = false;
        cfg.useCompass = false;
        cfg.r = 8;
        cfg.g = 8;
        cfg.b = 8;
        cfg.a = 8;
        AndroidCamera androidCamera = new AndroidCamera(rotationVector, mCameraControl.getSphereControl(), mSettingsControl);
        mManualControl = androidCamera;
        //initializing LibGDX spherical view
        initializeForView(androidCamera, cfg);
        if (graphics.getView() instanceof GLSurfaceView) {
            LOG.d(TAG, "creating layout");
            glView = (GLSurfaceView) graphics.getView();
            glView.setZOrderMediaOverlay(true);
            glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            glView.setKeepScreenOn(true);
            layout.addView(mSurfaceView);
            layout.addView(glView);
            layout.addView(mainView);
        }
        //attach layout to view
        setContentView(layout);
        //injecting view components
        ButterKnife.bind(this);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //delete files from temporary picture folder
        ImageRW.deleteTempFiles();
        ImageRW.deletePartFiles();

        mPicturePosition = PicturePosition.getInstance(mGridSize.getLAT(), mGridSize.getLON(), true);
        imageHandler = new Thread(() -> {
            LOG.d(TAG, "image handler call");
            while (mRunning) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                ArrayList<Integer> newImagePart = ImagePicker.loadPanoParts(mPicturePosition, usedPositions);
                if (newImagePart.size() == 3) {
                    Message message = new Message();
                    message.what = PROCESS_PART_IMAGES;
                    usedPositions.addAll(newImagePart);
                    Bundle data = new Bundle();
                    int id = imageCount++;
                    message.arg1 = id;
                    data.putIntegerArrayList(PART + id, newImagePart);
                    message.setData(data);
                    threadHandler.sendMessage(message);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraControl.startPreview();
        mShutterState = ShutterState.ready;
        loadPreferences();
        setCaptureBtnImage();
        setScopeImage();
        threadHandler = new Handler(msg -> {
            LOG.d(TAG, "handleMessage" + msg.what);
            switch (msg.what) {
                case START_PROCESSING: {
                    LOG.d(TAG, "START_PROCESSING");
                    isNotSaving = true;
                    mRunning = true;
                    imageHandler.start();
                    break;
                }
                case STOP_PROCESSING: {
                    LOG.d(TAG, "STOP_PROCESSING");
                    isNotSaving = true;
                    mRunning = false;
//                        imageHandler.stop();
                    break;
                }
                case PROCESS_PART_IMAGES: {
                    LOG.d(TAG, "PROCESS_PART_IMAGES");
                    new Thread(processPartPicture(msg.getData().getIntegerArrayList(PART + msg.arg1))).start();
                    break;
                }
                case SAVED_PART_IMAGE: {
                    LOG.d(TAG, "SAVED_PART_IMAGE");
                    showToastRunnable(getString(R.string.part_msg_is_saved) + (msg.arg1 == 1));
                    break;
                }
            }
            return true;
        });
        if (mPreferences.getPictureMode() == PictureMode.multithreaded)
            threadHandler.sendEmptyMessage(START_PROCESSING);
    }

    @Override
    protected void onPause() {
        mCameraControl.stopPreview();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (onBackBtnPressed) {
            Intent intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("EXIT", true);
            startActivity(intent);
        } else {
            onBackBtnPressed = true;
            showToast(R.string.msg_exit);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    onBackBtnPressed = false;
                }
            }, DOUBLE_BACK_PRESSED_DELAY);
        }
    }


    public void post(Runnable r) {
        handler.post(r);
    }

    @Override
    public void updateRender() {
        mManualControl.updateRender();
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        setScopeImage();
        if (isNotSaving && sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
            rotationVector.updateRotationVector(rotationMatrix);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    /**
     * this method is executed on new Thread.
     * first depending on selected picture mode method loads selected pictures to be processed.
     * Next pictures are passed to native openCV stitcher to be processed.
     * If the stitching process is successful the picture is saved
     *
     * @param pictureMode
     */
    void processPicture(final PictureMode pictureMode) {
        long time = System.currentTimeMillis();
        post(LOG.r(TAG, "processPicture BEGIN", time + "ms"));
        showProcessingDialog();
        final Runnable r = () -> {
            isNotSaving = false;
            final List<Mat> listImage;
            try {
                listImage = ImagePicker.loadPictures(pictureMode, mPicturePosition);
            } catch (Exception e) {
                post(LOG.r(TAG, "run: loadPictures failed", e));
                return;
            }
            post(LOG.r("processPicture", "loadPictureParts", (System.currentTimeMillis() - time) + "ms"));
            try {
                int images = listImage.size();
                if (images > 0) {
                    long[] tempObjAddress = new long[images];
                    for (int i = 0; i < images; i++) {
                        tempObjAddress[i] = listImage.get(i).getNativeObjAddr();
                    }
                    post(LOG.r("processPicture", "tempObjAddress", (System.currentTimeMillis() - time) + "ms"));

                    Mat result = new Mat();
                    // Call the OpenCV C++ Code to perform stitching process
                    try {
                        NativePanorama.processPanorama(tempObjAddress, result.getNativeObjAddr(), true);
                        post(LOG.r("processPicture", "processPanorama", (System.currentTimeMillis() - time) + "ms"));

                        //save to external storage
                        boolean isSaved = false;
                        if (!result.empty())
                            isSaved = ImageRW.saveResultImageExternal(result);
                        post(LOG.r("processPicture", "saveResultImageExternal", (System.currentTimeMillis() - time) + "ms"));
                        showToastRunnable(getString(R.string.msg_is_saved) + isSaved);
                    } catch (Exception e) {
                        post(LOG.r(TAG, "native processPanorama not working ", e));
                    }

                    for (Mat mat : listImage) mat.release();
                    listImage.clear();
                    post(LOG.r("processPicture", "clear memo listImage", (System.currentTimeMillis() - time) + "ms"));

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            hideProcessingDialog();
            isNotSaving = true;
            post(LOG.r("processPicture", "END", (System.currentTimeMillis() - time) + "ms"));
        };
        new Thread(r).start();

    }

    Runnable processPartPicture(final ArrayList<Integer> ids) {
        long time = System.currentTimeMillis();
        post(LOG.r(TAG, "processPartPicture BEGIN", time + "ms"));
        return () -> {
            final List<Mat> listImage;
            try {
                listImage = ImagePicker.loadPictureParts(ids);
            } catch (Exception e) {
                post(LOG.r(TAG, "run: loadPictureParts failed", e));
                return;
            }
            post(LOG.r("processPartPicture", "loadPictureParts", (System.currentTimeMillis() - time) + "ms"));
            try {
                int images = listImage.size();
                if (images > 0) {
                    post(LOG.r(TAG, "Pictures taken:" + images));
                    long[] tempObjAddress = new long[images];
                    for (int i = 0; i < images; i++) {
                        tempObjAddress[i] = listImage.get(i).getNativeObjAddr();
                    }
                    Mat result = new Mat();
                    // Call the OpenCV C++ Code to perform stitching process
                    try {
                        NativePanorama.processPanorama(tempObjAddress, result.getNativeObjAddr(), false);
                        post(LOG.r("processPartPicture", "processPanorama", (System.currentTimeMillis() - time) + "ms"));
                        //save to external storage
                        boolean isSaved = false;
                        if (!result.empty())
                            isSaved = ImageRW.savePartResultImageExternal(result);
                        post(LOG.r("processPartPicture", "savePartResultImageExternal", (System.currentTimeMillis() - time) + "ms"));
                        Message message = new Message();
                        message.what = SAVED_PART_IMAGE;
                        message.arg1 = isSaved ? 1 : 0;
                        showToastRunnable(getString(R.string.part_msg_is_saved) + isSaved);
                        threadHandler.sendMessage(message);
                    } catch (Exception e) {
                        post(LOG.r(TAG, "native processPanorama not working ", e));
                    }
                    for (Mat mat : listImage) mat.release();
                    listImage.clear();
                    post(LOG.r("processPartPicture", "clear memo listImage", (System.currentTimeMillis() - time) + "ms"));

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            post(LOG.r("processPartPicture", "END", (System.currentTimeMillis() - time) + "ms"));
        };
    }

    public void showToastRunnable(final String message) {
        post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showToast(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /***
     * when picture is processed, to release more cpu and gpu power camera preview is stopped, and
     * additionally process circle is shown
     */
    public synchronized void showProcessingDialog() {
        post(() -> {
            mCameraControl.stopPreview();
            mProgressBar.setVisibility(View.VISIBLE);
        });
    }

    public synchronized void hideProcessingDialog() {
        post(() -> {
            mCameraControl.startPreview();
            mProgressBar.setVisibility(View.GONE);
        });
    }

    private void loadPreferences() {
        switch (mPreferences.getActionMode()) {
            case Manual:
                mSwitchManual.setChecked(true);
                break;
            case FullAuto:
                mSwitchAuto.setChecked(true);
                break;
        }
        switch (mPreferences.getPictureMode()) {
            case auto:
                mSwitchAutoPicture.setChecked(true);
                break;
            case multithreaded:
                mSwitchMultithreaded.setChecked(true);
                break;
            case panorama:
                mSwitchPanorama.setChecked(true);
                break;
            case widePicture:
                mSwitchWide.setChecked(true);
                break;
            case picture360:
                mSwitch360.setChecked(true);
                break;
        }
        switch (mPreferences.getPictureQuality()) {
            case LOW:
                mSwitchLow.setChecked(true);
                break;
            case VERY_LOW:
                mSwitchVeryLow.setChecked(true);
                break;
            case HIGH:
                mSwitchHigh.setChecked(true);
                break;
        }
        mSettingsControl.setActionMode(mPreferences.getActionMode());
        mSettingsControl.setPictureMode(mPreferences.getPictureMode());
        mSettingsControl.setPictureQuality(mPreferences.getPictureQuality());
        mSaveDir.setText(mPreferences.getSaveDir());
    }

    private void setCaptureBtnImage() {
        switch (mShutterState) {
            case ready:
                switch (mSettingsControl.getActionMode()) {
                    case FullAuto:
                        captureBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.ready_auto));
                        break;
                    case Manual:
                        captureBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.ready));
                        break;
                }
                break;
            case recording:
                captureBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.rec));
                break;

        }
    }

    private void setScopeImage() {
        if (mManualControl.isCameraSteady()) {
            scope.setBackground(ContextCompat.getDrawable(this, R.drawable.scope));
        } else {
            scope.setBackground(ContextCompat.getDrawable(this, R.drawable.scope_2));
        }
    }

    private void onCaptureBtnClickAction() {
        switch (mShutterState) {
            case ready:
                switch (mSettingsControl.getActionMode()) {
                    case FullAuto:
                        mManualControl.startRendering();
                        mShutterState = ShutterState.recording;
                        break;
                    case Manual:
                        mManualControl.startRendering();
                        if (mPicturePosition.isCurrentPositionPossible())
                            mCameraControl.takePicture();
                        else showToast(getString(R.string.msg_take_picture_not_allowed));
                        break;
                }
                break;

            case recording:
                mManualControl.stopRendering();
                mShutterState = ShutterState.ready;
                break;

        }
        setCaptureBtnImage();
    }

    @OnClick(R.id.capture)
    void onCaptureClickListener() {
        if (isNotSaving)
            if (mShutterState == ShutterState.recording
                    || (mShutterState == ShutterState.ready
                    && mSettingsControl.getActionMode() == ActionMode.FullAuto))
                onCaptureBtnClickAction();
            else if (mManualControl.isCameraSteady()) {
                onCaptureBtnClickAction();
            } else showToast(getString(R.string.msg_do_not_move));
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.refresh_picture)
    void onRefreshClickListener() {
        if (isNotSaving) recreate();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.open_gallery)
    void onGalleryClickAction() {
        if (isNotSaving) {
            Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
            String folder = Environment.getExternalStorageDirectory() + "/PanoramaApp";
            intent.putExtra(GalleryActivity.INTENT_EXTRAS_FOLDER, folder);
            startActivity(intent);
            showToast(getString(R.string.msg_open_gallery));
        } else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.save_picture)
    void saveOnClickListener() {
        if (isNotSaving) {
            showToast(getString(R.string.msg_save));
            switch (mSettingsControl.getPictureMode()) {
                case auto:
                    processPicture(PictureMode.auto);
                    break;
                case multithreaded:
                    processPicture(PictureMode.multithreaded);
                    break;
                case panorama:
                    processPicture(PictureMode.panorama);
                    break;
                case widePicture:
                    processPicture(PictureMode.widePicture);
                    break;
                case picture360:
                    processPicture(PictureMode.picture360);
                    break;
            }
        } else showToast(R.string.msg_wait);
    }

    /**
     * this section is responsible to manage side navigation settings list
     *
     * @param item
     * @return
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @OnClick(R.id.mode_auto)
    void onSwitchAuto() {
        if (isNotSaving) {
            mPreferences.setActionMode(ActionMode.FullAuto);
            mPreferences.setLat(10);
            mPreferences.setLon(7);
            mSettingsControl.setActionMode(ActionMode.FullAuto);
            mSwitchManual.setChecked(false);
            setCaptureBtnImage();
            if (!mSwitchAuto.isChecked())
                mSwitchAuto.setChecked(true);
            recreate();
        } else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.mode_manual)
    void onSwitchManual() {
        if (isNotSaving)
            if (mSwitchAuto.isChecked()) {
                mPreferences.setActionMode(ActionMode.Manual);
                mPreferences.setLat(10);
                mPreferences.setLon(7);
                mSettingsControl.setActionMode(ActionMode.Manual);
                mSwitchAuto.setChecked(false);
                setCaptureBtnImage();
            } else onSwitchAuto();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.picture_auto)
    void onSwitchAutoPanorama() {
        if (isNotSaving) {
            threadHandler.sendEmptyMessage(STOP_PROCESSING);
            mPreferences.setPictureMode(PictureMode.auto);
            mPreferences.setLat(10);
            mPreferences.setLon(7);
            mSettingsControl.setPictureMode(PictureMode.auto);
            mSwitchPanorama.setChecked(false);
            mSwitchMultithreaded.setChecked(false);
            mSwitchWide.setChecked(false);
            mSwitch360.setChecked(false);
            if (!mSwitchAutoPicture.isChecked())
                mSwitchAutoPicture.setChecked(true);
            recreate();
        } else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.picture_multithreaded)
    void onSwitchMultithreadedPanorama() {
        if (isNotSaving) {
            if (mSwitchPanorama.isChecked() || mSwitchMultithreaded.isChecked() || mSwitch360.isChecked() || mSwitchWide.isChecked()) {
                mPreferences.setPictureMode(PictureMode.multithreaded);
                mPreferences.setLat(10);
                mPreferences.setLon(7);
                mSettingsControl.setPictureMode(PictureMode.multithreaded);
                mSwitchPanorama.setChecked(false);
                mSwitchWide.setChecked(false);
                mSwitchAutoPicture.setChecked(false);
                mSwitch360.setChecked(false);
                threadHandler.sendEmptyMessage(START_PROCESSING);
                recreate();
            } else onSwitchAutoPanorama();
        } else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.picture_panorama)
    void onSwitchPanorama() {
        if (isNotSaving)
            if (mSwitchPanorama.isChecked() || mSwitchMultithreaded.isChecked() || mSwitch360.isChecked() || mSwitchWide.isChecked()) {
                mPreferences.setPictureMode(PictureMode.panorama);
                mPreferences.setLat(7);
                mPreferences.setLon(3);
                mSettingsControl.setPictureMode(PictureMode.panorama);
                mSwitchAutoPicture.setChecked(false);
                mSwitchMultithreaded.setChecked(false);
                mSwitchWide.setChecked(false);
                mSwitch360.setChecked(false);
                recreate();
            } else onSwitchAutoPanorama();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.picture_wide)
    void onSwitchWide() {
        if (isNotSaving)
            if (mSwitchPanorama.isChecked() || mSwitchMultithreaded.isChecked() || mSwitch360.isChecked() || mSwitchWide.isChecked()) {
                mPreferences.setPictureMode(PictureMode.widePicture);
                mPreferences.setLat(10);
                mPreferences.setLon(7);
                mSettingsControl.setPictureMode(PictureMode.widePicture);
                mSwitchAutoPicture.setChecked(false);
                mSwitchMultithreaded.setChecked(false);
                mSwitchPanorama.setChecked(false);
                mSwitch360.setChecked(false);
                recreate();
            } else onSwitchAutoPanorama();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.picture_360)
    void onSwitch360() {
        if (isNotSaving)
            if (mSwitchPanorama.isChecked() || mSwitchMultithreaded.isChecked() || mSwitch360.isChecked() || mSwitchWide.isChecked()) {
                mPreferences.setPictureMode(PictureMode.picture360);
                mPreferences.setLat(7);
                mPreferences.setLon(5);
                mSettingsControl.setPictureMode(PictureMode.picture360);
                mSwitchMultithreaded.setChecked(false);
                mSwitchAutoPicture.setChecked(false);
                mSwitchWide.setChecked(false);
                mSwitchPanorama.setChecked(false);
                recreate();
            } else onSwitchAutoPanorama();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.quality_high)
    void onSwitchHigh() {
        if (isNotSaving)
            if (mSwitchLow.isChecked() || mSwitchVeryLow.isChecked()) {
                mPreferences.setPictureQuality(PictureQuality.HIGH);
                mSettingsControl.setPictureQuality(PictureQuality.HIGH);
                mSwitchLow.setChecked(false);
                mSwitchVeryLow.setChecked(false);
                recreate();
            } else onSwitchLow();
        else showToast(R.string.msg_wait);
    }

    @OnClick(R.id.quality_low)
    void onSwitchLow() {
        if (isNotSaving) {
            mPreferences.setPictureQuality(PictureQuality.LOW);
            mSettingsControl.setPictureQuality(PictureQuality.LOW);
            mSwitchHigh.setChecked(false);
            mSwitchVeryLow.setChecked(false);
            if (!mSwitchLow.isChecked())
                mSwitchLow.setChecked(true);
            recreate();
        } else showToast(R.string.msg_wait);

    }

    @OnClick(R.id.quality_very_low)
    void onSwitchVeryLow() {
        if (isNotSaving) {
            if (mSwitchLow.isChecked() || mSwitchHigh.isChecked()) {
                mPreferences.setPictureQuality(PictureQuality.VERY_LOW);
                mSettingsControl.setPictureQuality(PictureQuality.VERY_LOW);
                mSwitchHigh.setChecked(false);
                mSwitchLow.setChecked(false);
                recreate();
            } else onSwitchLow();
        } else showToast(R.string.msg_wait);


    }

    private enum ShutterState {
        ready, recording;

        public static ShutterState stringToEnum(String s) {
            try {
                return valueOf(s);
            } catch (Exception e) {
                LOG.e("ShutterState", "string casting failed", e);
                return ready;
            }
        }
    }
}