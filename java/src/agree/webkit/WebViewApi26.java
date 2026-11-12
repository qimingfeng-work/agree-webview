// Copyright 2024 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package agree.webkit;

import android.os.Build;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * API 26 (Android 8.0, O) 专属逻辑的隔离容器。
 *
 * <h2>为什么需要这个类</h2>
 *
 * agree 的目标是「一套 AAR 跑 Android 7.0 (API 24) 到 Android 14」。
 * 但 {@code WebView} 里有些 <b>@Override</b> 方法的重写签名引用了 API 26 才引入的类型
 * （{@code android.view.autofill.AutofillValue}、{@code View.onProvideAutofillVirtualStructure}）。
 *
 * <p>ART 在<b>类加载</b>时会校验该类所有方法的签名。若签名引用了不存在的类型，
 * 会抛 {@code NoClassDefFoundError}。这与「方法体内调用不存在的方法」不同：
 * 后者可以通过 {@code if (SDK_INT >= X)} 守住，前者<b>不能</b>——签名在类的常量池里，
 * 校验时机早于任何分支。
 *
 * <p>因此这些 {@code @Override} 方法必须从 {@code WebView} 中<b>彻底移除</b>。
 *
 * <h2>功能影响</h2>
 *
 * 移除 {@code WebView.autofill()} 后，Android 8.0+ 上系统不再通过 WebView 的虚方法
 * 分发自动填充事件，<b>AutofillService（密码管理器）对 WebView 内表单的填充失效</b>。
 *
 * <p>注意：这不影响 HTML {@code <input>} 的基本输入——那由 Chromium 引擎（.so）自己处理。
 * 失效的仅是 Android Autofill Framework 与 WebView 虚拟视图结构的集成。
 *
 * <h2>使用约定</h2>
 *
 * <ul>
 *   <li>本类所有方法必须由调用方先用 {@code Build.VERSION.SDK_INT >= O} 守卫；</li>
 *   <li>{@link RequiresApi} 仅用于 Lint 静态检查，不提供运行时保护。</li>
 * </ul>
 */
final class WebViewApi26 {

    /** 对应的 {@code Build.VERSION_CODES.O}（API 24 的 android.jar 里没有该常量）。 */
    static final int API_LEVEL = 26;

    /** {@code View.IMPORTANT_FOR_AUTOFILL_AUTO} 的数值。 */
    private static final int IMPORTANT_FOR_AUTOFILL_AUTO = 0;

    /** {@code View.IMPORTANT_FOR_AUTOFILL_YES} 的数值。 */
    private static final int IMPORTANT_FOR_AUTOFILL_YES = 1;

    /** 工具类，禁止实例化。 */
    private WebViewApi26() {
    }

    /**
     * 设置 WebView 对自动填充的默认重要性。
     *
     * <p>对齐 AOSP 的 {@code WebView} 构造器行为：WebView 默认参与自动填充，
     * 除非应用开发者通过 XML 属性显式覆盖。
     *
     * <p><b>为什么用反射</b>：本类虽由调用方用 {@code SDK_INT >= 26} 守卫，
     * 但 Java <b>编译期</b>仍会对照 {@code -bootclasspath android-24.jar} 解析符号，
     * 直接写 {@code view.getImportantForAutofill()} 在 API 24 上直接编译失败。
     * 反射把符号解析推迟到运行期，而运行期已被版本守卫拦住。
     *
     * <p>等价于 AOSP 的：
     * <pre>
     *   if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
     *       setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
     *   }
     * </pre>
     *
     * @param webView 目标 View 实例，不可为 {@code null}
     */
    static void initAutofillDefaults(View webView) {
        if (webView == null || Build.VERSION.SDK_INT < API_LEVEL) {
            return;
        }
        try {
            Method getter = View.class.getMethod("getImportantForAutofill");
            Method setter = View.class.getMethod("setImportantForAutofill", int.class);
            Object current = getter.invoke(webView);
            if (current instanceof Integer && (Integer) current == IMPORTANT_FOR_AUTOFILL_AUTO) {
                setter.invoke(webView, IMPORTANT_FOR_AUTOFILL_YES);
            }
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException e) {
            // API 26+ 上这两个方法必然存在；走到这里说明是定制 ROM 移除了它们。
            // 此时不设默认值也不影响 WebView 正常工作，静默降级即可。
        }
    }
}
