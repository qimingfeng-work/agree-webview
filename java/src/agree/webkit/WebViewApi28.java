// Copyright 2024 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package agree.webkit;

import android.os.Build;

/**
 * API 28 (Android 9.0, P) 专属逻辑的隔离容器。
 *
 * <h2>为什么需要这个类</h2>
 *
 * {@code android.view.textclassifier.TextClassifier} 是 API 28 引入的类型。
 * agree 的 {@code WebView} 有两个公开方法引用它：
 *
 * <pre>
 *   public void setTextClassifier(TextClassifier textClassifier)
 *   public TextClassifier getTextClassifier()
 * </pre>
 *
 * 在 API 24~27 上该类不存在，{@code WebView} 类加载时校验方法签名会抛
 * {@code NoClassDefFoundError}。因此这两个方法必须从 {@code WebView} 中移除签名里的
 * 强类型，改为 {@code Object}，真正的强类型调用下沉到本类。
 *
 * <h2>为什么要保持 {@code Object} 而不是直接删方法</h2>
 *
 * 直接删除会让 API 28+ 设备上的宿主 App 失去这些能力，属于<b>功能退化</b>。
 * 改为 {@code Object} 后：
 *
 * <ul>
 *   <li>Java 宿主：{@code setTextClassifier(TextClassifier.NO_OP)} 自动向上转型，<b>源码零改动</b>；</li>
 *   <li>Kotlin 宿主：{@code Object} 形参接受任意值，<b>源码零改动</b>；</li>
 *   <li>{@code getTextClassifier()} 返回值由 {@code TextClassifier} 变为 {@code Object}，
 *       宿主若显式接收为 {@code TextClassifier} 需补一次强制类型转换。</li>
 * </ul>
 *
 * <h2>使用约定</h2>
 *
 * <ul>
 *   <li>本类所有方法必须由调用方先用 {@code Build.VERSION.SDK_INT >= P} 守卫；</li>
 *   <li>传入类型不是 {@code TextClassifier} 时，在 API 28+ 上会抛
 *       {@code ClassCastException}——这是放弃编译期类型检查后必须接受的代价。</li>
 * </ul>
 */
final class WebViewApi28 {

    /** 对应的 {@code Build.VERSION_CODES.P}（API 24 的 android.jar 里没有该常量）。 */
    static final int API_LEVEL = 28;

    /** 工具类，禁止实例化。 */
    private WebViewApi28() {
    }

    /**
     * 转发 {@code WebView.setTextClassifier} 到 provider 层。
     *
     * <p>provider 层的 {@code setTextClassifier} 已随 API 24 兼容性改造把形参放宽为
     * {@code Object}，因此这里<b>不需要</b>（也无法）强制转型——{@code TextClassifier}
     * 类型本身在 API 24 的 android.jar 里不存在，任何显式引用都会编译失败。
     *
     * <p>类型正确性由调用链保证：宿主在 API 28+ 上只会传 {@code TextClassifier}。
     * 若传入其他类型，provider 的实现在使用时会抛 {@code ClassCastException}。
     *
     * @param provider   WebView 内部持有 {@code WebViewProvider} 实例
     * @param classifier 宿主传入的 TextClassifier 实例
     */
    static void setTextClassifier(WebViewProvider provider, Object classifier) {
        provider.setTextClassifier(classifier);
    }

    /**
     * 转发 {@code WebView.getTextClassifier} 到 provider 层。
     *
     * @param provider WebView 内部持有 {@code WebViewProvider} 实例
     * @return 当前的 TextClassifier 实例（以 Object 形式返回），可能为 {@code null}
     */
    static Object getTextClassifier(WebViewProvider provider) {
        return provider.getTextClassifier();
    }
}
