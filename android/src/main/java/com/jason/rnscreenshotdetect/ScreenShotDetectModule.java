package com.jason.rnscreenshotdetect;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 2017/11/15.
 */

public class ScreenShotDetectModule extends ReactContextBaseJavaModule implements  LifecycleEventListener {

    private final ReactApplicationContext mReactContext;
    private boolean isForeground = true;

    private static final  String TAG = "ScreenShotDetect";
    private static final  String ScreenShotEvent = "ScreenShotDetected";

    private long DATE_INTERVAL = 5 * 1000;
    private static long mStartListenTime;
    private Point sScreenSize;

    /** 截屏依据中的路径判断关键字 */
    private static final String[] KEYWORDS = {
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "screencap", "screen_cap", "screen-cap", "screen cap"
    };


    private static final String[] MEDIA_PROECtIONS = {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_TAKEN
    };
    /** api > 16 */
    private static final String[] MEDIA_PROJECTIONS_API_16 = {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.WIDTH,
            MediaStore.Images.ImageColumns.HEIGHT
    };

    private List<String> hasCallbackPaths = new ArrayList<String>();

    /** 内部存储器内容观察者 MediaContentObserver*/
    private  MediaContentObserver mInternalObserver;

    /** 外部存储器内容观察者 */
    private MediaContentObserver mExternalObserver;

    @Override
    public String getName() {
        return "RNScreenShotDetect";
    }

    public ScreenShotDetectModule(ReactApplicationContext reactContext) {

        super(reactContext);
        mReactContext = reactContext;

        mReactContext.addLifecycleEventListener(this);
        if (sScreenSize == null) {
            sScreenSize = getRealScreenSize();
            if (sScreenSize != null) {
                Log.d(TAG, "Screen Real Size: " + sScreenSize.x + " * " + sScreenSize.y);
            } else {
                Log.w(TAG, "Get screen real size failed.");
            }
        }
    }

    @ReactMethod
    public  void startListen() {

        register();
    }

    @ReactMethod
    public void stopListen() {
        unRegister();
    }
    /**
     * 注销掉ContentObserver
     */
    private void unRegister() {
        Log.i(TAG, "unRegister");
        if (mInternalObserver != null) {
            try {
                mReactContext.getContentResolver().unregisterContentObserver(mInternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mInternalObserver = null;
        }

        if (mExternalObserver != null) {
            try {
                mReactContext.getContentResolver().unregisterContentObserver(mExternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mExternalObserver = null;
        }
        // 清空数据
        mStartListenTime = 0;
        hasCallbackPaths.clear();
    }

    /**
     * 注册监听的ContentObserver
     */
    private void register() {

//        注册之前先监听
        unRegister();
        hasCallbackPaths.clear();
        mStartListenTime = System.currentTimeMillis();

        // 创建内容观察者
        mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        // 注册内容观察者
        mReactContext.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                false,
                mInternalObserver
        );
        mReactContext.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                false,
                mExternalObserver
        );
    }

    /**
     * 监听到变化时，触发，查找到最新的一条图片记录
     * @param contentUri
     */
    private void handleMediaContentChange(Uri contentUri) {
        Cursor cursor = null;

        try{
            // 数据改变时查询数据库中最后加入的一条数据
            cursor = mReactContext.getContentResolver().query(
                    contentUri,
                    Build.VERSION.SDK_INT < 16 ? MEDIA_PROECtIONS: MEDIA_PROJECTIONS_API_16,
                    null,
                    null,
                    MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1");

            if (cursor == null) {
                Log.e(TAG, "Deviant logic.");
                return;
            }

            if (!cursor.moveToFirst()) {
                Log.d(TAG, "Cursor no data");
                return;
            }

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            int dataTakenIndex =  cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int widthIndex = -1;
            int heightIndex = -1;
            if (Build.VERSION.SDK_INT >= 16) {
                widthIndex =  cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH);
                heightIndex =  cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT);
            }
            // 获取行数据
            String data = cursor.getString(dataIndex);
            long dateTaken = cursor.getLong(dataTakenIndex);
            int height =0;
            int width = 0;

            if (widthIndex >= 0 && heightIndex >= 0) {
                width = cursor.getInt(widthIndex);
                height = cursor.getInt(heightIndex);
            } else {
                // 获取图片的size
                Point size = getImageSize(data);
                width = size.x;
                height = size.y;
            }

            handleMediaRowData(data, dateTaken, width, height);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    private void handleMediaRowData(String data, long dateTaken, int width, int height) {
        if (checkScreenShot(data, dateTaken, width, height)) {
            Log.d(TAG, "ScreenShot: path = " + data + "; size = " + width + " * " + height
                    + "; date = " + dateTaken);
            WritableMap map = Arguments.createMap();

            try {
                map.putString("url", data);
                map.putDouble("timeStamp", dateTaken);
                // TODO 回调方法。
                if (!checkCallback(data)) {

                    mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit(ScreenShotEvent, map);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // 如果在观察区间媒体数据库有数据改变，又不符合截屏规则，则输出到 log 待分析
            Log.w(TAG, "Media content changed, but not screenshot: path = " + data
                    + "; size = " + width + " * " + height + "; date = " + dateTaken);
        }
    }

    /**
     * 判断指定的数据行是否符合截屏条件
     * @param data 截屏图片的url
     * @param dateTaken  截屏时间
     * @param width  图片的宽度
     * @param height  图片的高度
     * @return
     */
    private boolean checkScreenShot(String data, long dateTaken, int width, int height) {

        /**
         * 判断依据一： 时间判断
         * 如果图片的时间大于开始监听的时间，或者与当前时间的时间间隔相差大于5s，则表明不是截屏图片
         */
        if (dateTaken < mStartListenTime || (System.currentTimeMillis() - dateTaken) > DATE_INTERVAL) {
            return false;
        }

        /**
         * 判断依据二： 判断尺寸
         */
        if (sScreenSize != null) {
            // 如果图片尺寸超出屏幕, 则认为当前没有截屏
            if (!((width <= sScreenSize.x && height <= sScreenSize.y)
                    || height <= sScreenSize.x && width <= sScreenSize.y)) {
                return false;
            }
        }

        /**
         * 判断依据三： 路径判断
         */
        if (TextUtils.isEmpty(data)) {
            return false;
        }
        data = data.toLowerCase();

        // 判断图片路径是否含有指定的关键字之一, 如果有, 则认为当前截屏了
        for (String keyword: KEYWORDS) {
            if (data.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否已回调过, 某些手机ROM截屏一次会发出多次内容改变的通知; <br/>
     * 删除一个图片也会发通知, 同时防止删除图片时误将上一张符合截屏规则的图片当做是当前截屏.
     */
    private boolean checkCallback(String imagePath) {
        if (hasCallbackPaths.contains(imagePath)) {
            return true;
        }
        // 大概缓存15~20条记录便可
        if (hasCallbackPaths.size() >= 20) {
            for (int i = 0; i < 5; i++) {
                hasCallbackPaths.remove(0);
            }
        }
        hasCallbackPaths.add(imagePath);
        return false;
    }

    /**
     * 读取图片的大小
     * @param imagePath
     * @return
     */
    private Point getImageSize(String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 只读取图片的大小，不读取图片数据
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        return new Point(options.outWidth, options.outHeight);
    }


    /**
     * 获取屏幕分辨率
     */
    private Point getRealScreenSize() {
        Point screenSize = null;
        try {
            screenSize = new Point();
            WindowManager windowManager = (WindowManager) mReactContext.getSystemService(Context.WINDOW_SERVICE);
            Display defaultDisplay = windowManager.getDefaultDisplay();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                defaultDisplay.getRealSize(screenSize);
            } else {
                try {
                    Method mGetRawW = Display.class.getMethod("getRawWidth");
                    Method mGetRawH = Display.class.getMethod("getRawHeight");
                    screenSize.set(
                            (Integer) mGetRawW.invoke(defaultDisplay),
                            (Integer) mGetRawH.invoke(defaultDisplay)
                    );
                } catch (Exception e) {
                    screenSize.set(defaultDisplay.getWidth(), defaultDisplay.getHeight());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return screenSize;
    }

    @Override
    public void onHostResume() {
        isForeground = true;
    }

    @Override
    public void onHostPause() {
        isForeground =false;
    }

    @Override
    public void onHostDestroy() {
        isForeground =false;
    }

    private class MediaContentObserver extends ContentObserver {
        private Uri mContentUri;
        public MediaContentObserver(Uri contentUri) {
            super(null);
            mContentUri = contentUri;
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (isForeground) {
                handleMediaContentChange(uri);
            }
        }
    }
}
