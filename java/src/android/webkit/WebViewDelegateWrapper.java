/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
// import android.graphics.RecordingCanvas;  // API 29+，改用类名字符串判断（见 isHardwareAcceleratedCanvas）
import android.os.RemoteException;
import android.util.SparseArray;
import android.util.Log;
import android.view.View;
import android.graphics.Paint;
import android.graphics.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

/**
 * Delegate used by the WebView provider implementation to access
 * the required framework functionality needed to implement a {@link WebView}.
 *
 * @hide
 */
public final class WebViewDelegateWrapper {

    private Paint paint;

    public WebViewDelegateWrapper() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(3.0f);
        paint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Listener that gets notified whenever tracing has been enabled/disabled.
     */
    public interface OnTraceEnabledChangeListener {
        void onTraceEnabledChange(boolean enabled);
    }

    /**
     * Register a callback to be invoked when tracing for the WebView component has been
     * enabled/disabled.
     */
    public void setOnTraceEnabledChangeListener(final OnTraceEnabledChangeListener listener) {
		Log.i("agree", "setOnTraceEnabledChangeListener called");
    }

    /**
     * Returns {@code true} if the WebView trace tag is enabled and {@code false} otherwise.
     */
    public boolean isTraceTagEnabled() {
        return false;
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @deprecated Use {@link #drawWebViewFunctor(Canvas, int)}
     */
    @Deprecated
    public boolean canInvokeDrawGlFunctor(View containerView) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @deprecated Use {@link #drawWebViewFunctor(Canvas, int)}
     */
    @Deprecated
    public void invokeDrawGlFunctor(View containerView, long nativeDrawGLFunctor,
            boolean waitForCompletion) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @deprecated Use {@link #drawWebViewFunctor(Canvas, int)}
     */
    @Deprecated
    public void callDrawGlFunction(Canvas canvas, long nativeDrawGLFunctor) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @deprecated Use {@link #drawWebViewFunctor(Canvas, int)}
     */
    @Deprecated
    public void callDrawGlFunction(Canvas canvas, long nativeDrawGLFunctor,
            Runnable releasedRunnable) {
        throw new UnsupportedOperationException();
    }

    /**
     * Call webview draw functor. See API in draw_fn.h.
     *
     * <p><b>API 24 兼容性</b>：原实现用 {@code canvas instanceof RecordingCanvas} 做硬件加速判断，
     * 但 {@code android.graphics.RecordingCanvas} 是 API 29 (Q) 才引入的<b>公开</b>类型
     * （API 21~28 里存在的是 @hide 的 {@code DisplayListCanvas}），在 API 24 上编译不过、
     * 类加载时也会因常量池引用而校验失败。
     *
     * <p>改为按类名逐级判断，覆盖硬件加速 Canvas 在各 Android 版本中的实际类型：
     * <ul>
     *   <li>API 29+ ：{@code android.graphics.RecordingCanvas}</li>
     *   <li>API 21~28：{@code android.view.DisplayListCanvas}（@hide）</li>
     * </ul>
     * 判断走 {@code getClass().getName()} 字符串比较，不引入任何类型引用，
     * 因此对 API 24 完全安全。
     *
     * @param canvas  a {@link android.graphics.Canvas}，应为硬件加速 Canvas
     * @param functor created by AwDrawFn_CreateFunctor in draw_fn.h.
     */
    public void drawWebViewFunctor(Canvas canvas, int functor) {
        if (!isHardwareAcceleratedCanvas(canvas)) {
            throw new IllegalArgumentException(canvas.getClass().getName()
                    + " is not a hardware accelerated canvas");
        }
        //webkitDelegate.drawWebViewFunctor(canvas, functor);
        Log.i("agree", " WebViewDelegateWrapper drawWebViewFunctor called");
        //canvas.drawRect(20, 20, 300, 300, paint);
        //webkitDelegate.drawWebViewFunctor(canvas, functor);
        try {
            ////Class webViewDelegateClass = Class.forName("android.webkit.WebViewDelegate");
            //Class webViewDelegateClass = Class.forName("android.graphics.RecordingCanvas");
            ////Log.i("agree", "drawWebViewFunctor STEP 000");
            ////Object delegate = webViewDelegateClass.newInstance();
            ////Constructor newDelegate = webViewDelegateClass.getConstructor();
            //Log.i("agree", "drawWebViewFunctor STEP 001 ");
            ////Constructor newDelegate = webViewDelegateClass.getConstructor();
            //Method drawWebViewFunctor = webViewDelegateClass.getMethod("drawWebViewFunctor", int.class);
            //Log.i("agree", "drawWebViewFunctor STEP 002 " );
            ////newDelegate.setAccessible(true);
            //Log.i("agree", "drawWebViewFunctor STEP 003 " );
            //Log.i("agree", "drawWebViewFunctor STEP 004");
            ////Method drawWebViewFunctor = webViewDelegateClass.getMethod("drawWebViewFunctor");
            //Log.i("agree", "drawWebViewFunctor STEP 005");
            //drawWebViewFunctor.invoke(canvas, functor);
            //Log.i("agree", "drawWebViewFunctor STEP 006");
            //RecordingCanvas recordingCanvas = (RecordingCanvas) canvas;
            //Log.i("agree", "drawWebViewFunctor STEP 001" + canvas + "  status " + (canvas instanceof RecordingCanvas));
            //Class recordingCanvasClass = Class.forName("android.graphics.RecordingCanvas");
            //Log.i("agree", "drawWebViewFunctor STEP 002");
            //Method method = recordingCanvasClass.getMethod("drawWebViewFunctor", int.class);
            //Method method = recordingCanvasClass.getMethod("callDrawGLFunction2", long.class);
            //Log.i("agree", "drawWebViewFunctor STEP 003" + method);
            //method.invoke(recordingCanvas, functor, null);
            //Log.i("agree", "drawWebViewFunctor STEP 004");
            //drawWebViewFunctor.invoke(rc, functor);
            //Object mNativeCanvasWrapper = canvas.getClass().getMethod("getNativeCanvasWrapper").invoke(canvas);
            //Log.i("agree", "WebViewDelegateWrapper drawWebViewFunctor mNativeCanvasWrapper " + mNativeCanvasWrapper);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("agree", " WebViewDelegateWrapper drawWebViewFunctor invoke failed ");
        }
		//Log.i("agree", " *** WebViewDelegateWrapper drawWebViewFunctor called");
        //((RecordingCanvas) canvas).drawWebViewFunctor(functor);
    }

    /**
     * 判断 Canvas 是否为硬件加速 Canvas。
     *
     * <p><b>为什么用类名字符串判断而不是 instanceof</b>：
     * 硬件加速 Canvas 的类型在 Android 各版本间发生过变更——
     * <ul>
     *   <li>API 21~28：{@code android.view.DisplayListCanvas}（@hide，无法直接引用）</li>
     *   <li>API 29+  ：{@code android.graphics.RecordingCanvas}（公开 API）</li>
     * </ul>
     * 两种都无法同时满足「API 24 能编译 + API 29 能识别」：前者是 @hide 类型，
     * 后者在 API 24 的 android.jar 里不存在。因此退化为类名比较，全程不引入类型引用。
     *
     * <p>同时兼容两者共同继承的 {@code BaseRecordingCanvas}（@hide，API 22+），
     * 以防厂商 ROM 派生出未列出的子类。
     *
     * @param canvas 待判断的 Canvas
     * @return true 表示是硬件加速 Canvas
     */
    private static boolean isHardwareAcceleratedCanvas(Canvas canvas) {
        if (canvas == null) {
            return false;
        }
        // 沿继承链向上查找，命中任一硬件加速 Canvas 类型即为真。
        Class<?> cls = canvas.getClass();
        while (cls != null) {
            String name = cls.getName();
            if ("android.graphics.RecordingCanvas".equals(name)
                    || "android.view.DisplayListCanvas".equals(name)
                    || "android.view.BaseRecordingCanvas".equals(name)) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    /**
     * Detaches the draw GL functor.
     *
     * @param nativeDrawGLFunctor the pointer to the native functor that implements
     *        system/core/include/utils/Functor.h
     * @deprecated Use {@link #drawWebViewFunctor(Canvas, int)}
     */
    @Deprecated
    public void detachDrawGlFunctor(View containerView, long nativeDrawGLFunctor) {
		Log.i("agree", "WebViewDelegateWrapper detachDrawGlFunctor called");
    }

    /**
     * Returns the package id of the given {@code packageName}.
     */
    public int getPackageId(Resources resources, String packageName) {
		Log.i("agree", "WebViewDelegateWrapper getPackageId called, packageName " + packageName);
        return 0;
    }

    /**
     * Returns the application which is embedding the WebView.
     */
    public Application getApplication() {
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Object currentApplication = activityThreadClass.getMethod("currentApplication").invoke(null);
		    Log.i("agree", "WebViewDelegateWrapper getApplication called" + currentApplication);
            return (Application)currentApplication;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the error string for the given {@code errorCode}.
     */
    public String getErrorString(Context context, int errorCode) {
        Log.i("agree", " getErrorString called ");
        return "crError=" + errorCode ;//LegacyErrorStrings.getString(errorCode, context);
    }

    /**
     * Adds the WebView asset path to {@link android.content.res.AssetManager}.
     * If {@link android.content.res.Flags#FLAG_REGISTER_RESOURCE_PATHS} is enabled, this function
     * will be a no-op because the asset paths appending work will only be handled by
     * {@link android.content.res.Resources#registerResourcePaths(String, ApplicationInfo)},
     * otherwise it behaves the old way.
     */
    public void addWebViewAssetPath(Context context) {
		Log.i("agree", "addWebViewAssetPath called");
    }

    /**
     * Returns whether WebView should run in multiprocess mode.
     */
    public boolean isMultiProcessEnabled() {
        return false;
    }

    ///**
    // * Returns the data directory suffix to use, or null for none.
    // */
    public String getDataDirectorySuffix() {
        Log.i("agree", " getDataDirectorySuffix called");
        return "";
        //return WebViewFactory.getDataDirectorySuffix();
    }

    /**
     * Get the timestamps at which various WebView startup events occurred in this process.
     * This method must be called on the same thread where the
     * WebViewChromiumFactoryProvider#create method was invoked.
     */
    //public WebViewFactory.StartupTimestamps getStartupTimestamps() {
    //    return WebViewFactory.getStartupTimestamps();
    //}
}
