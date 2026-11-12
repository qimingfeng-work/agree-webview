// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package agree.webkit;

import android.os.Build;
import android.view.View;

import java.lang.reflect.Method;

/**
 * API 29 (Android 10, Q) 及以上专属逻辑的隔离容器。
 *
 * <h2>为什么必须单独一个类，而不是在 WebView 里直接写 if (SDK_INT >= Q)</h2>
 *
 * Android 运行时（ART）对类做**校验（verification）**的时机是**类加载**时，
 * 而不是方法执行时。{@code WebView} 继承自 {@link android.view.View}，
 * 当 ART 加载 {@code WebView} 类时会解析该类自身所有 {@code invokevirtual} 的目标方法。
 *
 * {@code View.getImportantForContentCapture()} / {@code View.setImportantForContentCapture(int)}
 * 是 **API 29 (Q)** 才新增的方法，在 API 26~28 的 framework.jar 中根本不存在。因此：
 *
 * <pre>
 *   // 这样写仍然会崩 —— 校验发生在类加载阶段，早于分支判断
 *   if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.Q) {
 *       getImportantForContentCapture();   // 方法引用仍留在 WebView 的常量池里
 *   }
 * </pre>
 *
 * 实测崩溃栈（华为 BZT-AL00 / Android 8.0 / API 26）：
 * <pre>
 *   java.lang.NoSuchMethodError: No virtual method getImportantForContentCapture()I
 *       in class Lagree/webkit/WebView; or its super classes
 *       at agree.webkit.WebView.&lt;init&gt;(WebView.java:600)
 * </pre>
 *
 * 把调用搬进本类后，API 26~28 上的类加载器永远不会加载本类（调用点被 SDK_INT 守卫），
 * 校验器自然也就不会去解析这些方法引用。这正是 AOSP support library 里
 * {@code GlueApiHelperForQ} 采用的同款手法。
 *
 * <h2>使用约定</h2>
 *
 * <ul>
 *   <li>本类所有方法都必须由调用方先用 {@code Build.VERSION.SDK_INT >= Q} 守卫；</li>
 *   <li>不要从 {@code WebView} 的字段初始化、构造器早期阶段之外的地方调用，
 *       避免在 <i>API 29+ 但 WebView 尚未初始化完成</i> 时误用。</li>
 * </ul>
 *
 * @see <a href="https://developer.android.com/reference/android/view/View#getImportantForContentCapture()">
 *     View.getImportantForContentCapture() — 加入于 API 29</a>
 */
final class WebViewApiQ {

    /** 对应的 {@code Build.VERSION_CODES.Q}（API 24 的 android.jar 里没有该常量）。 */
    static final int API_LEVEL = 29;

    /** {@code View.IMPORTANT_FOR_CONTENT_CAPTURE_AUTO} 的数值。 */
    private static final int IMPORTANT_FOR_CONTENT_CAPTURE_AUTO = 0;

    /** {@code View.IMPORTANT_FOR_CONTENT_CAPTURE_YES} 的数值。 */
    private static final int IMPORTANT_FOR_CONTENT_CAPTURE_YES = 1;

    /** 工具类，禁止实例化。 */
    private WebViewApiQ() {
    }

    /**
     * 设置 WebView 对内容捕获（ContentCapture）的默认重要性。
     *
     * <p>对齐 AOSP android11+ 的 {@code WebView} 构造器行为：WebView 默认应参与内容捕获，
     * 除非应用开发者通过 XML 属性或 API 显式覆盖为其他值。
     *
     * <p><b>为什么用反射</b>：{@code View.getImportantForContentCapture()} /
     * {@code setImportantForContentCapture(int)} / {@code IMPORTANT_FOR_CONTENT_CAPTURE_*}
     * 都是 API 29 (Q) 新增成员，在 API 24 的 android.jar 里不存在。Java 编译期会
     * 对照 bootclasspath 解析符号，直接调用会编译失败；反射把解析推迟到运行期，
     * 而运行期已被 {@code SDK_INT >= 29} 守卫拦住。
     *
     * @param webView 目标 WebView 实例，不可为 {@code null}
     */
    static void initContentCaptureDefaults(View webView) {
        if (webView == null || Build.VERSION.SDK_INT < API_LEVEL) {
            return;
        }
        try {
            Method getter = View.class.getMethod("getImportantForContentCapture");
            Method setter = View.class.getMethod("setImportantForContentCapture", int.class);
            Object current = getter.invoke(webView);
            if (current instanceof Integer
                    && (Integer) current == IMPORTANT_FOR_CONTENT_CAPTURE_AUTO) {
                setter.invoke(webView, IMPORTANT_FOR_CONTENT_CAPTURE_YES);
            }
        } catch (Exception e) {
            // API 29+ 上这两个方法必然存在；异常说明是定制 ROM 移除了它们，
            // 此时不设默认值不影响 WebView 正常工作，静默降级。
        }
    }
}
