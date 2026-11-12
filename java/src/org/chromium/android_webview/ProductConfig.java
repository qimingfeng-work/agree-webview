// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.android_webview;
/**
 *  Product configuration. Generated on a per-target basis.
 *
 *  <p><b>本文件是 AAR 场景的 ProductConfig 最终版</b>：APK 场景下由构建在 APK 层
 *  生成最终版，AAR 场景没有 APK 层，直接用本 stub。字段必须与
 *  {@code build/android/java/templates/ProductConfig.template} 生成的版本一致，
 *  否则 glue 层引用（如 {@code ProductConfig.IS_BUNDLE}）会在运行期抛
 *  {@code NoSuchFieldError}——树内生成的版本被 jar_excluded_patterns 排除，
 *  编译期用生成的、运行期用本 stub，两者字段不一致就会炸。
 */
public class ProductConfig {
    // Sorted list of locales that have an uncompressed .pak within assets.
    // Stored as an array because AssetManager.list() is slow.
    public static final String[] LOCALES = {"af","am","ar","as","az","be","bg","bn","bs","ca","cs","da","de","el","en-GB","en-US","es","es-419","et","eu","fa","fi","fil","fr","fr-CA","gl","gu","he","hi","hr","hu","hy","id","is","it","ja","ka","kk","km","kn","ko","ky","lo","lt","lv","mk","ml","mn","mr","ms","my","nb","ne","nl","or","pa","pl","pt-BR","pt-PT","ro","ru","si","sk","sl","sq","sr","sr-Latn","sv","sw","ta","te","th","tr","uk","ur","uz","vi","zh-CN","zh-HK","zh-TW","zu"};
   public static boolean USE_CHROMIUM_LINKER = false;
   public static boolean IS_BUNDLE = false;
}
