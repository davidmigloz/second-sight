package com.davidmiguel.secondsight;

import android.content.ContentValues;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.davidmiguel.secondsight.filters.Filter;
import com.davidmiguel.secondsight.filters.NoneFilter;
import com.davidmiguel.secondsight.filters.convolution.StrokeEdgesFilter;
import com.davidmiguel.secondsight.filters.curve.CrossProcessCurveFilter;
import com.davidmiguel.secondsight.filters.curve.PortraCurveFilter;
import com.davidmiguel.secondsight.filters.curve.ProviaCurveFilter;
import com.davidmiguel.secondsight.filters.curve.VelviaCurveFilter;
import com.davidmiguel.secondsight.filters.mixer.RecolorCMVFilter;
import com.davidmiguel.secondsight.filters.mixer.RecolorRCFilter;
import com.davidmiguel.secondsight.filters.mixer.RecolorRGVFilter;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements CvCameraViewListener2 {

    // A tag for log output
    private static final String TAG = CameraActivity.class.getSimpleName();
    // A key for storing the index of the active camera
    private static final String STATE_CAMERA_INDEX = "cameraIndex";
    // A key for storing the index of the active image size
    private static final String STATE_IMAGE_SIZE_INDEX = "imageSizeIndex";
    // An ID for items in the image size submenu
    private static final int MENU_GROUP_ID_SIZE = 2;
    // Keys for storing the indices of the active filters
    private static final String STATE_CURVE_FILTER_INDEX = "curveFilterIndex";
    private static final String STATE_MIXER_FILTER_INDEX = "mixerFilterIndex";
    private static final String STATE_CONVOLUTION_FILTER_INDEX = "convolutionFilterIndex";
    // The index of the active camera
    private int mCameraIndex;
    // The index of the active image size
    private int mImageSizeIndex;
    // Whether the active camera is front-facing
    // If so, the camera view should be mirrored
    private boolean mIsCameraFrontFacing;
    // The number of cameras on the device
    private int mNumCameras;
    // The camera view
    private CameraBridgeViewBase mCameraView;
    // The image sizes supported by the active camera
    private List<Size> mSupportedImageSizes;
    // Whether the next camera frame should be saved as a photo
    private boolean mIsPhotoPending;
    // A matrix that is used when saving photos
    private Mat mBgr;
    // Whether an asynchronous menu action is in progress
    // If so, menu interaction should be disabled
    private boolean mIsMenuLocked;
    // The filters
    private Filter[] mCurveFilters;
    private Filter[] mMixerFilters;
    private Filter[] mConvolutionFilters;
    // The indices of the active filters
    private int mCurveFilterIndex;
    private int mMixerFilterIndex;
    private int mConvolutionFilterIndex;
    // The OpenCV loader callback
    private BaseLoaderCallback mLoaderCallback =
            new BaseLoaderCallback(this) {
                @Override
                public void onManagerConnected(final int status) {
                    switch (status) {
                        case LoaderCallbackInterface.SUCCESS:
                            Log.d(TAG, "OpenCV loaded successfully");
                            mCameraView.enableView();
                            //mCameraView.enableFpsMeter();
                            mBgr = new Mat();
                            mCurveFilters = new Filter[] {
                                    new NoneFilter(),
                                    new PortraCurveFilter(),
                                    new ProviaCurveFilter(),
                                    new VelviaCurveFilter(),
                                    new CrossProcessCurveFilter()
                            };
                            mMixerFilters = new Filter[] {
                                    new NoneFilter(),
                                    new RecolorRCFilter(),
                                    new RecolorRGVFilter(),
                                    new RecolorCMVFilter()
                            };
                            mConvolutionFilters = new Filter[] {
                                    new NoneFilter(),
                                    new StrokeEdgesFilter()
                            };
                            break;
                        default:
                            super.onManagerConnected(status);
                            break;
                    }
                }
            };

    /**
     * Sets up the camera view and initializes data about the cameras. It also reads any previous
     * data about the active camera that may have been written by onSaveInstanceState.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // Don't switch off screen

        if (savedInstanceState != null) {
            mCameraIndex = savedInstanceState.getInt(
                    STATE_CAMERA_INDEX, 0);
            mImageSizeIndex = savedInstanceState.getInt(
                    STATE_IMAGE_SIZE_INDEX, 0);
            mCurveFilterIndex = savedInstanceState.getInt(
                    STATE_CURVE_FILTER_INDEX, 0);
            mMixerFilterIndex = savedInstanceState.getInt(
                    STATE_MIXER_FILTER_INDEX, 0);
            mConvolutionFilterIndex = savedInstanceState.getInt(
                    STATE_CONVOLUTION_FILTER_INDEX, 0);
        } else {
            mCameraIndex = 0;
            mImageSizeIndex = 0;
            mCurveFilterIndex = 0;
            mMixerFilterIndex = 0;
            mConvolutionFilterIndex = 0;
        }

        final Camera camera;
        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(mCameraIndex, cameraInfo);
        mIsCameraFrontFacing = (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
        mNumCameras = Camera.getNumberOfCameras();
        camera = Camera.open(mCameraIndex);

        final Parameters parameters = camera.getParameters();
        camera.release();
        mSupportedImageSizes = parameters.getSupportedPreviewSizes();
        final Size size = mSupportedImageSizes.get(mImageSizeIndex);
        mCameraView = new JavaCameraView(this, mCameraIndex);
        mCameraView.setMaxFrameSize(size.width, size.height);
        mCameraView.setCvCameraViewListener(this);
        setContentView(mCameraView);
    }

    /**
     * Disable camera when the activity goes into background.
     */
    @Override
    public void onPause() {
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        super.onPause();
    }

    /**
     * Initialize the OpenCV library when the activity comes into foreground.
     * (The camera view is enabled once the library is successfully initialized).
     * The menu interaction is reenabled.
     */
    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        mIsMenuLocked = false;
    }

    /**
     * Disable camera when the activity finishes.
     */
    @Override
    public void onDestroy() {
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        super.onDestroy();
    }

    /**
     * Saves the current camera index and the image size index.
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current camera index
        savedInstanceState.putInt(STATE_CAMERA_INDEX, mCameraIndex);

        // Save the current image size index
        savedInstanceState.putInt(STATE_IMAGE_SIZE_INDEX,
                mImageSizeIndex);

        // Save the current filter indices
        savedInstanceState.putInt(STATE_CURVE_FILTER_INDEX,
                mCurveFilterIndex);
        savedInstanceState.putInt(STATE_MIXER_FILTER_INDEX,
                mMixerFilterIndex);
        savedInstanceState.putInt(STATE_CONVOLUTION_FILTER_INDEX,
                mConvolutionFilterIndex);

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Load menu from its resource file. Then, if the device has only one camera, the Next Cam menu
     * item is removed. If the active camera supports more than one image size, a set of menu
     * options for all the supported sizes is created.
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_camera, menu);
        if (mNumCameras < 2) {
            // Remove the option to switch cameras, since there is only 1
            menu.removeItem(R.id.menu_next_camera);
        }
        int numSupportedImageSizes = mSupportedImageSizes.size();
        if (numSupportedImageSizes > 1) {
            final SubMenu sizeSubMenu = menu.addSubMenu(R.string.menu_image_size);
            for (int i = 0; i < numSupportedImageSizes; i++) {
                final Size size = mSupportedImageSizes.get(i);
                sizeSubMenu.add(MENU_GROUP_ID_SIZE, i, Menu.NONE,
                        String.format("%dx%d", size.width, size.height));
            }
        }
        return true;
    }

    /**
     * Handle any image size menu item by recreating the activity with the specified image size.
     * Similarly, it handles the Next Cam menu item by cycling to the next camera index and then
     * recreating the activity. (Image size index and camera index is saved in onSaveInstanceState
     * and restored in onCreate, where it is used to construct the camera view). It handles the
     * Take Photo menu item by setting a Boolean value, which we check in an OpenCV callback later.
     * In either case, it blocks any further handling of menu options until the current handling
     * is complete (for example, until onResume).
     */
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mIsMenuLocked) {
            return true;
        }
        if (item.getGroupId() == MENU_GROUP_ID_SIZE) {
            mImageSizeIndex = item.getItemId();
            recreate();
            return true;
        }
        switch (item.getItemId()) {
            case R.id.menu_next_curve_filter:
                mCurveFilterIndex++;
                if (mCurveFilterIndex == mCurveFilters.length) {
                    mCurveFilterIndex = 0;
                }
                return true;
            case R.id.menu_next_mixer_filter:
                mMixerFilterIndex++;
                if (mMixerFilterIndex == mMixerFilters.length) {
                    mMixerFilterIndex = 0;
                }
                return true;
            case R.id.menu_next_convolution_filter:
                mConvolutionFilterIndex++;
                if (mConvolutionFilterIndex ==
                        mConvolutionFilters.length) {
                    mConvolutionFilterIndex = 0;
                }
                return true;
            case R.id.menu_next_camera:
                mIsMenuLocked = true;
                // With another camera index, recreate the activity
                mCameraIndex++;
                if (mCameraIndex == mNumCameras) {
                    mCameraIndex = 0;
                }
                mImageSizeIndex = 0;
                recreate();
                return true;
            case R.id.menu_take_photo:
                mIsMenuLocked = true;
                // Next frame, take the photo
                mIsPhotoPending = true;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    /**
     * If the user has requested a photo, one should be taken. If the active camera is front-facing
     * (that is, user-facing), the camera view should be mirrored (horizontally flipped).
     */
    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        final Mat rgba = inputFrame.rgba();

        // Apply the active filters.
        mCurveFilters[mCurveFilterIndex].apply(rgba, rgba);
        mMixerFilters[mMixerFilterIndex].apply(rgba, rgba);
        mConvolutionFilters[mConvolutionFilterIndex].apply(rgba, rgba);

        if (mIsPhotoPending) {
            mIsPhotoPending = false;
            takePhoto(rgba);
        }

        if (mIsCameraFrontFacing) {
            // Mirror (horizontally flip) the preview
            Core.flip(rgba, rgba, 1);
        }

        return rgba;
    }

    /**
     * Saves the image to a disk and enable other apps to find it via Android's MediaStore.
     */
    private void takePhoto(final Mat rgba) {
        // Determine the path and metadata for the photo
        final long currentTimeMillis = System.currentTimeMillis();
        final String appName = getString(R.string.app_name);
        final String galleryPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).toString();
        final String albumPath = galleryPath + File.separator +
                appName;
        final String photoPath = albumPath + File.separator +
                currentTimeMillis + LabActivity.PHOTO_FILE_EXTENSION;
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, photoPath);
        values.put(Images.Media.MIME_TYPE, LabActivity.PHOTO_MIME_TYPE);
        values.put(Images.Media.TITLE, appName);
        values.put(Images.Media.DESCRIPTION, appName);
        values.put(Images.Media.DATE_TAKEN, currentTimeMillis);
        // Ensure that the album directory exists
        File album = new File(albumPath);
        if (!album.isDirectory() && !album.mkdirs()) {
            Log.e(TAG, "Failed to create album directory at " + albumPath);
            onTakePhotoFailed();
            return;
        }
        // Try to create the photo
        Imgproc.cvtColor(rgba, mBgr, Imgproc.COLOR_RGBA2BGR, 3);
        if (!Imgcodecs.imwrite(photoPath, mBgr)) {
            Log.e(TAG, "Failed to save photo to " + photoPath);
            onTakePhotoFailed();
        }
        Log.d(TAG, "Photo saved successfully to " + photoPath);
        // Try to insert the photo into the MediaStore
        Uri uri;
        try {
            uri = getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to insert photo into MediaStore");
            e.printStackTrace();
            // Since the insertion failed, delete the photo
            File photo = new File(photoPath);
            if (!photo.delete()) {
                Log.e(TAG, "Failed to delete non-inserted photo");
            }
            onTakePhotoFailed();
            return;
        }
        // Open the photo in LabActivity
        final Intent intent = new Intent(this, LabActivity.class);
        intent.putExtra(LabActivity.EXTRA_PHOTO_URI, uri);
        intent.putExtra(LabActivity.EXTRA_PHOTO_DATA_PATH,
                photoPath);
        startActivity(intent);
    }

    /**
     * Unlocks the menu interaction and shows an error message to the user when a failure is
     * encountered when saving the photo (i.e. no rights, no free space, name colision...).
     */
    private void onTakePhotoFailed() {
        mIsMenuLocked = false;
        // Show an error message
        final String errorMessage = getString(R.string.photo_error_message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CameraActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
