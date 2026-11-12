// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package agree.webkit;

import android.os.Build;
import android.view.Surface;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeMethods;

@JNINamespace("draw_fn")
public class MyDrawFunctor {
    private Surface mCurrentSurface;
    private final long mMyDrawFunctor;

    /**
     * 渲染路径选择：Android 10 (API 29) 起用 Vulkan（M119 的 Vulkan 系统要求 1.1，
     * Android 10 的兼容性定义要求 Vulkan 1.1）；API < 29（Android 7/8/9，Vulkan 1.0
     * loader 无法初始化 M119 的 Vulkan 1.1）走 GL/EGL 路径（官方 WebView 低版本同款）。
     */
    private static boolean useVulkan() {
        return Build.VERSION.SDK_INT >= 29;
    }

    public static long getDrawFnFunctionTable() {
        return MyDrawFunctorJni.get().getDrawFnFunctionTable(useVulkan());
    }

    public MyDrawFunctor() {
        mMyDrawFunctor = MyDrawFunctorJni.get().init(useVulkan());
    }

    public void setSurface(Surface surface, int width, int height) {
        if (mCurrentSurface == surface) {
            if (surface != null) {
                MyDrawFunctorJni.get().resizeSurface(mMyDrawFunctor, width, height);
            }
            return;
        }
        mCurrentSurface = surface;
        MyDrawFunctorJni.get().setSurface(mMyDrawFunctor, surface, width, height);
    }

    public void setOverlaysSurface(Surface surface) {
        MyDrawFunctorJni.get().setOverlaysSurface(mMyDrawFunctor, surface);
    }

    public void sync(int functor, boolean applyForceDark) {
        MyDrawFunctorJni.get().sync(mMyDrawFunctor, functor, applyForceDark);
    }

    // Uses functor from last sync.
    public int[] draw(int width, int height, int scrollX, int scrollY, boolean readbackQuadrants) {
        return MyDrawFunctorJni.get()
                .draw(mMyDrawFunctor, width, height, scrollX, scrollY, readbackQuadrants);
    }

    @NativeMethods
    interface Natives {
        long getDrawFnFunctionTable(boolean useVulkan);

        long init(boolean useVulkan);

        void setSurface(long nativeDrawFunctor, Surface surface, int width, int height);

        void resizeSurface(long nativeDrawFunctor, int width, int height);

        void setOverlaysSurface(long nativeDrawFunctor, Surface surface);

        void sync(long nativeDrawFunctor, int functor, boolean applyForceDark);

        int[] draw(long nativeDrawFunctor, int width, int height, int scrollX, int scrollY, boolean readbackQuadrants);
    }
}
