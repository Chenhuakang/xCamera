package me.shouheng.camerax.manager.impl;

import android.content.Context;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import me.shouheng.camerax.config.ConfigurationProvider;
import me.shouheng.camerax.config.calculator.CameraSizeCalculator;
import me.shouheng.camerax.enums.Camera;
import me.shouheng.camerax.enums.Flash;
import me.shouheng.camerax.enums.Media;
import me.shouheng.camerax.enums.Preview;
import me.shouheng.camerax.listener.CameraOpenListener;
import me.shouheng.camerax.listener.CameraPhotoListener;
import me.shouheng.camerax.listener.CameraVideoListener;
import me.shouheng.camerax.preview.CameraPreview;
import me.shouheng.camerax.preview.CameraPreviewCallback;
import me.shouheng.camerax.util.CameraHelper;
import me.shouheng.camerax.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author WngShhng (shouheng2015@gmail.com)
 * @version 2019/4/13 22:51
 */
public class Camera1Manager extends BaseCameraManager<Integer> {

    private static final String TAG = "Camera1Manager";

    private android.hardware.Camera camera;

    public Camera1Manager(CameraPreview cameraPreview) {
        super(cameraPreview);
        cameraFace = ConfigurationProvider.get().getDefaultCameraFace();
        cameraPreview.setCameraPreviewCallback(new CameraPreviewCallback() {
            @Override
            public void onAvailable(CameraPreview cameraPreview) {
                Logger.d(TAG, "onAvailable : " + cameraPreview.isAvailable());
                if (camera != null) {
                    setupPreview();
                }
            }
        });
    }

    @Override
    public void initialize(Context context) {
        super.initialize(context);
        initCameraInfo();
    }

    @Override
    public void openCamera(final CameraOpenListener cameraOpenListener) {
        super.openCamera(cameraOpenListener);
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG, "openCamera");
                try {
                    // TODO test the order of this method with callback from preview
                    camera = android.hardware.Camera.open(currentCameraId);
                    prepareCameraOutputs();
                    adjustCameraParameters(false, true, true);
                    if (cameraPreview.isAvailable()) {
                        setupPreview();
                    }
                    camera.startPreview();
                    showingPreview = true;
                    notifyCameraOpened();
                } catch (final Exception ex) {
                    Logger.e(TAG, "error : " + ex);
                    notifyCameraOpenError(ex);
                }
            }
        });
    }

    @Override
    public boolean isCameraOpened() {
        return camera != null;
    }

    @Override
    public void setMediaType(@Media.Type int mediaType) {
        if (this.mediaType == mediaType) {
            return;
        }
        this.mediaType = mediaType;
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        adjustCameraParameters(true, false, false);
                    } catch (Exception ex) {
                        Logger.e(TAG, "setMediaType : " + ex);
                    }
                }
            });
        }
    }

    @Override
    public void setVoiceEnable(boolean voiceEnable) {
        if (voiceEnabled == voiceEnable) {
            return;
        }
        this.voiceEnabled = voiceEnable;
    }

    @Override
    public boolean isVoiceEnable() {
        return voiceEnabled;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (this.isAutoFocus == autoFocus) {
            return;
        }
        this.isAutoFocus = autoFocus;
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    setFocusModeInternal(null);
                }
            });
        }
    }

    @Override
    public boolean isAutoFocus() {
        return isAutoFocus;
    }

    @Override
    public void setFlashMode(@Flash.FlashMode int flashMode) {
        if (this.flashMode == flashMode) {
            return;
        }
        this.flashMode = flashMode;
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    setFlashModeInternal(null);
                }
            });
        }
    }

    @Flash.FlashMode
    @Override
    public int getFlashMode() {
        return flashMode;
    }

    @Override
    public void setRoom(float zoom) {
        if (zoom == this.zoom || zoom > getMaxRoom()) {
            return;
        }
        this.zoom = zoom;
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    setZoomInternal(null);
                }
            });
        }
    }

    @Override
    public float getRoom() {
        return zoom;
    }

    @Override
    public float getMaxRoom() {
        if (maxZoom == 0) {
            maxZoom = zoomRatios.get(zoomRatios.size() - 1);
        }
        return maxZoom;
    }

    @Override
    public void takePicture(CameraPhotoListener cameraPhotoListener) {
        super.takePicture(cameraPhotoListener);
        if (!isCameraOpened()) {
            notifyCameraCaptureFailed(new RuntimeException("Camera not open yet!"));
            return;
        }
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!takingPicture) {
                            takingPicture = true;
                            camera.takePicture(voiceEnabled ? new android.hardware.Camera.ShutterCallback() {
                                @Override
                                public void onShutter() {
                                }
                            } : null, null, new android.hardware.Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(byte[] bytes, android.hardware.Camera camera) {
                                    takingPicture = false;
                                    notifyCameraPictureTaken(bytes);
                                }
                            });
                        } else {
                            Logger.i(TAG, "takePicture : taking picture");
                        }
                    } catch (Exception ex) {
                        takingPicture = false;
                        Logger.e(TAG, "takePicture error : " + ex);
                        notifyCameraCaptureFailed(new RuntimeException(ex));
                    }
                }
            });
        }
    }

    @Override
    public void startVideoRecord(File file, CameraVideoListener cameraVideoListener) {
        super.startVideoRecord(file, cameraVideoListener);
        if (videoRecording) {
            return;
        }
        if (isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (prepareVideoRecorder()) {
                        videoRecorder.start();
                        videoRecording = true;
                        notifyVideoRecordStart();
                    }
                }
            });
        }
    }

    @Override
    public void stopVideoRecord() {
        if (videoRecording && isCameraOpened()) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    safeStopVideoRecorder();
                    releaseVideoRecorder();
                    videoRecording = false;
                    notifyVideoRecordStop(videoOutFile);
                }
            });
        }
    }

    @Override
    public void resumePreview() {
        if (isCameraOpened()) {
            camera.startPreview();
        }
    }

    @Override
    public void closeCamera() {
    }

    /*--------------------------------------inner methods-----------------------------------------*/

    private void initCameraInfo() {
        numberOfCameras = android.hardware.Camera.getNumberOfCameras();
        for (int i=0; i<numberOfCameras; i++) {
            android.hardware.Camera.CameraInfo cameraInfo = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
                rearCameraId = i;
                rearCameraOrientation = cameraInfo.orientation;
            } else if (cameraInfo.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontCameraId = i;
                frontCameraOrientation = cameraInfo.orientation;
            }
        }

        currentCameraId = cameraFace == Camera.FACE_REAR ? rearCameraId : frontCameraId;
    }

    private void prepareCameraOutputs() {
        try {
            long start = System.currentTimeMillis();
            previewSizes = ConfigurationProvider.get().getPreviewSizes(camera);
            pictureSizes = ConfigurationProvider.get().getPictureSizes(camera);
            videoSizes = ConfigurationProvider.get().getVideoSizes(camera);
            zoomRatios = ConfigurationProvider.get().getZoomRatios(camera);
            Logger.d(TAG, "prepareCameraOutputs cost : " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception ex) {
            Logger.e(TAG, "error : " + ex);
            notifyCameraOpenError(new RuntimeException(ex));
        }
    }

    private void adjustCameraParameters(boolean forceCalculateSizes,
                                        boolean changeFocusMode,
                                        boolean changeFlashMode) {
        long start = System.currentTimeMillis();
        CameraSizeCalculator cameraSizeCalculator = ConfigurationProvider.get().getCameraSizeCalculator();
        android.hardware.Camera.Parameters parameters = camera.getParameters();
        if (mediaType == Media.TYPE_PICTURE && (pictureSize == null || forceCalculateSizes)) {
            pictureSize = cameraSizeCalculator.getPictureSize(pictureSizes, aspectRatio, userSize);
            previewSize = cameraSizeCalculator.getPicturePreviewSize(previewSizes, pictureSize);
            parameters.setPictureSize(pictureSize.width, pictureSize.height);
        }
        if (mediaType == Media.TYPE_VIDEO && (camcorderProfile == null || forceCalculateSizes)) {
            camcorderProfile = CameraHelper.getCamcorderProfile(mediaQuality, currentCameraId);
        }
        if (mediaType == Media.TYPE_VIDEO && (videoSize == null || forceCalculateSizes)) {
            videoSize = cameraSizeCalculator.getVideoSize(videoSizes, aspectRatio, userSize);
            previewSize = cameraSizeCalculator.getVideoPreviewSize(previewSizes, videoSize);
        }
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        Logger.d(TAG, "adjustCameraParameters size cost : " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        if (changeFocusMode) {
            setFocusModeInternal(parameters);
        }
        Logger.d(TAG, "adjustCameraParameters focus cost : " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        if (changeFlashMode) {
            setFlashModeInternal(parameters);
        }
        Logger.d(TAG, "adjustCameraParameters flash cost : " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        setZoomInternal(parameters);
        Logger.d(TAG, "adjustCameraParameters zoom cost : " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        if (showingPreview) {
            showingPreview = false;
            camera.stopPreview();
        }
        camera.setParameters(parameters);
        if (!showingPreview) {
            showingPreview = true;
            camera.startPreview();
        }
        Logger.d(TAG, "adjustCameraParameters restart preview cost : " + (System.currentTimeMillis() - start) + " ms");
    }

    private void setupPreview() {
        try {
            if (cameraPreview.getPreviewType() == Preview.SURFACE_VIEW) {
                if (showingPreview) {
                    camera.stopPreview();
                    showingPreview = false;
                }
                camera.setPreviewDisplay(cameraPreview.getSurfaceHolder());
                if (!showingPreview) {
                    camera.startPreview();
                    showingPreview = true;
                }
            } else {
                camera.setPreviewTexture(cameraPreview.getSurfaceTexture());
            }

            camera.setDisplayOrientation(CameraHelper.calDisplayOrientation(context, cameraFace,
                    cameraFace == Camera.FACE_FRONT ? frontCameraOrientation : rearCameraOrientation));
        } catch (IOException e) {
            notifyCameraOpenError(new RuntimeException(e));
        }
    }

    private boolean prepareVideoRecorder() {
        videoRecorder = new MediaRecorder();
        try {
            camera.lock();
            camera.unlock();
            videoRecorder.setCamera(camera);

            videoRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            videoRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

            videoRecorder.setOutputFormat(camcorderProfile.fileFormat);
            videoRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
            videoRecorder.setVideoSize(videoSize.width, videoSize.height);
            videoRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
            videoRecorder.setVideoEncoder(camcorderProfile.videoCodec);

            videoRecorder.setAudioEncodingBitRate(camcorderProfile.audioBitRate);
            videoRecorder.setAudioChannels(camcorderProfile.audioChannels);
            videoRecorder.setAudioSamplingRate(camcorderProfile.audioSampleRate);
            videoRecorder.setAudioEncoder(camcorderProfile.audioCodec);

            // TODO max oupt file size and max output duration configuration
            
            videoRecorder.setOutputFile(videoOutFile.toString());

            videoRecorder.setPreviewDisplay(cameraPreview.getSurface());
            videoRecorder.prepare();

            return true;
        } catch (IllegalStateException error) {
            Logger.e(TAG, "IllegalStateException preparing MediaRecorder: " + error.getMessage());
            notifyVideoRecordError(error);
        } catch (IOException error) {
            Logger.e(TAG, "IOException preparing MediaRecorder: " + error.getMessage());
            notifyVideoRecordError(error);
        } catch (Throwable error) {
            Logger.e(TAG, "Error during preparing MediaRecorder: " + error.getMessage());
            notifyVideoRecordError(error);
        }

        releaseVideoRecorder();
        return false;
    }

    private void setFocusModeInternal(@Nullable android.hardware.Camera.Parameters parameters) {
        boolean nullParameters = parameters == null;
        parameters = nullParameters ? camera.getParameters() : parameters;
        if (mediaType == Media.TYPE_VIDEO) {
            if (!turnVideoCameraFeaturesOn(parameters)) {
                setAutoFocusInternal(parameters);
            }
        } else if (mediaType == Media.TYPE_PICTURE) {
            if (!turnPhotoCameraFeaturesOn(parameters)) {
                setAutoFocusInternal(parameters);
            }
        }
        if (nullParameters) {
            camera.setParameters(parameters);
        }
    }

    private boolean turnPhotoCameraFeaturesOn(@NonNull android.hardware.Camera.Parameters parameters) {
        if (parameters.getSupportedFocusModes().contains(
                android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            return true;
        }
        return false;
    }

    private boolean turnVideoCameraFeaturesOn(@NonNull android.hardware.Camera.Parameters parameters) {
        if (parameters.getSupportedFocusModes().contains(
                android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            return true;
        }
        return false;
    }

    private void setAutoFocusInternal(@NonNull android.hardware.Camera.Parameters parameters) {
        try {
            final List<String> modes = parameters.getSupportedFocusModes();
            if (isAutoFocus && modes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);
            } else if (modes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_FIXED)) {
                parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_INFINITY)) {
                parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                parameters.setFocusMode(modes.get(0));
            }
        } catch (Exception ex) {
            Logger.e(TAG, "setAutoFocusInternal " + ex);
        }
    }

    private void setFlashModeInternal(@Nullable android.hardware.Camera.Parameters parameters) {
        boolean nullParameters = parameters == null;
        parameters = nullParameters ? camera.getParameters() : parameters;
        List<String> modes = parameters.getSupportedFlashModes();
        try {
            switch (flashMode) {
                case Flash.FLASH_ON:
                    setFlashModeOrAuto(parameters, modes, android.hardware.Camera.Parameters.FLASH_MODE_ON);
                    break;
                case Flash.FLASH_OFF:
                    setFlashModeOrAuto(parameters, modes, android.hardware.Camera.Parameters.FLASH_MODE_OFF);
                    break;
                case Flash.FLASH_AUTO:
                default:
                    if (modes.contains(android.hardware.Camera.Parameters.FLASH_MODE_AUTO)) {
                        parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_AUTO);
                    }
                    break;
            }
            if (nullParameters) {
                camera.setParameters(parameters);
            }
        } catch (Exception ex) {
            Logger.e(TAG, "setFlashModeInternal : " + ex);
        }
    }

    private void setFlashModeOrAuto(android.hardware.Camera.Parameters parameters, List<String> supportModes, String mode) {
        if (supportModes.contains(mode)) {
            parameters.setFlashMode(mode);
        } else {
            if (supportModes.contains(android.hardware.Camera.Parameters.FLASH_MODE_AUTO)) {
                parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_AUTO);
            }
        }
    }

    private void setZoomInternal(@Nullable android.hardware.Camera.Parameters parameters) {
        boolean nullParameters = parameters == null;
        parameters = nullParameters ? camera.getParameters() : parameters;
        if (parameters.isZoomSupported()) {
            parameters.setZoom(CameraHelper.getZoomIdxForZoomFactor(parameters.getZoomRatios(), zoom));
            if (nullParameters) {
                camera.setParameters(parameters);
            }
        }
    }

}
