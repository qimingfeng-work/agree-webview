// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.build;
/**
 *  Build configuration. Generated on a per-target basis.
 */
public class BuildConfig {
    public static boolean ENABLE_ASSERTS;
    public static boolean IS_UBSAN;
    public static boolean IS_CHROME_BRANDED;
    // The ID of the android string resource that stores the product version.
    // This layer of indirection is necessary to make the resource dependency
    // optional for android_apk targets/base_java (ex. for cronet).
    public static int R_STRING_PRODUCT_VERSION;
    // Minimum SDK Version supported by this apk.
    // M119 基线：内核 .so 的 .note.android.ident = 0x18 (24)，与
    // build/config/android/config.gni 的 default_min_sdk_version = 24 一致。
    // （原 stub 写死 26 是原作者 API 26 AAR 的残留，与本构建不符。）
    public static int MIN_SDK_VERSION = 24;
    // Value of android:versionCode.
    public static long VERSION_CODE = 20261111;
    public static boolean IS_BUNDLE;
    public static boolean IS_INCREMENTAL_INSTALL;
    public static boolean IS_FOR_TEST = false;
    public static boolean IS_CRONET_BUILD;
    public static boolean WRITE_CLANG_PROFILING_DATA;
    public static boolean ENABLE_DEBUG_LOGS = true;
    public static String[] APK_ASSETS_SUFFIXED_LIST = {};
    public static String APK_ASSETS_SUFFIX = null;
    // Enable features that are more typically available on desktop.
    public static boolean IS_DESKTOP_ANDROID;
    // Controls whether or not StrictModeContext is a no-op.
    public static boolean DISABLE_STRICT_MODE_CONTEXT;
}
