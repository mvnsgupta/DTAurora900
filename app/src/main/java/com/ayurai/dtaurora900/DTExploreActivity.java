package com.ayurai.dtaurora900;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.deptrum.usblite.callback.IDeviceListener;
import com.deptrum.usblite.callback.IStreamListener;
import com.deptrum.usblite.param.DTFrameStreamBean;
import com.deptrum.usblite.param.DeviceLoggerInfo;
import com.deptrum.usblite.param.StreamParam;
import com.deptrum.usblite.param.StreamType;
import com.deptrum.usblite.param.TemperatureType;
import com.deptrum.usblite.sdk.DeptrumSdkApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class DTExploreActivity extends AppCompatActivity {

    public static final int MESSAGE_UI = 0x01;
    private static final String TAG = "Depth_DTAurora900";
    private long mDrawRgbTime;
    private int mRgbCount = 0;
    private long mDrawDepTime;
    private int mDepCount = 0;

    private RelativeLayout rootLayout;
    private ImageView mRgbView;
    private ImageView mIrView;
    private ImageView mDepthView;
    private TextView tvCenterCount;

    private FaceView mFaceView;

    private Bitmap mDepthBitmap;
    private Bitmap mRgbBitmap = null;
    private Bitmap mIrBitmap = null;
    private final Size mPreviewSize = new Size(480, 768);
    private ExecutorService mOpenExecutors;
    private TextView tvFpsRgb;
    private TextView tvFpsIr;
    private TextView tvFpsDepth;
    private TextView tvSn;
    private TextView tvCameraName;
    private TextView tvCameraSdkVersion;
    private TextView tvCameraFwVersion;
    private TextView tvSensorTemperatureCamera;
    private TextView tvSensorTemperatureVsel;
    private TextView tvSensorTemperatureCpu;
    private TextView tvDebug;
    private ScheduledExecutorService mScheduledExecutorService ;
    private static final int IR_WIDTH = 400;
    private static final int IR_HEIGHT = 640;

    private static final int RGB_WIDTH = 480;
    private static final int RGB_HEIGHT = 768;

    private int mViewWidth = 0;
    private static final int VIEW_HEIGHT = 459;

    private byte[] mIrBits = null;
    private int mIrLength = 0;

    private byte[] mRgbBits = null;
    private int mRgbLength = 0;

    private int mColorLength = 0;
    private int[] mColor = null;

    private byte[] mDeDataByte = null;
    int mDeDataByteLength = 0;
    private ExecutorService rgbService = Executors.newSingleThreadExecutor();
    private ExecutorService irService = Executors.newSingleThreadExecutor();
    private ExecutorService depthService = Executors.newSingleThreadExecutor();

    private boolean isInDoor = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MESSAGE_UI) {
                if (null == mScheduledExecutorService) {
                    mScheduledExecutorService = Executors.newScheduledThreadPool(1);
                }
                mScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {

                        String snStr = tvSn.getText().toString();
                        if (TextUtils.isEmpty(snStr) || snStr.contains("无法")){
                            String sn = DeptrumSdkApi.getApi().getSerialNumber();
                            String cameraName = DeptrumSdkApi.getApi().getCameraName();
                            String cameraSDKVersion = DeptrumSdkApi.getApi().getCameraSDKVersion();
                            String cameraFwVersion = DeptrumSdkApi.getApi().getCameraFwVersion();

                            tvSn.post(new Runnable() {
                                @Override
                                public void run() {
                                    tvSn.setText(String.format("SN: %s", sn));
                                    tvCameraName.setText(String.format("CameraName: %s", cameraName));
                                    tvCameraSdkVersion.setText(String.format("CameraSdkVersion: %s", cameraSDKVersion));
                                    tvCameraFwVersion.setText(String.format("CameraFwVersion: %s", cameraFwVersion));
                                }
                            });
                        }
                        int cameraTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_CAMERA);
                        int vcselTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_VCSEL);
                        int cpuTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_CPU);
                        tvSensorTemperatureCamera.post(new Runnable() {
                            @Override
                            public void run() {
                                if (cameraTemp> 0 && cameraTemp < 1000){
                                    tvSensorTemperatureCamera.setText(cameraTemp > 0 ? String.format("Temperature_Camera: %d°", cameraTemp) : "Temperature_Camera: 异常");
                                }
                                if (vcselTemp> 0 && vcselTemp < 1000){
                                    tvSensorTemperatureVsel.setText(vcselTemp > 0 ? String.format("Temperature_Vcsel: %d°", vcselTemp) : "Temperature_Vcsel: 异常");
                                }
                                if (cpuTemp> 0 && cpuTemp < 1000){
                                    tvSensorTemperatureCpu.setText(cpuTemp > 0 ? String.format("Temperature_Cpu: %d°", cpuTemp) : "Temperature_Cpu: 异常");

                                }
                            }
                        });
                    }
                }, 0,3, TimeUnit.SECONDS);
            }
        }
    };

    private void exit(){
        DeptrumSdkApi.getApi().stopStream(StreamType.STREAM_RGB_IR_DEPTH);
        int ret = DeptrumSdkApi.getApi().close();
        if (0 == ret) {
            if (null != mOpenExecutors) {
                mOpenExecutors.shutdownNow();
                mOpenExecutors = null;
            }
            if (null != rgbService) {
                rgbService.shutdownNow();
                rgbService = null;
            }
            if (null != irService) {
                irService.shutdownNow();
                irService = null;
            }
            if (null != depthService) {
                depthService.shutdownNow();
                depthService = null;
            }
            if (null != mScheduledExecutorService) {
                mScheduledExecutorService.shutdownNow();
                mScheduledExecutorService = null;
            }
            finish();
        }
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            exit();
        }
    };

    private View.OnClickListener mOnGetInfoListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            DeviceLoggerInfo loggerInfo = new DeviceLoggerInfo();
            DeptrumSdkApi.getApi().getDeviceDebugInfo(loggerInfo);

            tvDebug.post(new Runnable() {
                @Override
                public void run() {
                    tvDebug.setText(loggerInfo.toString());
                }
            });
        }
    };

    private View.OnLongClickListener mOnGetInfoLongListener = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            if (isInDoor){
                if (0 == DeptrumSdkApi.getApi().setLaserDriver(3)){
                    Toast.makeText(DTExploreActivity.this, "已经切换到室外模式", Toast.LENGTH_SHORT).show();
                }
            }
            else {
                if (0 == DeptrumSdkApi.getApi().setLaserDriver(2)){
                    Toast.makeText(DTExploreActivity.this, "已经切换到室内模式", Toast.LENGTH_SHORT).show();
                }
            }
            isInDoor = (!isInDoor);
            return false;
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        exit();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(initUi());
        mOpenExecutors = Executors.newSingleThreadExecutor();
        mOpenExecutors.execute(new Runnable() {
            @Override
            public void run() {
                openDevice();
            }
        });
    }

    private RectF convertRgb(double tLeft, double tTop, double tRight, double tBottom){

        double xRatio = (((double)mViewWidth)/((double) RGB_WIDTH));
        double yRatio = (((double) VIEW_HEIGHT)/((double) RGB_HEIGHT));

        int left = (int)(tLeft * xRatio);
        int top = (int)(tTop * yRatio);
        int right = (int)(tRight * xRatio);
        int bottom = (int)(tBottom * yRatio);

        return new RectF(left,top,right,bottom);
    }

    private void openDevice() {
        long startTime = System.currentTimeMillis();
        DeptrumSdkApi.getApi().open(getApplicationContext(), new IDeviceListener() {
            @Override
            public void onAttach() {
                // device onAttach
            }

            @Override
            public void onDetach() {
                // device onAttach
            }

            @Override
            public void onOpenResult(int result) {
                if (0 == result) {
                    mHandler.sendEmptyMessage(MESSAGE_UI);
                    DeptrumSdkApi.getApi().setStreamListener(new IStreamListener() {
                        @Override
                        public void onFrame(DTFrameStreamBean iFrame) {

                            if (DTExploreActivity.this.isFinishing()){
                                return;
                            }

                            byte[] data = iFrame.getData();
                            final long timestamp = iFrame.getFrameStamp();
                            switch (iFrame.getImageType()) {
                                case RGB:
                                    handleRgb(iFrame, data, startTime, timestamp);
                                    break;
                                case IR:
                                    handleIr(iFrame, data);
                                    break;
                                case DEPTH:
                                    if (null != tvCenterCount){
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                tvCenterCount.setText("+ " + ((data[368159]&0x00ff)<<8|data[368158]&0x00ff) + "mm");
                                            }
                                        });
                                    }
                                    handleDepth(data, timestamp);
                                    break;
                                default:
                                    break;
                            }
                        }
                    });
                    DeptrumSdkApi.getApi().setScanFaceMode();
                    StreamParam param = new StreamParam();
                    param.width = 480;
                    param.height = 768;
                    DeptrumSdkApi.getApi().setStreamParam(param);
                    DeptrumSdkApi.getApi().startStream(StreamType.STREAM_RGB_IR_DEPTH);
                    DeptrumSdkApi.getApi().configSet("enable_dt_face_kit", "1");
                }
            }

            @Override
            public void onErrorEvent(String s, int i) {
                // onErrorEvent
            }
        });
    }

    private void handleRgb(DTFrameStreamBean iFrame, byte[] data, long startTime, long timestamp){
        long endTime = System.currentTimeMillis();
        Log.d("xjk open -> stream ", (endTime - startTime) + "");
        if (timestamp - mDrawRgbTime > 1000) {
            if (mRgbCount >= 26) {
                mRgbCount = 25;
            }
            if (mRgbCount <= 24) {
                mRgbCount++;
            }
            double rgbFps = mRgbCount;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    tvFpsRgb.setText(String.format("FPS_RGB: %s", rgbFps));
                }
            });
            tvFpsRgb.post(new Runnable() {
                @Override
                public void run() {
                    mDrawRgbTime = timestamp;
                    mRgbCount = 0;
                }
            });
        }
        mRgbCount++;

        if (null == data || mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3 != data.length) {
            return;
        }

        rgbService.execute(() ->{
            final RectF rectF = getrgb(iFrame.getFaceAeAreaSX(),iFrame.getFaceAeAreaSY(),iFrame.getFaceAeAreaW(),iFrame.getFaceAeAreaH());

            convertRgbToRgba(data, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            if (null != mRgbView) {
                mRgbView.post(new Runnable() {
                    @Override
                    public void run() {
                        mRgbView.setImageBitmap(mRgbBitmap);
                        mFaceView.setRgbFace(rectF);
                    }
                });
            }
        });
    }

    private void handleIr(DTFrameStreamBean iFrame, byte[] data){
        if (null == data || mPreviewSize.getWidth() * mPreviewSize.getHeight() != data.length) {
            return;
        }

        irService.execute(() ->{
            convertGrayToRgba(data, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            final RectF rectF = getIr(iFrame.getFaceAeAreaSX(),iFrame.getFaceAeAreaSY(),iFrame.getFaceAeAreaW(),iFrame.getFaceAeAreaH());

            if (null != mIrView) {
                mIrView.post(new Runnable() {
                    @Override
                    public void run() {
                        mIrView.setImageBitmap(mIrBitmap);
                        mFaceView.setIrFace(rectF);
                    }
                });
            }
        });
    }

    private void showIrDepth(long timestamp){
        if (timestamp - mDrawDepTime > 1000) {
            if (mDepCount > 13){
                mDepCount = 13;
            }
            final double depFps = mDepCount;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    tvFpsIr.setText(String.format("FPS_IR: %s", depFps));
                    tvFpsDepth.setText(String.format("FPS_DEPTH: %s", depFps));
                }
            });
            mDrawDepTime = timestamp;
            mDepCount = 0;
        }
        mDepCount++;
    }


    private void handleDepth(byte[] data, long timestamp){
        showIrDepth(timestamp);
        if (null == data || mPreviewSize.getWidth() * mPreviewSize.getHeight() * 2 != data.length) {
            return;
        }
        if (mDepthBitmap == null) {
            mDepthBitmap = Bitmap.createBitmap(mPreviewSize.getWidth(), mPreviewSize.getHeight(), Bitmap.Config.ARGB_8888);
        }
        if (null == mDeDataByte || mDeDataByteLength != mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3){
            mDeDataByte = new byte[mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3];
            mDeDataByteLength = mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3;
        }
        depthService.execute(() ->{
            for (int i = 0; i < mPreviewSize.getWidth() * mPreviewSize.getHeight(); i++) {
                short compare = (short) (data[i * 2 + 1] << 8 | (data[2 * i] & 0xff));
                if (compare > 20) {
                    mDeDataByte[i * 3] = 0;
                    mDeDataByte[i * 3 + 1] = 125;
                } else {
                    mDeDataByte[i * 3] = 125;
                    mDeDataByte[i * 3 + 1] = 0;
                }
                mDeDataByte[i * 3 + 2] = 0;
            }
            int[] pixels = convertByteToColor(mDeDataByte);
            mDepthBitmap.setPixels(pixels, 0, mPreviewSize.getWidth(), 0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            if (null != mDepthView) {
                mDepthView.post(new Runnable() {
                    @Override
                    public void run() {
                        mDepthView.setImageBitmap(mDepthBitmap);
                    }
                });
            }
        });
    }

    private RelativeLayout initUi() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int displayWidth = dm.widthPixels;
        int displayHeight = dm.heightPixels;

        mRgbView = new ImageView(this);
        mIrView = new ImageView(this);
        mDepthView = new ImageView(this);

        mFaceView = new FaceView(this);

        LinearLayout previewLayout = new LinearLayout(getApplicationContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewLayout.setLayoutParams(layoutParams);
        previewLayout.setOrientation(LinearLayout.HORIZONTAL);
        previewLayout.addView(mRgbView);
        previewLayout.addView(mIrView);
        previewLayout.addView(mDepthView);

        mViewWidth = displayWidth / 3;
        mRgbView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEW_HEIGHT));
        mIrView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEW_HEIGHT));
        mDepthView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEW_HEIGHT));

        tvFpsRgb = new TextView(getApplicationContext());
        tvFpsRgb.setTextColor(0xff00ff00);
        tvFpsIr = new TextView(getApplicationContext());
        tvFpsIr.setTextColor(0xff00ff00);
        tvFpsDepth = new TextView(getApplicationContext());
        tvFpsDepth.setTextColor(0xff00ff00);
        tvSn = new TextView(getApplicationContext());
        tvSn.setTextColor(0xff00ff00);
        tvCameraName = new TextView(getApplicationContext());
        tvCameraName.setTextColor(0xff00ff00);
        tvCameraSdkVersion = new TextView(getApplicationContext());
        tvCameraSdkVersion.setTextColor(0xff00ff00);
        tvCameraFwVersion = new TextView(getApplicationContext());
        tvCameraFwVersion.setTextColor(0xff00ff00);
        tvSensorTemperatureCamera = new TextView(getApplicationContext());
        tvSensorTemperatureCamera.setTextColor(0xff00ff00);
        tvSensorTemperatureVsel = new TextView(getApplicationContext());
        tvSensorTemperatureVsel.setTextColor(0xff00ff00);
        tvSensorTemperatureCpu = new TextView(getApplicationContext());
        tvSensorTemperatureCpu.setTextColor(0xff00ff00);
        tvDebug = new TextView(getApplicationContext());
        tvDebug.setTextColor(0xff00ff00);
        Button btnExit = new Button(getApplicationContext());
        btnExit.setText("Exit");
        btnExit.setOnClickListener(mOnClickListener);

        Button btnGetInfo = new Button(getApplicationContext());
        btnGetInfo.setText("GetInfo");
        btnGetInfo.setOnClickListener(mOnGetInfoListener);
        btnGetInfo.setOnLongClickListener(mOnGetInfoLongListener);

        int buttonWidth = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 450, getResources().getDisplayMetrics()));
        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(buttonWidth, RelativeLayout.LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        btnExit.setLayoutParams(textParams);
        btnGetInfo.setLayoutParams(textParams);


        LinearLayout linearLayout = new LinearLayout(getApplicationContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(previewLayout);
        linearLayout.addView(tvFpsRgb);
        linearLayout.addView(tvFpsIr);
        linearLayout.addView(tvFpsDepth);
        linearLayout.addView(tvSn);
        linearLayout.addView(tvCameraName);
        linearLayout.addView(tvCameraSdkVersion);
        linearLayout.addView(tvCameraFwVersion);
        linearLayout.addView(tvSensorTemperatureCamera);
        linearLayout.addView(tvSensorTemperatureVsel);
        linearLayout.addView(tvSensorTemperatureCpu);
        linearLayout.addView(tvDebug);
        linearLayout.addView(btnExit);
        linearLayout.addView(btnGetInfo);

        mFaceView.setLayoutParams(new LinearLayout.LayoutParams(displayWidth , displayHeight));

        rootLayout = new RelativeLayout(this);
        rootLayout.addView(linearLayout);
        rootLayout.addView(mFaceView);
        return rootLayout;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mDepthView != null){
            tvCenterCount = new TextView(this);
            tvCenterCount.setTextColor(0xff00ff00);
            tvCenterCount.setText("+");
            Rect globalRect = new Rect();
            mDepthView.getGlobalVisibleRect(globalRect);
            Log.i("LBC_","globalRect.right:"+globalRect.right+",globalRect.left:"+globalRect.left+",globalRect.bottom:"+globalRect.bottom+",globalRect.top:"+globalRect.top);
            tvCenterCount.setX((float)(globalRect.right-globalRect.left)/2+globalRect.left-5);
            tvCenterCount.setY((float)(globalRect.bottom-globalRect.top)/2+globalRect.top-5);
            rootLayout.addView(tvCenterCount,new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT ));
            rootLayout.invalidate();
        }
    }

    /**
     * gray convert to rgba
     *
     * @param data
     * @param width
     * @param height
     * @return
     */

    public void convertGrayToRgba(byte[] data, int width, int height) {
        try {
            int len = data.length * 4;
            if (null == mIrBits || len != mIrLength){
                mIrBits = new byte[data.length * 4];
                mIrLength = data.length * 4;
            }
            int i;
            for (i = 0; i < data.length; i++) {
                mIrBits[i * 4] = mIrBits[i * 4 + 1] = mIrBits[i * 4 + 2] = data[i];
                mIrBits[i * 4 + 3] = -1;
            }
            if (null == mIrBitmap){
                mIrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            }
            mIrBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(mIrBits));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void convertRgbToRgba(byte[] data, int width, int height) {
        try {
            int len = data.length / 3 * 4;
            if (null == mRgbBits || len != mRgbLength){
                mRgbBits = new byte[data.length / 3 * 4];
                mRgbLength = data.length / 3 * 4;
            }

            int i;
            for (i = 0; i < data.length / 3; i++) {
                mRgbBits[i * 4] = data[i * 3];
                mRgbBits[i * 4 + 1] = data[i * 3 + 1];
                mRgbBits[i * 4 + 2] = data[i * 3 + 2];
                mRgbBits[i * 4 + 3] = -1;
            }
            if (null == mRgbBitmap){
                mRgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            mRgbBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(mRgbBits));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int[] convertByteToColor(byte[] data) {
        int size = data.length;

        int arg = 0;
        if (size % 3 != 0) {
            arg = 1;
        }
        int length = size / 3 + arg;
        if (null == mColor || length != mColorLength){
            mColor = new int[length];
            mColorLength = length;
        }

        if (arg == 0) {
            for (int i = 0; i < mColor.length; ++i) {
                mColor[i] = (data[i * 3] << 16 & 0x00FF0000) |
                        (data[i * 3 + 1] << 8 & 0x0000FF00) |
                        (data[i * 3 + 2] & 0x000000FF) |
                        0xFF000000;
            }
        } else {
            for (int i = 0; i < mColor.length - 1; ++i) {
                mColor[i] = (data[i * 3] << 16 & 0x00FF0000) |
                        (data[i * 3 + 1] << 8 & 0x0000FF00) |
                        (data[i * 3 + 2] & 0x000000FF) |
                        0xFF000000;
            }
            mColor[mColor.length - 1] = 0xFF000000;
        }
        return mColor;
    }

    private static class FaceRoi{

        public static final int HORIZONTAL_IR_WIDTH = 640;
        public static  final int VERTICAL_RGB_HEIGHT = 1920;
        public static  final int CROP_LEFT_X = 14;
        public static  final int CROP_RIGHT_X = 106;
        public static  final int CROP_RGB_Y = 180;
        public static final double RGB_DOWN_SAMPLE_DIV_SCALE = 2.0;

        private int x;
        private int y;
        private int w;
        private int h;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getW() {
            return w;
        }

        public void setW(int w) {
            this.w = w;
        }

        public int getH() {
            return h;
        }

        public void setH(int h) {
            this.h = h;
        }
    }

    private void horizontalToVerticalRoi(FaceRoi outRoi, FaceRoi inRoi, int hWidth) {
        outRoi.setX(inRoi.getY());
        outRoi.setY(Math.max(hWidth - 1 - inRoi.getX() - inRoi.getW(),0));
        outRoi.setW(inRoi.getH());
        outRoi.setH(inRoi.getW());
    }

    private RectF convertIr(double tLeft, double tTop, double tRight, double tBottom){
        double xRatio = (((double)mViewWidth)/((double) IR_WIDTH));
        double yRatio = (((double) VIEW_HEIGHT)/((double) IR_HEIGHT));

        int left = (int)(tLeft * xRatio) + mViewWidth;
        int top = (int)(tTop * yRatio);
        int right = (int)(tRight * xRatio) + mViewWidth;
        int bottom = (int)(tBottom * yRatio);

        return new RectF(left,top,right,bottom);
    }

    private RectF getIr(int mx, int my, int mw, int mh){
        int x=0;
        int y=0;
        int w=0;
        int h =0;

        FaceRoi irRoi = new FaceRoi();
        irRoi.setX(mx);
        irRoi.setY(my);
        irRoi.setW(mw);
        irRoi.setH(mh);
        if((0 != irRoi.w)) {
            FaceRoi tempRoi = new FaceRoi();
            horizontalToVerticalRoi(tempRoi, irRoi, IR_HEIGHT);
            x=tempRoi.getX();
            y=tempRoi.getY();
            w=tempRoi.getW();
            h=tempRoi.getH();
            x= IR_WIDTH - 1 - x - w;
        }

        return convertIr((double)x, (double)y, (double)(x+w), (double)(y+h));
    }

    double estimateRoiFaceDepth(FaceRoi roi) {

        double coeff = 420.0;
        double z1 = 140.f / roi.w * coeff;
        double z2 = 190.f / roi.h * coeff;
        double z3 = 176.f / roi.h * coeff;
        double z4 = (z2 + z3) / 2;
        return z4 < z1 ? Math.ceil(z4) : Math.ceil(z1);
    }

    private int remapRoiSrcToDst(FaceRoi rgbRoi, FaceRoi irRoi, double depth){
        int irMaxRow = 640-2;
        int irMaxCol = 400-2;
        int rgbMaxRow = 1920-2;
        int rgbMaxCol = 1080-2;
        final double irCameraCenterX = 200.0;
        final double irCameraCenterY = 320.0;
        final double irFocalLength = 420.0;

        final double rgbCameraCenteX = 535.0;
        final double rgbCameraCenteY = 950.0;
        final double rgbFocalLength = 980.0;
        final double[] depthRgbR = {0.999930322, 0.0115743186, 0.00231346232,
                -0.0115872277, 0.999916852, 0.00564687,
                -0.00224791141, -0.00567328325, 0.999981403};
        final double[] depthRgbT = {-10.1174784, 0.136960134, 0.754690707};

        double[] roiVecRgbX = new double[4];
        double[] roiVecRgbY = new double[4];
        double[] irRoiTmp = new double[4];

        irRoiTmp[0] = irRoi.getX();
        irRoiTmp[1] = irRoi.getY();
        irRoiTmp[2] = irRoi.getW();
        irRoiTmp[3] = irRoi.getH();

        for (int i = 0; i < 4; i++)
        {
            double irRoiX = irRoiTmp[(i % 2) * 2];
            double irRoiY = irRoiTmp[(i / 2) * 2 + 1];
            if (irRoiX < 0){
                irRoiX = 0;
            }
            else if (irRoiX > irMaxCol) {
                irRoiX = irMaxCol;
            }

            if (irRoiY < 0){
                irRoiY = 0;
            }
            else if (irRoiY > irMaxRow) {
                irRoiY = irMaxRow;
            }
            double roiDepthX = irRoiX;
            double roiDepthY = irRoiY;
            double irRoiXd = depth * (roiDepthX - irCameraCenterX) / irFocalLength;
            double irRoiYd = depth * (roiDepthY - irCameraCenterY) / irFocalLength;
            double rgbRoiXd;
            double rgbRoiYd;
            double rgbRoiZd;

            rgbRoiXd = depthRgbR[0] * irRoiXd + depthRgbR[1] * irRoiYd + depthRgbR[2] * depth + depthRgbT[0];
            rgbRoiYd = depthRgbR[3] * irRoiXd + depthRgbR[4] * irRoiYd + depthRgbR[5] * depth + depthRgbT[1];
            rgbRoiZd = depthRgbR[6] * irRoiXd + depthRgbR[7] * irRoiYd + depthRgbR[8] * depth + depthRgbT[2];


            double rgbRoiXworld = rgbRoiXd * rgbFocalLength / rgbRoiZd +
                    rgbCameraCenteX;
            double rgbRoiYworld = rgbRoiYd * rgbFocalLength / rgbRoiZd +
                    rgbCameraCenteY;

            if (rgbRoiXworld < 0){
                rgbRoiXworld = 0;
            }
            if (rgbRoiYworld < 0){
                rgbRoiYworld = 0;
            }
            if (rgbRoiXworld > rgbMaxCol){
                rgbRoiXworld = rgbMaxCol;
            }
            if (rgbRoiYworld > rgbMaxRow){
                rgbRoiYworld = rgbMaxRow;
            }
            double rgbRoiX = rgbRoiXworld;
            double rgbRoiY = rgbRoiYworld;

            roiVecRgbX[i] = Math.floor(rgbRoiX);
            roiVecRgbY[i] = Math.floor(rgbRoiY);
        }

        Arrays.sort(roiVecRgbX);
        Arrays.sort(roiVecRgbY);
        rgbRoi.setX((int)(roiVecRgbX[0]));
        rgbRoi.setY((int)(roiVecRgbY[0]));
        rgbRoi.setW((int)(roiVecRgbX[3]));
        rgbRoi.setH((int)(roiVecRgbY[3]));

        return getInt(rgbRoi);
    }

    private int getInt(FaceRoi rgbRoi){

        int rgbMaxRow = 1920-2;
        int rgbMaxCol = 1080-2;

        if (rgbRoi.getX() < 0){
            rgbRoi.setX(0);
        }
        if (rgbRoi.getY() < 0){
            rgbRoi.setY(0);
        }
        if (rgbRoi.getW() > rgbMaxCol){
            rgbRoi.setW(rgbMaxCol);
        }
        if (rgbRoi.getH() > rgbMaxRow){
            rgbRoi.setH(rgbMaxRow);
        }

        if (rgbRoi.getX() >= rgbRoi.getW() || rgbRoi.getY() >= rgbRoi.getH()){
            return 3;
        }
        else{
            return 0;
        }
    }

    public void verticalToHorizontalRoi(FaceRoi outRoi, FaceRoi inRoi,int height) {
        outRoi.setX(Math.max(height - 1 - inRoi.getY()-inRoi.getH(),0));
        outRoi.setY(inRoi.x);
        outRoi.setW (inRoi.h);
        outRoi.setH(inRoi.w);
    }


    private int remapIrRoiToRgbRoi(FaceRoi horzRgbRoi, FaceRoi vertScaleRgbRoi, FaceRoi irRoi){

        int irWidth = FaceRoi.HORIZONTAL_IR_WIDTH;
        int rgbHeight = FaceRoi.VERTICAL_RGB_HEIGHT;
        FaceRoi tempRoi = new FaceRoi();

        horizontalToVerticalRoi(tempRoi,irRoi, irWidth);
        double depthFace = estimateRoiFaceDepth(tempRoi);

        tempRoi.setW(tempRoi.getX() + tempRoi.getW() - 1);
        tempRoi.setH(tempRoi.getY() + tempRoi.getH() - 1);

        remapRoiSrcToDst(horzRgbRoi, tempRoi, depthFace);

        horzRgbRoi.setX(horzRgbRoi.getX());
        horzRgbRoi.setY(horzRgbRoi.getY());
        horzRgbRoi.setW(horzRgbRoi.getW() - horzRgbRoi.getX());
        horzRgbRoi.setH(horzRgbRoi.getH() - horzRgbRoi.getY());

        FaceRoi tempRoiRgbRoi = new FaceRoi();
        tempRoiRgbRoi.setX (horzRgbRoi.getX());
        tempRoiRgbRoi.setY(horzRgbRoi.getY());
        tempRoiRgbRoi.setW(horzRgbRoi.getW());
        tempRoiRgbRoi.setH(horzRgbRoi.getH());
        verticalToHorizontalRoi(horzRgbRoi, tempRoiRgbRoi, rgbHeight);

        vertScaleRgbRoi.setX((int)((tempRoiRgbRoi.getX() - FaceRoi.CROP_LEFT_X) / FaceRoi.RGB_DOWN_SAMPLE_DIV_SCALE));
        vertScaleRgbRoi.setY((int)((tempRoiRgbRoi.getY() - FaceRoi.CROP_RGB_Y) / FaceRoi.RGB_DOWN_SAMPLE_DIV_SCALE));
        vertScaleRgbRoi.setW((int)(tempRoiRgbRoi.getW() / FaceRoi.RGB_DOWN_SAMPLE_DIV_SCALE));
        vertScaleRgbRoi.setH((int)(tempRoiRgbRoi.getH() / FaceRoi.RGB_DOWN_SAMPLE_DIV_SCALE));
        return 0;

    }

    private RectF getrgb(int mx, int my, int mw, int mh){
        int x = 0;
        int y = 0;
        int w = 0;
        int h = 0;
        FaceRoi irRoi = new FaceRoi();
        irRoi.setX(mx);
        irRoi.setY(my);
        irRoi.setW(mw);
        irRoi.setH(mh);
        if(0!=irRoi.getW()) {
            FaceRoi tempRoi = new FaceRoi();
            FaceRoi tempRgb = new FaceRoi();

            remapIrRoiToRgbRoi(tempRgb,tempRoi, irRoi);
            x=tempRoi.getX();
            y=tempRoi.getY();
            w=tempRoi.getW();
            h=tempRoi.getH();

            x = RGB_WIDTH - 1 - x - w;
        }

        return convertRgb((double)x,(double)y, (double)(x+w), (double)(y+h));

    }

    @Override
    protected void onDestroy() {
        mIrBits = null;
        mRgbBits = null;
        mColor = null;
        mDeDataByte = null;

        if(mDepthBitmap != null && !mDepthBitmap.isRecycled()){
            mDepthBitmap.recycle();
            mDepthBitmap = null;
        }

        if(mRgbBitmap != null && !mRgbBitmap.isRecycled()){
            mRgbBitmap.recycle();
            mRgbBitmap = null;
        }

        if(mIrBitmap != null && !mIrBitmap.isRecycled()){
            mIrBitmap.recycle();
            mIrBitmap = null;
        }
        super.onDestroy();

    }

    private static class FaceView extends View {

        private Paint mPaint;
        private String mColor = "#42ed45";
        private RectF mRgbFace = null;
        private RectF mIrFace = null;

        private void init(Context context) {
            mPaint = new Paint();
            mPaint.setColor(Color.parseColor(mColor));
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    1f,
                    context.getResources().getDisplayMetrics()));
            mPaint.setAntiAlias(true);
        }

        public FaceView(Context context) {
            super(context);
            init(context);
        }

        public FaceView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public FaceView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init(context);
        }

        public FaceView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            init(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (null != mRgbFace){
                canvas.drawRect(mRgbFace, mPaint);
            }
            if (null != mIrFace){
                canvas.drawRect(mIrFace, mPaint);
            }
        }

        public void setRgbFace(RectF face) {
            this.mRgbFace = face;
        }

        public void setIrFace(RectF face) {
            this.mIrFace = face;
            invalidate();
        }
    }
    private void storeImage(long timestamp,Bitmap image) {
        File folder=new File(Environment.getExternalStorageDirectory() + File.separator+"DepthMapFile" );
        folder.mkdirs();
        File pictureFile = new File(Environment.getExternalStorageDirectory() + File.separator+"DepthMapFile" + File.separator+ String.valueOf(timestamp) + "_depthMap.jpg");
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        Log.d(TAG, "storeImage: "+pictureFile);
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        Log.d(TAG, "storeImage: FileSaved");
    }

}
