package com.ayurai.dtaurora900;

import androidx.appcompat.app.AppCompatActivity;



import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.deptrum.usblite.callback.IDeviceListener;
import com.deptrum.usblite.callback.IStreamListener;
import com.deptrum.usblite.param.DTFrameStreamBean;
//import com.deptrum.usblite.param.DeviceLoggerInfo;
import com.deptrum.usblite.param.StreamParam;
import com.deptrum.usblite.param.StreamType;
import com.deptrum.usblite.param.TemperatureType;
import com.deptrum.usblite.sdk.DeptrumSdkApi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static final int MESSAGE_UI = 0x01;
    private long mDrawRgbTime;
    private int mRgbCount = 0;
    private long mDrawDepTime;
    private int mDepCount = 0;

    private ImageView mRGBView;
    private ImageView mIRView;
    private ImageView mDepthView;

    private FaceView mFaceView;

    private Bitmap mDepthBitmap;
    private Bitmap mRGBBitmap = null;
    private Bitmap mIRBitmap = null;
    private Size mPreviewSize = new Size(480, 768);
    private ExecutorService mOpenExecutors;
    private TextView tv_fps_rgb;
    private TextView tv_fps_ir;
    private TextView tv_fps_depth;
    private static TextView tv_sn;
    private static TextView tv_cameraName;
    private static TextView tv_cameraSdkVersion;
    private static TextView tv_cameraFwVersion;
    private static TextView tv_pSensorTemperature_camera;
    private static TextView tv_pSensorTemperature_vsel;
    private static TextView tv_pSensorTemperature_cpu;
    private static TextView tv_debug;
    private static Timer mTimer;
    private static final int IRWIDTH = 400;
    private static final int IRHEIGHT = 640;

    private static final int RGBWIDTH = 480;
    private static final int RGBHEIGHT = 768;

    private int mViewWidth = 0;
    private static final int VIEWHEIGHT = 459;

    private byte[] mIrBits = null;
    private int mIrLength = 0;

    private byte[] mRgbBits = null;
    private int mRgbLength = 0;

    // debug oom;
    private int mColorLenght = 0;
    private int[] mColor = null;

    private byte[] mDeDataByte = null;
    int mDeDataByteLenght = 0;
    private ExecutorService service = Executors.newSingleThreadExecutor();

    private boolean isInDoor = false;

    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_UI: {
                    if (null == mTimer) {
                        mTimer = new Timer();
                    }
                    mTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {

                            String sntv = tv_sn.getText().toString();
                            if (TextUtils.isEmpty(sntv) || sntv.contains("无法")){
                                String sn = DeptrumSdkApi.getApi().getSerialNumber();
                                String cameraName = DeptrumSdkApi.getApi().getCameraName();
                                String cameraSDKVersion = DeptrumSdkApi.getApi().getCameraSDKVersion();
                                String cameraFwVersion = DeptrumSdkApi.getApi().getCameraFwVersion();

                                tv_sn.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        tv_sn.setText(String.format("SN: %s", sn));
                                        tv_cameraName.setText(String.format("CameraName: %s", cameraName));
                                        tv_cameraSdkVersion.setText(String.format("CameraSdkVersion: %s", cameraSDKVersion));
                                        tv_cameraFwVersion.setText(String.format("CameraFwVersion: %s", cameraFwVersion));
                                    }
                                });
                            }
                            int cameraTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_CAMERA);
                            int vcselTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_VCSEL);
                            int cpuTemp = DeptrumSdkApi.getApi().getCameraTemp(TemperatureType.TEMPERATURE_MODE_CPU);
                            tv_pSensorTemperature_camera.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (cameraTemp> 0 && cameraTemp < 1000){
                                        tv_pSensorTemperature_camera.setText(cameraTemp > 0 ? String.format("Temperature_Camera: %d°", cameraTemp) : "Temperature_Camera: 异常");
                                    }
                                    if (vcselTemp> 0 && vcselTemp < 1000){
                                        tv_pSensorTemperature_vsel.setText(vcselTemp > 0 ? String.format("Temperature_Vcsel: %d°", vcselTemp) : "Temperature_Vcsel: 异常");
                                    }
                                    if (cpuTemp> 0 && cpuTemp < 1000){
                                        tv_pSensorTemperature_cpu.setText(cpuTemp > 0 ? String.format("Temperature_Cpu: %d°", cpuTemp) : "Temperature_Cpu: 异常");

                                    }
                                }
                            });
                        }
                    }, 0, 3000);
                }
                break;
            }
        }
    };

    private void exit(){
        DeptrumSdkApi.getApi().stopStream(StreamType.STREAM_RGB_IR_DEPTH);
        int ret = DeptrumSdkApi.getApi().close();
        if (0 == ret) {
            if (null != mOpenExecutors) {
                mOpenExecutors.shutdown();
                mOpenExecutors = null;
            }
            if (null != mTimer) {
                mTimer.cancel();
                mTimer = null;
            }
            if (null != service){
                service.shutdown();
                service = null;
            }
            finish();
        }
    }

    private void getDebugInfo(){
//        DeviceLoggerInfo loggerInfo = new DeviceLoggerInfo();
//        DeptrumSdkApi.getApi().getDeviceDebugInfo(loggerInfo);

        tv_debug.post(new Runnable() {
            @Override
            public void run() {
//                tv_debug.setText(loggerInfo.toString());
            }
        });
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
            getDebugInfo();
        }
    };

    private View.OnLongClickListener mOnGetInfoLongListener = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            if (isInDoor){
//                    if (0 == DeptrumSdkApi.getApi().setLaserDriver(3)){
                Toast.makeText(MainActivity.this, "已经切换到室外模式", Toast.LENGTH_SHORT).show();
//                    }
            }
            else {
//                    if (0 == DeptrumSdkApi.getApi().setLaserDriver(2)){
                Toast.makeText(MainActivity.this, "已经切换到室内模式", Toast.LENGTH_SHORT).show();
//                    }
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
        setContentView(initUI());
        mOpenExecutors = Executors.newSingleThreadExecutor();
        mOpenExecutors.execute(new Runnable() {
            @Override
            public void run() {
                openDevice();
            }
        });
    }

    private RectF convertRGB(double tleft, double ttop, double tright, double tbottom){

        double xRatio = (((double)mViewWidth)/((double)RGBWIDTH));
        double yRatio = (((double)VIEWHEIGHT)/((double)RGBHEIGHT));

        int left = (int)(tleft * xRatio);
        int top = (int)(ttop * yRatio);
        int right = (int)(tright * xRatio);
        int bottom = (int)(tbottom * yRatio);

        return new RectF(left,top,right,bottom);
    }

    private void openDevice() {
        long startTime = System.currentTimeMillis();
        DeptrumSdkApi.getApi().open(getApplicationContext(), new IDeviceListener() {
            @Override
            public void onAttach() {

            }

            @Override
            public void onDetach() {

            }

            @Override
            public void onOpenResult(int result) {
                if (0 == result) {
                    mHandler.sendEmptyMessage(MESSAGE_UI);
                    DeptrumSdkApi.getApi().setStreamListener(new IStreamListener() {
                        @Override
                        public void onFrame(DTFrameStreamBean iFrame) {

                            if (MainActivity.this.isFinishing()){
                                return;
                            }

                            byte[] data = iFrame.getData();
                            final long timestamp = iFrame.getFrameStamp();
                            switch (iFrame.getImageType()) {
                                case RGB: {
                                    long endTime = System.currentTimeMillis();
                                    Log.d("xjk open -> stream ", (endTime - startTime) + "");
                                    if (timestamp - mDrawRgbTime > 1000) {
                                        if (mRgbCount >= 26) {
                                            mRgbCount = 25;
                                        }
                                        if (mRgbCount <= 24) {
                                            mRgbCount++;
                                        }
                                        double rgbFps = (double) mRgbCount;
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                tv_fps_rgb.setText(String.format("FPS_RGB: %s", rgbFps));
                                            }
                                        });
                                        tv_fps_rgb.post(new Runnable() {
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

                                    service.execute(() ->{
                                        final RectF rectF = getrgb(iFrame.getFaceAeAreaSX(),iFrame.getFaceAeAreaSY(),iFrame.getFaceAeAreaW(),iFrame.getFaceAeAreaH());

                                        convertRGBToRGBA(data, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                                        if (null != mRGBView) {
                                            mRGBView.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mRGBView.setImageBitmap(mRGBBitmap);
                                                    mFaceView.setRGBFace(rectF);
                                                }
                                            });
                                        }
                                    });
                                }
                                break;
                                case IR: {
                                    if (null == data || mPreviewSize.getWidth() * mPreviewSize.getHeight() != data.length) {
                                        return;
                                    }

                                    service.execute(() ->{
                                        convertGrayToRGBA(data, mPreviewSize.getWidth(), mPreviewSize.getHeight());
                                        final RectF rectF = getIR(iFrame.getFaceAeAreaSX(),iFrame.getFaceAeAreaSY(),iFrame.getFaceAeAreaW(),iFrame.getFaceAeAreaH());

                                        if (null != mIRView) {
                                            mIRView.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mIRView.setImageBitmap(mIRBitmap);
                                                    mFaceView.setIRFace(rectF);
                                                }
                                            });
                                        }
                                    });
                                }
                                break;
                                case DEPTH: {
                                    if (timestamp - mDrawDepTime > 1000) {
                                        if (mDepCount > 13){
                                            mDepCount = 13;
                                        }
                                        final double depFps = (double) mDepCount;
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                tv_fps_ir.setText(String.format("FPS_IR: %s", depFps));
                                                tv_fps_depth.setText(String.format("FPS_DEPTH: %s", depFps));
                                            }
                                        });
                                        mDrawDepTime = timestamp;
                                        mDepCount = 0;
                                    }
                                    mDepCount++;

                                    if (null == data || mPreviewSize.getWidth() * mPreviewSize.getHeight() * 2 != data.length) {
                                        return;
                                    }
                                    if (mDepthBitmap == null) {
                                        mDepthBitmap = Bitmap.createBitmap(mPreviewSize.getWidth(), mPreviewSize.getHeight(), Bitmap.Config.ARGB_8888);
                                    }
                                    if (null == mDeDataByte || mDeDataByteLenght != mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3){
                                        mDeDataByte = new byte[mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3];
                                        mDeDataByteLenght = mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3;
//                                        byte[] deDataByte = new byte[mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3];
                                    }
                                    service.execute(() ->{
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

        });
    }

    private RelativeLayout initUI() {
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metric);
        int displayWidth = metric.widthPixels;     // 屏幕宽度（像素）
        int displayHeight = metric.heightPixels;

        mRGBView = new ImageView(this);
        mIRView = new ImageView(this);
        mDepthView = new ImageView(this);

        mFaceView = new FaceView(this);

        LinearLayout previewLayout = new LinearLayout(getApplicationContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewLayout.setLayoutParams(layoutParams);
        previewLayout.setOrientation(LinearLayout.HORIZONTAL);
        previewLayout.addView(mRGBView);
        previewLayout.addView(mIRView);
        previewLayout.addView(mDepthView);

        mViewWidth = displayWidth / 3;
        mRGBView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEWHEIGHT));
        mIRView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEWHEIGHT));
        mDepthView.setLayoutParams(new LinearLayout.LayoutParams(mViewWidth, VIEWHEIGHT));

        tv_fps_rgb = new TextView(getApplicationContext());
        tv_fps_rgb.setTextColor(0xff00ff00);
        tv_fps_ir = new TextView(getApplicationContext());
        tv_fps_ir.setTextColor(0xff00ff00);
        tv_fps_depth = new TextView(getApplicationContext());
        tv_fps_depth.setTextColor(0xff00ff00);
        tv_sn = new TextView(getApplicationContext());
        tv_sn.setTextColor(0xff00ff00);
        tv_cameraName = new TextView(getApplicationContext());
        tv_cameraName.setTextColor(0xff00ff00);
        tv_cameraSdkVersion = new TextView(getApplicationContext());
        tv_cameraSdkVersion.setTextColor(0xff00ff00);
        tv_cameraFwVersion = new TextView(getApplicationContext());
        tv_cameraFwVersion.setTextColor(0xff00ff00);
        tv_pSensorTemperature_camera = new TextView(getApplicationContext());
        tv_pSensorTemperature_camera.setTextColor(0xff00ff00);
        tv_pSensorTemperature_vsel = new TextView(getApplicationContext());
        tv_pSensorTemperature_vsel.setTextColor(0xff00ff00);
        tv_pSensorTemperature_cpu = new TextView(getApplicationContext());
        tv_pSensorTemperature_cpu.setTextColor(0xff00ff00);
        tv_debug = new TextView(getApplicationContext());
        tv_debug.setTextColor(0xff00ff00);
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
        linearLayout.addView(tv_fps_rgb);
        linearLayout.addView(tv_fps_ir);
        linearLayout.addView(tv_fps_depth);
        linearLayout.addView(tv_sn);
        linearLayout.addView(tv_cameraName);
        linearLayout.addView(tv_cameraSdkVersion);
        linearLayout.addView(tv_cameraFwVersion);
        linearLayout.addView(tv_pSensorTemperature_camera);
        linearLayout.addView(tv_pSensorTemperature_vsel);
        linearLayout.addView(tv_pSensorTemperature_cpu);
        linearLayout.addView(tv_debug);
        linearLayout.addView(btnExit);
        linearLayout.addView(btnGetInfo);

        mFaceView.setLayoutParams(new LinearLayout.LayoutParams(displayWidth , displayHeight));

        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.addView(linearLayout);
        rootLayout.addView(mFaceView);
        return rootLayout;
    }

    /**
     * gray convert to rgba
     *
     * @param data
     * @param width
     * @param height
     * @return
     */

    public void convertGrayToRGBA(byte[] data, int width, int height) {
        try {
            int len = data.length * 4;
            if (null == mIrBits || len != mIrLength){
                mIrBits = new byte[data.length * 4]; // RGBA 数组
                mIrLength = data.length * 4;
            }
//            byte[] Bits = new byte[data.length * 4]; // RGBA 数组
            int i;
            for (i = 0; i < data.length; i++) {
                // 原理：4个字节表示一个灰度，则RGB  = 灰度值，最后一个Alpha = 0xff;
                mIrBits[i * 4] = mIrBits[i * 4 + 1] = mIrBits[i * 4 + 2] = data[i];
                mIrBits[i * 4 + 3] = -1; // 0xff
            }
            // Bitmap.Config.ARGB_8888 表示：图像模式为8位
            if (null == mIRBitmap){
                mIRBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            }
            mIRBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(mIrBits));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void convertRGBToRGBA(byte[] data, int width, int height) {
        try {
            int len = data.length / 3 * 4;
            if (null == mRgbBits || len != mRgbLength){
                mRgbBits = new byte[data.length / 3 * 4]; // RGBA 数组
                mRgbLength = data.length / 3 * 4;
            }

//            byte[] Bits = new byte[data.length / 3 * 4]; // RGBA 数组
            int i;
            for (i = 0; i < data.length / 3; i++) {
                // 原理：4个字节表示一个灰度，则RGB  = 灰度值，最后一个Alpha = 0xff;
                mRgbBits[i * 4] = data[i * 3];
                mRgbBits[i * 4 + 1] = data[i * 3 + 1];
                mRgbBits[i * 4 + 2] = data[i * 3 + 2];
                mRgbBits[i * 4 + 3] = -1; // 0xff
            }
            // Bitmap.Config.ARGB_8888 表示：图像模式为8位
            if (null == mRGBBitmap){
                mRGBBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            mRGBBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(mRgbBits));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int[] convertByteToColor(byte[] data) {
        int size = data.length;
        if (size == 0) {
            return null;
        }
        // 理论上data的长度应该是3的倍数，这里做个兼容
        int arg = 0;
        if (size % 3 != 0) {
            arg = 1;
        }
        int lenght = size / 3 + arg;
        if (null == mColor || lenght != mColorLenght){
            mColor = new int[lenght];
            mColorLenght = lenght;
        }
        int red, green, blue;

        if (arg == 0) {                                    //  正好是3的倍数
            for (int i = 0; i < mColor.length; ++i) {
                mColor[i] = (data[i * 3] << 16 & 0x00FF0000) |
                        (data[i * 3 + 1] << 8 & 0x0000FF00) |
                        (data[i * 3 + 2] & 0x000000FF) |
                        0xFF000000;
            }
        } else {                                        // 不是3的倍数
            for (int i = 0; i < mColor.length - 1; ++i) {
                mColor[i] = (data[i * 3] << 16 & 0x00FF0000) |
                        (data[i * 3 + 1] << 8 & 0x0000FF00) |
                        (data[i * 3 + 2] & 0x000000FF) |
                        0xFF000000;
            }
            mColor[mColor.length - 1] = 0xFF000000;                    // 最后一个像素用黑色填充
        }
        return mColor;
    }

    private static class FaceRoi{

        public static int horizontal_ir_width = 640;
        public static  int vertical_rgb_height = 1920;
        public static  int crop_left_x = 14;
        public static  int crop_right_x = 106;
        public static  int crop_rgb_y = 180;//top/bottom
        public static double rgb_downsample_div_scale = 2.0;

        public int x;
        public int y;
        public int w;
        public int h;
    }

    private void HorizontalToVerticalROI(FaceRoi out_roi, FaceRoi in_roi, int h_width) {
        out_roi.x = in_roi.y;
        out_roi.y = Math.max(h_width - 1 - in_roi.x - in_roi.w,0);
        out_roi.w = in_roi.h;
        out_roi.h = in_roi.w;
    }

    private RectF convertIR(double tleft, double ttop, double tright, double tbottom){
        double xRatio = (((double)mViewWidth)/((double)IRWIDTH));
        double yRatio = (((double)VIEWHEIGHT)/((double)IRHEIGHT));

        int left = (int)(tleft * xRatio) + mViewWidth;
        int top = (int)(ttop * yRatio);
        int right = (int)(tright * xRatio) + mViewWidth;
        int bottom = (int)(tbottom * yRatio);

        return new RectF(left,top,right,bottom);
    }

    private RectF getIR(int mx, int my, int mw, int mh){
        int x=0,y=0,w=0,h =0;

        FaceRoi ir_roi = new FaceRoi();
        ir_roi.x = mx;
        ir_roi.y = my;
        ir_roi.w = mw;
        ir_roi.h = mh;
        if((0 != ir_roi.w)) {
            FaceRoi temp_roi = new FaceRoi();
            HorizontalToVerticalROI(temp_roi, ir_roi, IRHEIGHT);
            x=temp_roi.x; y=temp_roi.y; w=temp_roi.w; h=temp_roi.h;
            x=IRWIDTH - 1 - x - w;
        }

        RectF rectF = convertIR(x,y, x+w, y+h);
        return rectF;
    }

    // Estimate the depth of the face area according to the face roi on ir image
    double EstimateRoiFaceDepth(FaceRoi roi) {

        double coeff = 420.0;                // focale length of 400*640 ir
        double z1 = 140.f / roi.w * coeff;  // face width, man or woman
        double z2 = 190.f / roi.h * coeff;  // face height, man
        double z3 = 176.f / roi.h * coeff;  // face height, woman
        double z4 = (z2 + z3) / 2;
        return z4 < z1 ? Math.ceil(z4) : Math.ceil(z1);
    }

    private int RemapRoiSrcToDst(FaceRoi rgb_roi, FaceRoi ir_roi, double depth){
        int ir_max_row = 640-2, ir_max_col = 400-2, rgb_max_row = 1920-2, rgb_max_col = 1080-2;
        //downsample
        final double ir_camera_center_x = 200.0;
        final double ir_camera_center_y = 320.0;
        final double ir_focal_length = 420.0;
        // full
        final double rgb_camera_center_x = 535.0;
        final double rgb_camera_center_y = 950.0;
        final double rgb_focal_length = 980.0;
        final double depth_rgb_r[] = {0.999930322, 0.0115743186, 0.00231346232,
                -0.0115872277, 0.999916852, 0.00564687,
                -0.00224791141, -0.00567328325, 0.999981403};
        final double depth_rgb_t[] = {-10.1174784, 0.136960134, 0.754690707};

        double roi_vec_rgb_x[] = new double[4];
        double roi_vec_rgb_y[] = new double[4];
        double ir_roi_tmp[] = new double[4];

        ir_roi_tmp[0] = ir_roi.x;
        ir_roi_tmp[1] = ir_roi.y;
        ir_roi_tmp[2] = ir_roi.w;
        ir_roi_tmp[3] = ir_roi.h;

        for (int i = 0; i < 4; i++)
        {
            // Follow the roi order.
            double ir_roi_x = ir_roi_tmp[(i % 2) * 2];
            double ir_roi_y = ir_roi_tmp[(i / 2) * 2 + 1];
            if (ir_roi_x < 0)
                ir_roi_x = 0;
            else if (ir_roi_x > ir_max_col) {
                ir_roi_x = ir_max_col;
            }

            if (ir_roi_y < 0)
                ir_roi_y = 0;
            else if (ir_roi_y > ir_max_row) {
                ir_roi_y = ir_max_row;
            }
            double roi_depth_x = ir_roi_x;
            double roi_depth_y = ir_roi_y;
            double ir_roi_x_3d = depth * (roi_depth_x - ir_camera_center_x) / ir_focal_length;
            double ir_roi_y_3d = depth * (roi_depth_y - ir_camera_center_y) / ir_focal_length;
            double rgb_roi_x_3d, rgb_roi_y_3d, rgb_roi_z_3d;

            rgb_roi_x_3d = depth_rgb_r[0] * ir_roi_x_3d + depth_rgb_r[1] * ir_roi_y_3d + depth_rgb_r[2] * depth + depth_rgb_t[0];
            rgb_roi_y_3d = depth_rgb_r[3] * ir_roi_x_3d + depth_rgb_r[4] * ir_roi_y_3d + depth_rgb_r[5] * depth + depth_rgb_t[1];
            rgb_roi_z_3d = depth_rgb_r[6] * ir_roi_x_3d + depth_rgb_r[7] * ir_roi_y_3d + depth_rgb_r[8] * depth + depth_rgb_t[2];


            double rgb_roi_x_world = rgb_roi_x_3d * rgb_focal_length / rgb_roi_z_3d +
                    rgb_camera_center_x;
            double rgb_roi_y_world = rgb_roi_y_3d * rgb_focal_length / rgb_roi_z_3d +
                    rgb_camera_center_y;
            // Check boundary.
            if (rgb_roi_x_world < 0)
                rgb_roi_x_world = 0;
            if (rgb_roi_y_world < 0)
                rgb_roi_y_world = 0;
            if (rgb_roi_x_world > rgb_max_col)
                rgb_roi_x_world = rgb_max_col;
            if (rgb_roi_y_world > rgb_max_row)
                rgb_roi_y_world = rgb_max_row;
            double rgb_roi_x = rgb_roi_x_world;
            double rgb_roi_y = rgb_roi_y_world;
            // Upload point.
            roi_vec_rgb_x[i] = Math.floor(rgb_roi_x);
            roi_vec_rgb_y[i] = Math.floor(rgb_roi_y);
        }

//        qsort(roi_vec_rgb_x, 4, sizeof(int), cmp_length);
////        qsort(roi_vec_rgb_y, 4, sizeof(int), cmp_length);

        Arrays.sort(roi_vec_rgb_x);
        Arrays.sort(roi_vec_rgb_y);
        rgb_roi.x = (int)(roi_vec_rgb_x[0]);
        rgb_roi.y = (int)(roi_vec_rgb_y[0]);
        rgb_roi.w = (int)(roi_vec_rgb_x[3]);
        rgb_roi.h = (int)(roi_vec_rgb_y[3]);
        // Check boundary.
        if (rgb_roi.x < 0)
            rgb_roi.x = 0;
        if (rgb_roi.y < 0)
            rgb_roi.y = 0;
        if (rgb_roi.w > rgb_max_col)
            rgb_roi.w = rgb_max_col;
        if (rgb_roi.h > rgb_max_row)
            rgb_roi.h = rgb_max_row;

        if (rgb_roi.x >= rgb_roi.w || rgb_roi.y >= rgb_roi.h)
            return 3;
        else
            return 0;
    }

    public void VerticalToHorizontalROI(FaceRoi out_roi, FaceRoi in_roi,int height) {
        out_roi.x = Math.max(height - 1 - in_roi.y-in_roi.h,0);
        out_roi.y = in_roi.x;
        out_roi.w = in_roi.h;
        out_roi.h = in_roi.w;
    }


    private int RemapIrRoiToRgbRoi(FaceRoi horz_rgb_roi, FaceRoi vert_scale_rgb_roi, FaceRoi ir_roi){

        int ir_width = FaceRoi.horizontal_ir_width;
        int rgb_height = FaceRoi.vertical_rgb_height;
        FaceRoi temp_roi = new FaceRoi();

        HorizontalToVerticalROI(temp_roi,ir_roi, ir_width);
        double depth_face = EstimateRoiFaceDepth(temp_roi);

        temp_roi.w = temp_roi.x + temp_roi.w - 1;
        temp_roi.h = temp_roi.y + temp_roi.h - 1;

        RemapRoiSrcToDst(horz_rgb_roi, temp_roi, depth_face);
        //printf("vertical_roi_rgb_roi=%d,%d,%d,%d\n",horz_rgb_roi[0],horz_rgb_roi[1],horz_rgb_roi[2],horz_rgb_roi[3]);

        horz_rgb_roi.x = horz_rgb_roi.x;
        horz_rgb_roi.y = horz_rgb_roi.y;
        horz_rgb_roi.w = horz_rgb_roi.w - horz_rgb_roi.x;
        horz_rgb_roi.h = horz_rgb_roi.h - horz_rgb_roi.y;

        FaceRoi temp_roi_rgb_roi = new FaceRoi();
        temp_roi_rgb_roi.x = horz_rgb_roi.x;
        temp_roi_rgb_roi.y = horz_rgb_roi.y;
        temp_roi_rgb_roi.w = horz_rgb_roi.w;
        temp_roi_rgb_roi.h = horz_rgb_roi.h;
        //printf("temp_roi_rgb_roi=%d,%d,%d,%d\n",temp_roi_rgb_roi[0],temp_roi_rgb_roi[1],temp_roi_rgb_roi[2],temp_roi_rgb_roi[3]);
        VerticalToHorizontalROI(horz_rgb_roi, temp_roi_rgb_roi, rgb_height);

        vert_scale_rgb_roi.x = (int)((temp_roi_rgb_roi.x - FaceRoi.crop_left_x) / FaceRoi.rgb_downsample_div_scale);
        vert_scale_rgb_roi.y = (int)((temp_roi_rgb_roi.y - FaceRoi.crop_rgb_y) / FaceRoi.rgb_downsample_div_scale);
        vert_scale_rgb_roi.w = (int)(temp_roi_rgb_roi.w / FaceRoi.rgb_downsample_div_scale);
        vert_scale_rgb_roi.h = (int)(temp_roi_rgb_roi.h / FaceRoi.rgb_downsample_div_scale);
        //qDebug("div_rgb_roi=%d,%d,%d,%d\n",vert_scale_rgb_roi->x,vert_scale_rgb_roi->y,vert_scale_rgb_roi->w,vert_scale_rgb_roi->h);
        return 0;

    }

    private RectF getrgb(int mx, int my, int mw, int mh){
        int x = 0, y = 0, w = 0, h = 0;
        FaceRoi ir_roi = new FaceRoi();
        ir_roi.x =mx;
        ir_roi.y =my;
        ir_roi.w =mw;
        ir_roi.h =mh;
        if(0!=ir_roi.w)
        {
            FaceRoi temp_roi = new FaceRoi();
            FaceRoi temp_rgb = new FaceRoi();

            RemapIrRoiToRgbRoi(temp_rgb,temp_roi, ir_roi);
            x=temp_roi.x;
            y=temp_roi.y;
            w=temp_roi.w;
            h=temp_roi.h;

            x = RGBWIDTH - 1 - x - w;
        }

        RectF rectF = convertRGB(x,y, x+w, y+h);
        return rectF;

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

        if(mRGBBitmap != null && !mRGBBitmap.isRecycled()){
            mRGBBitmap.recycle();
            mRGBBitmap = null;
        }

        if(mIRBitmap != null && !mIRBitmap.isRecycled()){
            mIRBitmap.recycle();
            mIRBitmap = null;
        }
        super.onDestroy();

    }

    private static class FaceView extends View {

        private Paint mPaint;
        private String mCorlor = "#42ed45";
        private RectF mRGBFace = null;
        private RectF mIRFace = null;

        private void init(Context context) {
            mPaint = new Paint();
            mPaint.setColor(Color.parseColor(mCorlor));
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
            if (null != mRGBFace){
                canvas.drawRect(mRGBFace, mPaint);
            }
            if (null != mIRFace){
                canvas.drawRect(mIRFace, mPaint);
            }
        }

        public void setRGBFace(RectF face) {
            this.mRGBFace = face;
        }

        public void setIRFace(RectF face) {
            this.mIRFace = face;
            invalidate();
        }
    }

    }